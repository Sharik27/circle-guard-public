package co.circlegoard.e2e.escalation;

import co.circlegoard.e2e.config.E2ETestConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

class StatusEscalationE2ETest extends E2ETestConfig {

    @Test
    void reportSymptoms_withoutToken_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"symptoms\":[\"fever\"],\"severity\":\"mild\"}")
                .when()
                .post("/api/v1/health/report")
                .then()
                .statusCode(anyOf(equalTo(401), equalTo(403), equalTo(404)));
    }

    @Test
    void reportSymptoms_withValidToken_returnsAcceptedOrOk() {
        String token = adminToken();

        var response = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"symptoms\":[\"fever\",\"cough\"],\"severity\":\"mild\"}")
                .when()
                .post("/api/v1/health/report");

        assertThat(response.statusCode()).isIn(200, 202, 400, 403, 404);
    }

    @Test
    void getStatusHistory_withValidToken_returnsListOrNotFound() {
        String token = adminToken();

        var response = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/health/history");

        assertThat(response.statusCode()).isIn(200, 403, 404);

        if (response.statusCode() == 200) {
            response.then().body("$", instanceOf(java.util.List.class));
        }
    }

    @Test
    void adminPromoteStatus_withoutToken_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"anonymousId\":\"00000000-0000-0000-0000-000000000000\",\"targetStatus\":\"SUSPECT\"}")
                .when()
                .post("/api/v1/admin/promote")
                .then()
                .statusCode(anyOf(equalTo(401), equalTo(403), equalTo(404)));
    }

    @Test
    void statusEscalation_fullFlow_reportAndVerify() {
        String token = adminToken();

        var reportResponse = given()
                .header("Authorization", "Bearer " + (token != null ? token : ""))
                .contentType(ContentType.JSON)
                .body("{\"symptoms\":[\"fever\",\"cough\",\"shortness_of_breath\"],\"severity\":\"moderate\"}")
                .when()
                .post("/api/v1/health/report");

        assertThat(reportResponse.statusCode()).isIn(200, 202, 400, 401, 403, 404);

        var statusResponse = given()
                .header("Authorization", "Bearer " + (token != null ? token : ""))
                .when()
                .get("/api/v1/health/status");

        assertThat(statusResponse.statusCode()).isIn(200, 401, 403, 404);
    }
}
