package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.TestDataFactory;
import com.chris64233.recipeconsumption.domain.MaterialBatchRepository;
import com.chris64233.recipeconsumption.domain.OrderCompletionRepository;
import com.chris64233.recipeconsumption.domain.OrderStatus;
import com.chris64233.recipeconsumption.domain.ProductionOrderRepository;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.web.dto.AdjustmentRequest;
import com.chris64233.recipeconsumption.web.dto.CompleteOrderRequest;
import com.chris64233.recipeconsumption.web.dto.IssueRequest;
import com.chris64233.recipeconsumption.web.dto.ReturnRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.chris64233.recipeconsumption.IntegrationTestBase;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReturnAndAdjustmentServiceTest extends com.chris64233.recipeconsumption.IntegrationTestBase {

    @Autowired
    private TestDataFactory data;
    @Autowired
    private IssueService issueService;
    @Autowired
    private ReturnService returnService;
    @Autowired
    private AdjustmentService adjustmentService;
    @Autowired
    private OrderCompletionService completionService;
    @Autowired
    private MaterialBatchRepository batchRepository;
    @Autowired
    private ProductionOrderRepository orderRepository;
    @Autowired
    private OrderCompletionRepository completionRepository;

    private RecipeVersion recipe;

    @BeforeEach
    void setUp() {
        recipe = data.createStandardRecipe();
        data.order("PO-1", recipe, "100");
        data.availableBatch("B-F", TestDataFactory.FLOUR, "210");
        data.availableBatch("B-A", TestDataFactory.ALT_FLOUR, "100");
        data.availableBatch("B-W", TestDataFactory.WATER, "100");
    }

    private void issue(String bizNo, String batch, String material, String qty) {
        issueService.issue(new IssueRequest(bizNo, "PO-1", List.of(
                new IssueRequest.Line(material, List.of(
                        new IssueRequest.BatchPick(batch, new BigDecimal(qty)))))));
    }

    @Test
    void return_restores_batch_and_net_usage_but_keeps_issue_record() {
        issue("BIZ-1", "B-F", TestDataFactory.FLOUR, "210");

        // 退回 10：批次恢复，净领料 = 200
        returnService.returnMaterial(new ReturnRequest(
                "RET-1", "PO-1", TestDataFactory.FLOUR, "B-F",
                new BigDecimal("10"), "多领"));

        assertThat(batchRepository.findByBatchNo("B-F").orElseThrow().getAvailableQty())
                .isEqualByComparingTo("10");
    }

    @Test
    void return_cannot_exceed_issued_net_of_batch() {
        issue("BIZ-2", "B-F", TestDataFactory.FLOUR, "20");
        returnService.returnMaterial(new ReturnRequest(
                "RET-2", "PO-1", TestDataFactory.FLOUR, "B-F",
                new BigDecimal("15"), null));
        // 已退 15，可退净额剩 5
        assertThatThrownBy(() -> returnService.returnMaterial(new ReturnRequest(
                "RET-3", "PO-1", TestDataFactory.FLOUR, "B-F",
                new BigDecimal("6"), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("可退净额");
    }

    @Test
    void return_substitute_uses_conversion_ratio() {
        issue("BIZ-3", "B-A", TestDataFactory.FLOUR, "50"); // 50 × 0.8 = 40 当量
        var ret = returnService.returnMaterial(new ReturnRequest(
                "RET-4", "PO-1", TestDataFactory.FLOUR, "B-A",
                new BigDecimal("10"), null));
        assertThat(ret.getEquivalentQty()).isEqualByComparingTo("8"); // 10 × 0.8
        assertThat(batchRepository.findByBatchNo("B-A").orElseThrow().getAvailableQty())
                .isEqualByComparingTo("60");
    }

    @Test
    void duplicate_return_biz_no_rejected() {
        issue("BIZ-4", "B-F", TestDataFactory.FLOUR, "20");
        var req = new ReturnRequest("RET-DUP", "PO-1", TestDataFactory.FLOUR,
                "B-F", new BigDecimal("5"), null);
        returnService.returnMaterial(req);
        assertThatThrownBy(() -> returnService.returnMaterial(req))
                .isInstanceOf(IdempotentConflictException.class);
    }

    @Test
    void adjustment_corrects_stock_and_usage_and_enables_completion() {
        issue("BIZ-5", "B-F", TestDataFactory.FLOUR, "200");
        issue("BIZ-6", "B-W", TestDataFactory.WATER, "100");

        // 账面多发 5（实际只用 195）：负向调整 -5 库存已被扣减完（0），不能再减库存 →
        // 这里模拟盘盈：发现批次实物比账面多 5，正向调整 +5 库存、+5 用量并申报损耗 5
        adjustmentService.adjust(new AdjustmentRequest(
                "ADJ-1", "PO-1", TestDataFactory.FLOUR, "B-F",
                new BigDecimal("5"), new BigDecimal("5"), "盘盈计入损耗"));
        assertThat(batchRepository.findByBatchNo("B-F").orElseThrow().getAvailableQty())
                .isEqualByComparingTo("15");

        completionService.complete(new CompleteOrderRequest(
                "PO-1", new BigDecimal("100"), List.of(
                new CompleteOrderRequest.LossLine(TestDataFactory.FLOUR, new BigDecimal("5")),
                new CompleteOrderRequest.LossLine(TestDataFactory.WATER, new BigDecimal("0")))));
        assertThat(orderRepository.findByOrderNo("PO-1").orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void negative_adjustment_must_not_create_negative_stock() {
        issue("BIZ-7", "B-F", TestDataFactory.FLOUR, "200"); // 批次余 10
        assertThatThrownBy(() -> adjustmentService.adjust(new AdjustmentRequest(
                "ADJ-2", "PO-1", TestDataFactory.FLOUR, "B-F",
                new BigDecimal("-11"), new BigDecimal("-11"), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("库存将为负");
    }

    @Test
    void adjustment_equivalent_must_match_conversion_ratio() {
        issue("BIZ-8", "B-A", TestDataFactory.FLOUR, "50");
        assertThatThrownBy(() -> adjustmentService.adjust(new AdjustmentRequest(
                "ADJ-3", "PO-1", TestDataFactory.FLOUR, "B-A",
                new BigDecimal("10"), new BigDecimal("10"), null))) // 应 8
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("换算系数不一致");
    }

    @Test
    void no_return_or_adjustment_after_completion() {
        issue("BIZ-9", "B-F", TestDataFactory.FLOUR, "200");
        issue("BIZ-10", "B-W", TestDataFactory.WATER, "100");
        completionService.complete(new CompleteOrderRequest(
                "PO-1", new BigDecimal("100"), List.of(
                new CompleteOrderRequest.LossLine(TestDataFactory.FLOUR, new BigDecimal("0")),
                new CompleteOrderRequest.LossLine(TestDataFactory.WATER, new BigDecimal("0")))));

        assertThatThrownBy(() -> returnService.returnMaterial(new ReturnRequest(
                "RET-5", "PO-1", TestDataFactory.FLOUR, "B-F",
                new BigDecimal("1"), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("已完工");
        assertThatThrownBy(() -> adjustmentService.adjust(new AdjustmentRequest(
                "ADJ-4", "PO-1", TestDataFactory.FLOUR, "B-F",
                new BigDecimal("1"), new BigDecimal("1"), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("已完工");

        // 完工记录存在
        assertThat(completionRepository.findByOrderId(
                orderRepository.findByOrderNo("PO-1").orElseThrow().getId())).isPresent();
    }
}
