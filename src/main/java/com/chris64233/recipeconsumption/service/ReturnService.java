package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.domain.MaterialBatch;
import com.chris64233.recipeconsumption.domain.MaterialIssue;
import com.chris64233.recipeconsumption.domain.MaterialIssueBatchAllocation;
import com.chris64233.recipeconsumption.domain.MaterialIssueLine;
import com.chris64233.recipeconsumption.domain.MaterialReturn;
import com.chris64233.recipeconsumption.domain.MaterialReturnLine;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.dto.ReturnRequest;
import com.chris64233.recipeconsumption.repo.MaterialBatchRepository;
import com.chris64233.recipeconsumption.repo.MaterialIssueRepository;
import com.chris64233.recipeconsumption.repo.MaterialReturnLineRepository;
import com.chris64233.recipeconsumption.repo.MaterialReturnRepository;
import com.chris64233.recipeconsumption.repo.ProductionOrderRepository;
import com.chris64233.recipeconsumption.support.BusinessRuleException;
import com.chris64233.recipeconsumption.support.NotFoundException;
import com.chris64233.recipeconsumption.support.Quantities;
import com.chris64233.recipeconsumption.support.QuantityBalanceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 退料服务。退料只追加新的 {@link MaterialReturn} 记录并回补批次库存，
 * 原始领料单保持不变；退料数量受“该领料行/批次已领未退数量”约束。
 * 整个退料（含逐批回补）原子完成。
 */
@Service
public class ReturnService {

    private final ProductionOrderRepository orderRepository;
    private final MaterialIssueRepository issueRepository;
    private final MaterialReturnRepository returnRepository;
    private final MaterialReturnLineRepository returnLineRepository;
    private final MaterialBatchRepository batchRepository;

    public ReturnService(ProductionOrderRepository orderRepository,
                         MaterialIssueRepository issueRepository,
                         MaterialReturnRepository returnRepository,
                         MaterialReturnLineRepository returnLineRepository,
                         MaterialBatchRepository batchRepository) {
        this.orderRepository = orderRepository;
        this.issueRepository = issueRepository;
        this.returnRepository = returnRepository;
        this.returnLineRepository = returnLineRepository;
        this.batchRepository = batchRepository;
    }

    private record BatchReturn(MaterialIssueBatchAllocation allocation, BigDecimal quantity) {
    }

    @Transactional(readOnly = true)
    public MaterialReturn getReturn(String returnNo) {
        return returnRepository.findWithDetailsByReturnNo(returnNo)
                .orElseThrow(() -> new NotFoundException("退料单不存在: " + returnNo));
    }

