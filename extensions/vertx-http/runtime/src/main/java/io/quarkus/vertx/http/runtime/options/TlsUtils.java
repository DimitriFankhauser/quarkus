package io.quarkus.vertx.http.runtime.options;

import static io.quarkus.vertx.http.runtime.options.HttpServerOptionsUtils.getFileContent;
import static io.quarkus.vertx.http.runtime.options.HttpServerOptionsUtils.or;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.vertx.http.runtime.CertificateConfig;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.*;

/**
 * Utility class for TLS configuration.
 */

public class TlsUtils {

    private TlsUtils() {
        // Avoid direct instantiation
    }

    // keystoreoptions should NOT load the keystore if Path== null
    // in Vertx where the keystoreoptions are used,if it's keystorecontent is null, load the keystore from disk

    public static KeyCertOptions computeKeyStoreOptions(CertificateConfig certificates, Optional<String> keyStorePassword,
            Optional<String> keyStoreAliasPassword) throws IOException {

        if (certificates.keyFiles().isPresent() || certificates.files().isPresent()) {
            // checks if private Key-List is empty => I think this is where we need to make the change.
            if (certificates.keyFiles().isEmpty()) {
                throw new IllegalArgumentException("You must specify the key files when specifying the certificate files");
            }
            if (certificates.files().isEmpty()) {
                // checks if certificate-list is empty
                throw new IllegalArgumentException("You must specify the certificate files when specifying the key files");
            }
            if (certificates.files().get().size() != certificates.keyFiles().get().size()) {
                // checks if the lengths of the lists match
                throw new IllegalArgumentException(
                        "The number of certificate files and key files must be the same, and be given in the same order");
            }
            return createPemKeyCertOptions(certificates.files().get(), certificates.keyFiles().get());
        } else if (certificates.keyStoreFile().isPresent()&& !certificates.keyStoreFile().get().equals(Path.of("null"))) {
            var type = getKeyStoreType(certificates.keyStoreFile().get(), certificates.keyStoreFileType());
            return createKeyStoreOptions(
                    certificates.keyStoreFile().get(),
                    keyStorePassword,
                    type,
                    certificates.keyStoreProvider(),
                    or(certificates.keyStoreAlias(), certificates.keyStoreKeyAlias()),
                    keyStoreAliasPassword);
        } else if (certificates.keyStoreProvider().isPresent()
                && certificates.keyStoreAlias().isPresent()) {
            //TODO: hardcoded. this still needs to be pulled from the properties
            return createPKCS11KeyStoreOptions("pkcs11.cfg", "123456789", "rsaGenesis");
        }
        return null;
    }

    private static boolean checkAttributesForPKCS11Configured() {
        // read global config
        var configprovider = ConfigProvider.getConfig();
        Optional<String> providers = configprovider.getOptionalValue("quarkus.security.security-providers", String.class);
        Optional<String> configFile = configprovider.getOptionalValue("quarkus.security.security-provider-config.SunPKCS11",
                String.class);

        return providers.isPresent() && providers.get().contains("SunPKCS11") && configFile.isPresent()
                && configFile.get().contains(".cfg");

    }

    public static TrustOptions computeTrustOptions(CertificateConfig certificates, Optional<String> trustStorePassword)
            throws IOException {
        // Decide if we have a single trust store file or multiple trust store files (PEM)
        Path singleTrustStoreFile = getSingleTrustStoreFile(certificates);

        if (singleTrustStoreFile != null) { // We have a single trust store file.
            String type = getTruststoreType(singleTrustStoreFile, certificates.trustStoreFileType());
            if (type.equalsIgnoreCase("pem")) {
                byte[] cert = getFileContent(singleTrustStoreFile);
                return new PemTrustOptions()
                        .addCertValue(Buffer.buffer(cert));
            }

            if ((type.equalsIgnoreCase("pkcs12") || type.equalsIgnoreCase("jks"))) {
                // We cannot assume that custom type configured by the user requires a password.
                if (certificates.trustStorePassword().isEmpty() && trustStorePassword.isEmpty()) {
                    throw new IllegalArgumentException("No trust store password provided");
                }
            }

            return createKeyStoreOptions(
                    singleTrustStoreFile,
                    trustStorePassword,
                    type,
                    certificates.trustStoreProvider(),
                    certificates.trustStoreCertAlias(),
                    Optional.empty());
        }

        // We have multiple trust store files (PEM).
        if (certificates.trustStoreFiles().isPresent() && !certificates.trustStoreFiles().get().isEmpty()) {
            // Assuming PEM, as it's the only format with multiple files
            PemTrustOptions pemKeyCertOptions = new PemTrustOptions();
            for (Path path : certificates.trustStoreFiles().get()) {
                byte[] cert = getFileContent(path);
                pemKeyCertOptions.addCertValue(Buffer.buffer(cert));
            }
            return pemKeyCertOptions;
        }

        return null;
    }

