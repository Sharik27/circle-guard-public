package co.circlegoard.e2e.auth;

import co.circlegoard.e2e.config.E2ETestConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

class AuthE2ETest extends E2ETestConfig {

    @Test
    void login_withValidCredentials_returns200AndJwt() {
        String adminUser = System.getProperty("test.admin.user", "admin");
        String adminPass = System.getProperty("test.admin.password", "password");

        given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + adminUser + "\",\"password\":\"" + adminPass + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("token", not(emptyOrNullString()))
                .body("type", equalTo("Bearer"))
                .body("anonymousId", not(emptyOrNullString()));
    }

    @Test
    void login_withInvalidCredentials_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"nonexistent\",\"password\":\"wrongpass\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    void login_withMissingBody_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(401)));
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() {
        given()
                .when()
                .get("/api/v1/health/status")
                .then()
                .statusCode(401);
    }

    @Test
    void login_tokenIsUsableForAuthenticatedRequest() {
        String token = adminToken();
        assertThat(token).isNotNull();

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/health/status")
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(404)));
    }
}
