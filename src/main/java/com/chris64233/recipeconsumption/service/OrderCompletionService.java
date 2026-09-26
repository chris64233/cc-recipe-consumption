package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.domain.AdjustmentRecordRepository;
import com.chris64233.recipeconsumption.domain.IssueLineBatchRepository;
import com.chris64233.recipeconsumption.domain.OrderCompletion;
import com.chris64233.recipeconsumption.domain.OrderCompletionLoss;
import com.chris64233.recipeconsumption.domain.OrderCompletionRepository;
import com.chris64233.recipeconsumption.domain.OrderStatus;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.domain.ProductionOrderRepository;
import com.chris64233.recipeconsumption.domain.RecipeItem;
import com.chris64233.recipeconsumption.domain.ReturnRecordRepository;
import com.chris64233.recipeconsumption.support.QuantityMath;
import com.chris64233.recipeconsumption.web.dto.CompleteOrderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 完工核对服务。
 *
 * 数量关系（全部折标准原料）：
 *   净领料 = 累计领料 - 累计退料 + 累计调整（调整带符号）
 *   净领料 = 实际产量 × 标准单耗 + 损耗
 * 关系不成立（按统一精度比较）时拒绝完工；已完工工单不可重复完工。
 */
@Service
public class OrderCompletionService {

    private final ProductionOrderRepository orderRepository;
    private final OrderCompletionRepository completionRepository;
    private final IssueLineBatchRepository lineBatchRepository;
    private final ReturnRecordRepository returnRecordRepository;
    private final AdjustmentRecordRepository adjustmentRepository;
    private final Clock clock;

    public OrderCompletionService(ProductionOrderRepository orderRepository,
                                  OrderCompletionRepository completionRepository,
                                  IssueLineBatchRepository lineBatchRepository,
                                  ReturnRecordRepository returnRecordRepository,
                                  AdjustmentRecordRepository adjustmentRepository,
                                  Clock clock) {
        this.orderRepository = orderRepository;
        this.completionRepository = completionRepository;
        this.lineBatchRepository = lineBatchRepository;
        this.returnRecordRepository = returnRecordRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.clock = clock;
    }

    @Transactional
    public OrderCompletion complete(CompleteOrderRequest request) {
        Long orderId = orderRepository.findIdByOrderNo(request.orderNo())
                .orElseThrow(() -> new NotFoundException("工单不存在: " + request.orderNo()));
        ProductionOrder lockedOrder = orderRepository.findByIdForUpdate(orderId).orElseThrow();
        if (lockedOrder.getStatus() == OrderStatus.COMPLETED) {
            throw new BusinessRuleException("工单已完工，不能重复完工: " + request.orderNo());
        }

        BigDecimal actualQty = QuantityMath.normalize(request.actualQty());
        if (actualQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("实际产量必须大于 0");
        }

        Map<String, BigDecimal> lossByMaterial = new HashMap<>();
        Set<String> seenLossMaterials = new HashSet<>();
        for (CompleteOrderRequest.LossLine line : request.losses()) {
            if (!seenLossMaterials.add(line.materialCode())) {
                throw new BusinessRuleException("损耗行原料重复: " + line.materialCode());
            }
            BigDecimal loss = QuantityMath.normalize(line.lossQty());
            if (loss.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessRuleException("损耗不能为负: " + line.materialCode());
            }
            lossByMaterial.put(line.materialCode(), loss);
        }

        // 每个配方原料逐一核对
        for (RecipeItem item : lockedOrder.getRecipeVersion().getItems()) {
            String material = item.getMaterialCode();
            if (!lossByMaterial.containsKey(material)) {
                throw new BusinessRuleException("缺少配方原料的损耗申报行: " + material);
            }
        }
        for (String lossMaterial : lossByMaterial.keySet()) {
            boolean belongs = lockedOrder.getRecipeVersion().getItems().stream()
                    .anyMatch(i -> i.getMaterialCode().equals(lossMaterial));
            if (!belongs) {
                throw new BusinessRuleException("损耗行原料不属于配方: " + lossMaterial);
            }
        }

        for (RecipeItem item : lockedOrder.getRecipeVersion().getItems()) {
            String material = item.getMaterialCode();

            BigDecimal issued = QuantityMath.normalize(
                    lineBatchRepository.sumEquivalentByRecipeMaterial(lockedOrder.getId(), material));
            BigDecimal returned = QuantityMath.normalize(
                    returnRecordRepository.sumEquivalent(lockedOrder.getId(), material));
            BigDecimal adjusted = QuantityMath.normalize(
                    adjustmentRepository.sumEquivalent(lockedOrder.getId(), material));
            BigDecimal netIssued = QuantityMath.add(
                    QuantityMath.subtract(issued, returned), adjusted);
            if (netIssued.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessRuleException(String.format(
                        "原料 %s 净领料为负（领 %s - 退 %s + 调 %s），记录存在矛盾",
                        material, issued, returned, adjusted));
            }

            BigDecimal consumed = QuantityMath.multiply(item.getStandardQty(), actualQty);
            BigDecimal loss = lossByMaterial.get(material);
            BigDecimal required = QuantityMath.add(consumed, loss);

            if (netIssued.compareTo(required) != 0) {
                BigDecimal diff = QuantityMath.subtract(netIssued, required);
                throw new BusinessRuleException(String.format(
                        "原料 %s 数量关系不成立，拒绝完工：净领料 %s，实际产量消耗 %s（标准单耗 %s × 产量 %s），损耗 %s，差异 %s",
                        material, netIssued, consumed, item.getStandardQty(), actualQty, loss, diff));
            }
        }

        lockedOrder.complete(actualQty);
        OrderCompletion completion = new OrderCompletion(lockedOrder, actualQty, Instant.now(clock));
        for (RecipeItem item : lockedOrder.getRecipeVersion().getItems()) {
            completion.addLoss(new OrderCompletionLoss(
                    item.getMaterialCode(), lossByMaterial.get(item.getMaterialCode())));
        }
        return completionRepository.save(completion);
    }
}
