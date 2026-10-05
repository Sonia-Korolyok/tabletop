package com.tabletop.recipe.domain;

import java.math.BigDecimal;

/**
 * Result of parsing one ingredient line.
 * quantity and unit are null when the line has none ("salt to taste").
 */
public record ParsedIngredient(BigDecimal quantity, Unit unit, String name, String note) {
}
