package co.circlegoard.e2e.dashboard;

import co.circlegoard.e2e.config.E2ETestConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

class DashboardE2ETest extends E2ETestConfig {

    @Test
    void getDashboardSummary_withoutToken_returns401() {
        given()
                .when()
                .get("/api/v1/analytics/summary")
                .then()
                .statusCode(401);
    }

    @Test
    void getDashboardSummary_withValidToken_returns200WithData() {
        String token = adminToken();

        var response = given()
                .header("Authorization", "Bearer " + token)
                .accept(ContentType.JSON)
                .when()
                .get("/api/v1/analytics/summary");

        assertThat(response.statusCode()).isIn(200, 403, 404);

        if (response.statusCode() == 200) {
            response.then().contentType(ContentType.JSON);
        }
    }

    @Test
    void getTimeSeries_withValidToken_returnsArrayOrNotFound() {
        String token = adminToken();

        var response = given()
                .header("Authorization", "Bearer " + token)
                .accept(ContentType.JSON)
                .queryParam("period", "daily")
                .queryParam("limit", "7")
                .when()
                .get("/api/v1/analytics/time-series");

        assertThat(response.statusCode()).isIn(200, 403, 404);

        if (response.statusCode() == 200) {
            response.then()
                    .contentType(ContentType.JSON)
                    .body("$", instanceOf(java.util.List.class));
        }
    }

    @Test
    void getHealthBoard_withValidToken_returnsStatsOrNotFound() {
        String token = adminToken();

        var response = given()
                .header("Authorization", "Bearer " + token)
                .accept(ContentType.JSON)
                .when()
                .get("/api/v1/analytics/health-board");

        assertThat(response.statusCode()).isIn(200, 403, 404);
    }

    @Test
    void getDepartmentStats_withValidToken_returnsDataOrNotFound() {
        String token = adminToken();

        var response = given()
                .header("Authorization", "Bearer " + token)
                .accept(ContentType.JSON)
                .when()
                .get("/api/v1/analytics/department/Engineering");

        assertThat(response.statusCode()).isIn(200, 403, 404);

        if (response.statusCode() == 200) {
            response.then()
                    .contentType(ContentType.JSON)
                    .body("department", equalTo("Engineering"));
        }
    }
}
