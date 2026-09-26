package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.domain.InventoryAdjustment;
import com.chris64233.recipeconsumption.dto.AdjustmentRequest;
import com.chris64233.recipeconsumption.dto.AdjustmentView;
import com.chris64233.recipeconsumption.service.AdjustmentService;
import com.chris64233.recipeconsumption.service.ViewMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存调整接口（盘点盘盈盘亏 / 领料错误核销，原始领料记录不变）。
 */
@RestController
@RequestMapping("/api/adjustments")
public class AdjustmentController {

    private final AdjustmentService adjustmentService;
    private final ViewMapper viewMapper;

    public AdjustmentController(AdjustmentService adjustmentService, ViewMapper viewMapper) {
        this.adjustmentService = adjustmentService;
        this.viewMapper = viewMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentView create(@Valid @RequestBody AdjustmentRequest request) {
        InventoryAdjustment adjustment = adjustmentService.adjust(request);
        return viewMapper.toView(adjustment);
    }

    @GetMapping("/{adjustmentNo}")
    public AdjustmentView get(@PathVariable String adjustmentNo) {
        return viewMapper.toView(adjustmentService.getAdjustment(adjustmentNo));
    }
}
