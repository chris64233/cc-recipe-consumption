package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.domain.AdjustmentRecord;
import com.chris64233.recipeconsumption.domain.AdjustmentRecordRepository;
import com.chris64233.recipeconsumption.domain.MaterialBatch;
import com.chris64233.recipeconsumption.domain.MaterialBatchRepository;
import com.chris64233.recipeconsumption.domain.OrderStatus;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.domain.ProductionOrderRepository;
import com.chris64233.recipeconsumption.domain.RecipeItem;
import com.chris64233.recipeconsumption.domain.RecipeSubstitution;
import com.chris64233.recipeconsumption.support.QuantityMath;
import com.chris64233.recipeconsumption.web.dto.AdjustmentRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 调整服务：通过追加调整记录修复领料错误（如账实不符），原始领料记录不变。
 * qtyDelta 带符号修正批次库存（负向调整不得造成负库存），
 * equivalentDelta 必须与 qtyDelta 同号且等于其按规定系数折算的标准当量。
 * 已完工工单不允许调整。
 */
@Service
public class AdjustmentService {

    private final ProductionOrderRepository orderRepository;
    private final MaterialBatchRepository batchRepository;
    private final AdjustmentRecordRepository adjustmentRepository;
    private final Clock clock;

    public AdjustmentService(ProductionOrderRepository orderRepository,
                             MaterialBatchRepository batchRepository,
                             AdjustmentRecordRepository adjustmentRepository,
                             Clock clock) {
        this.orderRepository = orderRepository;
        this.batchRepository = batchRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.clock = clock;
    }

    @Transactional
    public AdjustmentRecord adjust(AdjustmentRequest request) {
        if (adjustmentRepository.findByBizNo(request.bizNo()).isPresent()) {
            throw new IdempotentConflictException("调整业务号已被使用: " + request.bizNo());
        }

        MaterialBatch lockedBatch = batchRepository.findByBatchNoForUpdate(request.batchNo())
                .orElseThrow(() -> new NotFoundException("原料批次不存在: " + request.batchNo()));

        Long orderId = orderRepository.findIdByOrderNo(request.orderNo())
                .orElseThrow(() -> new NotFoundException("工单不存在: " + request.orderNo()));
        ProductionOrder lockedOrder = orderRepository.findByIdForUpdate(orderId).orElseThrow();
        if (lockedOrder.getStatus() == OrderStatus.COMPLETED) {
            throw new BusinessRuleException("工单已完工，不能再调整: " + request.orderNo());
        }

        RecipeItem item = lockedOrder.getRecipeVersion().getItems().stream()
                .filter(i -> Objects.equals(i.getMaterialCode(), request.recipeMaterialCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "原料不属于工单绑定的配方: " + request.recipeMaterialCode()));

        BigDecimal ratio;
        if (Objects.equals(lockedBatch.getMaterialCode(), item.getMaterialCode())) {
            ratio = BigDecimal.ONE;
        } else {
            ratio = item.getSubstitutions().stream()
                    .filter(s -> Objects.equals(s.getSubstituteMaterialCode(), lockedBatch.getMaterialCode()))
                    .map(RecipeSubstitution::getConversionRatio)
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException(String.format(
                            "批次原料 %s 不允许替代配方原料 %s",
                            lockedBatch.getMaterialCode(), item.getMaterialCode())));
        }

        BigDecimal qtyDelta = QuantityMath.normalize(request.qtyDelta());
        if (qtyDelta.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessRuleException("调整数量不能为 0");
        }
        BigDecimal equivalentDelta = QuantityMath.normalize(request.equivalentDelta());
        BigDecimal expectedEquivalent = QuantityMath.toEquivalent(qtyDelta, ratio);
        if (equivalentDelta.compareTo(expectedEquivalent) != 0) {
            throw new BusinessRuleException(String.format(
                    "折标准当量与换算系数不一致：传入 %s，应为 %s（数量 %s × 系数 %s）",
                    equivalentDelta, expectedEquivalent, qtyDelta, ratio));
        }
        if (equivalentDelta.signum() * qtyDelta.signum() < 0) {
            throw new BusinessRuleException("库存调整量与用量调整量方向必须一致");
        }

        BigDecimal newQty = QuantityMath.add(lockedBatch.getAvailableQty(), qtyDelta);
        if (newQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException(String.format(
                    "调整后批次 %s 库存将为负：当前 %s，调整 %s",
                    lockedBatch.getBatchNo(), lockedBatch.getAvailableQty(), qtyDelta));
        }
        lockedBatch.setAvailableQty(newQty);

        AdjustmentRecord record = new AdjustmentRecord(request.bizNo(), lockedOrder, lockedBatch,
                item.getMaterialCode(), lockedBatch.getMaterialCode(), qtyDelta, equivalentDelta,
                request.reason(), Instant.now(clock));
        try {
            adjustmentRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            throw new IdempotentConflictException("调整业务号已被使用: " + request.bizNo());
        }
        return record;
    }
}