    @Transactional
    public MaterialReturn createReturn(ReturnRequest request) {
        MaterialIssue issue = issueRepository.findByIssueNo(request.issueNo())
                .orElseThrow(() -> new NotFoundException("领料单不存在: " + request.issueNo()));

        // 幂等先于状态校验：完工后的同号重放也要安全返回原退料单。
        MaterialReturn existing = returnRepository.findByReturnNo(request.returnNo()).orElse(null);
        if (existing != null) {
            if (!existing.getMaterialIssue().getId().equals(issue.getId())) {
                throw new BusinessRuleException("退料号 " + request.returnNo() + " 已用于其他领料单");
            }
            return existing;
        }

        // 锁工单，串行化同一工单的退料/领料/完工。
        ProductionOrder order = orderRepository.findForUpdateByOrderNo(issue.getProductionOrder().getOrderNo())
                .orElseThrow(() -> new NotFoundException("工单不存在"));
        order.requireOpen();

        Map<String, MaterialIssueLine> linesByRequirement = issue.getLines().stream()
                .collect(Collectors.toMap(MaterialIssueLine::getRequirementMaterialCode, l -> l,
                        (a, b) -> {
                            throw new BusinessRuleException("领料单存在重复主料行，数据异常");
                        },
                        LinkedHashMap::new));

        Set<String> seen = new HashSet<>();
        Map<MaterialIssueLine, List<BatchReturn>> plan = new LinkedHashMap<>();
        for (ReturnRequest.Line lineReq : request.lines()) {
            if (!seen.add(lineReq.requirementMaterialCode())) {
                throw new BusinessRuleException(
                        "一次退料中同一主料只能出现一行: " + lineReq.requirementMaterialCode());
            }
            MaterialIssueLine issueLine = linesByRequirement.get(lineReq.requirementMaterialCode());
            if (issueLine == null) {
                throw new BusinessRuleException(
                        "领料单 " + request.issueNo() + " 中没有主料 " + lineReq.requirementMaterialCode() + " 的记录");
            }
            BigDecimal qty = Quantities.require("退料数量", lineReq.quantity(), true);
            List<BatchReturn> batchReturns = planBatchReturns(issueLine, lineReq.batchNo(), qty);
            plan.put(issueLine, batchReturns);
        }

        // 锁定所有涉及的批次后再回补，按 id 排序加锁。
        Set<Long> batchIds = plan.values().stream().flatMap(List::stream)
                .map(br -> br.allocation().getBatchId()).collect(Collectors.toSet());
        Map<Long, MaterialBatch> batchMap = batchRepository.findForUpdateByIds(batchIds).stream()
                .collect(Collectors.toMap(MaterialBatch::getId, b -> b));

        MaterialReturn materialReturn = new MaterialReturn(request.returnNo(), order, issue, request.reason());
        for (Map.Entry<MaterialIssueLine, List<BatchReturn>> entry : plan.entrySet()) {
            MaterialIssueLine issueLine = entry.getKey();
            for (BatchReturn br : entry.getValue()) {
                MaterialBatch batch = batchMap.get(br.allocation().getBatchId());
                if (batch == null) {
                    throw new NotFoundException("批次不存在: " + br.allocation().getBatchNo());
                }
                batch.release(br.quantity());
                BigDecimal equivalent = Quantities.normalize(
                        br.quantity().multiply(issueLine.getConversionFactor()));
                new MaterialReturnLine(materialReturn, issueLine, br.allocation(), br.quantity(), equivalent);
            }
        }
        returnRepository.save(materialReturn);
        // 重新带明细加载返回，避免脱离事务后的懒加载。
        return returnRepository.findWithDetailsByReturnNo(request.returnNo()).orElseThrow();
    }

    /**
     * 计算一条退料行在原领料批次上的退回分布；
     * 指定批次时只能退该批，不指定时按原批次顺序（FEFO）回退。
     * 每个“领料行+批次”的累计退料不得超过其当时扣减量。
     */
    private List<BatchReturn> planBatchReturns(MaterialIssueLine issueLine, String batchNo, BigDecimal quantity) {
        List<MaterialIssueBatchAllocation> allocations = issueLine.getAllocations().stream()
                .sorted(Comparator.comparing(MaterialIssueBatchAllocation::getId))
                .toList();
        if (batchNo != null && !batchNo.isBlank()) {
            MaterialIssueBatchAllocation allocation = allocations.stream()
                    .filter(a -> a.getBatchNo().equals(batchNo))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException(
                            "领料行未从批次 " + batchNo + " 扣减，不能退到该批次"));
            return List.of(new BatchReturn(allocation,
                    withinRemaining(issueLine, allocation, quantity)));
        }

        List<BatchReturn> result = new ArrayList<>();
        BigDecimal remaining = quantity;
        for (MaterialIssueBatchAllocation allocation : allocations) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal alreadyReturned = Quantities.normalize(
                    returnLineRepository.sumReturnedForIssueLineAndBatch(issueLine.getId(), allocation.getBatchId()));
            BigDecimal availableToReturn = Quantities.subtract(allocation.getQuantity(), alreadyReturned);
            if (availableToReturn.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal take = availableToReturn.min(remaining);
            result.add(new BatchReturn(allocation, Quantities.normalize(take)));
            remaining = Quantities.normalize(remaining.subtract(take));
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new QuantityBalanceException("主料 " + issueLine.getRequirementMaterialCode()
                    + " 可退数量不足，尚缺 " + remaining + "（累计退料不能超过原始领料数量）");
        }
        return result;
    }

    private BigDecimal withinRemaining(MaterialIssueLine issueLine,
                                       MaterialIssueBatchAllocation allocation, BigDecimal quantity) {
        BigDecimal alreadyReturned = Quantities.normalize(
                returnLineRepository.sumReturnedForIssueLineAndBatch(issueLine.getId(), allocation.getBatchId()));
        BigDecimal availableToReturn = Quantities.subtract(allocation.getQuantity(), alreadyReturned);
        if (Quantities.gt(quantity, availableToReturn)) {
            throw new QuantityBalanceException("批次 " + allocation.getBatchNo() + " 对该领料行最多还可退 "
                    + availableToReturn + "，申请 " + quantity);
        }
        return quantity;
    }
}
