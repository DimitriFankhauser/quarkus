package io.quarkus.it.exampleendpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class ExampleEndpointTestCase {

    @Test
    public void testNullKeystoreIsLogged() throws IOException {
        Path logFile = Path.of("target/quarkus.log");
        assertThat(logFile).exists();
        String logContent = Files.readString(logFile);
        // Log format is "%C - %s%n": the TlsUtils class logs this message when the
        // key-store-file sentinel value "null" is detected, confirming the keystore
        // was checked and resolved to null.
        assertThat(logContent).contains("io.quarkus.vertx.http.runtime.options.TlsUtils - Keystore is null");
    }

    @Test
    public void testEndpointReachableOverHttp() {
        RestAssured.given()
                .when()
                .get("/testdimitri/test")
                .then()
                .statusCode(200);
    }
}
