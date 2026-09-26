package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.domain.MaterialIssue;
import com.chris64233.recipeconsumption.dto.IssueRequest;
import com.chris64233.recipeconsumption.dto.IssueView;
import com.chris64233.recipeconsumption.service.IssueService;
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
 * 领料接口。同一 issueNo 重复提交返回同一单据，不重复扣减（幂等）。
 */
@RestController
@RequestMapping("/api")
public class IssueController {

    private final IssueService issueService;
    private final ViewMapper viewMapper;

    public IssueController(IssueService issueService, ViewMapper viewMapper) {
        this.issueService = issueService;
        this.viewMapper = viewMapper;
    }

    @PostMapping("/orders/{orderNo}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public IssueView issue(@PathVariable String orderNo, @Valid @RequestBody IssueRequest request) {
        MaterialIssue issue = issueService.issue(orderNo, request);
        return viewMapper.toView(issue);
    }

    @GetMapping("/issues/{issueNo}")
    public IssueView get(@PathVariable String issueNo) {
        return viewMapper.toView(issueService.getIssue(issueNo));
    }
}
