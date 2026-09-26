package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.domain.ReturnRecord;
import com.chris64233.recipeconsumption.service.ReturnService;
import com.chris64233.recipeconsumption.web.dto.MutationResult;
import com.chris64233.recipeconsumption.web.dto.ReturnRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 退料：只追加记录、加回批次库存，不改原始领料记录。 */
@RestController
@RequestMapping("/api/returns")
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MutationResult returnMaterial(@Valid @RequestBody ReturnRequest request) {
        ReturnRecord r = returnService.returnMaterial(request);
        return new MutationResult(r.getBizNo(), r.getOrder().getOrderNo(),
                r.getBatch().getBatchNo(), r.getRecipeMaterialCode(), r.getMaterialCode(),
                r.getQty(), r.getEquivalentQty(), r.getCreatedAt());
    }
}
