package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.domain.MaterialBatch;
import com.chris64233.recipeconsumption.domain.MaterialBatchRepository;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.domain.ProductionOrderRepository;
import com.chris64233.recipeconsumption.domain.QualityStatus;
import com.chris64233.recipeconsumption.domain.RecipeItem;
import com.chris64233.recipeconsumption.domain.RecipeSubstitution;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.domain.RecipeVersionRepository;
import com.chris64233.recipeconsumption.support.QuantityMath;
import com.chris64233.recipeconsumption.web.dto.CreateBatchRequest;
import com.chris64233.recipeconsumption.web.dto.CreateOrderRequest;
import com.chris64233.recipeconsumption.web.dto.CreateRecipeRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/** 主数据服务：创建不可变配方版本、原料批次，以及绑定配方版本的生产工单。 */
@Service
public class MasterDataService {

    private final RecipeVersionRepository recipeRepository;
    private final MaterialBatchRepository batchRepository;
    private final ProductionOrderRepository orderRepository;

    public MasterDataService(RecipeVersionRepository recipeRepository,
                             MaterialBatchRepository batchRepository,
                             ProductionOrderRepository orderRepository) {
        this.recipeRepository = recipeRepository;
        this.batchRepository = batchRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public RecipeVersion createRecipe(CreateRecipeRequest request) {
        if (recipeRepository.findByRecipeCodeAndVersionNo(
                request.recipeCode(), request.versionNo()).isPresent()) {
            throw new IdempotentConflictException(String.format(
                    "配方版本已存在: %s/%s", request.recipeCode(), request.versionNo()));
        }
        RecipeVersion version = new RecipeVersion(
                request.recipeCode(), request.versionNo(), request.productCode());

        Set<String> itemMaterials = new HashSet<>();
        for (CreateRecipeRequest.Item itemReq : request.items()) {
            if (!itemMaterials.add(itemReq.materialCode())) {
                throw new BusinessRuleException("配方原料重复: " + itemReq.materialCode());
            }
            RecipeItem item = new RecipeItem(itemReq.materialCode(),
                    QuantityMath.normalize(itemReq.standardQty()));

            Set<String> subMaterials = new HashSet<>();
            if (itemReq.substitutions() != null) {
                for (CreateRecipeRequest.Substitution subReq : itemReq.substitutions()) {
                    if (subReq.substituteMaterialCode().equals(itemReq.materialCode())) {
                        throw new BusinessRuleException(
                                "替代料不能与配方原料相同: " + itemReq.materialCode());
                    }
                    if (!subMaterials.add(subReq.substituteMaterialCode())) {
                        throw new BusinessRuleException(
                                "替代料重复: " + subReq.substituteMaterialCode());
                    }
                    BigDecimal ratio = QuantityMath.normalizeRatio(subReq.conversionRatio());
                    if (ratio.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessRuleException("换算系数必须大于 0");
                    }
                    BigDecimal maxRatio = QuantityMath.normalizeRatio(subReq.maxRatio());
                    if (maxRatio.compareTo(BigDecimal.ZERO) <= 0
                            || maxRatio.compareTo(BigDecimal.ONE) > 0) {
                        throw new BusinessRuleException("最大替代比例必须在 (0, 1] 范围内");
                    }
                    item.addSubstitution(new RecipeSubstitution(
                            subReq.substituteMaterialCode(), ratio, maxRatio));
                }
            }
            version.addItem(item);
        }
        return recipeRepository.save(version);
    }

    @Transactional
    public MaterialBatch createBatch(CreateBatchRequest request) {
        if (batchRepository.findByBatchNo(request.batchNo()).isPresent()) {
            throw new IdempotentConflictException("批次号已存在: " + request.batchNo());
        }
        QualityStatus status;
        try {
            status = QualityStatus.valueOf(request.qualityStatus());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("未知质量状态: " + request.qualityStatus()
                    + "，可选: AVAILABLE/QUARANTINE/REJECTED");
        }
        return batchRepository.save(new MaterialBatch(
                request.batchNo(), request.materialCode(),
                QuantityMath.normalize(request.availableQty()),
                status, request.expiryDate()));
    }

    @Transactional
    public ProductionOrder createOrder(CreateOrderRequest request) {
        if (orderRepository.findByOrderNo(request.orderNo()).isPresent()) {
            throw new IdempotentConflictException("工单号已存在: " + request.orderNo());
        }
        RecipeVersion version = recipeRepository.findByRecipeCodeAndVersionNo(
                request.recipeCode(), request.versionNo())
                .orElseThrow(() -> new NotFoundException(String.format(
                        "配方版本不存在: %s/%s", request.recipeCode(), request.versionNo())));
        return orderRepository.save(new ProductionOrder(
                request.orderNo(), version, QuantityMath.normalize(request.plannedQty())));
    }
}
