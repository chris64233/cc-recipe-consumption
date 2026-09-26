package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.domain.CompletionLine;
import com.chris64233.recipeconsumption.domain.CompletionRecord;
import com.chris64233.recipeconsumption.domain.InventoryAdjustment;
import com.chris64233.recipeconsumption.domain.MaterialBatch;
import com.chris64233.recipeconsumption.domain.MaterialIssue;
import com.chris64233.recipeconsumption.domain.MaterialIssueBatchAllocation;
import com.chris64233.recipeconsumption.domain.MaterialIssueLine;
import com.chris64233.recipeconsumption.domain.MaterialReturn;
import com.chris64233.recipeconsumption.domain.MaterialReturnLine;
import com.chris64233.recipeconsumption.domain.ProductionOrder;
import com.chris64233.recipeconsumption.domain.Recipe;
import com.chris64233.recipeconsumption.domain.RecipeIngredient;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.dto.AdjustmentView;
import com.chris64233.recipeconsumption.dto.BatchView;
import com.chris64233.recipeconsumption.dto.CompletionView;
import com.chris64233.recipeconsumption.dto.IssueView;
import com.chris64233.recipeconsumption.dto.OrderView;
import com.chris64233.recipeconsumption.dto.RecipeView;
import com.chris64233.recipeconsumption.dto.ReturnView;
import com.chris64233.recipeconsumption.support.Quantities;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 领域对象 → 对外只读视图的装配，所有数量按统一精度输出。
 */
@Component
public class ViewMapper {

    public RecipeView toView(Recipe recipe) {
        List<RecipeView.VersionView> versions = recipe.getVersions().stream()
                .map(this::toVersionView)
                .toList();
        return new RecipeView(recipe.getId(), recipe.getCode(), recipe.getName(),
                recipe.getProductCode(), recipe.getCreatedAt(), versions);
    }

    public RecipeView.VersionView toVersionView(RecipeVersion version) {
        return new RecipeView.VersionView(version.getId(), version.getVersionNo(),
                version.isPublished(), version.getCreatedAt(),
                version.getIngredients().stream().map(this::toIngredientView).toList());
    }

    private RecipeView.IngredientView toIngredientView(RecipeIngredient ingredient) {
        List<RecipeView.SubstitutionView> subs = ingredient.getSubstitutions().stream()
                .map(s -> new RecipeView.SubstitutionView(s.getSubstituteMaterialCode(),
                        s.getSubstituteMaterialName(), s.getConversionFactor(),
                        s.getMaxSubstitutionRatio()))
                .toList();
        return new RecipeView.IngredientView(ingredient.getLineNo(), ingredient.getMaterialCode(),
                ingredient.getMaterialName(), ingredient.getStandardQuantityPerUnit(),
                ingredient.getUnit(), subs);
    }

    public BatchView toView(MaterialBatch batch) {
        return new BatchView(batch.getId(), batch.getBatchNo(), batch.getMaterialCode(),
                batch.getMaterialName(), batch.getAvailableQuantity(), batch.getQualityStatus(),
                batch.getExpiryDate(), batch.isUsable(LocalDate.now()), batch.getVersion());
    }

    public OrderView toView(ProductionOrder order) {
        return new OrderView(order.getId(), order.getOrderNo(),
                order.getRecipeVersion().getRecipe().getCode(),
                order.getRecipeVersion().getVersionNo(),
                order.getPlannedQuantity(), order.getStatus(),
                order.getActualQuantity(), order.getCreatedAt(), order.getCompletedAt());
    }

    public IssueView toView(MaterialIssue issue) {
        List<IssueView.LineView> lines = issue.getLines().stream().map(this::toIssueLineView).toList();
        return new IssueView(issue.getIssueNo(), issue.getProductionOrder().getOrderNo(),
                issue.getCreatedAt(), issue.getRemark(), lines);
    }

    private IssueView.LineView toIssueLineView(MaterialIssueLine line) {
        List<IssueView.AllocationView> allocations = line.getAllocations().stream()
                .map(a -> new IssueView.AllocationView(a.getBatchId(), a.getBatchNo(),
                        a.getMaterialCode(), a.getQuantity()))
                .toList();
        return new IssueView.LineView(line.getLineNo(), line.getRequirementMaterialCode(),
                line.getFulfillmentType().name(), line.getPickedMaterialCode(),
                line.getPickedMaterialName(), line.getPickedQuantity(),
                line.getConversionFactor(), line.getEquivalentPrimaryQuantity(), allocations);
    }

    public ReturnView toView(MaterialReturn materialReturn) {
        List<ReturnView.LineView> lines = materialReturn.getLines().stream()
                .map((MaterialReturnLine l) -> new ReturnView.LineView(
                        l.getIssueLine().getRequirementMaterialCode(), l.getBatchId(), l.getBatchNo(),
                        l.getMaterialCode(), l.getQuantity(), l.getEquivalentPrimaryQuantity()))
                .toList();
        return new ReturnView(materialReturn.getReturnNo(),
                materialReturn.getProductionOrder().getOrderNo(),
                materialReturn.getMaterialIssue().getIssueNo(),
                materialReturn.getCreatedAt(), materialReturn.getReason(), lines);
    }

    public AdjustmentView toView(InventoryAdjustment adjustment) {
        return new AdjustmentView(adjustment.getAdjustmentNo(),
                adjustment.getProductionOrder() == null ? null : adjustment.getProductionOrder().getOrderNo(),
                adjustment.getBatchId(), adjustment.getBatchNo(), adjustment.getMaterialCode(),
                adjustment.getDeltaQuantity(), adjustment.getRequirementMaterialCode(),
                adjustment.getEquivalentPrimaryQuantity(), adjustment.getCreatedAt(),
                adjustment.getReason());
    }

    public CompletionView toView(CompletionRecord record) {
        BigDecimal outputVariance = Quantities.normalize(
                record.getActualQuantity().subtract(record.getProductionOrder().getPlannedQuantity()));
        List<CompletionView.LineView> lines = record.getLines().stream()
                .map(this::toCompletionLineView)
                .toList();
        return new CompletionView(record.getCompletionNo(),
                record.getProductionOrder().getOrderNo(),
                record.getProductionOrder().getStatus().name(),
                record.getProductionOrder().getPlannedQuantity(),
                record.getActualQuantity(), outputVariance, false,
                record.getCreatedAt(), record.getRemark(), lines);
    }

    private CompletionView.LineView toCompletionLineView(CompletionLine line) {
        BigDecimal ratio = line.getExpectedConsumption().signum() == 0
                ? null
                : Quantities.normalizeRatio(
                        line.getSubstitutedQuantity().divide(line.getExpectedConsumption(),
                                Quantities.RATIO_SCALE, java.math.RoundingMode.HALF_UP));
        return new CompletionView.LineView(line.getRequirementMaterialCode(),
                line.getExpectedConsumption(), line.getIssuedQuantity(), line.getSubstitutedQuantity(),
                ratio, line.getReturnedQuantity(), line.getAdjustedQuantity(), line.getDeclaredLoss(),
                line.getUnexplainedVariance(),
                line.getUnexplainedVariance().abs().compareTo(CompletionService.VARIANCE_TOLERANCE) <= 0);
    }
}
