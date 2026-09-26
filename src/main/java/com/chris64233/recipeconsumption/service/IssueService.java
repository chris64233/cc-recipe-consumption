package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.domain.FulfillmentType;
import com.chris64233.recipeconsumption.domain.IngredientSubstitution;
import com.chris64233.recipeconsumption.domain.MaterialBatch;
import com.chris64233.recipeconsumption.domain.MaterialIssue;
import com.chris64233.recipeconsumption.domain.MaterialIssueBatchAllocation;
import com.chris64233.recipeconsumption.domain.MaterialIssueLine;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.domain.RecipeIngredient;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.dto.IssueRequest;
import com.chris64233.recipeconsumption.repo.MaterialBatchRepository;
import com.chris64233.recipeconsumption.repo.MaterialIssueLineRepository;
import com.chris64233.recipeconsumption.repo.MaterialIssueRepository;
import com.chris64233.recipeconsumption.repo.ProductionOrderRepository;
import com.chris64233.recipeconsumption.repo.RecipeVersionRepository;
import com.chris64233.recipeconsumption.support.BusinessRuleException;
import com.chris64233.recipeconsumption.support.NotFoundException;
import com.chris64233.recipeconsumption.support.Quantities;
import com.chris64233.recipeconsumption.support.QuantityBalanceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 生产领料核心服务。
 *
 * <p>一次领料在同一个数据库事务内完成：工单加锁 → 幂等判定 → 多批次 FEFO 分配
 * → 替代换算与最大替代比例校验 → 批次库存扣减 → 领料单/行/批次明细落库。
 * 任何一步失败整个事务回滚，绝不留下部分领料。
 */
@Service
public class IssueService {

    private final ProductionOrderRepository orderRepository;
    private final RecipeVersionRepository versionRepository;
    private final MaterialBatchRepository batchRepository;
    private final MaterialIssueRepository issueRepository;
    private final MaterialIssueLineRepository issueLineRepository;

    public IssueService(ProductionOrderRepository orderRepository,
                        RecipeVersionRepository versionRepository,
                        MaterialBatchRepository batchRepository,
                        MaterialIssueRepository issueRepository,
                        MaterialIssueLineRepository issueLineRepository) {
        this.orderRepository = orderRepository;
        this.versionRepository = versionRepository;
        this.batchRepository = batchRepository;
        this.issueRepository = issueRepository;
        this.issueLineRepository = issueLineRepository;
    }

    /** 单批次分配计划。 */
    private record AllocationPlan(MaterialBatch batch, BigDecimal quantity) {
    }

    /** 单行解析后的领料计划。 */
    private record LinePlan(IssueRequest.Line request,
                            RecipeIngredient ingredient,
                            FulfillmentType fulfillmentType,
                            String pickedMaterialCode,
                            BigDecimal pickedQuantity,
                            BigDecimal conversionFactor,
                            BigDecimal equivalentPrimary,
                            BigDecimal maxSubstitutionRatio,
                            List<AllocationPlan> allocations) {
    }

    @Transactional(readOnly = true)
    public MaterialIssue getIssue(String issueNo) {
        return issueRepository.findWithDetailsByIssueNo(issueNo)
                .orElseThrow(() -> new NotFoundException("领料单不存在: " + issueNo));
    }

