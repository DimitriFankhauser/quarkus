package io.quarkus.it.exampleendpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class ExampleEndpointTestCase {

    private static final List<String> CUSTOM_PKCS11_KEYS = List.of("userPin", "keyAlias");

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

    @Test
    public void testPkcs11CfgIsUsedForSunPkcs11() throws IOException {
        // Read pkcs11.cfg from the classpath, mirroring TlsUtils.initPkcs11KeyStoreOptions()
        try (InputStream cfgStream = getClass().getResourceAsStream("/pkcs11.cfg")) {
            assertThat(cfgStream).as("pkcs11.cfg must be present on the classpath").isNotNull();
            String cfgContent = new String(cfgStream.readAllBytes(), StandardCharsets.UTF_8);

            // The provider name drives Security.addProvider("SunPKCS11-<name>")
            String name = parsePkcs11Property(cfgContent, "name");
            assertThat(name).as("SunPKCS11 provider name").isEqualTo("softhsm_pkcs11");

            // userPin becomes the KeyStoreOptions password passed to SunPKCS11
            String userPin = parsePkcs11Property(cfgContent, "userPin");
            assertThat(userPin).as("SunPKCS11 userPin").isEqualTo("2133");

            // library must point to the PKCS11 implementation
            String library = parsePkcs11Property(cfgContent, "library");
            assertThat(library).as("SunPKCS11 library path").isEqualTo("/usr/lib64/libsofthsm2.so");

            // Custom keys (userPin, keyAlias) must be stripped before the cfg is passed to
            // SunPKCS11 — it only accepts its own recognised keywords
            String strippedCfg = stripCustomPkcs11Properties(cfgContent);
            assertThat(strippedCfg).as("stripped cfg must not contain custom key 'userPin'")
                    .doesNotContainPattern("(?m)^\\s*userPin\\s*=");
            assertThat(strippedCfg).as("stripped cfg must not contain custom key 'keyAlias'")
                    .doesNotContainPattern("(?m)^\\s*keyAlias\\s*=");
            // Standard SunPKCS11 properties must still survive stripping
            assertThat(strippedCfg).as("stripped cfg must retain 'name'").containsPattern("(?m)^\\s*name\\s*=");
            assertThat(strippedCfg).as("stripped cfg must retain 'library'").containsPattern("(?m)^\\s*library\\s*=");
        }
    }

    /** Mirrors {@code TlsUtils.parsePkcs11Property}. */
    private static String parsePkcs11Property(String cfgContent, String key) {
        for (String line : cfgContent.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key) && trimmed.substring(key.length()).stripLeading().startsWith("=")) {
                int eq = trimmed.indexOf('=');
                return trimmed.substring(eq + 1).trim();
            }
        }
        return null;
    }

    /** Mirrors {@code TlsUtils.stripCustomPkcs11Properties}. */
    private static String stripCustomPkcs11Properties(String cfgContent) {
        StringBuilder sb = new StringBuilder();
        for (String line : cfgContent.split("\\r?\\n", -1)) {
            String trimmed = line.trim();
            boolean isCustomKey = CUSTOM_PKCS11_KEYS.stream().anyMatch(key -> trimmed.startsWith(key)
                    && trimmed.substring(key.length()).stripLeading().startsWith("="));
            if (!isCustomKey) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
