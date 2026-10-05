package com.tabletop.recipe.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Recalculates an ingredient quantity for another number of servings and rounds it like a human would:
 * 133.33 g -> 135 g, 0.666 tsp -> ⅔ tsp, 1.5 eggs -> 2 eggs (≈).
 * The original quantity in the database is never changed.
 */
public final class ServingsScaler {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    /** More than 5% difference after rounding -> mark as approximate. */
    private static final BigDecimal APPROX_THRESHOLD = new BigDecimal("0.05");

    private static final BigDecimal[] FRACTIONS = {
            new BigDecimal("0"), new BigDecimal("0.125"), new BigDecimal("0.25"), new BigDecimal("0.333"),
            new BigDecimal("0.5"), new BigDecimal("0.667"), new BigDecimal("0.75"), new BigDecimal("1")};
    private static final String[] FRACTION_GLYPHS = {"", "⅛", "¼", "⅓", "½", "⅔", "¾", ""};

    public ScaledQuantity scale(BigDecimal quantity, Unit unit, int fromServings, int toServings) {
        if (quantity == null) return null;   // "salt to taste" stays as is
        if (fromServings <= 0 || toServings <= 0) {
            throw new IllegalArgumentException("Servings must be positive");
        }
        BigDecimal exact = quantity.multiply(BigDecimal.valueOf(toServings))
                .divide(BigDecimal.valueOf(fromServings), MC);

        Unit.Kind kind = unit == null ? Unit.Kind.COUNT : unit.kind();
        return switch (kind) {
            case MASS, VOLUME -> roundMetric(exact);
            case SPOON -> roundToFraction(exact, false);
            case COUNT -> roundToFraction(exact, unit == null || unit == Unit.PCS);
        };
    }

    /** 3.33 -> 3.3, 47.6 -> 48, 133.3 -> 135, 1234 -> 1230. */
    private ScaledQuantity roundMetric(BigDecimal exact) {
        BigDecimal rounded;
        if (exact.compareTo(BigDecimal.TEN) < 0) {
            rounded = exact.setScale(1, RoundingMode.HALF_UP);
        } else if (exact.compareTo(BigDecimal.valueOf(100)) < 0) {
            rounded = exact.setScale(0, RoundingMode.HALF_UP);
        } else if (exact.compareTo(BigDecimal.valueOf(1000)) < 0) {
            rounded = roundToStep(exact, BigDecimal.valueOf(5));
        } else {
            rounded = roundToStep(exact, BigDecimal.TEN);
        }
        rounded = rounded.stripTrailingZeros();
        return new ScaledQuantity(rounded, rounded.toPlainString(), isApproximate(exact, rounded));
    }

    /**
     * Spoons and cups: nearest ⅛/¼/⅓/½/⅔/¾. Whole items (eggs, pieces): nearest whole, at least 1.
     * Other countables (cloves, pinches): nearest ½.
     */
    private ScaledQuantity roundToFraction(BigDecimal exact, boolean wholeOnly) {
        if (wholeOnly) {
            BigDecimal whole = exact.setScale(0, RoundingMode.HALF_UP).max(BigDecimal.ONE);
            return new ScaledQuantity(whole, whole.toPlainString(), isApproximate(exact, whole));
        }
        BigDecimal intPart = exact.setScale(0, RoundingMode.FLOOR);
        BigDecimal frac = exact.subtract(intPart);
        int best = 0;
        for (int i = 1; i < FRACTIONS.length; i++) {
            if (frac.subtract(FRACTIONS[i]).abs().compareTo(frac.subtract(FRACTIONS[best]).abs()) < 0) best = i;
        }
        if (best == FRACTIONS.length - 1) {          // 0.95 -> next whole number
            intPart = intPart.add(BigDecimal.ONE);
            best = 0;
        }
        if (intPart.signum() == 0 && best == 0) best = 1;   // never show 0 tsp, at least ⅛

        BigDecimal value = intPart.add(FRACTIONS[best]).stripTrailingZeros();
        String glyph = FRACTION_GLYPHS[best];
        String display = intPart.signum() == 0 ? glyph
                : glyph.isEmpty() ? intPart.toPlainString() : intPart.toPlainString() + " " + glyph;
        return new ScaledQuantity(value, display, isApproximate(exact, value));
    }

    private static BigDecimal roundToStep(BigDecimal value, BigDecimal step) {
        return value.divide(step, 0, RoundingMode.HALF_UP).multiply(step);
    }

    private static boolean isApproximate(BigDecimal exact, BigDecimal rounded) {
        if (exact.signum() == 0) return false;
        BigDecimal diff = exact.subtract(rounded).abs().divide(exact, MC);
        return diff.compareTo(APPROX_THRESHOLD) > 0;
    }
}
