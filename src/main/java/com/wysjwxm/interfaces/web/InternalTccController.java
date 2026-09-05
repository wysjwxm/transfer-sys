package com.wysjwxm.interfaces.web;

import com.wysjwxm.application.TccParticipantService;
import com.wysjwxm.domain.exception.BizException;
import com.wysjwxm.domain.exception.ErrorCode;
import com.wysjwxm.domain.transfer.BranchRole;
import com.wysjwxm.interfaces.dto.Result;
import com.wysjwxm.interfaces.dto.TccBranchRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 节点间 TCC 参与者接口：把对端协调者的 Try/Confirm/Cancel 落到本节点自己的库。
 *
 * <p>响应沿用 {@link Result}：业务成败看 body.code（HTTP 恒 200），与本系统对外 API 同一约定，
 * 对端 {@code HttpTccPeer} 因此只需解析 code。接口层只做形态转换（字符串 role/amount → 领域对象），
 * 语义全在 {@link TccParticipantService}。</p>
 */
@RestController
@RequestMapping("/internal/tcc")
public class InternalTccController {

    private final TccParticipantService tccParticipantService;

    public InternalTccController(TccParticipantService tccParticipantService) {
        this.tccParticipantService = tccParticipantService;
    }

    @PostMapping("/try")
    public Result<Void> tryBranch(@RequestBody TccBranchRequest request) {
        invoke("try", request);
        return Result.ok(null);
    }

    @PostMapping("/confirm")
    public Result<Void> confirmBranch(@RequestBody TccBranchRequest request) {
        invoke("confirm", request);
        return Result.ok(null);
    }

    @PostMapping("/cancel")
    public Result<Void> cancelBranch(@RequestBody TccBranchRequest request) {
        invoke("cancel", request);
        return Result.ok(null);
    }

    private void invoke(String phase, TccBranchRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST);
        }
        BranchRole role = parseRole(request.getRole());
        BigDecimal amount = parseAmount(request.getAmount());
        switch (phase) {
            case "try":
                tccParticipantService.tryBranch(request.getTxnNo(), request.getUserId(), role, amount);
                break;
            case "confirm":
                tccParticipantService.confirmBranch(request.getTxnNo(), request.getUserId(), role, amount);
                break;
            default:
                tccParticipantService.cancelBranch(request.getTxnNo(), request.getUserId(), role, amount);
                break;
        }
    }

    private BranchRole parseRole(String role) {
        try {
            return BranchRole.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "非法 role: " + role);
        }
    }

    private BigDecimal parseAmount(String amount) {
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "非法 amount: " + amount);
        }
    }
}
