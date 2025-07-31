package com.mcpexample.demo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

@Component
public class IdentityVerificationApi {
    private static final Logger logger = LoggerFactory.getLogger(IdentityVerificationApi.class);

    @Value("${identity.verification.mcp.url}")
    private String identityVerificationMcpUrl;

    private final RestTemplate restTemplate;

    public IdentityVerificationApi(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // Java record for token validation response
    public record TokenValidationResponse(boolean valid, String error, boolean needsVerification, String message, String userId) {}

    public TokenValidationResponse validateToken(String token) {

        logger.info("Validating token: {}", token);
        try {
            // Julio - Changed from post to get to match the idv-mcp endpoint
            ResponseEntity<TokenValidationResponse> response = restTemplate.getForEntity(
                identityVerificationMcpUrl + "/validate-token?token=" + token,
                TokenValidationResponse.class
            );

            logger.info("Token validation response: {}", response.getBody());
            return response.getBody();
        } catch (Exception error) {
            logger.error("Error validating token: {}", error.getMessage());
            return new TokenValidationResponse(false, error.getMessage(), true, "Token validation failed", null);
        }
    }
}