package com.balanced.common.crypto;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Provides the encryption key to JPA converters (which aren't Spring-managed).
 * Reads the key from the balanced.encryption.key property and exposes it statically.
 */
@Component
public class EncryptionConfig {

    private static byte[] KEY;

    @Value("${balanced.encryption.key}")
    private String base64Key;

    @PostConstruct
    void init() {
        KEY = Base64.getDecoder().decode(base64Key);
        if (KEY.length != 32) {
            throw new IllegalStateException(
                    "balanced.encryption.key must be a Base64-encoded 256-bit (32-byte) key");
        }
    }

    public static byte[] getKey() {
        if (KEY == null) {
            throw new IllegalStateException("EncryptionConfig has not been initialized");
        }
        return KEY;
    }
}
