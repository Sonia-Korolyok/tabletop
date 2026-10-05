package com.tabletop.recipe.domain;

import java.math.BigDecimal;

/**
 * value: rounded number for math (grocery list), display: what the user sees ("1 ½", "250"),
 * approximate: true if rounding changed the value noticeably, UI shows "≈".
 */
public record ScaledQuantity(BigDecimal value, String display, boolean approximate) {
}
