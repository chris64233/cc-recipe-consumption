package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.domain.AdjustmentRecord;
import com.chris64233.recipeconsumption.service.AdjustmentService;
import com.chris64233.recipeconsumption.web.dto.AdjustmentRequest;
import com.chris64233.recipeconsumption.web.dto.MutationResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 库存/用量调整：以追加记录方式修复领料错误。 */
@RestController
@RequestMapping("/api/adjustments")
public class AdjustmentController {

    private final AdjustmentService adjustmentService;

    public AdjustmentController(AdjustmentService adjustmentService) {
        this.adjustmentService = adjustmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MutationResult adjust(@Valid @RequestBody AdjustmentRequest request) {
        AdjustmentRecord a = adjustmentService.adjust(request);
        return new MutationResult(a.getBizNo(), a.getOrder().getOrderNo(),
                a.getBatch().getBatchNo(), a.getRecipeMaterialCode(), a.getMaterialCode(),
                a.getQtyDelta(), a.getEquivalentDelta(), a.getCreatedAt());
    }
}
