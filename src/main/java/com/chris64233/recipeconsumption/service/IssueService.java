package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.domain.IssueLine;
import com.chris64233.recipeconsumption.domain.IssueLineBatch;
import com.chris64233.recipeconsumption.domain.IssueLineBatchRepository;
import com.chris64233.recipeconsumption.domain.IssueRecord;
import com.chris64233.recipeconsumption.domain.IssueRecordRepository;
import com.chris64233.recipeconsumption.domain.MaterialBatch;
import com.chris64233.recipeconsumption.domain.MaterialBatchRepository;
import com.chris64233.recipeconsumption.domain.OrderStatus;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.domain.ProductionOrderRepository;
import com.chris64233.recipeconsumption.domain.QualityStatus;
import com.chris64233.recipeconsumption.domain.RecipeItem;
import com.chris64233.recipeconsumption.domain.RecipeSubstitution;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.support.QuantityMath;
import com.chris64233.recipeconsumption.web.dto.IssueRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 领料服务。
 *
 * 关键保证：
 * 1. 原子性：校验、批次扣减、领料明细落库在同一事务内，任一原料不足或替代比例超限整体回滚；
 * 2. 幂等：bizNo 唯一，重复提交返回首次领料结果；
 * 3. 并发安全：先按批次 id 升序加悲观锁，再锁工单，避免负库存与死锁；
 *    替代比例按工单累计口径在锁内复核；
 * 4. 已完工工单拒绝领料。
 */
@Service
public class IssueService {

    private final ProductionOrderRepository orderRepository;
    private final MaterialBatchRepository batchRepository;
    private final IssueRecordRepository issueRecordRepository;
    private final IssueLineBatchRepository lineBatchRepository;
    private final Clock clock;

    public IssueService(ProductionOrderRepository orderRepository,
                        MaterialBatchRepository batchRepository,
                        IssueRecordRepository issueRecordRepository,
                        IssueLineBatchRepository lineBatchRepository,
                        Clock clock) {
        this.orderRepository = orderRepository;
        this.batchRepository = batchRepository;
        this.issueRecordRepository = issueRecordRepository;
        this.lineBatchRepository = lineBatchRepository;
        this.clock = clock;
    }

