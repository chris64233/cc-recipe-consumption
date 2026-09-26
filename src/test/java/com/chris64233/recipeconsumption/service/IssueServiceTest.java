package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.TestDataFactory;
import com.chris64233.recipeconsumption.domain.MaterialBatch;
import com.chris64233.recipeconsumption.domain.MaterialBatchRepository;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.domain.QualityStatus;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.web.dto.IssueRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.chris64233.recipeconsumption.IntegrationTestBase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssueServiceTest extends com.chris64233.recipeconsumption.IntegrationTestBase {

    @Autowired
    private TestDataFactory data;
    @Autowired
    private IssueService issueService;
    @Autowired
    private OrderCompletionService completionService;
    @Autowired
    private MaterialBatchRepository batchRepository;

    private RecipeVersion recipe;
    private ProductionOrder order;

    @BeforeEach
    void setUp() {
        recipe = data.createStandardRecipe();
        order = data.order("PO-1", recipe, "100"); // 面粉需求 200，水需求 100
    }

    private IssueRequest.BatchPick pick(String batchNo, String qty) {
        return new IssueRequest.BatchPick(batchNo, new BigDecimal(qty));
    }

    private IssueRequest request(String bizNo, String orderNo, List<IssueRequest.Line> lines) {
        return new IssueRequest(bizNo, orderNo, lines);
    }

    @Test
    void issue_from_multiple_batches_deducts_atomically() {
        data.availableBatch("B-F1", TestDataFactory.FLOUR, "150");
        data.availableBatch("B-F2", TestDataFactory.FLOUR, "60");
        data.availableBatch("B-W1", TestDataFactory.WATER, "100");

        issueService.issue(request("BIZ-1", "PO-1", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR,
                        List.of(pick("B-F1", "120"), pick("B-F2", "30"))),
                new IssueRequest.Line(TestDataFactory.WATER, List.of(pick("B-W1", "100"))))));

        assertThat(batchRepository.findByBatchNo("B-F1").orElseThrow().getAvailableQty())
                .isEqualByComparingTo("30");
        assertThat(batchRepository.findByBatchNo("B-F2").orElseThrow().getAvailableQty())
                .isEqualByComparingTo("30");
        assertThat(batchRepository.findByBatchNo("B-W1").orElseThrow().getAvailableQty())
                .isEqualByComparingTo("0");
    }

    @Test
    void insufficient_stock_for_any_material_rolls_back_all_deductions() {
        // 面粉充足、水不足
        data.availableBatch("B-F1", TestDataFactory.FLOUR, "300");
        data.availableBatch("B-W1", TestDataFactory.WATER, "50");

        assertThatThrownBy(() -> issueService.issue(request("BIZ-2", "PO-1", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(pick("B-F1", "200"))),
                new IssueRequest.Line(TestDataFactory.WATER, List.of(pick("B-W1", "100")))))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("库存不足");

        // 不允许留下部分领料
        assertThat(batchRepository.findByBatchNo("B-F1").orElseThrow().getAvailableQty())
                .isEqualByComparingTo("300");
        assertThat(batchRepository.findByBatchNo("B-W1").orElseThrow().getAvailableQty())
                .isEqualByComparingTo("50");
    }

    @Test
    void same_batch_used_in_multiple_lines_checked_against_total_deduction() {
        // 同一批次被同一次领料引用两次，合计 210 > 库存 200
        data.availableBatch("B-F1", TestDataFactory.FLOUR, "200");
        data.availableBatch("B-W1", TestDataFactory.WATER, "100");

        assertThatThrownBy(() -> issueService.issue(request("BIZ-3", "PO-1", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR,
                        List.of(pick("B-F1", "120"), pick("B-F1", "90"))),
                new IssueRequest.Line(TestDataFactory.WATER, List.of(pick("B-W1", "100")))))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("批次重复");
    }

    @Test
    void substitution_converted_by_ratio_and_recorded() {
        // 替代面粉：领 50 实物 × 0.8 = 40 标准当量；需求 200，替代上限 50（25%）
        data.availableBatch("B-F1", TestDataFactory.FLOUR, "200");
        data.availableBatch("B-A1", TestDataFactory.ALT_FLOUR, "100");
        data.availableBatch("B-W1", TestDataFactory.WATER, "100");

        var record = issueService.issue(request("BIZ-4", "PO-1", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR,
                        List.of(pick("B-F1", "160"), pick("B-A1", "50"))),
                new IssueRequest.Line(TestDataFactory.WATER, List.of(pick("B-W1", "100"))))));

        var flourLine = record.getLines().stream()
                .filter(l -> l.getRecipeMaterialCode().equals(TestDataFactory.FLOUR))
                .findFirst().orElseThrow();
        assertThat(flourLine.getEquivalentQty()).isEqualByComparingTo("200"); // 160 + 40
        var altBatch = flourLine.getBatches().stream()
                .filter(b -> b.isSubstitute()).findFirst().orElseThrow();
        assertThat(altBatch.getQty()).isEqualByComparingTo("50");
        assertThat(altBatch.getEquivalentQty()).isEqualByComparingTo("40");
        assertThat(altBatch.getConversionRatio()).isEqualByComparingTo("0.8");
    }

    @Test
    void substitution_exceeding_max_ratio_rejected_and_rolled_back() {
        data.availableBatch("B-A1", TestDataFactory.ALT_FLOUR, "100");
        data.availableBatch("B-W1", TestDataFactory.WATER, "100");

        // 63 × 0.8 = 50.4 > 50 上限
        assertThatThrownBy(() -> issueService.issue(request("BIZ-5", "PO-1", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(pick("B-A1", "63"))),
                new IssueRequest.Line(TestDataFactory.WATER, List.of(pick("B-W1", "100")))))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("替代比例超限");

        assertThat(batchRepository.findByBatchNo("B-A1").orElseThrow().getAvailableQty())
                .isEqualByComparingTo("100");
        assertThat(batchRepository.findByBatchNo("B-W1").orElseThrow().getAvailableQty())
                .isEqualByComparingTo("100");
    }

    @Test
    void substitution_ratio_checked_on_cumulative_orders_of_the_work_order() {
        data.availableBatch("B-A1", TestDataFactory.ALT_FLOUR, "100");
        data.availableBatch("B-F1", TestDataFactory.FLOUR, "300");
        data.availableBatch("B-W1", TestDataFactory.WATER, "200");

        // 第一次：50 × 0.8 = 40，未超 50 上限
        issueService.issue(request("BIZ-6", "PO-1", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(pick("B-A1", "50"))),
                new IssueRequest.Line(TestDataFactory.WATER, List.of(pick("B-W1", "50"))))));

        // 第二次再领 20 实物（16 当量），累计 56 > 50
        assertThatThrownBy(() -> issueService.issue(request("BIZ-7", "PO-1", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(pick("B-A1", "20")))))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("替代比例超限");

        // 边界：12.5 实物 = 10 当量，累计 50，恰好等于上限
        issueService.issue(request("BIZ-8", "PO-1", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(pick("B-A1", "12.5"))))));
    }

    @Test
    void unapproved_substitute_material_rejected() {
        data.availableBatch("B-X1", "UNKNOWN-FLOUR", "100");
        assertThatThrownBy(() -> issueService.issue(request("BIZ-9", "PO-1", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(pick("B-X1", "10")))))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不允许替代");
    }

    @Test
    void quarantine_and_expired_batches_cannot_be_issued() {
        data.batch("B-Q", TestDataFactory.FLOUR, "100",
                QualityStatus.QUARANTINE, LocalDate.now().plusDays(10));
        data.batch("B-E", TestDataFactory.FLOUR, "100",
                QualityStatus.AVAILABLE, LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> issueService.issue(request("BIZ-10", "PO-1", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(pick("B-Q", "10")))))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("质量状态");
        assertThatThrownBy(() -> issueService.issue(request("BIZ-11", "PO-1", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(pick("B-E", "10")))))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("有效期");
    }

    @Test
    void duplicate_biz_no_is_idempotent() {
        data.availableBatch("B-F1", TestDataFactory.FLOUR, "200");
        data.availableBatch("B-W1", TestDataFactory.WATER, "100");

        var req = request("BIZ-DUP", "PO-1", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(pick("B-F1", "10"))),
                new IssueRequest.Line(TestDataFactory.WATER, List.of(pick("B-W1", "10")))));
        issueService.issue(req);
        issueService.issue(req); // 重复提交

        assertThat(batchRepository.findByBatchNo("B-F1").orElseThrow().getAvailableQty())
                .isEqualByComparingTo("190");
        assertThat(batchRepository.findByBatchNo("B-W1").orElseThrow().getAvailableQty())
                .isEqualByComparingTo("90");
    }

    @Test
    void biz_no_reused_for_different_order_conflicts() {
        data.availableBatch("B-F1", TestDataFactory.FLOUR, "200");
        issueService.issue(request("BIZ-C", "PO-1", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(pick("B-F1", "10"))))));

        ProductionOrder other = data.order("PO-2", recipe, "50");
        assertThatThrownBy(() -> issueService.issue(request("BIZ-C", "PO-2", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(pick("B-F1", "5")))))))
                .isInstanceOf(IdempotentConflictException.class);
    }

    @Test
    void completed_order_cannot_be_issued() {
        data.availableBatch("B-DF", TestDataFactory.FLOUR, "20");
        data.availableBatch("B-DW", TestDataFactory.WATER, "10");
        ProductionOrder done = data.order("PO-DONE", recipe, "1");

        issueService.issue(request("BIZ-DONE-1", "PO-DONE", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(pick("B-DF", "2"))),
                new IssueRequest.Line(TestDataFactory.WATER, List.of(pick("B-DW", "1"))))));
        completionService.complete(new com.chris64233.recipeconsumption.web.dto.CompleteOrderRequest(
                "PO-DONE", new BigDecimal("1"), List.of(
                new com.chris64233.recipeconsumption.web.dto.CompleteOrderRequest.LossLine(
                        TestDataFactory.FLOUR, new BigDecimal("0")),
                new com.chris64233.recipeconsumption.web.dto.CompleteOrderRequest.LossLine(
                        TestDataFactory.WATER, new BigDecimal("0")))));

        assertThatThrownBy(() -> issueService.issue(request("BIZ-DONE-2", "PO-DONE", List.of(
                new IssueRequest.Line(TestDataFactory.FLOUR, List.of(pick("B-DF", "1")))))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已完工");
    }

    @Test
    void concurrent_issues_never_create_negative_stock() throws Exception {
        MaterialBatch shared = data.availableBatch("B-CONC", TestDataFactory.FLOUR, "100");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    issueService.issue(request("BIZ-CONC-" + idx, "PO-1", List.of(
                            new IssueRequest.Line(TestDataFactory.FLOUR,
                                    List.of(pick("B-CONC", "20"))))));
                    success.incrementAndGet();
                } catch (BusinessRuleException e) {
                    failure.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 库存 100，每单 20：恰好 5 单成功，其余因不足失败，库存不会为负
        assertThat(success.get()).isEqualTo(5);
        assertThat(failure.get()).isEqualTo(3);
        MaterialBatch reloaded = batchRepository.findById(shared.getId()).orElseThrow();
        assertThat(reloaded.getAvailableQty()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
