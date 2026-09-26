package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.domain.FulfillmentType;
import com.chris64233.recipeconsumption.domain.IngredientSubstitution;
import com.chris64233.recipeconsumption.domain.InventoryAdjustment;
import com.chris64233.recipeconsumption.domain.MaterialBatch;
import com.chris64233.recipeconsumption.domain.MaterialIssueBatchAllocation;
import com.chris64233.recipeconsumption.domain.MaterialReturnLine;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.domain.RecipeIngredient;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.dto.BatchTraceView;
import com.chris64233.recipeconsumption.dto.CompletionView;
import com.chris64233.recipeconsumption.dto.OrderUsageView;
import com.chris64233.recipeconsumption.dto.SubstitutionPreviewRequest;
import com.chris64233.recipeconsumption.dto.SubstitutionPreviewView;
import com.chris64233.recipeconsumption.repo.InventoryAdjustmentRepository;
import com.chris64233.recipeconsumption.repo.MaterialBatchRepository;
import com.chris64233.recipeconsumption.repo.MaterialIssueBatchAllocationRepository;
import com.chris64233.recipeconsumption.repo.MaterialIssueLineRepository;
import com.chris64233.recipeconsumption.repo.MaterialReturnLineRepository;
import com.chris64233.recipeconsumption.repo.ProductionOrderRepository;
import com.chris64233.recipeconsumption.repo.RecipeVersionRepository;
import com.chris64233.recipeconsumption.support.BusinessRuleException;
import com.chris64233.recipeconsumption.support.NotFoundException;
import com.chris64233.recipeconsumption.support.Quantities;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 只读查询：工单用料、替代试算、批次去向、产量差异（完工实绩或完工前预估）。
 */
@Service
public class QueryService {

    private final ProductionOrderRepository orderRepository;
    private final RecipeVersionRepository versionRepository;
    private final MaterialBatchRepository batchRepository;
    private final MaterialIssueLineRepository issueLineRepository;
    private final MaterialReturnLineRepository returnLineRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final MaterialIssueBatchAllocationRepository allocationRepository;
    private final com.chris64233.recipeconsumption.repo.CompletionRecordRepository completionRepository;
    private final ViewMapper viewMapper;

    public QueryService(ProductionOrderRepository orderRepository,
                        RecipeVersionRepository versionRepository,
                        MaterialBatchRepository batchRepository,
                        MaterialIssueLineRepository issueLineRepository,
                        MaterialReturnLineRepository returnLineRepository,
                        InventoryAdjustmentRepository adjustmentRepository,
                        MaterialIssueBatchAllocationRepository allocationRepository,
                        com.chris64233.recipeconsumption.repo.CompletionRecordRepository completionRepository,
                        ViewMapper viewMapper) {
        this.orderRepository = orderRepository;
        this.versionRepository = versionRepository;
        this.batchRepository = batchRepository;
        this.issueLineRepository = issueLineRepository;
        this.returnLineRepository = returnLineRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.allocationRepository = allocationRepository;
        this.completionRepository = completionRepository;
        this.viewMapper = viewMapper;
    }

    /** 工单用料汇总（每配方主料一行）。 */
    @Transactional(readOnly = true)
    public OrderUsageView orderUsage(String orderNo) {
        ProductionOrder order = requireOrder(orderNo);
        RecipeVersion version = loadVersion(order);

        List<OrderUsageView.LineView> lines = new ArrayList<>();
        for (RecipeIngredient ingredient : version.getIngredients()) {
            String code = ingredient.getMaterialCode();
            BigDecimal standardNeed = Quantities.multiply(
                    ingredient.getStandardQuantityPerUnit(), order.getPlannedQuantity());
            BigDecimal issuedTotal = Quantities.normalize(
                    issueLineRepository.sumEquivalentPrimary(order.getId(), code));
            BigDecimal issuedSub = Quantities.normalize(
                    issueLineRepository.sumEquivalentPrimaryByFulfillment(
                            order.getId(), code, FulfillmentType.SUBSTITUTE));
            BigDecimal issuedPrimary = Quantities.subtract(issuedTotal, issuedSub);
            BigDecimal returned = Quantities.normalize(
                    returnLineRepository.sumEquivalentPrimary(order.getId(), code));
            BigDecimal adjustedNet = Quantities.normalize(
                    adjustmentRepository.sumEquivalentPrimary(order.getId(), code));
            BigDecimal netConsumed = Quantities.normalize(
                    issuedTotal.subtract(returned).subtract(adjustedNet));

            // 最大替代比例按“该主料所有允许替代关系中的最宽松上限”展示；
            // 严格的逐替代料上限在领料时按具体替代关系校验。
            BigDecimal maxRatio = ingredient.getSubstitutions().stream()
                    .map(IngredientSubstitution::getMaxSubstitutionRatio)
                    .max(BigDecimal::compareTo)
                    .orElse(null);
            BigDecimal ratio = standardNeed.signum() == 0 ? Quantities.ZERO
                    : issuedSub.divide(standardNeed, Quantities.RATIO_SCALE, java.math.RoundingMode.HALF_UP);
            boolean within = maxRatio == null
                    || issuedSub.compareTo(Quantities.normalize(standardNeed.multiply(maxRatio))) <= 0;

            lines.add(new OrderUsageView.LineView(code, ingredient.getMaterialName(),
                    ingredient.getStandardQuantityPerUnit(), standardNeed,
                    issuedPrimary, issuedSub, issuedTotal, returned, adjustedNet, netConsumed,
                    Quantities.normalizeRatio(ratio), maxRatio, within));
        }
        return new OrderUsageView(order.getOrderNo(), version.getRecipe().getCode(),
                version.getVersionNo(), order.getPlannedQuantity(), order.getStatus().name(), lines);
    }

