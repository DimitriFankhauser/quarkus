package io.quarkus.it.bouncycastle;

import static org.awaitility.Awaitility.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.awaitility.core.ThrowingRunnable;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;

@QuarkusTest
public class BouncyCastleJsseTestCase {

    static final Logger LOG = Logger.getLogger(BouncyCastleJsseTestCase.class);

    @TestHTTPResource(tls = true)
    URL url;

    @Test
    public void testListProviders() {
        doTestListProviders();
    }

    protected void doTestListProviders() {
        RequestSpecification spec = new RequestSpecBuilder()
                .setBaseUri(String.format("%s://%s", url.getProtocol(), url.getHost()))
                .setPort(url.getPort())
                .build();
        RestAssured.given()
                .spec(spec)
                .when()
                .get("/jsse/listProviders")
                .then()
                .statusCode(200)
                .body(startsWith("Identity: CN=client"), containsString("SunJSSE"));
    }
}
