package com.explainly.common;

import com.explainly.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class TokenCipherTest {

    private static AppProperties props(String key) {
        return new AppProperties(null, new AppProperties.Security(null, null, null, key, null, null),
                null, null, null, null, null);
    }

    @Test
    void roundTripsAndUsesRandomIv() {
        TokenCipher cipher = new TokenCipher(props(Base64.getEncoder().encodeToString(new byte[32])));
        String a = cipher.encrypt("ya29.secret-token");
        String b = cipher.encrypt("ya29.secret-token");
        assertThat(a).isNotEqualTo(b).doesNotContain("secret");
        assertThat(cipher.decrypt(a)).isEqualTo("ya29.secret-token");
    }

    @Test
    void detectsTampering() {
        TokenCipher cipher = new TokenCipher(props(Base64.getEncoder().encodeToString(new byte[32])));
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt("value"));
        raw[raw.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(raw))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsWrongKeyLength() {
        assertThatThrownBy(() -> new TokenCipher(props(Base64.getEncoder().encodeToString(new byte[16]))))
                .isInstanceOf(IllegalStateException.class);
    }
}
