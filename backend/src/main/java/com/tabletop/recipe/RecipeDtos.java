package com.tabletop.recipe;

import com.tabletop.recipe.domain.ScaledQuantity;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class RecipeDtos {
    private RecipeDtos() {
    }

    /**
     * Create and update use the same body. Ingredients come as plain lines ("200 g flour"),
     * the server parses them. version is required for update (optimistic locking).
     */
    public record RecipeRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @Min(1) @Max(100) Integer servings,
            @Min(0) @Max(2880) Integer totalTimeMin,
            @Size(max = 2000) @Pattern(regexp = "https?://.*", message = "must start with http:// or https://")
            String sourceUrl,
            @Size(max = 200) String sourceAuthor,
            @Size(max = 10) String language,
            @Size(max = 5000) String notes,
            Long coverImageId,
            @Size(max = 100) List<@NotBlank @Size(max = 500) String> ingredients,
            @Size(max = 100) List<@NotBlank @Size(max = 5000) String> steps,
            @Size(max = 30) List<@NotBlank @Size(max = 50) String> tags,
            Integer version) {

        public List<String> ingredientsOrEmpty() { return ingredients == null ? List.of() : ingredients; }
        public List<String> stepsOrEmpty() { return steps == null ? List.of() : steps; }
        public List<String> tagsOrEmpty() { return tags == null ? List.of() : tags; }
    }

    public record FavoriteRequest(@NotNull Boolean favorite) {
    }

    public record IngredientResponse(Long id, String rawText, String name, BigDecimal quantity, String unit,
                                     String note, ScaledQuantity scaled) {
    }

    public record StepResponse(int position, String text, boolean aiGenerated) {
    }

    /** servings: original; requestedServings: what the user asked for via ?servings=, scaled values match it. */
    public record RecipeResponse(
            Long id, String title, String description, Integer servings, Integer requestedServings,
            Integer totalTimeMin, String sourceUrl, String sourceAuthor, SourceType sourceType,
            String language, Long coverImageId, String notes, boolean favorite, int version,
            OffsetDateTime createdAt, OffsetDateTime updatedAt,
            List<IngredientResponse> ingredients, List<StepResponse> steps, List<String> tags) {
    }
}
