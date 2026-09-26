package com.chris64233.recipeconsumption;

import com.chris64233.recipeconsumption.dto.AddVersionRequest;
import com.chris64233.recipeconsumption.dto.CreateBatchRequest;
import com.chris64233.recipeconsumption.dto.CreateOrderRequest;
import com.chris64233.recipeconsumption.dto.CreateRecipeRequest;
import com.chris64233.recipeconsumption.repo.CompletionRecordRepository;
import com.chris64233.recipeconsumption.repo.InventoryAdjustmentRepository;
import com.chris64233.recipeconsumption.repo.MaterialBatchRepository;
import com.chris64233.recipeconsumption.repo.MaterialIssueLineRepository;
import com.chris64233.recipeconsumption.repo.MaterialIssueRepository;
import com.chris64233.recipeconsumption.repo.MaterialReturnRepository;
import com.chris64233.recipeconsumption.repo.ProductionOrderRepository;
import com.chris64233.recipeconsumption.repo.RecipeRepository;
import com.chris64233.recipeconsumption.repo.RecipeVersionRepository;
import com.chris64233.recipeconsumption.domain.QualityStatus;
import com.chris64233.recipeconsumption.service.MasterDataService;
import com.chris64233.recipeconsumption.service.RecipeService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 服务层测试基类：每个用例前清空业务表，并提供标准配方/批次/工单夹具。
 *
 * <p>标准配方 R1/V1（每 1 单位产品）：
 * <ul>
 *   <li>主料 A：单耗 2；允许 B 替代，换算系数 1.25，最大替代比例 0.5；</li>
 *   <li>主料 C：单耗 1，无替代。</li>
 * </ul>
 * 计划产量 100 的工单标准需用：A=200（最多替代 100，折实领 B=80）、C=100。
 */
@SpringBootTest
abstract class AbstractServiceTest {

    protected static final String RECIPE = "R1";
    protected static final String VERSION = "V1";
    protected static final String MATERIAL_A = "A";
    protected static final String MATERIAL_B = "B";
    protected static final String MATERIAL_C = "C";

    @Autowired protected RecipeService recipeService;
    @Autowired protected MasterDataService masterDataService;
    @Autowired protected MaterialBatchRepository batchRepository;
    @Autowired protected RecipeRepository recipeRepository;
    @Autowired protected RecipeVersionRepository versionRepository;
    @Autowired protected ProductionOrderRepository orderRepository;
    @Autowired protected MaterialIssueRepository issueRepository;
    @Autowired protected MaterialIssueLineRepository issueLineRepository;
    @Autowired protected MaterialReturnRepository returnRepository;
    @Autowired protected InventoryAdjustmentRepository adjustmentRepository;
    @Autowired protected CompletionRecordRepository completionRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** 业务表（按外键依赖反序），每个用例前清空。 */
    private static final String[] TABLES_IN_DELETE_ORDER = {
            "completion_line",
            "completion_record",
            "inventory_adjustment",
            "material_return_line",
            "material_return",
            "material_issue_batch_alloc",
            "material_issue_line",
            "material_issue",
            "production_order",
            "material_batch",
            "ingredient_substitution",
            "recipe_ingredient",
            "recipe_version",
            "recipe"
    };

    @BeforeEach
    void cleanUp() {
        for (String table : TABLES_IN_DELETE_ORDER) {
            jdbcTemplate.update("delete from " + table);
        }
    }

    protected void createStandardRecipe() {
        recipeService.createRecipe(new CreateRecipeRequest(RECIPE, "标准配方", "P1"));
        AddVersionRequest request = new AddVersionRequest(VERSION, List.of(
                new AddVersionRequest.IngredientSpec(1, MATERIAL_A, "主料A",
                        new BigDecimal("2"), "kg",
                        List.of(new AddVersionRequest.SubstitutionSpec(
                                MATERIAL_B, "替代料B", new BigDecimal("1.25"), new BigDecimal("0.5")))),
                new AddVersionRequest.IngredientSpec(2, MATERIAL_C, "主料C",
                        new BigDecimal("1"), "kg", null)));
        recipeService.addVersion(RECIPE, request);
    }

    protected void createOrder(String orderNo, String plannedQuantity) {
        masterDataService.createOrder(new CreateOrderRequest(
                orderNo, RECIPE, VERSION, new BigDecimal(plannedQuantity)));
    }

    protected void createBatch(String batchNo, String materialCode, String quantity,
                               QualityStatus status, LocalDate expiryDate) {
        masterDataService.createBatch(new CreateBatchRequest(
                batchNo, materialCode, materialCode + "-名称", new BigDecimal(quantity),
                status, expiryDate));
    }

    protected void createBatch(String batchNo, String materialCode, String quantity) {
        createBatch(batchNo, materialCode, quantity, QualityStatus.AVAILABLE,
                LocalDate.now().plusDays(30));
    }
}
