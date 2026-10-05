-- When a recipe is edited, ingredients/steps are replaced as a whole list.
-- Hibernate flushes INSERTs before DELETEs, so for a moment the old and the new row
-- with the same (recipe_id, position) exist together. DEFERRABLE INITIALLY DEFERRED
-- makes PostgreSQL check uniqueness at COMMIT instead of after each statement.

ALTER TABLE recipe_ingredients
    DROP CONSTRAINT recipe_ingredients_recipe_id_position_key,
    ADD CONSTRAINT ux_recipe_ingredients_position UNIQUE (recipe_id, position) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE recipe_steps
    DROP CONSTRAINT recipe_steps_recipe_id_position_key,
    ADD CONSTRAINT ux_recipe_steps_position UNIQUE (recipe_id, position) DEFERRABLE INITIALLY DEFERRED;
