package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.domain.IngredientSubstitution;
import com.chris64233.recipeconsumption.domain.InventoryAdjustment;
import com.chris64233.recipeconsumption.domain.MaterialBatch;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.domain.RecipeIngredient;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.dto.AdjustmentRequest;
import com.chris64233.recipeconsumption.repo.InventoryAdjustmentRepository;
import com.chris64233.recipeconsumption.repo.MaterialBatchRepository;
import com.chris64233.recipeconsumption.repo.ProductionOrderRepository;
import com.chris64233.recipeconsumption.repo.RecipeVersionRepository;
import com.chris64233.recipeconsumption.support.BusinessRuleException;
import com.chris64233.recipeconsumption.support.NotFoundException;
import com.chris64233.recipeconsumption.support.Quantities;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 库存调整服务。调整是只追加的流水：盘盈（+）回补、盘亏（−）核销，
 * 不修改任何原始领料记录；盘亏受非负库存约束。挂工单的调整折成主料口径，
 * 参与完工核对。
 */
@Service
public class AdjustmentService {

    private final MaterialBatchRepository batchRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final ProductionOrderRepository orderRepository;
    private final RecipeVersionRepository versionRepository;

    public AdjustmentService(MaterialBatchRepository batchRepository,
                             InventoryAdjustmentRepository adjustmentRepository,
                             ProductionOrderRepository orderRepository,
                             RecipeVersionRepository versionRepository) {
        this.batchRepository = batchRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.orderRepository = orderRepository;
        this.versionRepository = versionRepository;
    }

    @Transactional(readOnly = true)
    public InventoryAdjustment getAdjustment(String adjustmentNo) {
        return adjustmentRepository.findByAdjustmentNo(adjustmentNo)
                .orElseThrow(() -> new NotFoundException("库存调整不存在: " + adjustmentNo));
    }

    @Transactional
    public InventoryAdjustment adjust(AdjustmentRequest request) {
        // 幂等快路径（加锁前）：编号已存在直接返回原记录。
        if (adjustmentRepository.existsByAdjustmentNo(request.adjustmentNo())) {
            return adjustmentRepository.findByAdjustmentNo(request.adjustmentNo()).orElseThrow();
        }

        // 与领料保持一致的加锁顺序：先工单后批次，避免跨操作死锁。
        ProductionOrder lockedOrder = null;
        if (request.orderNo() != null && !request.orderNo().isBlank()) {
            lockedOrder = orderRepository.findForUpdateByOrderNo(request.orderNo())
                    .orElseThrow(() -> new NotFoundException("工单不存在: " + request.orderNo()));
            lockedOrder.requireOpen();
        }
        final ProductionOrder order = lockedOrder;
        MaterialBatch batch = batchRepository.findForUpdateByBatchNo(request.batchNo())
                .orElseThrow(() -> new NotFoundException("原料批次不存在: " + request.batchNo()));

        // 锁内再次确认幂等，挡住并发的同号请求。
        return adjustmentRepository.findByAdjustmentNo(request.adjustmentNo())
                .orElseGet(() -> doAdjust(request, order, batch));
    }

    private InventoryAdjustment doAdjust(AdjustmentRequest request, ProductionOrder order, MaterialBatch batch) {
        BigDecimal delta = Quantities.normalize(request.deltaQuantity());
        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessRuleException("调整数量不能为 0");
        }

        String requirementCode = null;
        BigDecimal equivalent = null;
        if (order != null) {
            if (request.requirementMaterialCode() == null || request.requirementMaterialCode().isBlank()) {
                throw new BusinessRuleException("挂工单的调整必须指定折入的配方主料 requirementMaterialCode");
            }
            RecipeVersion version = versionRepository.findWithDetailsById(order.getRecipeVersion().getId())
                    .orElseThrow(() -> new IllegalStateException("工单绑定的配方版本丢失"));
            RecipeIngredient ingredient = version.findIngredient(request.requirementMaterialCode());
            if (ingredient == null) {
                throw new BusinessRuleException(
                        "配方中没有主料 " + request.requirementMaterialCode());
            }
            BigDecimal factor;
            if (batch.getMaterialCode().equals(ingredient.getMaterialCode())) {
                factor = BigDecimal.ONE;
            } else {
                IngredientSubstitution substitution = ingredient.findSubstitution(batch.getMaterialCode());
                if (substitution == null) {
                    throw new BusinessRuleException("批次物料 " + batch.getMaterialCode()
                            + " 既不是主料 " + ingredient.getMaterialCode() + "，也不是其允许的替代料");
                }
                factor = substitution.getConversionFactor();
            }
            requirementCode = ingredient.getMaterialCode();
            // 带符号折算：正为冲回，负为核销。
            equivalent = Quantities.normalize(delta.multiply(factor));
        }

        batch.adjust(delta);
        return adjustmentRepository.save(new InventoryAdjustment(
                request.adjustmentNo(), order, batch, delta, requirementCode, equivalent, request.reason()));
    }
}
