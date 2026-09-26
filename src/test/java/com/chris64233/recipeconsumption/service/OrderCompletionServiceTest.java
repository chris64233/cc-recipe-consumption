package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.TestDataFactory;
import com.chris64233.recipeconsumption.domain.ProductionOrderRepository;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.web.dto.CompleteOrderRequest;
import com.chris64233.recipeconsumption.web.dto.IssueRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.chris64233.recipeconsumption.IntegrationTestBase;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCompletionServiceTest extends com.chris64233.recipeconsumption.IntegrationTestBase {

    @Autowired
    private TestDataFactory data;
    @Autowired
    private IssueService issueService;
    @Autowired
    private OrderCompletionService completionService;
    @Autowired
    private ProductionOrderRepository orderRepository;

    private RecipeVersion recipe;

    @BeforeEach
    void setUp() {
        recipe = data.createStandardRecipe();
        data.order("PO-1", recipe, "100");
        data.availableBatch("B-F", TestDataFactory.FLOUR, "210");
        data.availableBatch("B-W", TestDataFactory.WATER, "110");
    }

    private void issueAll(String bizFlour, String flourQty, String bizWater, String waterQty) {
        issueService.issue(new IssueRequest(bizFlour, "PO-1", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(
                        new IssueRequest.BatchPick("B-F", new BigDecimal(flourQty)))))));
        issueService.issue(new IssueRequest(bizWater, "PO-1", List.of(
                new IssueRequest.Line(TestDataFactory.WATER, List.of(
                        new IssueRequest.BatchPick("B-W", new BigDecimal(waterQty)))))));
    }

    private CompleteOrderRequest complete(String actual, String flourLoss, String waterLoss) {
        return new CompleteOrderRequest("PO-1", new BigDecimal(actual), List.of(
                new CompleteOrderRequest.LossLine(TestDataFactory.FLOUR, new BigDecimal(flourLoss)),
                new CompleteOrderRequest.LossLine(TestDataFactory.WATER, new BigDecimal(waterLoss))));
    }

    @Test
    void completes_when_quantity_identity_holds() {
        issueAll("BIZ-F", "200", "BIZ-W", "100");
        // 实际产量 100：面粉应耗 200 + 损耗 0；水应耗 100 + 损耗 0
        var completion = completionService.complete(complete("100", "0", "0"));
        assertThat(completion.getActualQty()).isEqualByComparingTo("100");
        assertThat(orderRepository.findByOrderNo("PO-1").orElseThrow().getActualQty())
                .isEqualByComparingTo("100");
    }

    @Test
    void completes_with_declared_loss() {
        issueAll("BIZ-F", "205", "BIZ-W", "102");
        // 实际产量 100：面粉 200 应耗 + 5 损耗；水 100 应耗 + 2 损耗
        completionService.complete(complete("100", "5", "2"));
    }

    @Test
    void rejects_when_actual_output_lower_and_relation_broken() {
        issueAll("BIZ-F", "200", "BIZ-W", "100");
        // 实际产量 90：应耗面粉 180、水 90，净领料多出 20/10，损耗未申报 → 不成立
        assertThatThrownBy(() -> completionService.complete(complete("90", "0", "0")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("数量关系不成立");
        assertThat(orderRepository.findByOrderNo("PO-1").orElseThrow().getActualQty()).isNull();
    }

    @Test
    void lower_output_with_matching_loss_completes() {
        issueAll("BIZ-F", "200", "BIZ-W", "100");
        // 产量 90：应耗面粉 180，损耗 20；水应耗 90，损耗 10
        completionService.complete(complete("90", "20", "10"));
    }

    @Test
    void cannot_complete_twice() {
        issueAll("BIZ-F", "200", "BIZ-W", "100");
        completionService.complete(complete("100", "0", "0"));
        assertThatThrownBy(() -> completionService.complete(complete("100", "0", "0")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不能重复完工");
    }

    @Test
    void missing_loss_line_rejected() {
        issueAll("BIZ-F", "200", "BIZ-W", "100");
        var req = new CompleteOrderRequest("PO-1", new BigDecimal("100"), List.of(
                new CompleteOrderRequest.LossLine(TestDataFactory.FLOUR, new BigDecimal("0"))));
        assertThatThrownBy(() -> completionService.complete(req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("缺少配方原料的损耗申报行");
    }
}