    /** 替代计算试算：不落库、不锁库存，只给出换算结果和比例判断。 */
    @Transactional(readOnly = true)
    public SubstitutionPreviewView previewSubstitution(String orderNo, SubstitutionPreviewRequest request) {
        ProductionOrder order = requireOrder(orderNo);
        RecipeVersion version = loadVersion(order);

        List<SubstitutionPreviewView.LineView> lines = new ArrayList<>();
        for (SubstitutionPreviewRequest.Line line : request.lines()) {
            RecipeIngredient ingredient = version.findIngredient(line.requirementMaterialCode());
            if (ingredient == null) {
                throw new BusinessRuleException(
                        "物料 " + line.requirementMaterialCode() + " 不在工单绑定的配方版本中");
            }
            IngredientSubstitution substitution = ingredient.findSubstitution(line.substituteMaterialCode());
            if (substitution == null) {
                throw new BusinessRuleException("配方不允许用 " + line.substituteMaterialCode()
                        + " 替代主料 " + ingredient.getMaterialCode());
            }
            BigDecimal requestedEquivalent = Quantities.require(
                    "substituteEquivalentQuantity", line.substituteEquivalentQuantity(), true);
            // 需要实领的替代料数量 = 想替代的折主料量 / 换算系数。
            BigDecimal requiredPicked = requestedEquivalent.divide(
                    substitution.getConversionFactor(), Quantities.SCALE, java.math.RoundingMode.HALF_UP);
            BigDecimal standardNeed = Quantities.multiply(
                    ingredient.getStandardQuantityPerUnit(), order.getPlannedQuantity());
            BigDecimal maxAllowed = Quantities.normalize(
                    standardNeed.multiply(substitution.getMaxSubstitutionRatio()));
            BigDecimal already = Quantities.normalize(
                    issueLineRepository.sumEquivalentPrimaryByFulfillmentForPicked(
                            order.getId(), ingredient.getMaterialCode(),
                            substitution.getSubstituteMaterialCode(), FulfillmentType.SUBSTITUTE));
            BigDecimal projectedTotal = Quantities.add(already, requestedEquivalent);
            BigDecimal projectedRatio = standardNeed.signum() == 0 ? Quantities.ZERO
                    : projectedTotal.divide(standardNeed, Quantities.RATIO_SCALE,
                            java.math.RoundingMode.HALF_UP);
            lines.add(new SubstitutionPreviewView.LineView(ingredient.getMaterialCode(),
                    substitution.getSubstituteMaterialCode(), substitution.getConversionFactor(),
                    requestedEquivalent, requiredPicked, standardNeed,
                    substitution.getMaxSubstitutionRatio(), maxAllowed, already,
                    Quantities.normalizeRatio(projectedRatio),
                    projectedTotal.compareTo(maxAllowed) <= 0));
        }
        return new SubstitutionPreviewView(order.getOrderNo(), order.getPlannedQuantity(), lines);
    }

    /** 批次去向：扣减、回补、调整流水。 */
    @Transactional(readOnly = true)
    public BatchTraceView traceBatch(String batchNo) {
        MaterialBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new NotFoundException("原料批次不存在: " + batchNo));

        List<BatchTraceView.IssueFlow> issueFlows = new ArrayList<>();
        for (MaterialIssueBatchAllocation allocation : allocationRepository.findByBatchIdOrderByIdAsc(batch.getId())) {
            var line = allocation.getIssueLine();
            var issue = line.getIssue();
            issueFlows.add(new BatchTraceView.IssueFlow(
                    issue.getProductionOrder().getOrderNo(), issue.getIssueNo(), issue.getCreatedAt(),
                    line.getRequirementMaterialCode(), line.getFulfillmentType().name(),
                    allocation.getQuantity()));
        }

