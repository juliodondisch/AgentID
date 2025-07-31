package com.incodesmile.idvmcp;

import com.incodesmile.idvmcp.dto.WorkforceVerificationStatus;
import com.incodesmile.idvmcp.tool_dto.CheckVerificationStatusResponse;
import com.incodesmile.idvmcp.tool_dto.GetTokenResponse;
import com.incodesmile.idvmcp.tool_dto.StartVerificationResponse;
import com.incodesmile.idvmcp.tool_dto.TokenValidationResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class VerificationService {
    private static final Logger logger = LoggerFactory.getLogger(IdvMcpService.class);
    private final IncodeIdApiClient incodeIdApiClient;
    private final JwtTokenUtil jwtTokenUtil;

    private static final Map<String, String> tokenMap = new ConcurrentHashMap<>();

    public StartVerificationResponse start(String userEmail) {
        logger.info("Starting verification for email: {}", userEmail);
        try {
            String serverToken = incodeIdApiClient.getServerToken();
            var res = incodeIdApiClient.generateVerificationLink(serverToken, userEmail);
            logger.info("Generated verification link: {}", res);
            return new StartVerificationResponse(
                    res.verificationTraceId(),
                    res.verificationLink(),
                    "Verification link generated successfully.",
                    true
            );
        } catch (Exception e) {
            logger.error("Error occurred while starting verification", e);
            return new StartVerificationResponse(
                    null,
                    null,
                    "Failed to start verification process. Error generating verification link: " + e.getMessage(),
                    false
            );
        }
    }

    public CheckVerificationStatusResponse checkStatus(String verificationTraceId) {
        logger.info("Checking verification status for verificationTraceId: {}", verificationTraceId);
        if (verificationTraceId == null || verificationTraceId.isEmpty()) {
            return new CheckVerificationStatusResponse(
                    "Verification trace ID is required.",
                    false,
                    null,
                    null,
                    null
            );
        }

        // REMOVE - temp verification trace id for testing
        if (verificationTraceId.equals("temp-verification-trace-id")) {
            var email = "ognjen.samardzic@incode.com";
            var token = "temp-token";
            tokenMap.put(email, token);
            logger.info("Created verification token and cached into tokenMap for {}", email);
            return new CheckVerificationStatusResponse(
                    "Verification successful! Your authentication token is: temp-token",
                    true,
                    "SUCCESS",
                    "temp-token",
                    "ognjen.samardzic@incode.com"
            );
        }

        var verificationInfo = incodeIdApiClient.getPendingVerifications().get(verificationTraceId);
        if (verificationInfo == null) {
            return new CheckVerificationStatusResponse(
                    "Verification not found. Please start a new verification process.",
                    false,
                    null,
                    null,
                    null
            );
        }

        try {
            var res = incodeIdApiClient.checkVerificationStatus(verificationTraceId);
            logger.info("Verification status response: {}", res);
            var data = res.verification();

            if (WorkforceVerificationStatus.SUCCESS.equals(data.status())) {
                var email = verificationInfo.userEmail();
                var token = jwtTokenUtil.createUserToken(email, 3);
                tokenMap.put(email, token);
                logger.info("Created verification token and cached into tokenMap for {}", email);
                return new CheckVerificationStatusResponse(
                        "Verification successful! Your authentication token is: " + token,
                        true,
                        data.status().name(),
                        token,
                        email
                );
            } else if (WorkforceVerificationStatus.FAILED.equals(data.status())) {
                String failReason = data.failReason().name();
                return new CheckVerificationStatusResponse(
                        "Verification failed. Reason: " + failReason,
                        false,
                        data.status().name(),
                        null,
                        null
                );
            } else {
                return new CheckVerificationStatusResponse(
                        "Verification is still in progress. Please check again in a few moments.",
                        false,
                        data.status().name(),
                        null,
                        null
                );
            }

        } catch (Exception e) {
            logger.error("Error occurred while checking verification status", e);
            return new CheckVerificationStatusResponse(
                    "Failed to check verification status. Error: " + e.getMessage(),
                    false,
                    null,
                    null,
                    null
            );
        }
    }


    public TokenValidationResponse validateToken(String token) {
        logger.info("Validating token: {}", token);

        // REMOVE - temp token for testing
        if (token.equals("temp-token")) {
            return new TokenValidationResponse(
                    "Token is valid.",
                    true,
                    true,
                    "ognjen.samardzic@incode.com"
            );
        }
        
        try {
            var validation = jwtTokenUtil.validateToken(token);
            logger.info("JWT token validated: {}", validation);
            if (validation.valid()) {
                String email = validation.decoded().getSubject();
                return new TokenValidationResponse(
                        "Token is valid.",
                        true,
                        true,
                        email
                );
            } else {
                return new TokenValidationResponse(
                        "Token is not valid",
                        false,
                        false,
                        null
                );
            }
        } catch (Exception e) {
            logger.error("Error occurred while validating token", e);
            return new TokenValidationResponse(
                    "Failed to validate token. Error: " + e.getMessage(),
                    false,
                    false,
                    null
            );
        }
    }


    public GetTokenResponse getToken(String email) {
        logger.info("Get user token: {}", email);

        var token = tokenMap.get(email);
        if (token != null) {
            return new GetTokenResponse(
                    token,
                    "Token retrieved successfully for " + email,
                    true
            );
        } else {
            return new GetTokenResponse(
                    null,
                    "Token not found. Please verify your identity first.",
                    false
            );
        }
    }
}
