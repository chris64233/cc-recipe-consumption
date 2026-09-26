package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.service.QueryService;
import com.chris64233.recipeconsumption.service.dto.BatchTraceView;
import com.chris64233.recipeconsumption.service.dto.OrderMaterialUsageView;
import com.chris64233.recipeconsumption.service.dto.ProductionVarianceView;
import com.chris64233.recipeconsumption.service.dto.SubstitutionView;
import com.chris64233.recipeconsumption.web.dto.IssueRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 查询：工单用料、替代计算预览、批次去向、产量差异。 */
@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/orders/{orderNo}/usage")
    public OrderMaterialUsageView usage(@PathVariable String orderNo) {
        return queryService.usage(orderNo);
    }

    @PostMapping("/substitution-preview")
    public List<SubstitutionView> preview(@Valid @RequestBody IssueRequest request) {
        return queryService.previewSubstitution(request);
    }

    @GetMapping("/batches/{batchNo}/trace")
    public BatchTraceView trace(@PathVariable String batchNo) {
        return queryService.batchTrace(batchNo);
    }

    @GetMapping("/orders/{orderNo}/variance")
    public ProductionVarianceView variance(@PathVariable String orderNo) {
        return queryService.variance(orderNo);
    }
}
