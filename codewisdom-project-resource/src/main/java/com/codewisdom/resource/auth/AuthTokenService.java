package com.codewisdom.resource.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * 轻量 HMAC 令牌（MVP）：userId:expiry:signature，无第三方 JWT 依赖。
 */
@Component
public class AuthTokenService {

    private static final String HMAC_ALG = "HmacSHA256";

    private final byte[] secret;
    private final long ttlSeconds;

    public AuthTokenService(
            @Value("${CW_AUTH_SECRET:codewisdom-dev-secret-change-in-production}") String secret,
            @Value("${codewisdom.auth.token-ttl-hours:168}") long ttlHours) {
        this.secret = sha256(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlHours * 3600L;
    }

    public String issueToken(long userId) {
        long expiryEpochSec = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = userId + ":" + expiryEpochSec;
        String signature = sign(payload);
        String raw = payload + ":" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public AuthUser parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length != 3) {
                return null;
            }
            long userId = Long.parseLong(parts[0]);
            long expiry = Long.parseLong(parts[1]);
            if (Instant.now().getEpochSecond() > expiry) {
                return null;
            }
            String payload = parts[0] + ":" + parts[1];
            if (!sign(payload).equals(parts[2])) {
                return null;
            }
            return new AuthUser(userId, expiry);
        } catch (Exception ex) {
            return null;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret, HMAC_ALG));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("令牌签名失败", ex);
        }
    }

    public record AuthUser(long userId, long expiryEpochSec) {
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
