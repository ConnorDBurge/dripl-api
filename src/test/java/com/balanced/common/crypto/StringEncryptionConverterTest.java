package com.balanced.common.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class StringEncryptionConverterTest {

    private final StringEncryptionConverter converter = new StringEncryptionConverter();

    @BeforeAll
    static void setUpKey() throws Exception {
        byte[] testKey = "balanced-test-encryption-key-32b".getBytes();
        Field keyField = EncryptionConfig.class.getDeclaredField("KEY");
        keyField.setAccessible(true);
        keyField.set(null, testKey);
    }

    @Test
    void roundTrip_encryptsAndDecrypts() {
        String original = "test_access_token_12345";

        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void ciphertext_differsFromPlaintext() {
        String original = "test_access_token_12345";

        String encrypted = converter.convertToDatabaseColumn(original);

        assertThat(encrypted).isNotEqualTo(original);
        assertThat(encrypted).isNotBlank();
    }

    @Test
    void ciphertext_isBase64Encoded() {
        String original = "some_token";

        String encrypted = converter.convertToDatabaseColumn(original);

        byte[] decoded = Base64.getDecoder().decode(encrypted);
        // IV (12 bytes) + ciphertext + auth tag (16 bytes) should be larger than plaintext
        assertThat(decoded.length).isGreaterThan(original.length());
    }

    @Test
    void encrypt_producesUniqueCiphertextEachTime() {
        String original = "same_token_value";

        String encrypted1 = converter.convertToDatabaseColumn(original);
        String encrypted2 = converter.convertToDatabaseColumn(original);

        // Different IVs means different ciphertext each time
        assertThat(encrypted1).isNotEqualTo(encrypted2);

        // But both decrypt to the same value
        assertThat(converter.convertToEntityAttribute(encrypted1)).isEqualTo(original);
        assertThat(converter.convertToEntityAttribute(encrypted2)).isEqualTo(original);
    }

    @Test
    void nullInput_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void emptyString_roundTrips() {
        String encrypted = converter.convertToDatabaseColumn("");
        assertThat(converter.convertToEntityAttribute(encrypted)).isEmpty();
    }

    @Test
    void longValue_roundTrips() {
        String original = "a".repeat(1000);

        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }
}
