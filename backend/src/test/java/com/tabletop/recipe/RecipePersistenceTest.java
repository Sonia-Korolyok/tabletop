package com.tabletop.recipe;

import com.tabletop.folder.Folder;
import com.tabletop.tag.Tag;
import com.tabletop.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Checks that entities match the Flyway schema and that cascades work as intended. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class RecipePersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    EntityManager em;
    @Autowired
    RecipeRepository recipes;

    User sonia;

    @BeforeEach
    void setUp() {
        sonia = new User("sonia@example.com", "hash", "Sonia");
        em.persist(sonia);
    }

    @Test
    void savesRecipeWithChildrenInOneCallAndReadsThemInOrder() {
        Recipe lasagna = new Recipe(sonia, "  Классическая мясная лазанья ", SourceType.MANUAL);
        lasagna.replaceIngredients(List.of(
                new RecipeIngredient("500 г фарша", "фарш", new BigDecimal("500"), "г", null),
                new RecipeIngredient("соль по вкусу", "соль", null, null, "по вкусу")));
        lasagna.replaceSteps(List.of(new RecipeStep("Обжарить фарш", false), new RecipeStep("Собрать слои", false)));

        recipes.save(lasagna);          // cascade = ALL: ingredients and steps are inserted too
        em.flush();
        em.clear();                     // forget everything, next read really goes to the database

        Recipe loaded = recipes.findOwned(lasagna.getId(), sonia.getId()).orElseThrow();
        assertThat(loaded.getTitle()).isEqualTo("Классическая мясная лазанья");
        assertThat(loaded.getIngredients()).extracting(RecipeIngredient::getName).containsExactly("фарш", "соль");
        assertThat(loaded.getSteps()).extracting(RecipeStep::getPosition).containsExactly(0, 1);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void replacingIngredientsDeletesOrphans() {
        Recipe pizza = new Recipe(sonia, "Pepperoni pizza", SourceType.MANUAL);
        pizza.replaceIngredients(List.of(
                new RecipeIngredient("pepperoni", "pepperoni", null, null, null),
                new RecipeIngredient("mozzarella", "mozzarella", null, null, null)));
        recipes.save(pizza);
        em.flush();

        // same positions 0 and 1 are reused: works thanks to DEFERRABLE constraint from V3
        pizza.replaceIngredients(List.of(
                new RecipeIngredient("mushrooms", "mushrooms", null, null, null),
                new RecipeIngredient("pepperoni", "pepperoni", null, null, null)));
        em.flush();
        em.clear();

        Long rows = em.createQuery("select count(i) from RecipeIngredient i where i.recipe.id = :id", Long.class)
                .setParameter("id", pizza.getId()).getSingleResult();
        assertThat(rows).isEqualTo(2);  // orphanRemoval deleted the old two
    }

    @Test
    void tagsAndFoldersAreManyToMany() {
        Tag italian = new Tag(sonia, "Italian");
        Folder dinners = new Folder(sonia, "Dinners");
        em.persist(italian);
        em.persist(dinners);

        Recipe a = new Recipe(sonia, "Lasagna", SourceType.MANUAL);
        Recipe b = new Recipe(sonia, "Pizza", SourceType.MANUAL);
        a.replaceTags(Set.of(italian));
        b.replaceTags(Set.of(italian));
        a.replaceFolders(Set.of(dinners));
        recipes.saveAll(List.of(a, b));
        em.flush();
        em.clear();

        assertThat(recipes.findOwnedWithDetails(b.getId(), sonia.getId()).orElseThrow().getTags())
                .extracting(Tag::getName).containsExactly("Italian");
        // deleting a recipe removes only the link rows, the tag itself stays
        recipes.deleteById(a.getId());
        em.flush();
        assertThat(em.find(Tag.class, italian.getId())).isNotNull();
    }

    @Test
    void softDeletedRecipeIsInvisible() {
        Recipe r = recipes.save(new Recipe(sonia, "Borscht", SourceType.MANUAL));
        r.softDelete(java.time.OffsetDateTime.now());
        em.flush();

        assertThat(recipes.findOwned(r.getId(), sonia.getId())).isEmpty();
        assertThat(recipes.findById(r.getId())).isPresent();   // still in the table, can be restored
    }

    @Test
    void someoneElsesRecipeIsNotFound() {
        User other = new User("other@example.com", "hash", null);
        em.persist(other);
        Recipe r = recipes.save(new Recipe(other, "Secret soup", SourceType.MANUAL));

        assertThat(recipes.findOwned(r.getId(), sonia.getId())).isEmpty();
    }

    @Test
    void staleVersionIsRejected() {
        Recipe r = recipes.save(new Recipe(sonia, "Pie", SourceType.MANUAL));
        em.flush();
        // another device already saved this recipe: version in the database moved on
        em.createNativeQuery("update recipes set version = version + 1 where id = " + r.getId()).executeUpdate();

        r.updateDetails("Shepherd's pie", null, 4, 90, null, null, "en", null);
        assertThatThrownBy(() -> em.flush()).isInstanceOf(OptimisticLockException.class);
    }
}
