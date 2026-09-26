package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.TestDataFactory;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.service.dto.BatchTraceView;
import com.chris64233.recipeconsumption.service.dto.OrderMaterialUsageView;
import com.chris64233.recipeconsumption.service.dto.ProductionVarianceView;
import com.chris64233.recipeconsumption.service.dto.SubstitutionView;
import com.chris64233.recipeconsumption.web.dto.CompleteOrderRequest;
import com.chris64233.recipeconsumption.web.dto.IssueRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.chris64233.recipeconsumption.IntegrationTestBase;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryServiceTest extends com.chris64233.recipeconsumption.IntegrationTestBase {

    @Autowired
    private TestDataFactory data;
    @Autowired
    private IssueService issueService;
    @Autowired
    private ReturnService returnService;
    @Autowired
    private OrderCompletionService completionService;
    @Autowired
    private QueryService queryService;

    private RecipeVersion recipe;

    @BeforeEach
    void setUp() {
        recipe = data.createStandardRecipe();
        data.order("PO-Q", recipe, "100"); // 面粉需求 200，水 100
        data.availableBatch("B-F1", TestDataFactory.FLOUR, "160");
        data.availableBatch("B-F2", TestDataFactory.FLOUR, "210");
        data.availableBatch("B-A1", TestDataFactory.ALT_FLOUR, "50");
        data.availableBatch("B-W1", TestDataFactory.WATER, "100");
    }

    private void issue(String bizNo, String material, String batch, String qty) {
        issueService.issue(new IssueRequest(bizNo, "PO-Q", List.of(
                new IssueRequest.Line(material, List.of(
                        new IssueRequest.BatchPick(batch, new BigDecimal(qty)))))));
    }

    @Test
    void usage_shows_net_consumption_and_substitute_breakdown() {
        issue("BIZ-Q1", TestDataFactory.FLOUR, "B-F1", "160");
        issue("BIZ-Q2", TestDataFactory.FLOUR, "B-A1", "50"); // 50 × 0.8 = 40 当量
        issue("BIZ-Q3", TestDataFactory.WATER, "B-W1", "100");

        OrderMaterialUsageView usage = queryService.usage("PO-Q");
        OrderMaterialUsageView.MaterialUsage flour = usage.materials().stream()
                .filter(m -> m.recipeMaterialCode().equals(TestDataFactory.FLOUR))
                .findFirst().orElseThrow();

        assertThat(flour.demandQty()).isEqualByComparingTo("200");
        assertThat(flour.issuedEquivalentQty()).isEqualByComparingTo("200");
        assertThat(flour.netEquivalentQty()).isEqualByComparingTo("200");
        assertThat(flour.substitutes()).hasSize(1);
        var sub = flour.substitutes().get(0);
        assertThat(sub.materialCode()).isEqualTo(TestDataFactory.ALT_FLOUR);
        assertThat(sub.equivalentQty()).isEqualByComparingTo("40");
        assertThat(sub.ratioInDemand()).isEqualByComparingTo("0.200000");
        assertThat(sub.overMaxRatio()).isFalse();
    }

    @Test
    void usage_reflects_returns() {
        issue("BIZ-Q4", TestDataFactory.FLOUR, "B-F2", "100");
        returnService.returnMaterial(new com.chris64233.recipeconsumption.web.dto.ReturnRequest(
                "RET-Q1", "PO-Q", TestDataFactory.FLOUR, "B-F2",
                new BigDecimal("30"), null));

        OrderMaterialUsageView.MaterialUsage flour = queryService.usage("PO-Q").materials().stream()
                .filter(m -> m.recipeMaterialCode().equals(TestDataFactory.FLOUR))
                .findFirst().orElseThrow();
        assertThat(flour.issuedEquivalentQty()).isEqualByComparingTo("100");
        assertThat(flour.returnedEquivalentQty()).isEqualByComparingTo("30");
        assertThat(flour.netEquivalentQty()).isEqualByComparingTo("70");
    }

    @Test
    void substitution_preview_calculates_equivalent_and_cap() {
        List<SubstitutionView> views = queryService.previewSubstitution(new IssueRequest(
                "PREVIEW", "PO-Q", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(
                        new IssueRequest.BatchPick("B-A1", new BigDecimal("50")))))));

        assertThat(views).hasSize(1);
        SubstitutionView v = views.get(0);
        assertThat(v.equivalentQty()).isEqualByComparingTo("40");
        assertThat(v.demandQty()).isEqualByComparingTo("200");
        assertThat(v.maxRatio()).isEqualByComparingTo("0.25");
        assertThat(v.capQty()).isEqualByComparingTo("50");
        assertThat(v.allowed()).isTrue();
    }

    @Test
    void substitution_preview_flags_over_limit_after_prior_issues() {
        issue("BIZ-Q5", TestDataFactory.FLOUR, "B-A1", "50"); // 已用 40 当量
        List<SubstitutionView> views = queryService.previewSubstitution(new IssueRequest(
                "PREVIEW2", "PO-Q", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(
                        new IssueRequest.BatchPick("B-A1", new BigDecimal("20"))))))); // 16 当量 → 56
        assertThat(views.get(0).allowed()).isFalse();
        assertThat(views.get(0).totalEquivalentQty()).isEqualByComparingTo("56");
    }

    @Test
    void batch_trace_lists_issue_and_return_movements() {
        issue("BIZ-Q6", TestDataFactory.FLOUR, "B-F2", "80");
        returnService.returnMaterial(new com.chris64233.recipeconsumption.web.dto.ReturnRequest(
                "RET-Q2", "PO-Q", TestDataFactory.FLOUR, "B-F2",
                new BigDecimal("20"), null));

        BatchTraceView trace = queryService.batchTrace("B-F2");
        assertThat(trace.availableQty()).isEqualByComparingTo("150"); // 210 - 80 + 20
        assertThat(trace.movements()).hasSize(2);
        assertThat(trace.movements().get(0).type()).isEqualTo("ISSUE");
        assertThat(trace.movements().get(0).qty()).isEqualByComparingTo("80");
        assertThat(trace.movements().get(1).type()).isEqualTo("RETURN");
        assertThat(trace.movements().get(1).qty()).isEqualByComparingTo("20");
    }

    @Test
    void variance_after_completion_has_zero_unexplained_difference() {
        issue("BIZ-Q7", TestDataFactory.FLOUR, "B-F2", "205");
        issue("BIZ-Q8", TestDataFactory.WATER, "B-W1", "100");
        completionService.complete(new CompleteOrderRequest(
                "PO-Q", new BigDecimal("100"), List.of(
                new CompleteOrderRequest.LossLine(TestDataFactory.FLOUR, new BigDecimal("5")),
                new CompleteOrderRequest.LossLine(TestDataFactory.WATER, new BigDecimal("0")))));

        ProductionVarianceView variance = queryService.variance("PO-Q");
        assertThat(variance.outputVariance()).isEqualByComparingTo("0");
        assertThat(variance.materials()).allSatisfy(m ->
                assertThat(m.unexplainedVariance()).isEqualByComparingTo("0"));
        var flour = variance.materials().stream()
                .filter(m -> m.materialCode().equals(TestDataFactory.FLOUR))
                .findFirst().orElseThrow();
        assertThat(flour.netIssuedQty()).isEqualByComparingTo("205");
        assertThat(flour.standardConsumption()).isEqualByComparingTo("200");
        assertThat(flour.declaredLossQty()).isEqualByComparingTo("5");
    }
}
