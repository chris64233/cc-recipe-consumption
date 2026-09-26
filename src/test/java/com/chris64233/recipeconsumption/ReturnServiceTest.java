package com.chris64233.recipeconsumption;

import com.chris64233.recipeconsumption.domain.MaterialIssue;
import com.chris64233.recipeconsumption.domain.MaterialReturn;
import com.chris64233.recipeconsumption.dto.IssueRequest;
import com.chris64233.recipeconsumption.dto.ReturnRequest;
import com.chris64233.recipeconsumption.service.CompletionService;
import com.chris64233.recipeconsumption.service.IssueService;
import com.chris64233.recipeconsumption.service.ReturnService;
import com.chris64233.recipeconsumption.support.QuantityBalanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 退料：回补批次、原始领料不变、不可超退、完工后拒绝、退料号幂等。
 */
class ReturnServiceTest extends AbstractServiceTest {

    @Autowired private IssueService issueService;
    @Autowired private ReturnService returnService;
    @Autowired private CompletionService completionService;

    @BeforeEach
    void setUp() {
        createStandardRecipe();
        createOrder("WO-1", "100");
        // 两批 A：早批 30、晚批 100；领 50（早批 30 + 晚批 20）。
        createBatch("A-EARLY", MATERIAL_A, "30", null, LocalDate.now().plusDays(10));
        createBatch("A-LATE", MATERIAL_A, "100", null, LocalDate.now().plusDays(60));
        issueService.issue("WO-1", new IssueRequest("IS-1", null,
                List.of(new IssueRequest.Line(MATERIAL_A, null, new BigDecimal("50")))));
    }

    @Test
    void returnRestoresOriginalBatchesAndLeavesIssueUntouched() {
        MaterialReturn materialReturn = returnService.createReturn(new ReturnRequest(
                "RT-1", "IS-1", "余料退回",
                List.of(new ReturnRequest.Line(MATERIAL_A, null, new BigDecimal("40")))));

        // 默认按原扣减顺序回补：早批 30 + 晚批 10。
        assertThat(materialReturn.getLines()).hasSize(2);
        assertThat(masterDataService.getBatch("A-EARLY").getAvailableQuantity())
                .isEqualByComparingTo("30");
        assertThat(masterDataService.getBatch("A-LATE").getAvailableQuantity())
                .isEqualByComparingTo("90");

        // 原始领料记录数量不变。
        MaterialIssue issue = issueService.getIssue("IS-1");
        assertThat(issue.getLines().getFirst().getPickedQuantity())
                .isEqualByComparingTo("50");
    }

    @Test
    void canReturnToASpecifiedOriginalBatch() {
        MaterialReturn materialReturn = returnService.createReturn(new ReturnRequest(
                "RT-1", "IS-1", null,
                List.of(new ReturnRequest.Line(MATERIAL_A, "A-LATE", new BigDecimal("20")))));
        assertThat(materialReturn.getLines()).singleElement()
                .satisfies(line -> assertThat(line.getBatchNo()).isEqualTo("A-LATE"));
        assertThat(masterDataService.getBatch("A-LATE").getAvailableQuantity())
                .isEqualByComparingTo("100");
    }

    @Test
    void cannotReturnMoreThanIssuedFromBatch() {
        // A-LATE 对 IS-1 只扣过 20，退 21 必须失败。
        assertThatThrownBy(() -> returnService.createReturn(new ReturnRequest(
                "RT-BAD", "IS-1", null,
                List.of(new ReturnRequest.Line(MATERIAL_A, "A-LATE", new BigDecimal("21"))))))
                .isInstanceOf(QuantityBalanceException.class);
        assertThat(masterDataService.getBatch("A-LATE").getAvailableQuantity())
                .isEqualByComparingTo("80");
    }

    @Test
    void cumulativeReturnsCannotExceedIssue() {
        returnService.createReturn(new ReturnRequest("RT-1", "IS-1", null,
                List.of(new ReturnRequest.Line(MATERIAL_A, null, new BigDecimal("40")))));
        // 已退 40，只剩 10 可退。
        assertThatThrownBy(() -> returnService.createReturn(new ReturnRequest(
                "RT-2", "IS-1", null,
                List.of(new ReturnRequest.Line(MATERIAL_A, null, new BigDecimal("10.0001"))))))
                .isInstanceOf(QuantityBalanceException.class);
    }

    @Test
    void cannotReturnAfterOrderCompleted() {
        // 补齐 C 领料：实际产量 25 时 A 应耗 50（已领 50）、C 应耗 25。
        createBatch("C-EARLY", MATERIAL_C, "100", null, LocalDate.now().plusDays(10));
        issueService.issue("WO-1", new com.chris64233.recipeconsumption.dto.IssueRequest(
                "IS-C", null,
                List.of(new com.chris64233.recipeconsumption.dto.IssueRequest.Line(
                        MATERIAL_C, null, new BigDecimal("25")))));
        completionService.complete("WO-1", new com.chris64233.recipeconsumption.dto.CompleteRequest(
                "CP-1", new BigDecimal("25"), null, null));

        assertThatThrownBy(() -> returnService.createReturn(new ReturnRequest(
                "RT-LATE", "IS-1", null,
                List.of(new ReturnRequest.Line(MATERIAL_A, null, new BigDecimal("1"))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已完工");
    }

    @Test
    void sameReturnNoIsIdempotent() {
        var request = new ReturnRequest("RT-IDEM", "IS-1", null,
                List.of(new ReturnRequest.Line(MATERIAL_A, null, new BigDecimal("10"))));
        MaterialReturn first = returnService.createReturn(request);
        MaterialReturn again = returnService.createReturn(request);
        assertThat(again.getId()).isEqualTo(first.getId());
        // 早批只回补一次 10：0 → 10（不是 20）。
        assertThat(masterDataService.getBatch("A-EARLY").getAvailableQuantity())
                .isEqualByComparingTo("10");
    }
}
