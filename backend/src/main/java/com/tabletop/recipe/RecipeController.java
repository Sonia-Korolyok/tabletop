package com.tabletop.recipe;

import com.tabletop.common.CurrentUser;
import com.tabletop.recipe.RecipeDtos.FavoriteRequest;
import com.tabletop.recipe.RecipeDtos.RecipeRequest;
import com.tabletop.recipe.RecipeDtos.RecipeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService service;

    public RecipeController(RecipeService service) {
        this.service = service;
    }

    /** 201 Created + Location header with the new recipe's address. */
    @PostMapping
    public ResponseEntity<RecipeResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody RecipeRequest req) {
        RecipeResponse created = service.create(CurrentUser.id(jwt), req);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public RecipeResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                              @RequestParam(required = false) Integer servings) {
        return service.get(CurrentUser.id(jwt), id, servings);
    }

    @PutMapping("/{id}")
    public RecipeResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                 @Valid @RequestBody RecipeRequest req) {
        return service.update(CurrentUser.id(jwt), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.delete(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/restore")
    public RecipeResponse restore(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.restore(CurrentUser.id(jwt), id);
    }

    @PatchMapping("/{id}/favorite")
    public RecipeResponse favorite(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                   @Valid @RequestBody FavoriteRequest req) {
        return service.setFavorite(CurrentUser.id(jwt), id, req.favorite());
    }
}
