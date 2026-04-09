package io.quarkus.it.nullkeystore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * CDI service for RSA encryption and decryption using a PKCS#11 HSM.
 * <p>
 * All HSM-specific properties are read from {@code application.properties}:
 * <ul>
 * <li>{@code hsm.user-pin} – PIN used to unlock the HSM token and retrieve private keys</li>
 * <li>{@code hsm.key-alias} – alias of the RSA key pair stored in the HSM</li>
 * </ul>
 * The SunPKCS11 provider is configured via the {@code pkcs11.cfg} classpath resource.
 * Custom properties ({@code userPin}, {@code keyAlias}) are stripped from that file before
 * it is passed to the JCA so that only standard SunPKCS11 keywords remain.
 */
@ApplicationScoped
public class HsmCryptoService {

    private static final Logger LOG = Logger.getLogger(HsmCryptoService.class);

    // Custom keys that Quarkus reads from pkcs11.cfg but that SunPKCS11 does not understand.
    private static final List<String> CUSTOM_PKCS11_KEYS = List.of("userPin", "keyAlias");

    @ConfigProperty(name = "hsm.user-pin")
    String userPin;

    @ConfigProperty(name = "hsm.key-alias")
    String keyAlias;

    private KeyStore hsmKeyStore;

    @PostConstruct
    void setup() {
        try {
            registerPkcs11Provider();
            hsmKeyStore = KeyStore.getInstance("PKCS11");
            hsmKeyStore.load(null, userPin.toCharArray());
        } catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException e) {
            LOG.error("Failed to initialise HSM KeyStore", e);
        }
    }

    public String encrypt(String message) {
        try {
            RSAPublicKey publicKey = (RSAPublicKey) hsmKeyStore.getCertificate(keyAlias).getPublicKey();
            Cipher encryptCipher = Cipher.getInstance("RSA");
            encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedBytes = encryptCipher.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException | KeyStoreException e) {
            LOG.error("Encryption failed", e);
            return "";
        }
    }

    public String decrypt(String ciphertext) {
        try {
            PrivateKey privateKey = (PrivateKey) hsmKeyStore.getKey(keyAlias, userPin.toCharArray());
            Cipher decryptCipher = Cipher.getInstance("RSA", hsmKeyStore.getProvider());
            decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedBytes = decryptCipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | IllegalBlockSizeException
                | BadPaddingException | UnrecoverableKeyException | KeyStoreException | InvalidKeyException e) {
            LOG.error("Decryption failed", e);
            return "";
        }
    }

    /**
     * Reads {@code pkcs11.cfg} from the classpath, strips the custom Quarkus properties
     * ({@code userPin}, {@code keyAlias}) that SunPKCS11 does not accept, and registers the
     * resulting provider under the name {@code SunPKCS11-<name>} if it is not already present.
     */
    private void registerPkcs11Provider() throws IOException {
        try (InputStream cfgStream = getClass().getResourceAsStream("/pkcs11.cfg")) {
            if (cfgStream == null) {
                LOG.warn("pkcs11.cfg not found on the classpath – skipping SunPKCS11 provider registration");
                return;
            }
            String cfgContent = new String(cfgStream.readAllBytes(), StandardCharsets.UTF_8);
            String providerName = parsePkcs11Property(cfgContent, "name");
            String sunPkcs11Cfg = stripCustomPkcs11Properties(cfgContent);

            String registeredName = "SunPKCS11-" + providerName;
            if (Security.getProvider(registeredName) == null) {
                Provider sunPkcs11 = Security.getProvider("SunPKCS11");
                Provider pkcsProvider = sunPkcs11.configure("--\n" + sunPkcs11Cfg);
                Security.addProvider(pkcsProvider);
                LOG.info("Registered PKCS11 provider: " + registeredName);
            }
        }
    }

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
}
