package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.dto.CreateOrderRequest;
import com.chris64233.recipeconsumption.dto.OrderUsageView;
import com.chris64233.recipeconsumption.dto.OrderView;
import com.chris64233.recipeconsumption.service.MasterDataService;
import com.chris64233.recipeconsumption.service.QueryService;
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

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final MasterDataService masterDataService;
    private final QueryService queryService;
    private final ViewMapper viewMapper;

    public OrderController(MasterDataService masterDataService, QueryService queryService,
                           ViewMapper viewMapper) {
        this.masterDataService = masterDataService;
        this.queryService = queryService;
        this.viewMapper = viewMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView create(@Valid @RequestBody CreateOrderRequest request) {
        ProductionOrder order = masterDataService.createOrder(request);
        return viewMapper.toView(order);
    }

    @GetMapping("/{orderNo}")
    public OrderView get(@PathVariable String orderNo) {
        return viewMapper.toView(masterDataService.getOrder(orderNo));
    }

    /** 工单用料汇总。 */
    @GetMapping("/{orderNo}/usage")
    public OrderUsageView usage(@PathVariable String orderNo) {
        return queryService.orderUsage(orderNo);
    }
}
