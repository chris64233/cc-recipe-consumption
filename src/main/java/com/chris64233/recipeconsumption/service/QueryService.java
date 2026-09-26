package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.domain.AdjustmentRecord;
import com.chris64233.recipeconsumption.domain.AdjustmentRecordRepository;
import com.chris64233.recipeconsumption.domain.IssueLineBatch;
import com.chris64233.recipeconsumption.domain.IssueLineBatchRepository;
import com.chris64233.recipeconsumption.domain.MaterialBatch;
import com.chris64233.recipeconsumption.domain.MaterialBatchRepository;
import com.chris64233.recipeconsumption.domain.OrderCompletion;
import com.chris64233.recipeconsumption.domain.OrderCompletionRepository;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.domain.ProductionOrderRepository;
import com.chris64233.recipeconsumption.domain.RecipeItem;
import com.chris64233.recipeconsumption.domain.RecipeSubstitution;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.domain.ReturnRecord;
import com.chris64233.recipeconsumption.domain.ReturnRecordRepository;
import com.chris64233.recipeconsumption.service.dto.BatchTraceView;
import com.chris64233.recipeconsumption.service.dto.OrderMaterialUsageView;
import com.chris64233.recipeconsumption.service.dto.ProductionVarianceView;
import com.chris64233.recipeconsumption.service.dto.SubstitutionView;
import com.chris64233.recipeconsumption.support.QuantityMath;
import com.chris64233.recipeconsumption.web.dto.IssueRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 查询服务：工单用料、替代计算预览、批次去向、产量差异。
 * 所有数量均为统一精度下的折标准原料口径（另注明实物数量的除外）。
 */
@Service
@Transactional(readOnly = true)
public class QueryService {

    private final ProductionOrderRepository orderRepository;
    private final MaterialBatchRepository batchRepository;
    private final IssueLineBatchRepository lineBatchRepository;
    private final ReturnRecordRepository returnRecordRepository;
    private final AdjustmentRecordRepository adjustmentRepository;
    private final OrderCompletionRepository completionRepository;

    public QueryService(ProductionOrderRepository orderRepository,
                        MaterialBatchRepository batchRepository,
                        IssueLineBatchRepository lineBatchRepository,
                        ReturnRecordRepository returnRecordRepository,
                        AdjustmentRecordRepository adjustmentRepository,
                        OrderCompletionRepository completionRepository) {
        this.orderRepository = orderRepository;
        this.batchRepository = batchRepository;
        this.lineBatchRepository = lineBatchRepository;
        this.returnRecordRepository = returnRecordRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.completionRepository = completionRepository;
    }

    /** 工单用料：逐配方原料的领用/退料/调整/净用量及替代构成。 */
    public OrderMaterialUsageView usage(String orderNo) {
        ProductionOrder order = loadOrder(orderNo);
        RecipeVersion recipe = order.getRecipeVersion();

        List<IssueLineBatch> allBatches = lineBatchRepository.findByOrderId(order.getId());
        List<ReturnRecord> returns = returnRecordRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        List<AdjustmentRecord> adjustments = adjustmentRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());

