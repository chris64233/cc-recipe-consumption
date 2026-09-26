package com.chris64233.recipeconsumption;

import com.chris64233.recipeconsumption.domain.FulfillmentType;
import com.chris64233.recipeconsumption.domain.MaterialIssue;
import com.chris64233.recipeconsumption.domain.QualityStatus;
import com.chris64233.recipeconsumption.dto.IssueRequest;
import com.chris64233.recipeconsumption.repo.MaterialIssueRepository;
import com.chris64233.recipeconsumption.service.IssueService;
import com.chris64233.recipeconsumption.support.BusinessRuleException;
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
 * 领料：多批次 FEFO 扣减、原子性、质量状态/效期、替代换算与最大替代比例。
 */
class IssueServiceTest extends AbstractServiceTest {

    @Autowired private IssueService issueService;
    @Autowired private MaterialIssueRepository issueRepository;

    @BeforeEach
    void setUp() {
        createStandardRecipe();
        createOrder("WO-1", "100");
    }

    private IssueRequest.Line line(String requirement, String picked, String quantity) {
        return new IssueRequest.Line(requirement, picked, new BigDecimal(quantity));
    }

    @Test
    void pullsAcrossMultipleBatchesInFefoOrder() {
        // A 有两批：效期早的 30，效期晚的 100。领 50 应先扣早批再扣晚批。
        createBatch("A-EARLY", MATERIAL_A, "30", QualityStatus.AVAILABLE,
                LocalDate.now().plusDays(10));
        createBatch("A-LATE", MATERIAL_A, "100", QualityStatus.AVAILABLE,
                LocalDate.now().plusDays(60));

        MaterialIssue issue = issueService.issue("WO-1", new IssueRequest(
                "IS-1", null, List.of(line(MATERIAL_A, null, "50"))));

        assertThat(issue.getLines()).hasSize(1);
        var allocations = issue.getLines().getFirst().getAllocations();
        assertThat(allocations).extracting(a -> a.getBatchNo())
                .containsExactly("A-EARLY", "A-LATE");
        assertThat(allocations).extracting(a -> a.getQuantity())
                .containsExactly(new BigDecimal("30.0000"), new BigDecimal("20.0000"));
        assertThat(masterDataService.getBatch("A-EARLY").getAvailableQuantity())
                .isEqualByComparingTo("0");
        assertThat(masterDataService.getBatch("A-LATE").getAvailableQuantity())
                .isEqualByComparingTo("80");
    }

    @Test
    void rollsBackEverythingWhenAnyMaterialShort() {
        createBatch("A-BATCH", MATERIAL_A, "200");
        // C 只有 90，需要 100 → 整单失败。
        createBatch("C-BATCH", MATERIAL_C, "90");

        assertThatThrownBy(() -> issueService.issue("WO-1", new IssueRequest(
                "IS-FAIL", null,
                List.of(line(MATERIAL_A, null, "200"), line(MATERIAL_C, null, "100")))))
                .isInstanceOf(QuantityBalanceException.class)
                .hasMessageContaining("C");

        // 原子性：A 不能被扣掉，领料单不能存在。
        assertThat(masterDataService.getBatch("A-BATCH").getAvailableQuantity())
                .isEqualByComparingTo("200");
        assertThat(issueRepository.findByIssueNo("IS-FAIL")).isEmpty();
    }

    @Test
    void rejectsQuarantinedAndExpiredBatchesEvenWhenTheyAreOnlyStock() {
        createBatch("A-QUAR", MATERIAL_A, "100", QualityStatus.QUARANTINED,
                LocalDate.now().plusDays(30));
        createBatch("A-EXPIRED", MATERIAL_A, "100", QualityStatus.AVAILABLE,
                LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> issueService.issue("WO-1", new IssueRequest(
                "IS-1", null, List.of(line(MATERIAL_A, null, "10")))))
                .isInstanceOf(QuantityBalanceException.class);
        // 冻结/过期批次库存不得被动。
        assertThat(masterDataService.getBatch("A-QUAR").getAvailableQuantity())
                .isEqualByComparingTo("100");
        assertThat(masterDataService.getBatch("A-EXPIRED").getAvailableQuantity())
                .isEqualByComparingTo("100");
    }

    @Test
    void substituteConvertsQuantityAndRecordsPrimaryEquivalent() {
        createBatch("B-1", MATERIAL_B, "100");

        // 用 B 替代 50 单位主料需求：实领 B = 50 / 1.25 = 40，折主料 50。
        MaterialIssue issue = issueService.issue("WO-1", new IssueRequest(
                "IS-SUB", null, List.of(line(MATERIAL_A, MATERIAL_B, "40"))));

        var l = issue.getLines().getFirst();
        assertThat(l.getFulfillmentType()).isEqualTo(FulfillmentType.SUBSTITUTE);
        assertThat(l.getPickedQuantity()).isEqualByComparingTo("40");
        assertThat(l.getConversionFactor()).isEqualByComparingTo("1.25");
        assertThat(l.getEquivalentPrimaryQuantity()).isEqualByComparingTo("50");
        assertThat(masterDataService.getBatch("B-1").getAvailableQuantity())
                .isEqualByComparingTo("60");
    }