        List<BatchTraceView.ReturnFlow> returnFlows = new ArrayList<>();
        for (MaterialReturnLine line : returnLineRepository.findByBatchIdOrderByIdAsc(batch.getId())) {
            returnFlows.add(new BatchTraceView.ReturnFlow(
                    line.getMaterialReturn().getProductionOrder().getOrderNo(),
                    line.getMaterialReturn().getReturnNo(),
                    line.getMaterialReturn().getCreatedAt(),
                    line.getMaterialReturn().getMaterialIssue().getIssueNo(),
                    line.getQuantity()));
        }

        List<BatchTraceView.AdjustmentFlow> adjustmentFlows = new ArrayList<>();
        for (InventoryAdjustment adjustment : adjustmentRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId())) {
            adjustmentFlows.add(new BatchTraceView.AdjustmentFlow(
                    adjustment.getProductionOrder() == null ? null
                            : adjustment.getProductionOrder().getOrderNo(),
                    adjustment.getAdjustmentNo(), adjustment.getCreatedAt(),
                    adjustment.getDeltaQuantity(), adjustment.getReason()));
        }

        return new BatchTraceView(batch.getBatchNo(), batch.getMaterialCode(), batch.getMaterialName(),
                batch.getAvailableQuantity(), batch.getQualityStatus().name(),
                issueFlows, returnFlows, adjustmentFlows);
    }

    /**
     * 产量差异查询。
     * <ul>
     *   <li>已完工：返回完工实绩（实际产量、逐主料配平明细）；</li>
     *   <li>未完工：按可选的预估实际产量（缺省取计划产量）实时计算逐主料未说明差异，
     *       provisional=true，用于完工前自查，不写任何记录。</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public CompletionView outputVariance(String orderNo, BigDecimal projectedActualQuantity) {
        ProductionOrder order = requireOrder(orderNo);
        return completionRepository.findByProductionOrder_Id(order.getId())
                .map(this::completedView)
                .orElseGet(() -> provisionalView(order, projectedActualQuantity));
    }

    private CompletionView completedView(
            com.chris64233.recipeconsumption.domain.CompletionRecord record) {
        return viewMapper.toView(record);
    }

    private CompletionView provisionalView(ProductionOrder order, BigDecimal projectedActualQuantity) {
        RecipeVersion version = loadVersion(order);
        BigDecimal actual = projectedActualQuantity == null
                ? order.getPlannedQuantity()
                : Quantities.require("projectedActualQuantity", projectedActualQuantity, true);
        List<CompletionView.LineView> lineViews = new ArrayList<>();
        for (RecipeIngredient ingredient : version.getIngredients()) {
            String code = ingredient.getMaterialCode();
            BigDecimal expected = Quantities.multiply(ingredient.getStandardQuantityPerUnit(), actual);
            BigDecimal issued = Quantities.normalize(
                    issueLineRepository.sumEquivalentPrimary(order.getId(), code));
            BigDecimal substituted = Quantities.normalize(
                    issueLineRepository.sumEquivalentPrimaryByFulfillment(
                            order.getId(), code, FulfillmentType.SUBSTITUTE));
            BigDecimal returned = Quantities.normalize(
                    returnLineRepository.sumEquivalentPrimary(order.getId(), code));
            BigDecimal adjustedNet = Quantities.normalize(
                    adjustmentRepository.sumEquivalentPrimary(order.getId(), code));
            // 预估时申报损耗未知，按 0 处理，差异即“尚需通过退料/调整/损耗说明的量”。
            BigDecimal variance = Quantities.normalize(
                    issued.subtract(returned).subtract(adjustedNet).subtract(expected));
            BigDecimal ratio = expected.signum() == 0 ? null
                    : substituted.divide(expected, Quantities.RATIO_SCALE, java.math.RoundingMode.HALF_UP);
            lineViews.add(new CompletionView.LineView(code, expected, issued, substituted,
                    ratio == null ? null : Quantities.normalizeRatio(ratio), returned, adjustedNet,
                    Quantities.ZERO, variance,
                    variance.abs().compareTo(CompletionService.VARIANCE_TOLERANCE) <= 0));
        }
        BigDecimal outputVariance = Quantities.normalize(actual.subtract(order.getPlannedQuantity()));
        return new CompletionView(null, order.getOrderNo(), order.getStatus().name(),
                order.getPlannedQuantity(), actual, outputVariance, true,
                order.getCreatedAt(), null, lineViews);
    }

    private ProductionOrder requireOrder(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new NotFoundException("工单不存在: " + orderNo));
    }

    private RecipeVersion loadVersion(ProductionOrder order) {
        return versionRepository.findWithDetailsById(order.getRecipeVersion().getId())
                .orElseThrow(() -> new IllegalStateException("工单绑定的配方版本丢失"));
    }
}
