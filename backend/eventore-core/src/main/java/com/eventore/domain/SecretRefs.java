package com.eventore.domain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves credential secret references so plaintext secrets never need to be
 * stored in connection profiles. Supported reference formats:
 *
 * <ul>
 *   <li>{@code env:NAME} — value of the environment variable NAME</li>
 *   <li>{@code file:/path/to/secret} — trimmed content of the file (e.g. a
 *       mounted Kubernetes/Vault-agent secret)</li>
 *   <li>anything else — returned verbatim (inline secret)</li>
 * </ul>
 *
 * Vault integration is expected to be done via the Vault agent sidecar or CSI
 * driver, which materializes secrets as files consumable through {@code file:}.
 */
public final class SecretRefs {

    private static final int MAX_FILE_SECRET_BYTES = 64 * 1024;

    private SecretRefs() {}

    public static String resolve(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("env:")) {
            String name = value.substring("env:".length()).trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Secret reference 'env:' is missing a variable name");
            }
            String resolved = System.getenv(name);
            if (resolved == null) {
                throw new IllegalArgumentException(
                        "Secret reference points to undefined environment variable: " + name);
            }
            return resolved;
        }
        if (value.startsWith("file:")) {
            String path = value.substring("file:".length()).trim();
            if (path.isEmpty()) {
                throw new IllegalArgumentException("Secret reference 'file:' is missing a path");
            }
            if (path.contains("..")) {
                throw new IllegalArgumentException(
                        "Secret reference 'file:' path must not contain '..': " + path);
            }
            try {
                return readFileSecret(Path.of(path), path);
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to read secret file: " + path, e);
            }
        }
        if (value.startsWith("vault:")) {
            throw new IllegalArgumentException(
                    "Direct vault: references are not supported; mount the secret via the Vault agent "
                            + "or CSI driver and reference it with file:/path instead");
        }
        return value;
    }

    private static String readFileSecret(Path filePath, String pathForMessage) throws IOException {
        try (InputStream in = Files.newInputStream(filePath)) {
            byte[] bytes = in.readNBytes(MAX_FILE_SECRET_BYTES + 1);
            if (bytes.length > MAX_FILE_SECRET_BYTES) {
                throw new IllegalArgumentException(
                        "Secret file exceeds maximum size of 64KB: " + pathForMessage);
            }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        }
    }
}
