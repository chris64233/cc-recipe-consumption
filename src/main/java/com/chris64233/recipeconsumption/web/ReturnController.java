package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.domain.MaterialReturn;
import com.chris64233.recipeconsumption.dto.ReturnRequest;
import com.chris64233.recipeconsumption.dto.ReturnView;
import com.chris64233.recipeconsumption.service.ReturnService;
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
 * 退料接口。退料追加新单并回补库存，不修改原始领料记录。
 */
@RestController
@RequestMapping("/api/returns")
public class ReturnController {

    private final ReturnService returnService;
    private final ViewMapper viewMapper;

    public ReturnController(ReturnService returnService, ViewMapper viewMapper) {
        this.returnService = returnService;
        this.viewMapper = viewMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReturnView create(@Valid @RequestBody ReturnRequest request) {
        MaterialReturn materialReturn = returnService.createReturn(request);
        return viewMapper.toView(materialReturn);
    }

    @GetMapping("/{returnNo}")
    public ReturnView get(@PathVariable String returnNo) {
        return viewMapper.toView(returnService.getReturn(returnNo));
    }
}