        List<OrderMaterialUsageView.MaterialUsage> materials = new ArrayList<>();
        for (RecipeItem item : recipe.getItems()) {
            String material = item.getMaterialCode();
            BigDecimal demand = QuantityMath.multiply(item.getStandardQty(), order.getPlannedQty());

            // 按实物原料聚合领用（标准原料自身 + 各替代料）
            Map<String, BigDecimal> issuedByActual = new LinkedHashMap<>();
            BigDecimal issuedTotal = BigDecimal.ZERO;
            for (IssueLineBatch ilb : allBatches) {
                if (!Objects.equals(ilb.getLine().getRecipeMaterialCode(), material)) {
                    continue;
                }
                issuedTotal = QuantityMath.add(issuedTotal, ilb.getEquivalentQty());
                issuedByActual.merge(ilb.getMaterialCode(), ilb.getEquivalentQty(), QuantityMath::add);
            }

            BigDecimal returned = returns.stream()
                    .filter(r -> Objects.equals(r.getRecipeMaterialCode(), material))
                    .map(ReturnRecord::getEquivalentQty)
                    .reduce(BigDecimal.ZERO, QuantityMath::add);
            BigDecimal adjusted = adjustments.stream()
                    .filter(a -> Objects.equals(a.getRecipeMaterialCode(), material))
                    .map(AdjustmentRecord::getEquivalentDelta)
                    .reduce(BigDecimal.ZERO, QuantityMath::add);
            BigDecimal net = QuantityMath.add(QuantityMath.subtract(issuedTotal, returned), adjusted);

            List<OrderMaterialUsageView.SubstituteUsage> subs = new ArrayList<>();
            for (RecipeSubstitution sub : item.getSubstitutions()) {
                BigDecimal subEquiv = QuantityMath.normalize(
                        issuedByActual.getOrDefault(sub.getSubstituteMaterialCode(), BigDecimal.ZERO));
                if (subEquiv.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                BigDecimal ratioInDemand = demand.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : QuantityMath.normalizeRatio(
                                subEquiv.divide(demand, QuantityMath.RATIO_SCALE, java.math.RoundingMode.HALF_UP));
                subs.add(new OrderMaterialUsageView.SubstituteUsage(
                        sub.getSubstituteMaterialCode(), subEquiv, ratioInDemand,
                        subEquiv.compareTo(QuantityMath.multiply(demand, sub.getMaxRatio())) > 0));
            }

            materials.add(new OrderMaterialUsageView.MaterialUsage(
                    material, item.getStandardQty(), demand,
                    issuedTotal, returned, adjusted, net, subs));
        }

        return new OrderMaterialUsageView(order.getId(), order.getOrderNo(),
                recipe.getRecipeCode(), recipe.getVersionNo(), order.getPlannedQty(),
                order.getActualQty(), order.getStatus().name(), materials);
    }

