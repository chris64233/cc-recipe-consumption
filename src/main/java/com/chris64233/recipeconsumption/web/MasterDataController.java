package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.domain.MaterialBatch;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.domain.RecipeSubstitution;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.service.MasterDataService;
import com.chris64233.recipeconsumption.web.dto.CreateBatchRequest;
import com.chris64233.recipeconsumption.web.dto.CreateOrderRequest;
import com.chris64233.recipeconsumption.web.dto.CreateRecipeRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** 配方版本、原料批次、生产工单主数据。 */
@RestController
@RequestMapping("/api")
public class MasterDataController {

    private final MasterDataService masterDataService;

    public MasterDataController(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    @PostMapping("/recipes")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeView createRecipe(@Valid @RequestBody CreateRecipeRequest request) {
        return toView(masterDataService.createRecipe(request));
    }

    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public BatchView createBatch(@Valid @RequestBody CreateBatchRequest request) {
        MaterialBatch batch = masterDataService.createBatch(request);
        return new BatchView(batch.getBatchNo(), batch.getMaterialCode(),
                batch.getAvailableQty(), batch.getQualityStatus().name(), batch.getExpiryDate());
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView createOrder(@Valid @RequestBody CreateOrderRequest request) {
        ProductionOrder order = masterDataService.createOrder(request);
        RecipeVersion v = order.getRecipeVersion();
        return new OrderView(order.getOrderNo(), v.getRecipeCode(), v.getVersionNo(),
                order.getPlannedQty(), order.getStatus().name());
    }

    private RecipeView toView(RecipeVersion v) {
        List<ItemView> items = v.getItems().stream()
                .map(i -> new ItemView(i.getMaterialCode(), i.getStandardQty(),
                        i.getSubstitutions().stream()
                                .map(this::toSub)
                                .toList()))
                .toList();
        return new RecipeView(v.getRecipeCode(), v.getVersionNo(), v.getProductCode(), items);
    }

    private SubView toSub(RecipeSubstitution s) {
        return new SubView(s.getSubstituteMaterialCode(), s.getConversionRatio(), s.getMaxRatio());
    }

    public record RecipeView(String recipeCode, String versionNo, String productCode,
                             List<ItemView> items) {
    }

    public record ItemView(String materialCode, BigDecimal standardQty, List<SubView> substitutions) {
    }

    public record SubView(String substituteMaterialCode, BigDecimal conversionRatio,
                          BigDecimal maxRatio) {
    }

    public record BatchView(String batchNo, String materialCode, BigDecimal availableQty,
                            String qualityStatus, java.time.LocalDate expiryDate) {
    }

    public record OrderView(String orderNo, String recipeCode, String versionNo,
                            BigDecimal plannedQty, String status) {
    }
}
