package simulation;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import org.mindrot.jbcrypt.BCrypt;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * 스트레스 테스트 — c6i.large 스펙 기준 최대 동시 접속 한계 탐색
 *
 * 목적:
 *   CPU 2 / Memory 4g(c6i.large 기준) 제약 환경에서 수강신청 전체 플로우를
 *   몇 명 동시 접속까지 처리할 수 있는지 Breaking point를 찾는다.
 *
 * 시나리오:
 *   로그인 → 강의목록 → 수강신청 → 3초 대기 → 내 수강신청 조회 → 취소 → 수강 변경
 *
 * 주입 전략: incrementUsersPerSec
 *   10/s → 20/s → 30/s → 40/s → 50/s (각 30초 유지, 5초 램프업)
 *   총 주입 인원 약 5100명 → USER_COUNT = 5500 (버퍼 포함)
 *
 * 계정 생성:
 *   HTTP 회원가입 대신 JDBC 배치 INSERT — 5500개 계정을 수초 내 생성
 *   비밀번호는 BCrypt 해싱 1회 후 전체 계정에 동일 적용
 */
public class StressTestSimulation extends Simulation {

    private static final String BASE_URL = "http://localhost:8080";
    private static final int USER_COUNT = 5500;
    private static final int ENROLL_COURSE_ID = 3;
    private static final int CHANGE_COURSE_ID = 4;
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final String DB_URL = System.getenv().getOrDefault("GATLING_DB_URL", "jdbc:mysql://localhost:3306/classq?serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
    private static final String DB_USER = System.getenv().getOrDefault("GATLING_DB_USER", "root");
    private static final String DB_PASS = System.getenv().getOrDefault("GATLING_DB_PASS", "root1234");

    private final Iterator<Map<String, Object>> userFeeder =
        Stream.iterate(1, i -> i + 1)
            .limit(USER_COUNT)
            .map(i -> Collections.<String, Object>singletonMap("userIndex", i))
            .iterator();

    HttpProtocolBuilder httpProtocol = http
        .baseUrl(BASE_URL)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json");

    @Override
    public void before() {
        System.out.println("=== [StressTest] 초기화 시작 ===");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

            // enrollment 정리
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM enrollment WHERE course_id IN (?, ?)")) {
                ps.setInt(1, ENROLL_COURSE_ID);
                ps.setInt(2, CHANGE_COURSE_ID);
                System.out.println("[StressTest] enrollment 정리: " + ps.executeUpdate() + "건 삭제");
            }

            // BCrypt 해싱 1회 (모든 계정에 동일 적용)
            String hashedPassword = BCrypt.hashpw("Loadtest1!", BCrypt.gensalt(8));

            // account 배치 INSERT
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO account (email, password, role, status, created_at) " +
                    "VALUES (?, ?, 'STUDENT', 'ACTIVE', NOW())")) {
                for (int i = 1; i <= USER_COUNT; i++) {
                    ps.setString(1, "loadtest" + i + "@test.com");
                    ps.setString(2, hashedPassword);
                    ps.addBatch();
                    if (i % 500 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }

            // student 배치 INSERT (account email로 account_id 서브쿼리)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO student (account_id, name, department_id, grade, created_at) " +
                    "SELECT a.id, ?, 1, 1, NOW() FROM account a WHERE a.email = ?")) {
                for (int i = 1; i <= USER_COUNT; i++) {
                    ps.setString(1, "부하테스트" + i);
                    ps.setString(2, "loadtest" + i + "@test.com");
                    ps.addBatch();
                    if (i % 500 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }

            System.out.println("[StressTest] 계정 " + USER_COUNT + "개 생성 완료");

        } catch (Exception e) {
            throw new IllegalStateException("[StressTest] DB 초기화 실패: " + e.getMessage(), e);
        }

        // Redis 초기화
        try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
            jedis.set("enrollment:course:" + ENROLL_COURSE_ID, "9999");
            jedis.set("enrollment:course:" + CHANGE_COURSE_ID, "9999");

            String cursor = "0";
            do {
                var result = jedis.scan(cursor, new ScanParams().match("credits:student:*").count(200));
                cursor = result.getCursor();
                if (!result.getResult().isEmpty()) jedis.del(result.getResult().toArray(new String[0]));
            } while (!cursor.equals("0"));

            cursor = "0";
            do {
                var result = jedis.scan(cursor, new ScanParams().match("schedule:student:*").count(200));
                cursor = result.getCursor();
                if (!result.getResult().isEmpty()) jedis.del(result.getResult().toArray(new String[0]));
            } while (!cursor.equals("0"));
        }
        System.out.println("[StressTest] Redis 초기화 완료");
    }

    ScenarioBuilder stressFlow = scenario("스트레스 테스트 — 수강신청 전체 플로우")
        .feed(userFeeder)
        .exec(http("1. 로그인")
            .post("/api/v1/auth/login")
            .body(StringBody(session ->
                "{\"email\":\"loadtest" + session.getInt("userIndex") + "@test.com\",\"password\":\"Loadtest1!\"}"
            ))
            .check(status().is(200))
            .check(jsonPath("$.accessToken").saveAs("token"))
        )
        .exec(http("2. 강의 목록 조회")
            .get("/api/v1/courses")
            .header("Authorization", session -> "Bearer " + session.getString("token"))
        )
        .exec(http("3. 수강신청")
            .post("/api/v1/enrollments")
            .header("Authorization", session -> "Bearer " + session.getString("token"))
            .body(StringBody("{\"courseId\":" + ENROLL_COURSE_ID + "}"))
            .check(status().in(201, 400, 409))
            .check(status().saveAs("enrollStatus"))
        )
        .doIf(session -> session.getInt("enrollStatus") == 201).then(
            pause(Duration.ofSeconds(3))
            .exec(http("4. 내 수강신청 조회")
                .get("/api/v1/enrollments/me")
                .header("Authorization", session -> "Bearer " + session.getString("token"))
                .check(
                    jsonPath("$[?(@.courseId==" + ENROLL_COURSE_ID + " && @.status=='COMPLETED')].enrollmentId")
                        .optional()
                        .saveAs("enrollmentId")
                )
            )
            .doIf(session -> session.contains("enrollmentId")).then(
                exec(http("5. 수강신청 취소")
                    .delete(session -> "/api/v1/enrollments/" + session.getString("enrollmentId"))
                    .header("Authorization", session -> "Bearer " + session.getString("token"))
                    .check(status().in(200, 204))
                )
                .exec(http("6. 수강 변경")
                    .post("/api/v1/enrollments")
                    .header("Authorization", session -> "Bearer " + session.getString("token"))
                    .body(StringBody("{\"courseId\":" + CHANGE_COURSE_ID + "}"))
                    .check(status().in(201, 400, 409))
                )
            )
        );

    {
        setUp(
            stressFlow.injectOpen(
                incrementUsersPerSec(10)
                    .times(5)
                    .eachLevelLasting(Duration.ofSeconds(30))
                    .startingFrom(10)
                    .separatedByRampsLasting(Duration.ofSeconds(5))
            )
        ).protocols(httpProtocol);
    }
}