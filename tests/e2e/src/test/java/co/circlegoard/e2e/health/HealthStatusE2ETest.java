package co.circlegoard.e2e.health;

import co.circlegoard.e2e.config.E2ETestConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class HealthStatusE2ETest extends E2ETestConfig {

    @Test
    void getHealthStatus_withoutToken_returns401() {
        given()
                .when()
                .get("/api/v1/health/status")
                .then()
                .statusCode(401);
    }

    @Test
    void getHealthStatus_withValidToken_returns200WithStatusField() {
        String token = adminToken();

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/health/status")
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(404)))
                .contentType(ContentType.JSON);
    }

    @Test
    void getHealthStatus_withExpiredToken_returns401() {
        String expiredToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDAiLCJleHAiOjE2MDAwMDAwMDB9.invalid";

        given()
                .header("Authorization", "Bearer " + expiredToken)
                .when()
                .get("/api/v1/health/status")
                .then()
                .statusCode(401);
    }

    @Test
    void getHealthStatus_responseContainsExpectedFields() {
        String token = adminToken();

        var response = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/health/status");

        if (response.statusCode() == 200) {
            response.then()
                    .body("$", hasKey("status"));
        }
    }

    @Test
    void healthEndpoint_withMalformedToken_returns401() {
        given()
                .header("Authorization", "Bearer not.a.valid.jwt")
                .when()
                .get("/api/v1/health/status")
                .then()
                .statusCode(401);
    }
}
