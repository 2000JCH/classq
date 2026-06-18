package simulation;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * 시나리오: 수강신청 전체 플로우
 *
 * 300명이 동시에 아래 순서로 요청을 보낸다.
 *   1. 로그인
 *   2. 강의 목록 조회
 *   3. 수강신청 (UI/UX, course_id=3)
 *   4. 내 수강신청 목록 조회 → enrollmentId 추출 (Kafka 처리 대기 3초 후)
 *   5. 수강신청 취소
 *   6. 수강 변경 — 다른 강의(리더쉽, course_id=4) 재신청
 *
 * 측정 목적:
 *   - 로그인 + 수강신청 + 취소 + 재신청이 이어지는 실제 사용자 여정 전체 응답시간
 *   - Redis-only 동기 구간 처리량
 *   - HikariCP 커넥션 풀 압박 (로그인 BCrypt + 조회 쿼리 동시 발생)
 *
 * before() 설정:
 *   - 300명 계정 생성 (이미 존재하면 409 무시)
 *   - Redis enrollment:course:3 = 300, enrollment:course:4 = 300 (전원 수강신청 가능)
 *   - 이전 테스트 학생 캐시(credits, schedule) 초기화
 */
public class EnrollmentFlowSimulation extends Simulation {

    private static final String BASE_URL = "http://localhost:8080";
    private static final int USER_COUNT = 300;
    private static final int ENROLL_COURSE_ID = 3;   // UI/UX
    private static final int CHANGE_COURSE_ID = 4;   // 리더쉽
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;

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
        System.out.println("=== [EnrollmentFlow] 계정 생성 및 Redis 초기화 시작 ===");

        HttpClient client = HttpClient.newHttpClient();

        for (int i = 1; i <= USER_COUNT; i++) {
            String body = String.format(
                "{\"email\":\"loadtest%d@test.com\",\"password\":\"Loadtest1!\",\"name\":\"부하테스트%d\",\"departmentId\":1,\"grade\":1}",
                i, i
            );
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/v1/auth/signup"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
                client.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {}
        }
        System.out.println("[EnrollmentFlow] 회원가입 완료");

        try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
            jedis.set("enrollment:course:" + ENROLL_COURSE_ID, String.valueOf(USER_COUNT));
            jedis.set("enrollment:course:" + CHANGE_COURSE_ID, String.valueOf(USER_COUNT));

            ScanParams params = new ScanParams().count(200);

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
        System.out.println("[EnrollmentFlow] Redis 초기화 완료");
    }

    ScenarioBuilder enrollmentFlow = scenario("수강신청 전체 플로우")
        .feed(userFeeder)
        // 1. 로그인
        .exec(http("1. 로그인")
            .post("/api/v1/auth/login")
            .body(StringBody(session ->
                "{\"email\":\"loadtest" + session.getInt("userIndex") + "@test.com\",\"password\":\"Loadtest1!\"}"
            ))
            .check(jsonPath("$.accessToken").saveAs("token"))
        )
        // 2. 강의 목록 조회
        .exec(http("2. 강의 목록 조회")
            .get("/api/v1/courses")
            .header("Authorization", session -> "Bearer " + session.getString("token"))
        )
        // 3. 수강신청 (POST는 body 없음 — enrollmentId는 GET /me 에서 추출)
        .exec(http("3. 수강신청")
            .post("/api/v1/enrollments")
            .header("Authorization", session -> "Bearer " + session.getString("token"))
            .body(StringBody("{\"courseId\":" + ENROLL_COURSE_ID + "}"))
            .check(status().in(201, 400, 409))
            .check(status().saveAs("enrollStatus"))
        )
        // Kafka Consumer 비동기 처리 대기 (DB INSERT 완료 후 enrollmentId 조회 가능)
        .doIf(session -> session.getInt("enrollStatus") == 201).then(
            pause(Duration.ofSeconds(3))
            // 4. 내 수강신청 목록 조회 — enrollmentId 추출
            .exec(http("4. 내 수강신청 조회")
                .get("/api/v1/enrollments/me")
                .header("Authorization", session -> "Bearer " + session.getString("token"))
                .check(
                    jsonPath("$[?(@.courseId==" + ENROLL_COURSE_ID + " && @.status=='COMPLETED')].enrollmentId")
                        .optional()
                        .saveAs("enrollmentId")
                )
            )
            // 5. 수강신청 취소 (enrollmentId 조회 성공한 경우만)
            .doIf(session -> session.contains("enrollmentId")).then(
                exec(http("5. 수강신청 취소")
                    .delete(session -> "/api/v1/enrollments/" + session.getString("enrollmentId"))
                    .header("Authorization", session -> "Bearer " + session.getString("token"))
                    .check(status().in(200, 204))
                )
                // 6. 수강 변경 — 다른 강의로 재신청
                .exec(http("6. 수강 변경")
                    .post("/api/v1/enrollments")
                    .header("Authorization", session -> "Bearer " + session.getString("token"))
                    .body(StringBody("{\"courseId\":" + CHANGE_COURSE_ID + "}"))
                    .check(status().in(201, 400, 409))
                )
            )
        );

    {
        setUp(enrollmentFlow.injectOpen(atOnceUsers(USER_COUNT)))
            .protocols(httpProtocol);
    }
}
