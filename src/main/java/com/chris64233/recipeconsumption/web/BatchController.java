package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.domain.MaterialBatch;
import com.chris64233.recipeconsumption.dto.BatchView;
import com.chris64233.recipeconsumption.dto.CreateBatchRequest;
import com.chris64233.recipeconsumption.service.MasterDataService;
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
@RequestMapping("/api/batches")
public class BatchController {

    private final MasterDataService masterDataService;
    private final ViewMapper viewMapper;

    public BatchController(MasterDataService masterDataService, ViewMapper viewMapper) {
        this.masterDataService = masterDataService;
        this.viewMapper = viewMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BatchView create(@Valid @RequestBody CreateBatchRequest request) {
        MaterialBatch batch = masterDataService.createBatch(request);
        return viewMapper.toView(batch);
    }

    @GetMapping("/{batchNo}")
    public BatchView get(@PathVariable String batchNo) {
        return viewMapper.toView(masterDataService.getBatch(batchNo));
    }
}