    @Test
    void rejectsSubstituteNotAllowedByRecipe() {
        createBatch("X-1", "X", "100");
        assertThatThrownBy(() -> issueService.issue("WO-1", new IssueRequest(
                "IS-1", null, List.of(line(MATERIAL_A, "X", "10")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不允许");
    }

    @Test
    void enforcesMaxSubstitutionRatioWithinSingleIssue() {
        createBatch("B-1", MATERIAL_B, "1000");
        // 标准需用 A=200，最大替代比例 0.5 → 最多折主料 100，即实领 B=80。
        // 领 81（折 101.25）即超比例。
        assertThatThrownBy(() -> issueService.issue("WO-1", new IssueRequest(
                "IS-1", null, List.of(line(MATERIAL_A, MATERIAL_B, "81")))))
                .isInstanceOf(QuantityBalanceException.class)
                .hasMessageContaining("替代比例");
        assertThat(masterDataService.getBatch("B-1").getAvailableQuantity())
                .isEqualByComparingTo("1000");
    }

    @Test
    void enforcesMaxSubstitutionRatioAcrossCumulativeIssues() {
        createBatch("B-1", MATERIAL_B, "1000");
        createBatch("A-1", MATERIAL_A, "1000");

        // 第一次替代 40 B（折 50），再领主料 A 50，共满足 A 需求 100。
        issueService.issue("WO-1", new IssueRequest("IS-1", null,
                List.of(line(MATERIAL_A, MATERIAL_B, "40"))));
        issueService.issue("WO-1", new IssueRequest("IS-2", null,
                List.of(line(MATERIAL_A, null, "50"))));
        // 第二次再替代 40 B（累计折 100，恰好上限）允许；再多即拒绝。
        issueService.issue("WO-1", new IssueRequest("IS-3", null,
                List.of(line(MATERIAL_A, MATERIAL_B, "40"))));
        assertThatThrownBy(() -> issueService.issue("WO-1", new IssueRequest(
                "IS-4", null, List.of(line(MATERIAL_A, MATERIAL_B, "0.0001")))))
                .isInstanceOf(QuantityBalanceException.class)
                .hasMessageContaining("累计替代量");
    }

    @Test
    void sameIssueNoIsIdempotentAndDoesNotDeductTwice() {
        createBatch("A-1", MATERIAL_A, "100");
        var request = new IssueRequest("IS-IDEMPOTENT", "第一次",
                List.of(line(MATERIAL_A, null, "10")));

        MaterialIssue first = issueService.issue("WO-1", request);
        MaterialIssue again = issueService.issue("WO-1",
                new IssueRequest("IS-IDEMPOTENT", "重复提交",
                        List.of(line(MATERIAL_A, null, "99"))));

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(again.getIssueNo()).isEqualTo(first.getIssueNo());
        assertThat(again.getRemark()).isEqualTo("第一次");
        // 仓储中只有一张单，且与首次返回的是同一条记录。
        assertThat(masterDataService.getBatch("A-1").getAvailableQuantity())
                .isEqualByComparingTo("90");
        assertThat(issueRepository.findByIssueNo("IS-IDEMPOTENT")).isPresent()
                .get().extracting(MaterialIssue::getId).isEqualTo(first.getId());
        assertThat(issueRepository.count()).isEqualTo(1);
    }

    @Test
    void cannotIssueAgainstUnknownRecipeMaterial() {
        createBatch("Z-1", "Z", "10");
        assertThatThrownBy(() -> issueService.issue("WO-1", new IssueRequest(
                "IS-1", null, List.of(line("Z", null, "1")))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void allowsPrimaryAndSubstituteLinesForSameRequirementInOneIssue() {
        createBatch("A-1", MATERIAL_A, "1000");
        createBatch("B-1", MATERIAL_B, "1000");

        MaterialIssue issue = issueService.issue("WO-1", new IssueRequest("IS-MIX", null, List.of(
                line(MATERIAL_A, MATERIAL_B, "40"),  // 折主料 50
                line(MATERIAL_A, null, "150"))));   // 主料 150

        assertThat(issue.getLines()).hasSize(2);
        assertThat(issue.getLines()).extracting(l -> l.getFulfillmentType())
                .containsExactly(FulfillmentType.SUBSTITUTE, FulfillmentType.PRIMARY);
        // A 合计满足 200，替代占比 50/200=0.25，未超 0.5。
        assertThat(issueLineRepository.sumEquivalentPrimaryByFulfillment(
                issue.getProductionOrder().getId(), MATERIAL_A, FulfillmentType.SUBSTITUTE))
                .isEqualByComparingTo("50");
    }

    @Test
    void issueWithHighPrecisionIsNormalizedToFourDecimals() {
        createBatch("B-1", MATERIAL_B, "1000");
        // 领料数量先按统一精度 HALF_UP 规整：40.123456 → 40.1235；
        // 再换算 40.1235 × 1.25 = 50.154375 → 50.1544。
        MaterialIssue issue = issueService.issue("WO-1", new IssueRequest(
                "IS-PRECISION", null, List.of(line(MATERIAL_A, MATERIAL_B, "40.123456"))));
        assertThat(issue.getLines().getFirst().getPickedQuantity()).isEqualByComparingTo("40.1235");
        assertThat(issue.getLines().getFirst().getPickedQuantity().scale()).isEqualTo(4);
        assertThat(issue.getLines().getFirst().getEquivalentPrimaryQuantity())
                .isEqualByComparingTo("50.1544");
    }
}
