package com.tabletop.recipe;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    /** A live recipe of this user, or empty (also for someone else's recipe -> 404). */
    @Query("select r from Recipe r where r.id = :id and r.owner.id = :ownerId and r.deletedAt is null")
    Optional<Recipe> findOwned(Long id, Long ownerId);

    /** Same, but loads tags and cover in the same SQL query to avoid N+1 on the recipe screen. */
    @EntityGraph(attributePaths = {"tags", "coverImage"})
    @Query("select r from Recipe r where r.id = :id and r.owner.id = :ownerId and r.deletedAt is null")
    Optional<Recipe> findOwnedWithDetails(Long id, Long ownerId);
}
