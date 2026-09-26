package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.domain.CompletionRecord;
import com.chris64233.recipeconsumption.dto.CompleteRequest;
import com.chris64233.recipeconsumption.dto.CompletionView;
import com.chris64233.recipeconsumption.service.CompletionService;
import com.chris64233.recipeconsumption.service.ViewMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 完工与产量差异接口。
 */
@RestController
@RequestMapping("/api/orders/{orderNo}")
public class CompletionController {

    private final CompletionService completionService;
    private final ViewMapper viewMapper;
    private final com.chris64233.recipeconsumption.service.QueryService queryService;

    public CompletionController(CompletionService completionService, ViewMapper viewMapper,
                                com.chris64233.recipeconsumption.service.QueryService queryService) {
        this.completionService = completionService;
        this.viewMapper = viewMapper;
        this.queryService = queryService;
    }

    /** 提交完工：逐主料核对数量关系，不平衡返回 422 且工单保持未完工。 */
    @PostMapping("/completions")
    @ResponseStatus(HttpStatus.CREATED)
    public CompletionView complete(@PathVariable String orderNo,
                                   @Valid @RequestBody CompleteRequest request) {
        CompletionRecord record = completionService.complete(orderNo, request);
        return viewMapper.toView(record);
    }

    /**
     * 产量差异查询。已完工返回完工实绩；未完工返回 provisional 预估
     * （可用 actualQuantity 参数指定预估实际产量，缺省按计划产量）。
     */
    @GetMapping("/variance")
    public CompletionView variance(@PathVariable String orderNo,
                                   @RequestParam(name = "actualQuantity", required = false)
                                   java.math.BigDecimal projectedActualQuantity) {
        return queryService.outputVariance(orderNo, projectedActualQuantity);
    }
}
