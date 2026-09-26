package com.chris64233.recipeconsumption;

import com.chris64233.recipeconsumption.domain.CompletionRecord;
import com.chris64233.recipeconsumption.domain.OrderStatus;
import com.chris64233.recipeconsumption.dto.AdjustmentRequest;
import com.chris64233.recipeconsumption.dto.CompleteRequest;
import com.chris64233.recipeconsumption.dto.CompletionView;
import com.chris64233.recipeconsumption.dto.IssueRequest;
import com.chris64233.recipeconsumption.dto.ReturnRequest;
import com.chris64233.recipeconsumption.service.AdjustmentService;
import com.chris64233.recipeconsumption.service.CompletionService;
import com.chris64233.recipeconsumption.service.IssueService;
import com.chris64233.recipeconsumption.service.QueryService;
import com.chris64233.recipeconsumption.service.ReturnService;
import com.chris64233.recipeconsumption.support.QuantityBalanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 完工产量核对：配平方（已领 − 退料 − 调整净值 − 申报损耗 = 标准单耗 × 实际产量），
 * 不平衡不得完工；完工后禁止领料。
 */
class CompletionServiceTest extends AbstractServiceTest {

    @Autowired private IssueService issueService;
    @Autowired private ReturnService returnService;
    @Autowired private AdjustmentService adjustmentService;
    @Autowired private CompletionService completionService;
    @Autowired private QueryService queryService;

    private IssueRequest.Line al(String material, String picked, String qty) {
        return new IssueRequest.Line(material, picked, new BigDecimal(qty));
    }

    @BeforeEach
    void setUp() {
        createStandardRecipe();
        createOrder("WO-1", "100");
        createBatch("A-1", MATERIAL_A, "1000");
        createBatch("B-1", MATERIAL_B, "1000");
        createBatch("C-1", MATERIAL_C, "1000");
    }

    private void issueFullPrimary() {
        issueService.issue("WO-1", new IssueRequest("IS-1", null, List.of(
                al(MATERIAL_A, null, "200"), al(MATERIAL_C, null, "100"))));
    }

