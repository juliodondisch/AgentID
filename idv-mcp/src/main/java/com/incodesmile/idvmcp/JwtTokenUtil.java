package com.incodesmile.idvmcp;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenUtil {

    private static final String secret = "your_jwt_secret_key_56480001234567890987654321";

    // Record for token validation response
    public record TokenValidationResult(boolean valid, Claims decoded, String error) {
        // Convenience constructor for valid results
        public TokenValidationResult(boolean valid, Claims decoded) {
            this(valid, decoded, null);
        }

        // Convenience constructor for invalid results
        public TokenValidationResult(boolean valid, String error) {
            this(valid, null, error);
        }
    }

    // Create a user token with the specified expiration time
    public String createUserToken(String userId, long expirationMinutes) {

        // Hardcoding temp token to true
        if (userId.equals("ognjen.samardzic@incode.com")) {
            return "temp.token";
        }

        // Default to 3 minutes if not specified
        if (expirationMinutes <= 0) {
            expirationMinutes = 3;
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "user_token");

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMinutes * 60 * 1000);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    // Validate a JWT token
    public TokenValidationResult validateToken(String token) {

        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return new TokenValidationResult(true, claims);
        } catch (ExpiredJwtException e) {
            return new TokenValidationResult(false, "Token expired");
        } catch (SignatureException e) {
            return new TokenValidationResult(false, "Invalid signature");
        } catch (Exception e) {
            return new TokenValidationResult(false, e.getMessage());
        }
    }

    // Get signing key from secret
    private Key getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}