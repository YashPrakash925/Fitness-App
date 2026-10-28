package com.fitnesstracker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TokenService {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final long TOKEN_LIFETIME_SECONDS = 86_400;

    private final byte[] secret;
    private final ObjectMapper json;

    public TokenService(String configuredSecret, ObjectMapper json) {
        if (configuredSecret == null || configuredSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set to at least 32 characters");
        }
        this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        this.json = json;
    }

    public String create(long userId, String email) {
        try {
            var header = ENCODER.encodeToString(json.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            var claims = new LinkedHashMap<String, Object>();
            claims.put("sub", Long.toString(userId));
            claims.put("email", email);
            claims.put("iat", Instant.now().getEpochSecond());
            claims.put("exp", Instant.now().plusSeconds(TOKEN_LIFETIME_SECONDS).getEpochSecond());
            var payload = ENCODER.encodeToString(json.writeValueAsBytes(claims));
            var unsigned = header + "." + payload;
            return unsigned + "." + ENCODER.encodeToString(sign(unsigned));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create login token", ex);
        }
    }

    public long requireUserId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ApiException(401, "Sign in to continue");
        }
        try {
            var parts = authorizationHeader.substring(7).split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException("Malformed token");
            var signedContent = parts[0] + "." + parts[1];
            var providedSignature = DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(sign(signedContent), providedSignature)) throw new IllegalArgumentException("Invalid token");
            var claims = json.readValue(DECODER.decode(parts[1]), new TypeReference<Map<String, Object>>() {});
            var exp = ((Number) claims.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= exp) throw new IllegalArgumentException("Expired token");
            return Long.parseLong(String.valueOf(claims.get("sub")));
        } catch (Exception ex) {
            throw new ApiException(401, "Your session has expired. Please sign in again.");
        }
    }

    private byte[] sign(String value) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    }
}
