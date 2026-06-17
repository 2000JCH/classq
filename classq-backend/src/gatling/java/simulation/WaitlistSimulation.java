package simulation;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import redis.clients.jedis.Jedis;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class WaitlistSimulation extends Simulation {

    private static final int USER_COUNT = 300;
    private static final long COURSE_ID = 3L;
    private static final int WAITLIST_LIMIT = 30;
    private static final String BASE_URL = "http://localhost:8080";
    private static final String DB_URL = "jdbc:mysql://localhost:3306/classq";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "root1234";

    private static final List<String> tokens = new ArrayList<>();

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json");

    ScenarioBuilder waitlistRush = scenario("대기자 등록 폭주 - rank 정확도 검증")
            .exec(session -> {
                int idx = (int) (session.userId() - 1);
                return session.set("accessToken", tokens.get(idx));
            })
            .exec(http("대기자 등록")
                    .post("/api/v1/waitlists")
                    .header("Authorization", session -> "Bearer " + session.getString("accessToken"))
                    .body(StringBody("""
                            {"courseId": %d}
                            """.formatted(COURSE_ID)))
                    .check(status().in(201, 409, 500))
            );

    {
        setUp(
                waitlistRush.injectOpen(atOnceUsers(USER_COUNT))
        ).protocols(httpProtocol);
    }

    @Override
    public void before() {
        HttpClient client = HttpClient.newHttpClient();

        // 1. 회원가입 (이미 있으면 409 무시)
        System.out.println("계정 " + USER_COUNT + "명 가입 중...");
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
                client.send(HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/v1/auth/signup"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(), HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                System.out.println("가입 실패 (loadtest" + i + "): " + e.getMessage());
            }
        }

        // 2. 로그인 후 token 수집
        System.out.println("300명 로그인 중...");
        for (int i = 1; i <= USER_COUNT; i++) {
            String loginBody = """
                    {
                        "email": "loadtest%d@test.com",
                        "password": "Loadtest1!"
                    }
                    """.formatted(i);
            try {
                HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                        .build(), HttpResponse.BodyHandlers.ofString());
                String token = response.body()
                        .replaceAll(".*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");
                tokens.add(token);
            } catch (Exception e) {
                System.out.println("로그인 실패 (loadtest" + i + "): " + e.getMessage());
                tokens.add(""); // 인덱스 유지
            }
        }
        System.out.println("토큰 수집 완료: " + tokens.size() + "개");

        // 3. MySQL waitlist 이전 테스트 데이터 삭제 (soft-delete 이력 포함 완전 삭제)
        System.out.println("이전 waitlist 데이터 초기화 중...");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            int deleted = stmt.executeUpdate("""
                    DELETE FROM waitlist
                    WHERE course_id = %d
                      AND student_id IN (
                          SELECT id FROM student WHERE name LIKE '부하테스트%%'
                      )
                    """.formatted(COURSE_ID));
            System.out.println("삭제된 waitlist 행: " + deleted);
        } catch (Exception e) {
            System.out.println("MySQL waitlist 초기화 실패: " + e.getMessage());
        }

        // 4. Redis 초기화
        System.out.println("Redis 초기화 중...");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             Jedis jedis = new Jedis("localhost", 6379)) {

            jedis.set("waitlist:course:" + COURSE_ID, String.valueOf(WAITLIST_LIMIT));
            jedis.set("enrollment:course:" + COURSE_ID, "0"); // 정원 소진 상태 시뮬레이션
            jedis.del("lock:course:" + COURSE_ID);

            ResultSet rs = stmt.executeQuery(
                    "SELECT a.id FROM account a JOIN student s ON s.account_id = a.id WHERE s.name LIKE '부하테스트%'");
            while (rs.next()) {
                long accountId = rs.getLong(1);
                jedis.del("schedule:student:" + accountId);
                jedis.del("credits:student:" + accountId);
            }
        } catch (Exception e) {
            System.out.println("Redis 초기화 실패: " + e.getMessage());
        }

        System.out.println("준비 완료. 대기자 등록 폭주 시작 (300명 동시, 대기 슬롯 " + WAITLIST_LIMIT + "개)...");
    }

    @Override
    public void after() {
        System.out.println("\n===== rank 정확도 검증 =====");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

            // 등록된 대기자 수 확인
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                         SELECT COUNT(*) AS cnt FROM waitlist
                         WHERE course_id = %d AND deleted_at IS NULL
                         """.formatted(COURSE_ID))) {
                if (rs.next()) {
                    System.out.println("등록된 대기자 수: " + rs.getInt("cnt") + " / " + WAITLIST_LIMIT);
                }
            }

            // rank 중복 여부 확인
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                         SELECT `rank`, COUNT(*) AS cnt FROM waitlist
                         WHERE course_id = %d AND deleted_at IS NULL
                         GROUP BY `rank` HAVING COUNT(*) > 1
                         """.formatted(COURSE_ID))) {
                int duplicates = 0;
                while (rs.next()) {
                    System.out.println("rank 중복 발견: rank=" + rs.getInt("rank") + ", count=" + rs.getInt("cnt"));
                    duplicates++;
                }
                if (duplicates == 0) {
                    System.out.println("✅ rank 중복 없음 — Redisson 분산 락 정상 동작");
                } else {
                    System.out.println("❌ rank 중복 " + duplicates + "건 발생");
                }
            }

        } catch (Exception e) {
            System.out.println("rank 검증 실패: " + e.getMessage());
        }
        System.out.println("===========================\n");
    }
}