    @Transactional
    public MaterialIssue issue(String orderNo, IssueRequest request) {
        // 1. 锁工单，串行化同一工单上的所有领料；幂等判定在锁内进行。
        ProductionOrder order = orderRepository.findForUpdateByOrderNo(orderNo)
                .orElseThrow(() -> new NotFoundException("工单不存在: " + orderNo));
        MaterialIssue existing = issueRepository.findByIssueNo(request.issueNo()).orElse(null);
        if (existing != null) {
            if (!existing.getProductionOrder().getId().equals(order.getId())) {
                throw new BusinessRuleException("领料号 " + request.issueNo() + " 已用于其他工单");
            }
            // 幂等：重新带明细加载后原样返回，不重复扣减。
            return issueRepository.findWithDetailsByIssueNo(request.issueNo()).orElseThrow();
        }
        order.requireOpen();

        RecipeVersion version = versionRepository.findWithDetailsById(order.getRecipeVersion().getId())
                .orElseThrow(() -> new IllegalStateException("工单绑定的配方版本丢失"));
        LocalDate today = LocalDate.now();

        // 2. 解析与校验所有行（先不改动库存）。
        List<LinePlan> linePlans = resolveLines(request, version);

        // 3. 一次性锁定本次涉及的全部物料批次（按 id 排序加锁，避免跨工单死锁）。
        Set<String> pickedCodes = new HashSet<>();
        linePlans.forEach(p -> pickedCodes.add(p.pickedMaterialCode()));
        List<MaterialBatch> lockedBatches = batchRepository.findForUpdateByMaterialCodes(pickedCodes);
        Map<String, List<MaterialBatch>> batchesByMaterial = new LinkedHashMap<>();
        for (MaterialBatch batch : lockedBatches) {
            batchesByMaterial.computeIfAbsent(batch.getMaterialCode(), k -> new ArrayList<>()).add(batch);
        }

        // 4. FEFO 分配；替代比例按“主料 + 具体替代料”逐对聚合，
        //    用“历史累计 + 本单该替代料全部行”一次性校验。
        Map<String, BigDecimal> substituteInThisIssue = new LinkedHashMap<>();
        List<LinePlan> finalized = new ArrayList<>();
        for (LinePlan plan : linePlans) {
            List<MaterialBatch> candidates = batchesByMaterial.getOrDefault(plan.pickedMaterialCode(), List.of())
                    .stream()
                    .filter(b -> b.isUsable(today))
                    .sorted(Comparator.comparing(MaterialBatch::getExpiryDate).thenComparing(MaterialBatch::getId))
                    .toList();
            List<AllocationPlan> allocations = allocate(plan, candidates);
            LinePlan finalizedPlan = new LinePlan(plan.request(), plan.ingredient(), plan.fulfillmentType(),
                    plan.pickedMaterialCode(), plan.pickedQuantity(), plan.conversionFactor(),
                    plan.equivalentPrimary(), plan.maxSubstitutionRatio(), allocations);
            if (finalizedPlan.fulfillmentType() == FulfillmentType.SUBSTITUTE) {
                substituteInThisIssue.merge(
                        finalizedPlan.ingredient().getMaterialCode() + "|" + finalizedPlan.pickedMaterialCode(),
                        finalizedPlan.equivalentPrimary(), Quantities::add);
            }
            finalized.add(finalizedPlan);
        }
        for (Map.Entry<String, BigDecimal> entry : substituteInThisIssue.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            LinePlan anySubstitute = finalized.stream()
                    .filter(p -> p.fulfillmentType() == FulfillmentType.SUBSTITUTE
                            && p.ingredient().getMaterialCode().equals(parts[0])
                            && p.pickedMaterialCode().equals(parts[1]))
                    .findFirst().orElseThrow();
            checkSubstitutionRatio(order, anySubstitute, entry.getValue());
        }

        // 5. 全部校验通过后才真正扣减库存并写单据（原子提交）。
        MaterialIssue issue = new MaterialIssue(request.issueNo(), order, request.remark());
        int idx = 1;
        for (LinePlan plan : finalized) {
            MaterialIssueLine line = new MaterialIssueLine(issue, idx++,
                    plan.ingredient().getMaterialCode(), plan.fulfillmentType(),
                    plan.pickedMaterialCode(), pickedName(plan), plan.pickedQuantity(),
                    plan.conversionFactor(), plan.equivalentPrimary());
            for (AllocationPlan allocation : plan.allocations()) {
                allocation.batch().reserve(allocation.quantity());
                new MaterialIssueBatchAllocation(line, allocation.batch(), allocation.quantity());
            }
        }
        return issueRepository.save(issue);
    }

