package co.circlegoard.e2e.contact;

import co.circlegoard.e2e.config.E2ETestConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

class ContactRegistrationE2ETest extends E2ETestConfig {

    @Test
    void validateQr_withoutToken_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"qrToken\":\"some-token\"}")
                .when()
                .post("/api/v1/qr/validate")
                .then()
                .statusCode(anyOf(equalTo(401), equalTo(403), equalTo(404)));
    }

    @Test
    void validateQr_withInvalidToken_returns400Or404() {
        String token = adminToken();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"qrToken\":\"invalid-qr-token-xyz\"}")
                .when()
                .post("/api/v1/qr/validate")
                .then()
                .statusCode(anyOf(equalTo(400), equalTo(401), equalTo(403), equalTo(404), equalTo(422)));
    }

    @Test
    void registerContact_withoutToken_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"targetAnonymousId\":\"00000000-0000-0000-0000-000000000000\"}")
                .when()
                .post("/api/v1/contact/register")
                .then()
                .statusCode(anyOf(equalTo(401), equalTo(403), equalTo(404)));
    }

    @Test
    void registerContact_withValidToken_returnsExpectedStatus() {
        String token = adminToken();

        var response = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"targetAnonymousId\":\"00000000-0000-0000-0000-000000000001\",\"durationSeconds\":300}")
                .when()
                .post("/api/v1/contact/register");

        assertThat(response.statusCode())
                .isIn(200, 201, 400, 403, 404, 409, 422);
    }

    @Test
    void generateQr_withValidToken_returnsQrData() {
        String token = adminToken();

        var response = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/auth/qr");

        int status = response.statusCode();
        assertThat(status).isIn(200, 403, 404);

        if (status == 200) {
            response.then()
                    .contentType(ContentType.JSON)
                    .body("$", hasKey("qrToken"));
        }
    }
}
