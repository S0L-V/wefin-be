package com.solv.wefin.web.trading.account;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trading/accounts")
public class AccountController {
    @GetMapping
    public String getAccounts() {
        return "OK";
    }
}

