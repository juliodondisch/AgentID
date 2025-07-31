package com.incodesmile.idvmcp.api.http;

import com.incodesmile.idvmcp.VerificationService;
import com.incodesmile.idvmcp.tool_dto.CheckVerificationStatusResponse;
import com.incodesmile.idvmcp.tool_dto.GetTokenResponse;
import com.incodesmile.idvmcp.tool_dto.StartVerificationResponse;
import com.incodesmile.idvmcp.tool_dto.TokenValidationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VerificationController {
    private final VerificationService verificationService;

    @GetMapping("start")
    public StartVerificationResponse start(@RequestParam(required = false) String userEmail) {
        if (userEmail == null) {
            userEmail = "ognjen.samardzic@incode.com";
        }
        return verificationService.start(userEmail);
    }

    @GetMapping("status")
    public CheckVerificationStatusResponse checkStatus(@RequestParam String id) {
        return verificationService.checkStatus(id);
    }

    @GetMapping("validate-token")
    public TokenValidationResponse validateToken(@RequestParam String token) {
        return verificationService.validateToken(token);
    }

    @GetMapping("get-token")
    public GetTokenResponse getToken(@RequestParam(required = false) String email) {
        if (email == null) {
            email = "ognjen.samardzic@incode.com";
        }
        
        return verificationService.getToken(email);
    }
}
