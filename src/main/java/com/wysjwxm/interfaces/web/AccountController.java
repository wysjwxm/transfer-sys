package com.wysjwxm.interfaces.web;

import com.wysjwxm.application.TransferAppService;
import com.wysjwxm.domain.account.Account;
import com.wysjwxm.interfaces.dto.AccountResponse;
import com.wysjwxm.interfaces.dto.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账户查询接口。
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final TransferAppService transferAppService;

    public AccountController(TransferAppService transferAppService) {
        this.transferAppService = transferAppService;
    }

    /** 查询账户余额。 */
    @GetMapping("/{userId}")
    public Result<AccountResponse> getAccount(@PathVariable Long userId) {
        Account account = transferAppService.getAccount(userId);
        return Result.ok(new AccountResponse(account.getUserId(), account.getBalance()));
    }
}
