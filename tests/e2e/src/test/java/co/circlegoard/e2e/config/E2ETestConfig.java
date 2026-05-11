package co.circlegoard.e2e.config;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;

import static io.restassured.RestAssured.given;

public abstract class E2ETestConfig {

    @BeforeAll
    static void configure() {
        RestAssured.baseURI = System.getProperty("gateway.url", "http://localhost:8080");
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    protected static String obtainToken(String username, String password) {
        // Auth login always targets the auth-service directly (gateway does not proxy /api/v1/auth/login)
        String authBase = System.getProperty("auth.url", RestAssured.baseURI);
        Response response = given()
                .baseUri(authBase)
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .post("/api/v1/auth/login");

        if (response.statusCode() == 200) {
            return response.jsonPath().getString("token");
        }
        return null;
    }

    protected static String adminToken() {
        return obtainToken(
                System.getProperty("test.admin.user", "super_admin"),
                System.getProperty("test.admin.password", "password")
        );
    }
}
