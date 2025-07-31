package com.incodelabs.alignedexecutionengine.integration.verification;

import com.incodelabs.alignedexecutionengine.integration.verification.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
@RequiredArgsConstructor
public class IncodeVerificationApiClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${incode.verification.api.base-url}")
    private String baseUrl;
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");
        return headers;
    }
    
    /**
     * Start a new identity verification process
     * GET /start?userEmail={email}
     */
    public StartVerificationResponse startVerification(String userEmail) {
        try {
            String url = baseUrl + "/start";
            if (userEmail != null && !userEmail.isEmpty()) {
                url += "?userEmail=" + userEmail;
            }
            
            HttpEntity<?> request = new HttpEntity<>(createHeaders());
            
            log.info("Starting verification process for user: {}", userEmail);
            ResponseEntity<StartVerificationResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, request, StartVerificationResponse.class);
            
            StartVerificationResponse result = response.getBody();
            log.info("Verification response: {}", result);
            log.info("Verification started successfully with trace ID: {}", 
                result != null ? result.getVerificationTraceId() : "null");
            return result;
            
        } catch (HttpClientErrorException e) {
            log.error("Error starting verification: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new StartVerificationResponse();
        } catch (Exception e) {
            log.error("Unexpected error starting verification for user: {}", userEmail, e);
            return new StartVerificationResponse();
        }
    }
    
    /**
     * Check the status of a verification process
     * GET /status?id={verificationId}
     */
    public VerificationStatusResponse checkVerificationStatus(String verificationId) {
        try {
            String url = baseUrl + "/status?id=" + verificationId;
            HttpEntity<?> request = new HttpEntity<>(createHeaders());
            
            log.info("Checking verification status for ID: {}", verificationId);
            ResponseEntity<VerificationStatusResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, request, VerificationStatusResponse.class);
            
            VerificationStatusResponse result = response.getBody();
            log.info("Verification status check completed with status: {}", 
                result != null ? result.getStatus() : "null");
            return result;
            
        } catch (HttpClientErrorException e) {
            log.error("Error checking verification status: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new VerificationStatusResponse();
        } catch (Exception e) {
            log.error("Unexpected error checking verification status for ID: {}", verificationId, e);
            return new VerificationStatusResponse();
        }
    }
    
    /**
     * Get an authentication token for a user
     * GET /get-token?email={email}
     */
    public TokenResponse getToken(String userEmail) {
        try {
            String url = baseUrl + "/get-token";
            log.info("Getting token for user: {} from URL: {}", userEmail, url);
            if (userEmail != null && !userEmail.isEmpty()) {
                url += "?email=" + userEmail;
            }
            
            HttpEntity<?> request = new HttpEntity<>(createHeaders());
            
            log.info("Getting token for user: {}", userEmail);
            ResponseEntity<TokenResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, request, TokenResponse.class);
            
            TokenResponse result = response.getBody();
            log.info("Token retrieved successfully for user: {}", userEmail);
            log.info("Token Response: {}", result.getToken());
            return result;
            
        } catch (HttpClientErrorException e) {
            log.error("Error getting token: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new TokenResponse();
        } catch (Exception e) {
            log.error("Unexpected error getting token for user: {}", userEmail, e);
            return new TokenResponse();
        }
    }
    
    /**
     * Validate an authentication token
     * GET /validate-token?token={token}
     */
    public TokenValidationResponse validateToken(String token) {
        try {
            String url = baseUrl + "/validate-token?token=" + token;
            HttpEntity<?> request = new HttpEntity<>(createHeaders());
            
            log.info("Validating token");
            ResponseEntity<TokenValidationResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, request, TokenValidationResponse.class);
            
            TokenValidationResponse result = response.getBody();
            log.info("Token validation completed with result: {}", 
                result != null ? result.isValid() : "null");
            return result;
            
        } catch (HttpClientErrorException e) {
            log.error("Error validating token: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new TokenValidationResponse();
        } catch (Exception e) {
            log.error("Unexpected error validating token", e);
            return new TokenValidationResponse();
        }
    }
}