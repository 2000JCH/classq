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
import java.util.ArrayList;
import java.util.List;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class EnrollmentSimulationV2 extends Simulation {

    private static final int USER_COUNT = 300;
    private static final long COURSE_ID = 3L;
    private static final int CAPACITY = 30;
    private static final String BASE_URL = "http://localhost:8080";
    private static final String DB_URL = "jdbc:mysql://localhost:3306/classq";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "root1234";

    // before()에서 채워지고, 시나리오 실행 시 lazily 참조됨
    private static final List<String> tokens = new ArrayList<>();

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json");

    ScenarioBuilder enrollmentRush = scenario("수강신청 폭주 V2 - 순수 수강신청 구간만 측정")
            .exec(session -> {
                int idx = (int) (session.userId() - 1);
                return session.set("accessToken", tokens.get(idx));
            })
            .exec(http("수강신청")
                    .post("/api/v1/enrollments")
                    .header("Authorization", session -> "Bearer " + session.getString("accessToken"))
                    .body(StringBody("""
                            {"courseId": %d}
                            """.formatted(COURSE_ID)))
                    .check(status().in(201, 400, 409))
            );

    {
        setUp(
                enrollmentRush.injectOpen(atOnceUsers(USER_COUNT))
        ).protocols(httpProtocol);
    }

    @Override
    public void before() {
        HttpClient client = HttpClient.newHttpClient();

        // 1. 회원가입 (이미 있으면 409로 통과)
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
                client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(BASE_URL + "/api/v1/auth/signup"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );
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
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(BASE_URL + "/api/v1/auth/login"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );
                String token = response.body()
                        .replaceAll(".*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");
                tokens.add(token);
            } catch (Exception e) {
                System.out.println("로그인 실패 (loadtest" + i + "): " + e.getMessage());
            }
        }

        System.out.println("토큰 수집 완료: " + tokens.size() + "개");

        // 3. MySQL enrollment 초기화
        System.out.println("이전 테스트 enrollment 초기화 중...");
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

        // 4. Redis 초기화
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

        System.out.println("준비 완료. 순수 수강신청 폭주 시작...");
    }
}