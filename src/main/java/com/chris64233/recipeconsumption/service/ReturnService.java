package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.domain.IssueLineBatchRepository;
import com.chris64233.recipeconsumption.domain.MaterialBatch;
import com.chris64233.recipeconsumption.domain.MaterialBatchRepository;
import com.chris64233.recipeconsumption.domain.OrderStatus;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.domain.ProductionOrderRepository;
import com.chris64233.recipeconsumption.domain.RecipeItem;
import com.chris64233.recipeconsumption.domain.RecipeSubstitution;
import com.chris64233.recipeconsumption.domain.ReturnRecord;
import com.chris64233.recipeconsumption.domain.ReturnRecordRepository;
import com.chris64233.recipeconsumption.support.QuantityMath;
import com.chris64233.recipeconsumption.web.dto.ReturnRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 退料服务。退料只追加新记录并把实物数量加回批次，原始领料记录永不修改。
 * 已完工工单不允许退料；退回数量不得超过该工单从该批次实际领用的净额。
 */
@Service
public class ReturnService {

    private final ProductionOrderRepository orderRepository;
    private final MaterialBatchRepository batchRepository;
    private final ReturnRecordRepository returnRecordRepository;
    private final IssueLineBatchRepository lineBatchRepository;
    private final Clock clock;

    public ReturnService(ProductionOrderRepository orderRepository,
                         MaterialBatchRepository batchRepository,
                         ReturnRecordRepository returnRecordRepository,
                         IssueLineBatchRepository lineBatchRepository,
                         Clock clock) {
        this.orderRepository = orderRepository;
        this.batchRepository = batchRepository;
        this.returnRecordRepository = returnRecordRepository;
        this.lineBatchRepository = lineBatchRepository;
        this.clock = clock;
    }

    @Transactional
    public ReturnRecord returnMaterial(ReturnRequest request) {
        if (returnRecordRepository.findByBizNo(request.bizNo()).isPresent()) {
            throw new IdempotentConflictException("退料业务号已被使用: " + request.bizNo());
        }

        MaterialBatch lockedBatch = batchRepository.findByBatchNoForUpdate(request.batchNo())
                .orElseThrow(() -> new NotFoundException("原料批次不存在: " + request.batchNo()));

        // 先锁批次再锁工单，与领料保持一致的全局资源顺序
        Long orderId = orderRepository.findIdByOrderNo(request.orderNo())
                .orElseThrow(() -> new NotFoundException("工单不存在: " + request.orderNo()));
        ProductionOrder lockedOrder = orderRepository.findByIdForUpdate(orderId).orElseThrow();
        if (lockedOrder.getStatus() == OrderStatus.COMPLETED) {
            throw new BusinessRuleException("工单已完工，不能再退料: " + request.orderNo());
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

        BigDecimal qty = QuantityMath.normalize(request.qty());
        if (!QuantityMath.isPositive(qty)) {
            throw new BusinessRuleException("退料数量必须大于 0");
        }

        BigDecimal issued = QuantityMath.normalize(lineBatchRepository.sumQtyByBatch(
                lockedOrder.getId(), item.getMaterialCode(), lockedBatch.getId()));
        BigDecimal alreadyReturned = QuantityMath.normalize(returnRecordRepository.sumQty(
                lockedOrder.getId(), item.getMaterialCode(), lockedBatch.getId()));
        BigDecimal returnable = QuantityMath.subtract(issued, alreadyReturned);
        if (qty.compareTo(returnable) > 0) {
            throw new BusinessRuleException(String.format(
                    "退料数量超过该工单从批次 %s 的可退净额：本次 %s，可退 %s",
                    lockedBatch.getBatchNo(), qty, returnable));
        }

        BigDecimal equivalent = QuantityMath.toEquivalent(qty, ratio);
        lockedBatch.setAvailableQty(QuantityMath.add(lockedBatch.getAvailableQty(), qty));

        ReturnRecord record = new ReturnRecord(request.bizNo(), lockedOrder, lockedBatch,
                item.getMaterialCode(), lockedBatch.getMaterialCode(), qty, equivalent,
                request.reason(), Instant.now(clock));
        try {
            returnRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            throw new IdempotentConflictException("退料业务号已被使用: " + request.bizNo());
        }
        return record;
    }
}
