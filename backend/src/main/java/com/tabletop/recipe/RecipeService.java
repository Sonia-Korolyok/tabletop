package com.tabletop.recipe;

import com.tabletop.common.BadRequestException;
import com.tabletop.common.ConflictException;
import com.tabletop.common.NotFoundException;
import com.tabletop.image.ImageRepository;
import com.tabletop.recipe.RecipeDtos.*;
import com.tabletop.recipe.domain.IngredientParser;
import com.tabletop.recipe.domain.ParsedIngredient;
import com.tabletop.recipe.domain.ServingsScaler;
import com.tabletop.recipe.domain.Unit;
import com.tabletop.tag.Tag;
import com.tabletop.tag.TagRepository;
import com.tabletop.user.User;
import com.tabletop.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class RecipeService {

    private final RecipeRepository recipes;
    private final UserRepository users;
    private final TagRepository tags;
    private final ImageRepository images;
    private final IngredientParser parser = new IngredientParser();
    private final ServingsScaler scaler = new ServingsScaler();
    private final Clock clock;

    public RecipeService(RecipeRepository recipes, UserRepository users, TagRepository tags,
                         ImageRepository images, Clock clock) {
        this.recipes = recipes;
        this.users = users;
        this.tags = tags;
        this.images = images;
        this.clock = clock;
    }

    @Transactional
    public RecipeResponse create(Long userId, RecipeRequest req) {
        User owner = users.getReferenceById(userId);   // no SELECT, just a reference for the foreign key
        Recipe recipe = new Recipe(owner, req.title(), SourceType.MANUAL);
        apply(recipe, owner, req);
        return toResponse(recipes.save(recipe), null);
    }

    @Transactional(readOnly = true)
    public RecipeResponse get(Long userId, Long recipeId, Integer servings) {
        Recipe recipe = findOwned(userId, recipeId);
        if (servings != null && (servings < 1 || servings > 100)) {
            throw new BadRequestException("servings must be between 1 and 100");
        }
        return toResponse(recipe, servings);
    }

    @Transactional
    public RecipeResponse update(Long userId, Long recipeId, RecipeRequest req) {
        if (req.version() == null) {
            throw new BadRequestException("version is required to update a recipe");
        }
        Recipe recipe = findOwned(userId, recipeId);
        // Client edited an old copy: someone saved in between. Same rule as @Version, checked early.
        if (recipe.getVersion() != req.version()) {
            throw new ConflictException("The recipe was changed on another device. Reload it and try again.");
        }
        apply(recipe, recipe.getOwner(), req);
        recipes.flush();   // run UPDATE now, so the response already has the new version
        return toResponse(recipe, null);
    }

    @Transactional
    public void delete(Long userId, Long recipeId) {
        findOwned(userId, recipeId).softDelete(OffsetDateTime.now(clock));
    }

    @Transactional
    public RecipeResponse restore(Long userId, Long recipeId) {
        Recipe recipe = recipes.findByIdAndOwnerId(recipeId, userId)
                .orElseThrow(() -> new NotFoundException("Recipe not found"));
        recipe.restore();
        recipes.flush();
        return toResponse(recipe, null);
    }

    @Transactional
    public RecipeResponse setFavorite(Long userId, Long recipeId, boolean favorite) {
        Recipe recipe = findOwned(userId, recipeId);
        recipe.setFavorite(favorite);
        recipes.flush();
        return toResponse(recipe, null);
    }

    private Recipe findOwned(Long userId, Long recipeId) {
        return recipes.findOwnedWithDetails(recipeId, userId)
                .orElseThrow(() -> new NotFoundException("Recipe not found"));
    }

    private void apply(Recipe recipe, User owner, RecipeRequest req) {
        recipe.updateDetails(req.title(), req.description(), req.servings(), req.totalTimeMin(),
                req.sourceUrl(), req.sourceAuthor(), req.language(), req.notes());

        recipe.replaceIngredients(req.ingredientsOrEmpty().stream().map(line -> {
            ParsedIngredient p = parser.parse(line);
            return new RecipeIngredient(line.trim(), p.name(), p.quantity(),
                    p.unit() == null ? null : p.unit().key(), p.note());
        }).toList());

        recipe.replaceSteps(req.stepsOrEmpty().stream().map(text -> new RecipeStep(text.trim(), false)).toList());
        recipe.replaceTags(resolveTags(owner, req.tagsOrEmpty()));

        if (req.coverImageId() == null) {
            recipe.setCoverImage(null);
        } else {
            recipe.setCoverImage(images.findByIdAndOwnerId(req.coverImageId(), owner.getId())
                    .orElseThrow(() -> new BadRequestException("coverImageId: image not found")));
        }
    }

    /** "Italian", "italian " and "ITALIAN" are one tag. Existing tags are reused, new ones created. */
    private Set<Tag> resolveTags(User owner, List<String> names) {
        Map<String, String> byLower = new LinkedHashMap<>();
        for (String n : names) byLower.putIfAbsent(n.trim().toLowerCase(Locale.ROOT), n.trim());
        if (byLower.isEmpty()) return Set.of();

        Set<Tag> result = new HashSet<>(tags.findByOwnerAndLowerNames(owner.getId(), byLower.keySet()));
        Set<String> existing = new HashSet<>();
        for (Tag t : result) existing.add(t.getName().toLowerCase(Locale.ROOT));
        byLower.forEach((lower, original) -> {
            if (!existing.contains(lower)) result.add(tags.save(new Tag(owner, original)));
        });
        return result;
    }

    private RecipeResponse toResponse(Recipe r, Integer requestedServings) {
        boolean scale = requestedServings != null && r.getServings() != null;
        List<IngredientResponse> ingredients = r.getIngredients().stream().map(i -> new IngredientResponse(
                i.getId(), i.getRawText(), i.getName(), i.getQuantity(), i.getUnit(), i.getNote(),
                scale ? scaler.scale(i.getQuantity(), Unit.fromKey(i.getUnit()).orElse(null),
                        r.getServings(), requestedServings) : null)).toList();
        List<StepResponse> steps = r.getSteps().stream()
                .map(s -> new StepResponse(s.getPosition(), s.getText(), s.isAiGenerated())).toList();
        List<String> tagNames = r.getTags().stream().map(Tag::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();

        return new RecipeResponse(r.getId(), r.getTitle(), r.getDescription(), r.getServings(),
                scale ? requestedServings : r.getServings(), r.getTotalTimeMin(), r.getSourceUrl(),
                r.getSourceAuthor(), r.getSourceType(), r.getLanguage(),
                r.getCoverImage() == null ? null : r.getCoverImage().getId(), r.getNotes(), r.isFavorite(),
                r.getVersion(), r.getCreatedAt(), r.getUpdatedAt(), ingredients, steps, tagNames);
    }
}
