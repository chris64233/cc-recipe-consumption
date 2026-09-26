package com.chris64233.recipeconsumption;

import com.chris64233.recipeconsumption.domain.InventoryAdjustment;
import com.chris64233.recipeconsumption.dto.AdjustmentRequest;
import com.chris64233.recipeconsumption.dto.CompleteRequest;
import com.chris64233.recipeconsumption.dto.IssueRequest;
import com.chris64233.recipeconsumption.service.AdjustmentService;
import com.chris64233.recipeconsumption.service.CompletionService;
import com.chris64233.recipeconsumption.service.IssueService;
import com.chris64233.recipeconsumption.support.BusinessRuleException;
import com.chris64233.recipeconsumption.support.QuantityBalanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 库存调整：盘盈盘亏、非负约束、替代料批次按系数折主料、挂工单约束、幂等、
 * 原始领料不变。
 */
class AdjustmentServiceTest extends AbstractServiceTest {

    @Autowired private AdjustmentService adjustmentService;
    @Autowired private IssueService issueService;
    @Autowired private CompletionService completionService;

    @BeforeEach
    void setUp() {
        createStandardRecipe();
        createOrder("WO-1", "100");
        createBatch("A-1", MATERIAL_A, "100");
        createBatch("B-1", MATERIAL_B, "100");
    }

    @Test
    void standaloneStocktakeAdjustmentDoesNotRequireOrder() {
        InventoryAdjustment adjustment = adjustmentService.adjust(new AdjustmentRequest(
                "ADJ-1", null, "A-1", new BigDecimal("5"), null, "盘盈"));
        assertThat(adjustment.getProductionOrder()).isNull();
        assertThat(adjustment.getEquivalentPrimaryQuantity()).isNull();
        assertThat(masterDataService.getBatch("A-1").getAvailableQuantity()).isEqualByComparingTo("105");
    }

    @Test
    void negativeAdjustmentCannotDriveStockBelowZero() {
        assertThatThrownBy(() -> adjustmentService.adjust(new AdjustmentRequest(
                "ADJ-BAD", null, "A-1", new BigDecimal("-100.0001"), null, "盘亏")))
                .isInstanceOf(QuantityBalanceException.class);
        assertThat(masterDataService.getBatch("A-1").getAvailableQuantity()).isEqualByComparingTo("100");
    }

    @Test
    void orderAdjustmentRequiresRecipeMaterialLink() {
        assertThatThrownBy(() -> adjustmentService.adjust(new AdjustmentRequest(
                "ADJ-BAD", "WO-1", "A-1", new BigDecimal("1"), null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("requirementMaterialCode");
    }

    @Test
    void substituteBatchAdjustmentConvertsWithFactor() {
        // B 批盘亏 4（实物），折主料 A = 4 × 1.25 = 5。
        InventoryAdjustment adjustment = adjustmentService.adjust(new AdjustmentRequest(
                "ADJ-B", "WO-1", "B-1", new BigDecimal("-4"), MATERIAL_A, "替代料核销"));
        assertThat(adjustment.getRequirementMaterialCode()).isEqualTo(MATERIAL_A);
        assertThat(adjustment.getEquivalentPrimaryQuantity()).isEqualByComparingTo("-5");
    }

    @Test
    void adjustmentAgainstUnrelatedMaterialIsRejected() {
        createBatch("Z-1", "Z", "10");
        assertThatThrownBy(() -> adjustmentService.adjust(new AdjustmentRequest(
                "ADJ-Z", "WO-1", "Z-1", new BigDecimal("1"), MATERIAL_A, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("替代料");
    }

    @Test
    void sameAdjustmentNoIsIdempotentAndOriginalIssueStaysIntact() {
        issueService.issue("WO-1", new IssueRequest("IS-1", null,
                List.of(new IssueRequest.Line(MATERIAL_A, null, new BigDecimal("100")))));
        // 领料错误：实际只用了 90，盘盈回补 10。
        var request = new AdjustmentRequest(
                "ADJ-IDEM", "WO-1", "A-1", new BigDecimal("10"), MATERIAL_A, "修复多领");
        InventoryAdjustment first = adjustmentService.adjust(request);
        InventoryAdjustment again = adjustmentService.adjust(request);
        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(masterDataService.getBatch("A-1").getAvailableQuantity()).isEqualByComparingTo("10");
        // 原始领料记录仍是 100，不被修改。
        assertThat(issueService.getIssue("IS-1").getLines().getFirst().getPickedQuantity())
                .isEqualByComparingTo("100");
    }

    @Test
    void cannotAdjustForCompletedOrder() {
        // setUp 的 A-1 只有 100，补齐 A 的第二批次与 C 批次后按计划产量足额领料。
        createBatch("A-2", MATERIAL_A, "200");
        createBatch("C-1", MATERIAL_C, "100");
        issueService.issue("WO-1", new IssueRequest("IS-1", null, List.of(
                new IssueRequest.Line(MATERIAL_A, null, new BigDecimal("200")),
                new IssueRequest.Line(MATERIAL_C, null, new BigDecimal("100")))));
        completionService.complete("WO-1", new CompleteRequest(
                "CP-1", new BigDecimal("100"), null, null));

        assertThatThrownBy(() -> adjustmentService.adjust(new AdjustmentRequest(
                "ADJ-LATE", "WO-1", "A-1", new BigDecimal("1"), MATERIAL_A, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已完工");
    }
}
