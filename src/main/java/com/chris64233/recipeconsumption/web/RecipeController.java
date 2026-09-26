package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.domain.Recipe;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.dto.AddVersionRequest;
import com.chris64233.recipeconsumption.dto.CreateRecipeRequest;
import com.chris64233.recipeconsumption.dto.RecipeView;
import com.chris64233.recipeconsumption.service.RecipeService;
import com.chris64233.recipeconsumption.service.ViewMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;
    private final ViewMapper viewMapper;

    public RecipeController(RecipeService recipeService, ViewMapper viewMapper) {
        this.recipeService = recipeService;
        this.viewMapper = viewMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeView create(@Valid @RequestBody CreateRecipeRequest request) {
        Recipe recipe = recipeService.createRecipe(request);
        return viewMapper.toView(recipe);
    }

    @GetMapping("/{recipeCode}")
    public RecipeView get(@PathVariable String recipeCode) {
        return viewMapper.toView(recipeService.getRecipe(recipeCode));
    }

    @PostMapping("/{recipeCode}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeView.VersionView addVersion(@PathVariable String recipeCode,
                                             @Valid @RequestBody AddVersionRequest request) {
        RecipeVersion version = recipeService.addVersion(recipeCode, request);
        return viewMapper.toVersionView(version);
    }

    @GetMapping("/{recipeCode}/versions/{versionNo}")
    public RecipeView.VersionView getVersion(@PathVariable String recipeCode,
                                             @PathVariable String versionNo) {
        return viewMapper.toVersionView(recipeService.getVersion(recipeCode, versionNo));
    }
}
