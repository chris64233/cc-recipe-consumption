package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.dto.BatchTraceView;
import com.chris64233.recipeconsumption.dto.SubstitutionPreviewRequest;
import com.chris64233.recipeconsumption.dto.SubstitutionPreviewView;
import com.chris64233.recipeconsumption.service.QueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询接口：替代计算试算、批次去向。工单用料见 {@link OrderController}，
 * 产量差异见 {@link CompletionController}。
 */
@RestController
@RequestMapping("/api")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    /** 替代计算试算（不落库、不扣库存）。 */
    @PostMapping("/orders/{orderNo}/substitutions/preview")
    public SubstitutionPreviewView preview(@PathVariable String orderNo,
                                           @Valid @RequestBody SubstitutionPreviewRequest request) {
        return queryService.previewSubstitution(orderNo, request);
    }

    /** 批次去向：扣减/退料回补/调整流水。 */
    @GetMapping("/batches/{batchNo}/trace")
    public BatchTraceView trace(@PathVariable String batchNo) {
        return queryService.traceBatch(batchNo);
    }
}
