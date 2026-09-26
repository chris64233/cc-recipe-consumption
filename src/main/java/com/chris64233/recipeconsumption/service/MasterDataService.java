package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.domain.MaterialBatch;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.domain.QualityStatus;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.dto.CreateBatchRequest;
import com.chris64233.recipeconsumption.dto.CreateOrderRequest;
import com.chris64233.recipeconsumption.repo.MaterialBatchRepository;
import com.chris64233.recipeconsumption.repo.ProductionOrderRepository;
import com.chris64233.recipeconsumption.support.DuplicateBusinessNoException;
import com.chris64233.recipeconsumption.support.NotFoundException;
import com.chris64233.recipeconsumption.support.Quantities;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 原料批次建档、生产工单创建。工单创建时把配方版本以外键形式固定下来。
 */
@Service
public class MasterDataService {

    private final MaterialBatchRepository batchRepository;
    private final ProductionOrderRepository orderRepository;
    private final RecipeService recipeService;

    public MasterDataService(MaterialBatchRepository batchRepository,
                             ProductionOrderRepository orderRepository,
                             RecipeService recipeService) {
        this.batchRepository = batchRepository;
        this.orderRepository = orderRepository;
        this.recipeService = recipeService;
    }

    @Transactional
    public MaterialBatch createBatch(CreateBatchRequest request) {
        if (batchRepository.findByBatchNo(request.batchNo()).isPresent()) {
            throw new DuplicateBusinessNoException("批次号已存在: " + request.batchNo());
        }
        QualityStatus status = request.qualityStatus() == null ? QualityStatus.AVAILABLE : request.qualityStatus();
        return batchRepository.save(new MaterialBatch(
                request.batchNo(), request.materialCode(), request.materialName(),
                Quantities.normalize(request.availableQuantity()), status, request.expiryDate()));
    }

    @Transactional
    public ProductionOrder createOrder(CreateOrderRequest request) {
        if (orderRepository.existsByOrderNo(request.orderNo())) {
            throw new DuplicateBusinessNoException("工单号已存在: " + request.orderNo());
        }
        RecipeVersion version = recipeService.getVersion(request.recipeCode(), request.versionNo());
        return orderRepository.save(new ProductionOrder(
                request.orderNo(), version, Quantities.normalize(request.plannedQuantity())));
    }

    @Transactional(readOnly = true)
    public MaterialBatch getBatch(String batchNo) {
        return batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new NotFoundException("原料批次不存在: " + batchNo));
    }

    @Transactional(readOnly = true)
    public ProductionOrder getOrder(String orderNo) {
        return orderRepository.findWithDetailsByOrderNo(orderNo)
                .orElseThrow(() -> new NotFoundException("工单不存在: " + orderNo));
    }
}
