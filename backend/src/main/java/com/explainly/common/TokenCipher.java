package com.explainly.common;

import com.explainly.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM encryption for OAuth tokens at rest. Key: base64 of 32 random bytes in TOKEN_ENCRYPTION_KEY. */
@Component
public class TokenCipher {
    private static final Logger log = LoggerFactory.getLogger(TokenCipher.class);
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(AppProperties props) {
        String configured = props.security().tokenEncryptionKey();
        byte[] keyBytes;
        if (configured == null || configured.isBlank()) {
            keyBytes = new byte[32];
            random.nextBytes(keyBytes);
            log.warn("TOKEN_ENCRYPTION_KEY is not set: using an ephemeral key. Connected YouTube accounts will "
                    + "need to be reconnected after a restart. Set TOKEN_ENCRYPTION_KEY in production.");
        } else {
            keyBytes = Base64.getDecoder().decode(configured.trim());
            if (keyBytes.length != 32) {
                throw new IllegalStateException("TOKEN_ENCRYPTION_KEY must be base64 of exactly 32 bytes");
            }
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) return null;
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            return new String(cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
