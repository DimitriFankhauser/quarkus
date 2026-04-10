package io.quarkus.it.bouncycastle;

import static org.awaitility.Awaitility.given;
import static org.hamcrest.Matchers.containsString;

import java.net.URL;

import org.jboss.logging.Logger;
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
                //without this the restAssured client doesn't "trust" the server
                .setKeyStore("client-keystore.jks", "password")
                .setTrustStore("client-truststore.jks", "secret")
                .setPort(url.getPort())
                .build();
        RestAssured.given()
                .spec(spec)
                .when()
                .get("/jsse/listProviders")
                .then()
                .statusCode(200)
                .body(containsString("SunJSSE"));
    }
}
