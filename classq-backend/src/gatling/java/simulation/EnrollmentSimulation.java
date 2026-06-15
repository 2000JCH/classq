package simulation;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import redis.clients.jedis.Jedis;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class EnrollmentSimulation extends Simulation {

    // 부하 테스트 설정값 — 숫자만 바꾸면 됨
    private static final int USER_COUNT = 300;
    private static final long COURSE_ID = 3L;   // UI/UX (정원 30명)
    private static final int CAPACITY = 30;
    private static final String BASE_URL = "http://localhost:8080";
    private static final String DB_URL = "jdbc:mysql://localhost:3306/classq";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "root1234";

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json");

    // 로그인 → 수강신청 (BCrypt 제외 — 순수 수강신청 성능만 측정)
    ScenarioBuilder enrollmentRush = scenario("수강신청 폭주")
            .exec(http("로그인")
                    .post("/api/v1/auth/login")
                    .body(StringBody(session ->
                            """
                            {
                                "email": "loadtest%d@test.com",
                                "password": "Loadtest1!"
                            }
                            """.formatted(session.userId())
                    ))
                    .check(
                            status().is(200),
                            jsonPath("$.accessToken").saveAs("accessToken")
                    )
            )
            .exec(http("수강신청")
                    .post("/api/v1/enrollments")
                    .header("Authorization", session -> "Bearer " + session.getString("accessToken"))
                    .body(StringBody("""
                            {"courseId": %d}
                            """.formatted(COURSE_ID)))
                    // 201: 성공 / 400: 마감(ENROLLMENT_CLOSED) / 409: 이미 신청됨
                    .check(status().in(201, 400, 409))
            );

    {
        setUp(
                enrollmentRush.injectOpen(atOnceUsers(USER_COUNT))
        ).protocols(httpProtocol);
    }

    @Override
    public void before() {
        // 1. 테스트 계정 생성 (이미 있으면 409로 통과)
        System.out.println("테스트 계정 " + USER_COUNT + "명 준비 중...");
        HttpClient client = HttpClient.newHttpClient();
        for (int i = 1; i <= USER_COUNT; i++) {
            String body = """
                    {
                        "email": "loadtest%d@test.com",
                        "password": "Loadtest1!",
                        "role": "STUDENT",
                        "name": "부하테스트%d",
                        "departmentId": 1,
                        "grade": 1
                    }
                    """.formatted(i, i);
            try {
                client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(BASE_URL + "/api/v1/auth/signup"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );
            } catch (Exception e) {
                System.out.println("계정 생성 실패 (loadtest" + i + "): " + e.getMessage());
            }
        }

        // 2. 이전 테스트 enrollment 데이터 초기화 (MySQL soft delete)
        System.out.println("이전 테스트 enrollment 데이터 초기화 중...");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    DELETE FROM enrollment
                    WHERE course_id = %d
                      AND student_id IN (
                          SELECT id FROM student WHERE name LIKE '부하테스트%%'
                      )
                    """.formatted(COURSE_ID));
        } catch (Exception e) {
            System.out.println("MySQL 초기화 실패: " + e.getMessage());
        }

        // 3. Redis 초기화 (잔여석 + 부하테스트 학생 시간표/학점 캐시)
        System.out.println("Redis 초기화 중...");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT a.id FROM account a JOIN student s ON s.account_id = a.id WHERE s.name LIKE '부하테스트%'");
             Jedis jedis = new Jedis("localhost", 6379)) {

            jedis.set("enrollment:course:" + COURSE_ID, String.valueOf(CAPACITY));

            while (rs.next()) {
                long accountId = rs.getLong(1);
                jedis.del("schedule:student:" + accountId);
                jedis.del("credits:student:" + accountId);
            }
        } catch (Exception e) {
            System.out.println("Redis 초기화 실패: " + e.getMessage());
        }

        System.out.println("준비 완료. 수강신청 폭주 시작...");
    }
}