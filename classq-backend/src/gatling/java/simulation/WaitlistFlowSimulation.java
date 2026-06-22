package simulation;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * 시나리오: 대기자 전체 플로우
 *
 * 300명이 동시에 아래 순서로 요청을 보낸다.
 *   1. 로그인
 *   2. 강의 목록 조회
 *   3. 대기자 등록 (UI/UX, course_id=3 — 정원 소진 상태)
 *   4. 대기자 취소 (등록 성공한 30명만)
 *   5. 대기자 재등록 (취소로 복구된 슬롯에 재등록)
 *
 * 측정 목적:
 *   - 대기자 등록 폭주 시 Redisson 분산 락 직렬화 응답시간
 *   - rank 정확도 (DB 확인으로 중복 0건 검증)
 *   - 대기자 취소 구간 처리량
 *
 * before() 설정:
 *   - 300명 계정 생성 (이미 존재하면 409 무시)
 *   - Redis enrollment:course:3 = 0 (정원 소진 → 대기자 등록 유도)
 *   - Redis waitlist:course:3 = 30 (대기 슬롯 30개)
 *   - Redisson 락 키 제거 (이전 테스트 잔여 락 해제)
 *   - 이전 테스트 학생 캐시(credits, schedule) 초기화
 */
public class WaitlistFlowSimulation extends Simulation {

    private static final String BASE_URL = "http://localhost:8080";
    private static final int USER_COUNT = 300;
    private static final int COURSE_ID = 3;         // UI/UX
    private static final int WAITLIST_LIMIT = 30;
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
        System.out.println("=== [WaitlistFlow] 계정 생성 및 Redis 초기화 시작 ===");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("DELETE FROM waitlist WHERE course_id = ?")) {
            ps.setInt(1, COURSE_ID);
            int rows = ps.executeUpdate();
            System.out.println("[WaitlistFlow] DB waitlist 정리 완료: " + rows + "건 삭제");
        } catch (Exception e) {
            throw new IllegalStateException("[WaitlistFlow] DB 정리 실패 — 테스트 중단: " + e.getMessage(), e);
        }

        HttpClient client = HttpClient.newHttpClient();

        for (int i = 1; i <= USER_COUNT; i++) {
            String body = String.format(
                "{\"email\":\"loadtest%d@test.com\",\"password\":\"Loadtest1!\",\"role\":\"STUDENT\",\"name\":\"부하테스트%d\",\"departmentId\":1,\"grade\":1}",
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
        System.out.println("[WaitlistFlow] 회원가입 완료");

        try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
            // 정원 소진 상태 → 모두 대기자 등록으로 유도
            jedis.set("enrollment:course:" + COURSE_ID, "0");
            jedis.set("waitlist:course:" + COURSE_ID, String.valueOf(WAITLIST_LIMIT));

            // ZSET 초기화 (이전 테스트 잔여 순번 데이터 제거)
            jedis.del("waitlist:zset:course:" + COURSE_ID);

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
        System.out.println("[WaitlistFlow] Redis 초기화 완료");
    }

    ScenarioBuilder waitlistFlow = scenario("대기자 전체 플로우")
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
        // 3. 대기자 등록 (30명 성공, 270명 409 WAITLIST_CLOSED)
        .exec(http("3. 대기자 등록")
            .post("/api/v1/waitlists")
            .header("Authorization", session -> "Bearer " + session.getString("token"))
            .body(StringBody("{\"courseId\":" + COURSE_ID + "}"))
            .check(status().in(201, 409))
            .check(status().saveAs("waitStatus"))
            .check(jsonPath("$.waitlistId").optional().saveAs("waitlistId"))
        )
        // 4. 대기자 취소 + 재등록 (등록 성공한 30명만)
        .doIf(session -> session.getInt("waitStatus") == 201).then(
            exec(http("4. 대기자 취소")
                .delete(session -> "/api/v1/waitlists/" + session.getString("waitlistId"))
                .header("Authorization", session -> "Bearer " + session.getString("token"))
                .check(status().in(200, 204))
            )
            // 5. 대기자 재등록 (취소로 복구된 슬롯에 재등록)
            .exec(http("5. 대기자 재등록")
                .post("/api/v1/waitlists")
                .header("Authorization", session -> "Bearer " + session.getString("token"))
                .body(StringBody("{\"courseId\":" + COURSE_ID + "}"))
                .check(status().in(201, 409))
            )
        );

    {
        setUp(waitlistFlow.injectOpen(atOnceUsers(USER_COUNT)))
            .protocols(httpProtocol);
    }
}
