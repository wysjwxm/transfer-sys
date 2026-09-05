package com.wysjwxm.interfaces.web;

import com.wysjwxm.application.TransferAppService;
import com.wysjwxm.application.TransferCommand;
import com.wysjwxm.application.TransferResult;
import com.wysjwxm.domain.exception.BizException;
import com.wysjwxm.domain.exception.ErrorCode;
import com.wysjwxm.interfaces.dto.Result;
import com.wysjwxm.interfaces.dto.TransferRequest;
import com.wysjwxm.interfaces.dto.TransferResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 转账接口（接口层：只做参数形态转换，不承载业务规则）。
 */
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferAppService transferAppService;

    public TransferController(TransferAppService transferAppService) {
        this.transferAppService = transferAppService;
    }

    /** 发起一笔行内转账：用户 A 转给用户 B。 */
    @PostMapping
    public Result<TransferResponse> transfer(@RequestBody TransferRequest request) {
        TransferCommand command = toCommand(request);
        TransferResult result = transferAppService.transfer(command);
        return Result.ok(new TransferResponse(result.getTxnNo()));
    }

    private TransferCommand toCommand(TransferRequest request) {
        if (request == null || request.getFromUserId() == null || request.getToUserId() == null
                || request.getAmount() == null || request.getAmount().trim().isEmpty()) {
            throw new BizException(ErrorCode.INVALID_REQUEST);
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(request.getAmount().trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.INVALID_REQUEST);
        }
        return new TransferCommand(request.getFromUserId(), request.getToUserId(),
                amount, request.getRequestNo());
    }
}
