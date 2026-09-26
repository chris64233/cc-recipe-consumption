package com.chris64233.recipeconsumption;

import com.chris64233.recipeconsumption.domain.MaterialBatch;
import com.chris64233.recipeconsumption.domain.MaterialBatchRepository;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.domain.ProductionOrderRepository;
import com.chris64233.recipeconsumption.domain.QualityStatus;
import com.chris64233.recipeconsumption.domain.RecipeItem;
import com.chris64233.recipeconsumption.domain.RecipeSubstitution;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.domain.RecipeVersionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 测试数据构造助手。 */
@Component
public class TestDataFactory {

    public static final String RECIPE = "R-BREAD";
    public static final String VERSION = "v1";
    public static final String FLOUR = "FLOUR";
    public static final String ALT_FLOUR = "FLOUR-B";
    public static final String WATER = "WATER";

    private final RecipeVersionRepository recipeRepository;
    private final MaterialBatchRepository batchRepository;
    private final ProductionOrderRepository orderRepository;

    public TestDataFactory(RecipeVersionRepository recipeRepository,
                           MaterialBatchRepository batchRepository,
                           ProductionOrderRepository orderRepository) {
        this.recipeRepository = recipeRepository;
        this.batchRepository = batchRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public RecipeVersion createStandardRecipe() {
        RecipeVersion v = new RecipeVersion(RECIPE, VERSION, "BREAD");
        RecipeItem flour = new RecipeItem(FLOUR, new BigDecimal("2.0000"));
        // 1 单位替代面粉折 0.8 标准面粉当量，最多替代 25%
        flour.addSubstitution(new RecipeSubstitution(
                ALT_FLOUR, new BigDecimal("0.800000"), new BigDecimal("0.250000")));
        v.addItem(flour);
        v.addItem(new RecipeItem(WATER, new BigDecimal("1.0000")));
        return recipeRepository.save(v);
    }

    @Transactional
    public MaterialBatch batch(String batchNo, String material, String qty,
                               QualityStatus status, LocalDate expiry) {
        return batchRepository.save(new MaterialBatch(
                batchNo, material, new BigDecimal(qty), status, expiry));
    }

    @Transactional
    public MaterialBatch availableBatch(String batchNo, String material, String qty) {
        return batch(batchNo, material, qty, QualityStatus.AVAILABLE, LocalDate.now().plusDays(30));
    }

    @Transactional
    public ProductionOrder order(String orderNo, RecipeVersion version, String plannedQty) {
        return orderRepository.save(new ProductionOrder(
                orderNo, version, new BigDecimal(plannedQty)));
    }
}
