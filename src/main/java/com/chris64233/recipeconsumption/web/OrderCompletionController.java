package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.domain.OrderCompletion;
import com.chris64233.recipeconsumption.domain.OrderCompletionLoss;
import com.chris64233.recipeconsumption.service.OrderCompletionService;
import com.chris64233.recipeconsumption.web.dto.CompleteOrderRequest;
import com.chris64233.recipeconsumption.web.dto.CompletionResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 工单完工与产量核对。 */
@RestController
@RequestMapping("/api/completions")
public class OrderCompletionController {

    private final OrderCompletionService completionService;

    public OrderCompletionController(OrderCompletionService completionService) {
        this.completionService = completionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompletionResult complete(@Valid @RequestBody CompleteOrderRequest request) {
        OrderCompletion c = completionService.complete(request);
        return new CompletionResult(c.getOrder().getOrderNo(), c.getActualQty(),
                c.getCompletedAt(), c.getLosses().stream().map(this::toView).toList());
    }

    private CompletionResult.LossResult toView(OrderCompletionLoss loss) {
        return new CompletionResult.LossResult(loss.getMaterialCode(), loss.getLossQty());
    }
}