    /**
     * 替代计算预览：对一笔尚未提交的领料请求，按配方换算关系计算
     * 每个替代料的折标准当量、工单累计口径与最大比例上限，allowed 表示是否放行。
     * 只读，不锁库存、不扣减。
     */
    public List<SubstitutionView> previewSubstitution(IssueRequest request) {
        ProductionOrder order = loadOrder(request.orderNo());
        Map<String, RecipeItem> items = order.getRecipeVersion().getItems().stream()
                .collect(java.util.stream.Collectors.toMap(RecipeItem::getMaterialCode, i -> i));

        List<SubstitutionView> views = new ArrayList<>();
        for (IssueRequest.Line line : request.lines()) {
            RecipeItem item = items.get(line.recipeMaterialCode());
            if (item == null) {
                throw new BusinessRuleException("原料不属于工单绑定的配方: " + line.recipeMaterialCode());
            }
            BigDecimal demand = QuantityMath.multiply(item.getStandardQty(), order.getPlannedQty());

            // 按实物原料汇总本次申请的实物数量
            Map<String, BigDecimal> physicalByMaterial = new LinkedHashMap<>();
            for (IssueRequest.BatchPick pick : line.batches()) {
                MaterialBatch batch = batchRepository.findByBatchNo(pick.batchNo())
                        .orElseThrow(() -> new NotFoundException("原料批次不存在: " + pick.batchNo()));
                physicalByMaterial.merge(batch.getMaterialCode(),
                        QuantityMath.normalize(pick.qty()), QuantityMath::add);
            }

            for (RecipeSubstitution sub : item.getSubstitutions()) {
                BigDecimal physical = QuantityMath.normalize(
                        physicalByMaterial.getOrDefault(sub.getSubstituteMaterialCode(), BigDecimal.ZERO));
                if (physical.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                BigDecimal equivalent = QuantityMath.toEquivalent(physical, sub.getConversionRatio());
                BigDecimal prior = QuantityMath.normalize(lineBatchRepository.sumEquivalent(
                        order.getId(), item.getMaterialCode(), sub.getSubstituteMaterialCode()));
                BigDecimal total = QuantityMath.add(prior, equivalent);
                BigDecimal cap = QuantityMath.multiply(demand, sub.getMaxRatio());
                views.add(new SubstitutionView(
                        item.getMaterialCode(), sub.getSubstituteMaterialCode(),
                        sub.getConversionRatio(), physical, equivalent, prior, total,
                        demand, sub.getMaxRatio(), cap, total.compareTo(cap) <= 0));
            }
        }
        return views;
    }

    /** 批次去向：当前库存与在各工单上的领/退/调整流水。 */
    public BatchTraceView batchTrace(String batchNo) {
        MaterialBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new NotFoundException("原料批次不存在: " + batchNo));

        List<BatchTraceView.Movement> movements = new ArrayList<>();
        for (IssueLineBatch ilb : lineBatchRepository.findByBatchId(batch.getId())) {
            movements.add(new BatchTraceView.Movement(
                    "ISSUE", ilb.getLine().getRecord().getCreatedAt(),
                    ilb.getLine().getRecord().getBizNo(),
                    ilb.getLine().getRecord().getOrder().getOrderNo(),
                    ilb.getLine().getRecipeMaterialCode(),
                    ilb.getQty(), ilb.getEquivalentQty(), null));
        }
        for (ReturnRecord r : returnRecordRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId())) {
            movements.add(new BatchTraceView.Movement(
                    "RETURN", r.getCreatedAt(), r.getBizNo(), r.getOrder().getOrderNo(),
                    r.getRecipeMaterialCode(), r.getQty(), r.getEquivalentQty(), r.getReason()));
        }
        for (AdjustmentRecord a : adjustmentRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId())) {
            movements.add(new BatchTraceView.Movement(
                    "ADJUSTMENT", a.getCreatedAt(), a.getBizNo(), a.getOrder().getOrderNo(),
                    a.getRecipeMaterialCode(), a.getQtyDelta(), a.getEquivalentDelta(), a.getReason()));
        }
        movements.sort(Comparator.comparing(BatchTraceView.Movement::time));

        return new BatchTraceView(batch.getBatchNo(), batch.getMaterialCode(),
                batch.getAvailableQty(), batch.getQualityStatus().name(), movements);
    }

    /** 产量差异：产量差异及逐原料净领料与应耗/损耗的差异。 */
    public ProductionVarianceView variance(String orderNo) {
        ProductionOrder order = loadOrder(orderNo);
        boolean completed = order.getActualQty() != null;
        BigDecimal referenceQty = completed ? order.getActualQty() : order.getPlannedQty();
        BigDecimal outputVariance = completed
                ? QuantityMath.subtract(order.getActualQty(), order.getPlannedQty())
                : null;

        Map<String, BigDecimal> declaredLossByMaterial = new LinkedHashMap<>();
        if (completed) {
            OrderCompletion completion = completionRepository.findByOrderId(order.getId()).orElseThrow();
            completion.getLosses().forEach(l ->
                    declaredLossByMaterial.put(l.getMaterialCode(), QuantityMath.normalize(l.getLossQty())));
        }

        OrderMaterialUsageView usage = usage(orderNo);
        List<ProductionVarianceView.MaterialVariance> rows = new ArrayList<>();
        for (OrderMaterialUsageView.MaterialUsage m : usage.materials()) {
            BigDecimal standardConsumption =
                    QuantityMath.multiply(m.standardQty(), referenceQty);
            BigDecimal declaredLoss = QuantityMath.normalize(
                    declaredLossByMaterial.getOrDefault(m.recipeMaterialCode(), BigDecimal.ZERO));
            BigDecimal unexplained = QuantityMath.subtract(
                    m.netEquivalentQty(), QuantityMath.add(standardConsumption, declaredLoss));
            rows.add(new ProductionVarianceView.MaterialVariance(
                    m.recipeMaterialCode(), m.netEquivalentQty(),
                    standardConsumption, declaredLoss, unexplained));
        }

        return new ProductionVarianceView(order.getOrderNo(), order.getStatus().name(),
                order.getPlannedQty(), order.getActualQty(), outputVariance, rows);
    }

    private ProductionOrder loadOrder(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new NotFoundException("工单不存在: " + orderNo));
    }
}
