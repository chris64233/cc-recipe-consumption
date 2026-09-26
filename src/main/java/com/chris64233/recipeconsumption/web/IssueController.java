package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.domain.IssueLine;
import com.chris64233.recipeconsumption.domain.IssueLineBatch;
import com.chris64233.recipeconsumption.domain.IssueRecord;
import com.chris64233.recipeconsumption.service.IssueService;
import com.chris64233.recipeconsumption.web.dto.IssueRequest;
import com.chris64233.recipeconsumption.web.dto.IssueResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 生产领料。 */
@RestController
@RequestMapping("/api/issues")
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssueResult issue(@Valid @RequestBody IssueRequest request) {
        return toView(issueService.issue(request));
    }

    private IssueResult toView(IssueRecord record) {
        List<IssueResult.LineResult> lines = record.getLines().stream()
                .map(this::toLineView)
                .toList();
        return new IssueResult(record.getBizNo(), record.getOrder().getOrderNo(),
                record.getCreatedAt(), lines);
    }

    private IssueResult.LineResult toLineView(IssueLine line) {
        List<IssueResult.BatchResult> batches = line.getBatches().stream()
                .map(this::toBatchView)
                .toList();
        return new IssueResult.LineResult(line.getRecipeMaterialCode(),
                line.getEquivalentQty(), batches);
    }

    private IssueResult.BatchResult toBatchView(IssueLineBatch b) {
        return new IssueResult.BatchResult(b.getBatch().getBatchNo(), b.getMaterialCode(),
                b.getQty(), b.getEquivalentQty(), b.isSubstitute(), b.getConversionRatio());
    }
}