    private static Path getSingleTrustStoreFile(CertificateConfig certificates) {
        Path singleTrustStoreFile = null;
        if (certificates.trustStoreFile().isPresent()) {
            singleTrustStoreFile = certificates.trustStoreFile().get();
        }
        if (certificates.trustStoreFiles().isPresent()) {
            if (singleTrustStoreFile != null) {
                throw new IllegalArgumentException("You cannot specify both `trustStoreFile` and `trustStoreFiles`");
            }
            if (certificates.trustStoreFiles().get().size() == 1) {
                singleTrustStoreFile = certificates.trustStoreFiles().get().get(0);
            }
        }
        return singleTrustStoreFile;
    }

    static String getTruststoreType(Path singleTrustStoreFile, Optional<String> userType) {
        String type;
        if (userType.isPresent()) {
            type = userType.get().toLowerCase();
        } else {
            type = getTruststoreTypeFromFileName(singleTrustStoreFile);
        }
        return type;
    }

    private static String getKeystoreTypeFromFileName(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".p12") || name.endsWith(".pkcs12") || name.endsWith(".pfx")) {
            return "pkcs12";
        } else if (name.endsWith(".jks") || name.endsWith(".keystore")) {
            return "jks";
        } else if (name.endsWith(".key") || name.endsWith(".crt") || name.endsWith(".pem")) {
            return "pem";
        } else {
            throw new IllegalArgumentException("Could not determine the keystore type from the file name: " + path
                    + ". Configure the `quarkus.http.ssl.certificate.key-store-file-type` property.");

        }

    }

    private static String getTruststoreTypeFromFileName(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".p12") || name.endsWith(".pkcs12") || name.endsWith(".pfx")) {
            return "pkcs12";
        } else if (name.endsWith(".jks") || name.endsWith(".truststore")) {
            return "jks";
        } else if (name.endsWith(".ca") || name.endsWith(".crt") || name.endsWith(".pem")) {
            return "pem";
        } else {
            throw new IllegalArgumentException("Could not determine the truststore type from the file name: " + path
                    + ". Configure the `quarkus.http.ssl.certificate.trust-store-file-type` property.");

        }

    }

    private static KeyStoreOptions createKeyStoreOptions(Path path, Optional<String> password, String type,
            Optional<String> provider, Optional<String> alias,
            Optional<String> aliasPassword) throws IOException {
        byte[] data = getFileContent(path);
        return new KeyStoreOptions()
                .setPassword(password.orElse(null))
                .setValue(Buffer.buffer(data))
                .setType(type.toUpperCase())
                .setProvider(provider.orElse(null))
                .setAlias(alias.orElse(null))
                .setAliasPassword(aliasPassword.orElse(null));
    }

    // cannot create a buffer out of an HSM because that is exactly the point of them
    private static KeyStoreOptions createKeyStoreOptions(String type, Optional<String> provider, Optional<String> password)
            throws IOException {
        return new KeyStoreOptions()
                .setPassword(password.orElse(null))
                .setType(type.toUpperCase())
                .setProvider(provider.orElse(null));
    }

    static String getKeyStoreType(Path path, Optional<String> fileType) {
        final String type;
        if (fileType.isPresent()) {
            type = fileType.get().toLowerCase();
        } else {
            type = getKeystoreTypeFromFileName(path);
        }
        return type;
    }

    private static PemKeyCertOptions createPemKeyCertOptions(List<Path> certFile, List<Path> keyFile) throws IOException {
        List<Buffer> certificates = new ArrayList<>();
        List<Buffer> keys = new ArrayList<>();

        for (Path p : certFile) {
            final byte[] cert = getFileContent(p);
            certificates.add(Buffer.buffer(cert));
        }

        for (Path p : keyFile) {
            final byte[] key = getFileContent(p);
            keys.add(Buffer.buffer(key));
        }

        return new PemKeyCertOptions()
                .setCertValues(certificates)
                .setKeyValues(keys);
    }

    private static PKCS11KeyCertOptions createPKCS11KeyStoreOptions(String configPath, String userPin, String keyAlias) {
        return new PKCS11KeyCertOptions(configPath, userPin, keyAlias);
    }
}