    @Test
    void completesWhenQuantitiesBalance() {
        issueFullPrimary();
        CompletionRecord record = completionService.complete("WO-1", new CompleteRequest(
                "CP-1", new BigDecimal("100"), null, null));

        assertThat(record.getLines()).hasSize(2);
        assertThat(record.getLines()).allSatisfy(line ->
                assertThat(line.getUnexplainedVariance()).isEqualByComparingTo("0"));
        assertThat(masterDataService.getOrder("WO-1").getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(masterDataService.getOrder("WO-1").getActualQuantity())
                .isEqualByComparingTo("100");
    }

    @Test
    void rejectsCompletionAndKeepsOrderOpenWhenVarianceExists() {
        issueFullPrimary(); // 领了 200A/100C
        assertThatThrownBy(() -> completionService.complete("WO-1", new CompleteRequest(
                "CP-BAD", new BigDecimal("90"), null, null))) // 应耗 180A/90C，差异 20/10
                .isInstanceOf(QuantityBalanceException.class)
                .hasMessageContaining("产量核对不通过");

        assertThat(masterDataService.getOrder("WO-1").getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(completionRepository.findByCompletionNo("CP-BAD")).isEmpty();
    }

    @Test
    void declaredLossBalancesLowerOutput() {
        issueFullPrimary();
        // 实际 90：应耗 180A/90C；申报损耗 20A/10C 后配平。
        completionService.complete("WO-1", new CompleteRequest(
                "CP-1", new BigDecimal("90"),
                List.of(new CompleteRequest.LossLine(MATERIAL_A, new BigDecimal("20")),
                        new CompleteRequest.LossLine(MATERIAL_C, new BigDecimal("10"))),
                null));
        assertThat(masterDataService.getOrder("WO-1").getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void returnedMaterialBalancesLowerOutput() {
        issueFullPrimary();
        // 实际 90，余料 20A/10C 退回，应耗 180A/90C。
        returnService.createReturn(new ReturnRequest("RT-1", "IS-1", null, List.of(
                new ReturnRequest.Line(MATERIAL_A, null, new BigDecimal("20")),
                new ReturnRequest.Line(MATERIAL_C, null, new BigDecimal("10")))));
        CompletionRecord record = completionService.complete("WO-1", new CompleteRequest(
                "CP-1", new BigDecimal("90"), null, null));
        assertThat(record.getLines()).allSatisfy(line ->
                assertThat(line.getUnexplainedVariance()).isEqualByComparingTo("0"));
    }

    @Test
    void positiveAdjustmentBalancesOverIssuedMaterial() {
        // 多领 20A/10C（账上出库但实物未走），正向调整回补批次，实际 90。
        issueFullPrimary();
        adjustmentService.adjust(new AdjustmentRequest(
                "ADJ-A", "WO-1", "A-1", new BigDecimal("20"), MATERIAL_A, "冲回多领"));
        adjustmentService.adjust(new AdjustmentRequest(
                "ADJ-C", "WO-1", "C-1", new BigDecimal("10"), MATERIAL_C, "冲回多领"));
        CompletionRecord record = completionService.complete("WO-1", new CompleteRequest(
                "CP-1", new BigDecimal("90"), null, null));
        assertThat(record.getLines()).allSatisfy(line ->
                assertThat(line.getUnexplainedVariance()).isEqualByComparingTo("0"));
        assertThat(masterDataService.getBatch("A-1").getAvailableQuantity()).isEqualByComparingTo("820");
    }

    @Test
    void negativeAdjustmentBalancesUnderRecordedIssue() {
        // 领料少记 20A/10C（实物已走），负向调整核销批次，实际 100。
        issueService.issue("WO-1", new IssueRequest("IS-1", null, List.of(
                al(MATERIAL_A, null, "180"), al(MATERIAL_C, null, "90"))));
        adjustmentService.adjust(new AdjustmentRequest(
                "ADJ-A", "WO-1", "A-1", new BigDecimal("-20"), MATERIAL_A, "补记少领"));
        adjustmentService.adjust(new AdjustmentRequest(
                "ADJ-C", "WO-1", "C-1", new BigDecimal("-10"), MATERIAL_C, "补记少领"));
        completionService.complete("WO-1", new CompleteRequest(
                "CP-1", new BigDecimal("100"), null, null));
        assertThat(masterDataService.getBatch("A-1").getAvailableQuantity()).isEqualByComparingTo("800");
    }

    @Test
    void substituteIsCheckedOnPrimaryEquivalentBasis() {
        // A：替代 40B（折 50 主料）+ 主料 150；C 主料 100。实际 100，应耗 A=200、C=100，配平。
        issueService.issue("WO-1", new IssueRequest("IS-1", null, List.of(
                al(MATERIAL_A, MATERIAL_B, "40"),
                al(MATERIAL_A, null, "150"),
                al(MATERIAL_C, null, "100"))));
        CompletionRecord record = completionService.complete("WO-1", new CompleteRequest(
                "CP-1", new BigDecimal("100"), null, null));
        var lineA = record.getLines().stream()
                .filter(l -> l.getRequirementMaterialCode().equals(MATERIAL_A)).findFirst().orElseThrow();
        assertThat(lineA.getIssuedQuantity()).isEqualByComparingTo("200");
        assertThat(lineA.getSubstitutedQuantity()).isEqualByComparingTo("50");
    }

    @Test
    void cannotIssueAfterCompletion() {
        issueFullPrimary();
        completionService.complete("WO-1", new CompleteRequest(
                "CP-1", new BigDecimal("100"), null, null));
        assertThatThrownBy(() -> issueService.issue("WO-1", new IssueRequest(
                "IS-2", null, List.of(al(MATERIAL_C, null, "1")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已完工");
    }

    @Test
    void completionNoIsIdempotent() {
        issueFullPrimary();
        var request = new CompleteRequest("CP-IDEM", new BigDecimal("100"), null, null);
        CompletionRecord first = completionService.complete("WO-1", request);
        CompletionRecord again = completionService.complete("WO-1", request);
        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(completionRepository.count()).isEqualTo(1);
    }

    @Test
    void provisionalVarianceIsAvailableBeforeCompletion() {
        issueFullPrimary();
        // 通过查询服务以只读方式预估实际 90 时的差异，工单仍保持未完工。
        CompletionView preview = queryService.outputVariance("WO-1", new BigDecimal("90"));
        assertThat(preview.provisional()).isTrue();
        assertThat(preview.completionNo()).isNull();
        assertThat(preview.lines()).anySatisfy(line ->
                assertThat(line.unexplainedVariance()).isEqualByComparingTo("20"));
        assertThat(masterDataService.getOrder("WO-1").getStatus()).isEqualTo(OrderStatus.OPEN);
    }
}
