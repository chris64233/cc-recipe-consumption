package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.domain.CompletionLine;
import com.chris64233.recipeconsumption.domain.CompletionRecord;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.domain.RecipeIngredient;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.dto.CompleteRequest;
import com.chris64233.recipeconsumption.repo.CompletionRecordRepository;
import com.chris64233.recipeconsumption.repo.InventoryAdjustmentRepository;
import com.chris64233.recipeconsumption.repo.MaterialIssueLineRepository;
import com.chris64233.recipeconsumption.repo.MaterialReturnLineRepository;
import com.chris64233.recipeconsumption.repo.ProductionOrderRepository;
import com.chris64233.recipeconsumption.repo.RecipeVersionRepository;
import com.chris64233.recipeconsumption.support.BusinessRuleException;
import com.chris64233.recipeconsumption.support.NotFoundException;
import com.chris64233.recipeconsumption.support.Quantities;
import com.chris64233.recipeconsumption.support.QuantityBalanceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工单完工与产量核对。
 *
 * <p>按配方主料逐行配平（全部折主料口径）：
 * <pre>
 * 实际净耗 = 已领总量 − 退料量 − 调整净值(正冲回/负核销)
 * 未说明差异 = 实际净耗 − 标准单耗×实际产量 − 申报损耗
 * </pre>
 * 任一主料的未说明差异不为 0（超出 {@value #VARIANCE_TOLERANCE} 容差）即拒绝完工；
 * 必须先通过退料（说明余料）、调整（核销盘亏/冲回盘盈）或申报损耗把账做平。
 */
@Service
public class CompletionService {

    /** 配平容差：数量统一 4 位小数，容差取 1 个最小单位。 */
    public static final BigDecimal VARIANCE_TOLERANCE = new BigDecimal("0.0001");

    private final ProductionOrderRepository orderRepository;
    private final RecipeVersionRepository versionRepository;
    private final CompletionRecordRepository completionRepository;
    private final MaterialIssueLineRepository issueLineRepository;
    private final MaterialReturnLineRepository returnLineRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;

    public CompletionService(ProductionOrderRepository orderRepository,
                             RecipeVersionRepository versionRepository,
                             CompletionRecordRepository completionRepository,
                             MaterialIssueLineRepository issueLineRepository,
                             MaterialReturnLineRepository returnLineRepository,
                             InventoryAdjustmentRepository adjustmentRepository) {
        this.orderRepository = orderRepository;
        this.versionRepository = versionRepository;
        this.completionRepository = completionRepository;
        this.issueLineRepository = issueLineRepository;
        this.returnLineRepository = returnLineRepository;
        this.adjustmentRepository = adjustmentRepository;
    }

    @Transactional
    public CompletionRecord complete(String orderNo, CompleteRequest request) {
        // 幂等先于状态校验：完工后的重放也要安全返回原记录。
        CompletionRecord existing = completionRepository.findByCompletionNo(request.completionNo()).orElse(null);
        if (existing != null) {
            if (!existing.getProductionOrder().getOrderNo().equals(orderNo)) {
                throw new BusinessRuleException("完工号 " + request.completionNo() + " 已用于其他工单");
            }
            return existing;
        }

        ProductionOrder order = orderRepository.findForUpdateByOrderNo(orderNo)
                .orElseThrow(() -> new NotFoundException("工单不存在: " + orderNo));
        order.requireOpen();
        if (completionRepository.findByProductionOrder_Id(order.getId()).isPresent()) {
            throw new BusinessRuleException("工单已有完工记录");
        }

        BigDecimal actualQuantity = Quantities.require("actualQuantity", request.actualQuantity(), true);
        Map<String, BigDecimal> losses = request.lossMap();
        RecipeVersion version = versionRepository.findWithDetailsById(order.getRecipeVersion().getId())
                .orElseThrow(() -> new IllegalStateException("工单绑定的配方版本丢失"));
        for (String lossCode : losses.keySet()) {
            if (version.findIngredient(lossCode) == null) {
                throw new BusinessRuleException("申报损耗的物料 " + lossCode + " 不在配方版本中");
            }
        }

        CompletionRecord record = new CompletionRecord(
                request.completionNo(), order, actualQuantity, request.remark());
        List<String> failures = new ArrayList<>();

        for (RecipeIngredient ingredient : version.getIngredients()) {
            String code = ingredient.getMaterialCode();
            BigDecimal expected = Quantities.multiply(ingredient.getStandardQuantityPerUnit(), actualQuantity);
            BigDecimal issued = Quantities.normalize(
                    issueLineRepository.sumEquivalentPrimary(order.getId(), code));
            BigDecimal substituted = Quantities.normalize(
                    issueLineRepository.sumEquivalentPrimaryByFulfillment(
                            order.getId(), code,
                            com.chris64233.recipeconsumption.domain.FulfillmentType.SUBSTITUTE));
            BigDecimal returned = Quantities.normalize(
                    returnLineRepository.sumEquivalentPrimary(order.getId(), code));
            BigDecimal adjustedNet = Quantities.normalize(
                    adjustmentRepository.sumEquivalentPrimary(order.getId(), code));
            BigDecimal declaredLoss = Quantities.normalize(losses.getOrDefault(code, BigDecimal.ZERO));

            // 未说明差异 = 已领 − 退料 − 调整净值 − 申报损耗 − 应耗
            BigDecimal variance = Quantities.normalize(
                    issued.subtract(returned).subtract(adjustedNet)
                            .subtract(declaredLoss).subtract(expected));

            if (variance.abs().compareTo(VARIANCE_TOLERANCE) > 0) {
                failures.add("主料 " + code + ": 已领(折主料) " + issued + " − 退料 " + returned
                        + " − 调整净值 " + adjustedNet + " − 申报损耗 " + declaredLoss
                        + " ≠ 应耗 " + expected + "，未说明差异 " + variance);
            }

            new CompletionLine(record, code, expected, issued, substituted, returned,
                    adjustedNet, declaredLoss, variance);
        }

        if (!failures.isEmpty()) {
            // 明细已在内存构建但不保存，事务回滚；抛出明确的不平衡信息。
            throw new QuantityBalanceException("产量核对不通过，工单不得完工: " + String.join("; ", failures));
        }

        order.markCompleted(actualQuantity);
        return completionRepository.save(record);
    }
}
