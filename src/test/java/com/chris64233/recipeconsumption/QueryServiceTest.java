package com.chris64233.recipeconsumption;

import com.chris64233.recipeconsumption.dto.BatchTraceView;
import com.chris64233.recipeconsumption.dto.CompleteRequest;
import com.chris64233.recipeconsumption.dto.IssueRequest;
import com.chris64233.recipeconsumption.dto.OrderUsageView;
import com.chris64233.recipeconsumption.dto.SubstitutionPreviewRequest;
import com.chris64233.recipeconsumption.dto.SubstitutionPreviewView;
import com.chris64233.recipeconsumption.service.CompletionService;
import com.chris64233.recipeconsumption.service.IssueService;
import com.chris64233.recipeconsumption.service.QueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 查询：工单用料汇总、替代计算试算、批次去向、产量差异。
 */
class QueryServiceTest extends AbstractServiceTest {

    @Autowired private IssueService issueService;
    @Autowired private CompletionService completionService;
    @Autowired private QueryService queryService;

    @BeforeEach
    void setUp() {
        createStandardRecipe();
        createOrder("WO-1", "100");
        createBatch("A-1", MATERIAL_A, "1000");
        createBatch("B-1", MATERIAL_B, "1000");
        createBatch("C-1", MATERIAL_C, "1000");
        // A：替代 40B（折 50）+ 主料 150；C：主料 100。
        issueService.issue("WO-1", new IssueRequest("IS-1", null, List.of(
                new IssueRequest.Line(MATERIAL_A, MATERIAL_B, new BigDecimal("40")),
                new IssueRequest.Line(MATERIAL_A, null, new BigDecimal("150")),
                new IssueRequest.Line(MATERIAL_C, null, new BigDecimal("100")))));
    }

    @Test
    void orderUsageShowsStandardIssuedAndSubstitutionRatio() {
        OrderUsageView usage = queryService.orderUsage("WO-1");
        OrderUsageView.LineView lineA = usage.lines().stream()
                .filter(l -> l.requirementMaterialCode().equals(MATERIAL_A)).findFirst().orElseThrow();

        assertThat(lineA.standardRequiredQuantity()).isEqualByComparingTo("200");
        assertThat(lineA.issuedPrimaryQuantity()).isEqualByComparingTo("150");
        assertThat(lineA.issuedSubstituteQuantity()).isEqualByComparingTo("50");
        assertThat(lineA.issuedTotalEquivalent()).isEqualByComparingTo("200");
        assertThat(lineA.netConsumedQuantity()).isEqualByComparingTo("200");
        assertThat(lineA.substitutionRatio()).isEqualByComparingTo("0.25");
        assertThat(lineA.maxSubstitutionRatio()).isEqualByComparingTo("0.5");
        assertThat(lineA.substitutionWithinLimit()).isTrue();
    }

    @Test
    void substitutionPreviewComputesPickedQuantityAndProjectedRatio() {
        // 已替代折 50；再申请替代折 50 → 实领 B = 50/1.25 = 40，累计 100，占比 0.5 恰好上限。
        SubstitutionPreviewView view = queryService.previewSubstitution("WO-1",
                new SubstitutionPreviewRequest(List.of(
                        new SubstitutionPreviewRequest.Line(
                                MATERIAL_A, MATERIAL_B, new BigDecimal("50")))));
        var line = view.lines().getFirst();
        assertThat(line.requiredPickedQuantity()).isEqualByComparingTo("40");
        assertThat(line.maxAllowedEquivalentQuantity()).isEqualByComparingTo("100");
        assertThat(line.alreadySubstitutedEquivalent()).isEqualByComparingTo("50");
        assertThat(line.projectedSubstitutionRatio()).isEqualByComparingTo("0.5");
        assertThat(line.withinLimit()).isTrue();
    }

    @Test
    void substitutionPreviewFlagsBreach() {
        SubstitutionPreviewView view = queryService.previewSubstitution("WO-1",
                new SubstitutionPreviewRequest(List.of(
                        new SubstitutionPreviewRequest.Line(
                                MATERIAL_A, MATERIAL_B, new BigDecimal("50.0001")))));
        assertThat(view.lines().getFirst().withinLimit()).isFalse();
    }

    @Test
    void batchTraceListsIssueReturnAndAdjustmentFlows() {
        BatchTraceView trace = queryService.traceBatch("B-1");
        assertThat(trace.issues()).hasSize(1);
        assertThat(trace.issues().getFirst().issueNo()).isEqualTo("IS-1");
        assertThat(trace.issues().getFirst().fulfillmentType()).isEqualTo("SUBSTITUTE");
        assertThat(trace.issues().getFirst().quantity()).isEqualByComparingTo("40");
        assertThat(trace.currentAvailableQuantity()).isEqualByComparingTo("960");
    }

    @Test
    void completedVarianceIsReturnedAfterCompletion() {
        completionService.complete("WO-1", new CompleteRequest(
                "CP-1", new BigDecimal("100"), null, null));
        var variance = queryService.outputVariance("WO-1", null);
        assertThat(variance.provisional()).isFalse();
        assertThat(variance.completionNo()).isEqualTo("CP-1");
        assertThat(variance.actualQuantity()).isEqualByComparingTo("100");
        assertThat(variance.outputVariance()).isEqualByComparingTo("0");
        assertThat(variance.lines()).allMatch(CompletionViewLine -> CompletionViewLine.balanced());
    }
}
