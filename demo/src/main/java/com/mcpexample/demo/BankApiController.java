package com.mcpexample.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class BankApiController {

    @Autowired
    private BankMcpService bankMcpService;

    @PostMapping("/api/withdraw")
    public Object withdraw(@RequestBody Map<String, Object> request) {
        Double amount = Double.valueOf(request.get("amount").toString());
        String token = (String) request.get("token");
        return bankMcpService.withdraw(amount, token);
    }

    @PostMapping("/api/deposit")
    public Object deposit(@RequestBody Map<String, Object> request) {
        Double amount = Double.valueOf(request.get("amount").toString());
        String token = (String) request.get("token");
        return bankMcpService.deposit(amount, token);
    }

    @GetMapping("/api/balance")
    public Object getBalance() {
        return bankMcpService.getBalance();
    }

    @PostMapping("/api/store-token")
    public Object storeToken(@RequestBody Map<String, Object> request) {
        String token = (String) request.get("token");
        return bankMcpService.storeToken(token);
    }
} 