    @Transactional
    public IssueRecord issue(IssueRequest request) {
        // 幂等快速通道
        IssueRecord existing = issueRecordRepository.findByBizNo(request.bizNo()).orElse(null);
        if (existing != null) {
            return replayOrConflict(existing, request);
        }

        Long orderId = orderRepository.findIdByOrderNo(request.orderNo())
                .orElseThrow(() -> new NotFoundException("工单不存在: " + request.orderNo()));

        // 一次性加锁加载所有涉及批次（id 升序，全局资源顺序，防止死锁），
        // 不在锁外预读实体，避免并发更新后持久化上下文版本冲突
        List<String> batchNos = request.lines().stream()
                .flatMap(l -> l.batches().stream())
                .map(IssueRequest.BatchPick::batchNo)
                .distinct()
                .toList();
        Map<String, MaterialBatch> lockedBatchesByNo = batchRepository.findByBatchNosForUpdate(batchNos)
                .stream()
                .collect(Collectors.toMap(MaterialBatch::getBatchNo, b -> b, (a, b) -> a, LinkedHashMap::new));
        for (String batchNo : batchNos) {
            if (!lockedBatchesByNo.containsKey(batchNo)) {
                throw new NotFoundException("原料批次不存在: " + batchNo);
            }
        }
        Map<Long, MaterialBatch> lockedBatches = lockedBatchesByNo.values().stream()
                .collect(Collectors.toMap(MaterialBatch::getId, b -> b));

        // 批次锁之后再锁工单：避免与其它事务形成锁顺序环
        ProductionOrder lockedOrder = orderRepository.findByIdForUpdate(orderId).orElseThrow();
        if (lockedOrder.getStatus() == OrderStatus.COMPLETED) {
            throw new BusinessRuleException("工单已完工，不能继续领料: " + request.orderNo());
        }

        // 锁内二次幂等检查
        existing = issueRecordRepository.findByBizNo(request.bizNo()).orElse(null);
        if (existing != null) {
            return replayOrConflict(existing, request);
        }

        RecipeVersion recipe = lockedOrder.getRecipeVersion();
        Map<String, RecipeItem> itemsByMaterial = recipe.getItems().stream()
                .collect(Collectors.toMap(RecipeItem::getMaterialCode, i -> i));

        LocalDate today = LocalDate.now(clock);

        // 同一请求内配方原料行不可重复
        Set<String> seenRecipeMaterials = new HashSet<>();
        for (IssueRequest.Line line : request.lines()) {
            if (!seenRecipeMaterials.add(line.recipeMaterialCode())) {
                throw new BusinessRuleException(
                        "同一领料请求中配方原料重复: " + line.recipeMaterialCode());
            }
        }

        // 先完成全部校验与扣减量计算，任何一步失败都抛异常 → 整个事务回滚，不留部分领料
        List<LinePlan> plans = new ArrayList<>();
        Map<Long, BigDecimal> deductions = new HashMap<>();

        for (IssueRequest.Line line : request.lines()) {
            RecipeItem item = itemsByMaterial.get(line.recipeMaterialCode());
            if (item == null) {
                throw new BusinessRuleException(
                        "原料不属于工单绑定的配方: " + line.recipeMaterialCode());
            }
            Map<String, RecipeSubstitution> substitutes = item.getSubstitutions().stream()
                    .collect(Collectors.toMap(RecipeSubstitution::getSubstituteMaterialCode, s -> s));

            // 本请求内该配方原料各实物原料的折标准当量合计
            Map<String, BigDecimal> requestedEquivByMaterial = new LinkedHashMap<>();
            List<PickPlan> picks = new ArrayList<>();

            Set<Long> lineBatchIds = new HashSet<>();
            for (IssueRequest.BatchPick pick : line.batches()) {
                MaterialBatch batch = lockedBatchesByNo.get(pick.batchNo());
                if (!lineBatchIds.add(batch.getId())) {
                    throw new BusinessRuleException(
                            "同一领料行内批次重复: " + pick.batchNo());
                }

                BigDecimal ratio;
                boolean substitute;
                if (Objects.equals(batch.getMaterialCode(), item.getMaterialCode())) {
                    ratio = BigDecimal.ONE;
                    substitute = false;
                } else {
                    RecipeSubstitution sub = substitutes.get(batch.getMaterialCode());
                    if (sub == null) {
                        throw new BusinessRuleException(String.format(
                                "批次 %s 的原料 %s 不允许替代配方原料 %s",
                                pick.batchNo(), batch.getMaterialCode(), item.getMaterialCode()));
                    }
                    ratio = sub.getConversionRatio();
                    substitute = true;
                }

                if (batch.getQualityStatus() != QualityStatus.AVAILABLE) {
                    throw new BusinessRuleException(String.format(
                            "批次 %s 质量状态为 %s，不允许领料",
                            batch.getBatchNo(), batch.getQualityStatus()));
                }
                if (batch.getExpiryDate().isBefore(today)) {
                    throw new BusinessRuleException(String.format(
                            "批次 %s 已过有效期（%s）", batch.getBatchNo(), batch.getExpiryDate()));
                }

                BigDecimal qty = QuantityMath.normalize(pick.qty());
                if (!QuantityMath.isPositive(qty)) {
                    throw new BusinessRuleException("领料数量必须大于 0: " + batch.getBatchNo());
                }
                BigDecimal equivalent = QuantityMath.toEquivalent(qty, ratio);

                picks.add(new PickPlan(batch, qty, equivalent, substitute, ratio));
                requestedEquivByMaterial.merge(batch.getMaterialCode(), equivalent, QuantityMath::add);
                deductions.merge(batch.getId(), qty, QuantityMath::add);
            }

            // 最大替代比例：按工单累计（历史领料 + 本次）折标准当量复核
            BigDecimal demand = QuantityMath.multiply(item.getStandardQty(), lockedOrder.getPlannedQty());
            for (Map.Entry<String, BigDecimal> entry : requestedEquivByMaterial.entrySet()) {
                String materialCode = entry.getKey();
                boolean isSubstitute = !materialCode.equals(item.getMaterialCode());
                if (!isSubstitute) {
                    continue;
                }
                RecipeSubstitution sub = substitutes.get(materialCode);
                BigDecimal prior = QuantityMath.normalize(
                        lineBatchRepository.sumEquivalent(lockedOrder.getId(),
                                item.getMaterialCode(), materialCode));
                BigDecimal after = QuantityMath.add(prior, entry.getValue());
                BigDecimal cap = QuantityMath.multiply(demand, sub.getMaxRatio());
                if (after.compareTo(cap) > 0) {
                    throw new BusinessRuleException(String.format(
                            "替代料 %s 替代比例超限：累计折标准当量 %s，上限 %s（需求量 %s × 最大比例 %s）",
                            materialCode, after, cap, demand, sub.getMaxRatio()));
                }
            }

            plans.add(new LinePlan(item.getMaterialCode(), picks));
        }

        // 库存充足性：同一批次可能在多行出现，必须按合计扣减校验，杜绝负库存
        for (Map.Entry<Long, BigDecimal> entry : deductions.entrySet()) {
            MaterialBatch batch = lockedBatches.get(entry.getKey());
            BigDecimal need = entry.getValue();
            if (batch.getAvailableQty().compareTo(need) < 0) {
                throw new BusinessRuleException(String.format(
                        "批次 %s（原料 %s）库存不足：需要 %s，可用 %s",
                        batch.getBatchNo(), batch.getMaterialCode(), need, batch.getAvailableQty()));
            }
        }

        // 全部校验通过：执行扣减并落明细
        IssueRecord record = new IssueRecord(request.bizNo(), lockedOrder, Instant.now(clock));
        for (LinePlan plan : plans) {
            IssueLine line = new IssueLine(plan.recipeMaterial());
            BigDecimal lineEquivalent = BigDecimal.ZERO;
            for (PickPlan pick : plan.picks()) {
                MaterialBatch batch = lockedBatches.get(pick.batch().getId());
                batch.setAvailableQty(QuantityMath.subtract(batch.getAvailableQty(), pick.qty()));
                line.addBatch(new IssueLineBatch(batch, batch.getMaterialCode(), pick.qty(),
                        pick.equivalent(), pick.substitute(), pick.ratio()));
                lineEquivalent = QuantityMath.add(lineEquivalent, pick.equivalent());
            }
            line.setEquivalentQty(lineEquivalent);
            record.addLine(line);
        }

        try {
            issueRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // 并发下 bizNo 唯一约束兜底
            throw new IdempotentConflictException("领料业务号已被使用: " + request.bizNo());
        }
        return record;
    }

    private IssueRecord replayOrConflict(IssueRecord existing, IssueRequest request) {
        if (!existing.getOrder().getOrderNo().equals(request.orderNo())) {
            throw new IdempotentConflictException(
                    "业务号 " + request.bizNo() + " 已用于其它工单，不能重复使用");
        }
        // 幂等命中的记录可能来自锁外只读查询，在事务内初始化懒加载关联，
        // 供事务提交后的响应序列化使用
        existing.getOrder().getOrderNo();
        existing.getLines().forEach(line -> line.getBatches()
                .forEach(batch -> batch.getBatch().getBatchNo()));
        return existing;
    }

    private record PickPlan(MaterialBatch batch, BigDecimal qty, BigDecimal equivalent,
                            boolean substitute, BigDecimal ratio) {
    }

    private record LinePlan(String recipeMaterial, List<PickPlan> picks) {
    }
}