    private List<LinePlan> resolveLines(IssueRequest request, RecipeVersion version) {
        List<LinePlan> plans = new ArrayList<>();
        // 同一主料可以同时有主料行和替代行（一次领料部分替代），但同一实际物料只能一行。
        Set<String> seenRequirementPicked = new HashSet<>();
        Set<String> seenRequirementPrimary = new HashSet<>();
        for (IssueRequest.Line line : request.lines()) {
            RecipeIngredient ingredient = version.findIngredient(line.requirementMaterialCode());
            if (ingredient == null) {
                throw new BusinessRuleException(
                        "物料 " + line.requirementMaterialCode() + " 不在工单绑定的配方版本中");
            }
            BigDecimal pickedQuantity = Quantities.require("pickedQuantity", line.pickedQuantity(), true);

            FulfillmentType fulfillmentType;
            String pickedMaterialCode;
            BigDecimal conversionFactor;
            BigDecimal maxRatio;
            if (line.pickedMaterialCode() == null || line.pickedMaterialCode().isBlank()
                    || line.pickedMaterialCode().equals(ingredient.getMaterialCode())) {
                fulfillmentType = FulfillmentType.PRIMARY;
                pickedMaterialCode = ingredient.getMaterialCode();
                conversionFactor = BigDecimal.ONE.setScale(Quantities.RATIO_SCALE);
                maxRatio = null;
                if (!seenRequirementPrimary.add(line.requirementMaterialCode())) {
                    throw new BusinessRuleException(
                            "一次领料中主料 " + line.requirementMaterialCode() + " 的直接领料只能有一行");
                }
            } else {
                IngredientSubstitution substitution = ingredient.findSubstitution(line.pickedMaterialCode());
                if (substitution == null) {
                    throw new BusinessRuleException("配方不允许用 " + line.pickedMaterialCode()
                            + " 替代主料 " + ingredient.getMaterialCode());
                }
                fulfillmentType = FulfillmentType.SUBSTITUTE;
                pickedMaterialCode = substitution.getSubstituteMaterialCode();
                conversionFactor = substitution.getConversionFactor();
                maxRatio = substitution.getMaxSubstitutionRatio();
            }
            if (!seenRequirementPicked.add(line.requirementMaterialCode() + "|" + pickedMaterialCode)) {
                throw new BusinessRuleException("一次领料中主料 " + line.requirementMaterialCode()
                        + " 的同一实际物料 " + pickedMaterialCode + " 只能出现一行");
            }
            BigDecimal equivalent = Quantities.normalize(pickedQuantity.multiply(conversionFactor));
            plans.add(new LinePlan(line, ingredient, fulfillmentType, pickedMaterialCode,
                    pickedQuantity, conversionFactor, equivalent, maxRatio, List.of()));
        }
        return plans;
    }

    /**
     * 从可用批次中按 FEFO 顺序凑齐 pickedQuantity；任一批次不足时整体失败。
     */
    private List<AllocationPlan> allocate(LinePlan plan, List<MaterialBatch> candidates) {
        List<AllocationPlan> allocations = new ArrayList<>();
        BigDecimal remaining = plan.pickedQuantity();
        for (MaterialBatch batch : candidates) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal take = batch.getAvailableQuantity().min(remaining);
            if (take.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            allocations.add(new AllocationPlan(batch, Quantities.normalize(take)));
            remaining = Quantities.normalize(remaining.subtract(take));
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new QuantityBalanceException("物料 " + plan.pickedMaterialCode()
                    + " 合格且在有效期内的批次库存不足，尚缺 " + remaining
                    + "（待检/不合格/过期批次不可领用）");
        }
        return allocations;
    }

    /**
     * 替代比例的累计校验：该“主料+替代料”历史已替代量 + 本单替代量
     * ≤ 该替代关系的最大比例 × 主料标准需用量。
     */
    private void checkSubstitutionRatio(ProductionOrder order, LinePlan plan, BigDecimal thisIssueEquivalent) {
        BigDecimal alreadySubstituted = Quantities.normalize(
                issueLineRepository.sumEquivalentPrimaryByFulfillmentForPicked(
                        order.getId(), plan.ingredient().getMaterialCode(),
                        plan.pickedMaterialCode(), FulfillmentType.SUBSTITUTE));
        BigDecimal standardNeed = Quantities.multiply(
                plan.ingredient().getStandardQuantityPerUnit(), order.getPlannedQuantity());
        BigDecimal maxAllowed = Quantities.normalize(standardNeed.multiply(plan.maxSubstitutionRatio()));
        BigDecimal total = Quantities.add(alreadySubstituted, thisIssueEquivalent);
        if (Quantities.gt(total, maxAllowed)) {
            throw new QuantityBalanceException("主料 " + plan.ingredient().getMaterialCode()
                    + " 用替代料 " + plan.pickedMaterialCode() + " 的累计替代量 " + total
                    + " 超过最大替代比例允许的 " + maxAllowed
                    + "（标准需用量 " + standardNeed + "，比例上限 " + plan.maxSubstitutionRatio() + "）");
        }
    }

    private String pickedName(LinePlan plan) {
        return plan.fulfillmentType() == FulfillmentType.PRIMARY
                ? plan.ingredient().getMaterialName()
                : plan.ingredient().findSubstitution(plan.pickedMaterialCode()).getSubstituteMaterialName();
    }
}
