package com.eventore.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretRefsTest {

    @TempDir
    Path tempDir;

    @Test
    void plainValuesPassThroughUnchanged() {
        assertThat(SecretRefs.resolve("super-secret")).isEqualTo("super-secret");
        assertThat(SecretRefs.resolve(null)).isNull();
    }

    @Test
    void envReferenceResolvesExistingVariable() {
        String name = System.getenv().keySet().stream().findFirst().orElseThrow();
        assertThat(SecretRefs.resolve("env:" + name)).isEqualTo(System.getenv(name));
    }

    @Test
    void envReferenceFailsForUndefinedVariable() {
        assertThatThrownBy(() -> SecretRefs.resolve("env:EVENTORE_DOES_NOT_EXIST_12345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("undefined environment variable");
    }

    @Test
    void fileReferenceReadsTrimmedContent() throws IOException {
        Path secretFile = tempDir.resolve("secret.txt");
        Files.writeString(secretFile, "  token-value\n");
        assertThat(SecretRefs.resolve("file:" + secretFile)).isEqualTo("token-value");
    }

    @Test
    void fileReferenceFailsForMissingFile() {
        assertThatThrownBy(() -> SecretRefs.resolve("file:" + tempDir.resolve("missing.txt")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to read secret file");
    }

    @Test
    void vaultReferenceIsRejectedWithGuidance() {
        assertThatThrownBy(() -> SecretRefs.resolve("vault:secret/data/broker"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file:");
    }

    @Test
    void envReferenceFailsWhenVariableNameMissing() {
        assertThatThrownBy(() -> SecretRefs.resolve("env:"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing a variable name");
    }

    @Test
    void fileReferenceFailsWhenPathMissing() {
        assertThatThrownBy(() -> SecretRefs.resolve("file:"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing a path");
    }

    @Test
    void fileReferenceRejectsPathTraversal() {
        assertThatThrownBy(() -> SecretRefs.resolve("file:../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain '..'");
    }

    @Test
    void fileReferenceAcceptsContentUpTo64Kb() throws IOException {
        Path secretFile = tempDir.resolve("large.txt");
        Files.writeString(secretFile, "x".repeat(64 * 1024));
        assertThat(SecretRefs.resolve("file:" + secretFile)).hasSize(64 * 1024);
    }

    @Test
    void fileReferenceRejectsContentOver64Kb() throws IOException {
        Path secretFile = tempDir.resolve("too-large.txt");
        Files.writeString(secretFile, "x".repeat(64 * 1024 + 1));
        assertThatThrownBy(() -> SecretRefs.resolve("file:" + secretFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum size of 64KB");
    }

    @Test
    void connectionProfileResolvesCredentialReferences() throws IOException {
        Path secretFile = tempDir.resolve("password.txt");
        Files.writeString(secretFile, "p@ss");
        ConnectionProfile profile = new ConnectionProfile();
        profile.setCredentials(java.util.Map.of("password", "file:" + secretFile));
        assertThat(profile.credential("password")).isEqualTo("p@ss");
    }
}
