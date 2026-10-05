package com.tabletop.recipe.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServingsScalerTest {

    private final ServingsScaler scaler = new ServingsScaler();

    @ParameterizedTest(name = "{0} {1}: {2} -> {3} servings = {4} (≈ {5})")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            200   | G     | 4 | 4  | 200  | false
            200   | G     | 4 | 8  | 400  | false
            200   | G     | 3 | 2  | 135  | false
            500   | G     | 4 | 10 | 1250 | false
            5     | ML    | 3 | 2  | 3.3  | false
            1     | TSP   | 4 | 2  | ½    | false
            1     | TSP   | 3 | 2  | ⅔    | false
            1     | TSP   | 4 | 1  | ¼    | false
            1     | TSP   | 8 | 1  | ⅛    | false
            0.5   | TSP   | 8 | 1  | ⅛    | true
            1.5   | CUP   | 2 | 3  | 2 ¼  | false
            2     | TBSP  | 3 | 4  | 2 ⅔  | false
            3     | -     | 4 | 2  | 2    | true
            1     | -     | 4 | 1  | 1    | true
            2     | CLOVE | 4 | 3  | 1 ½  | false
            """)
    void scalesAndRoundsLikeAHuman(BigDecimal qty, Unit unit, int from, int to, String display, boolean approx) {
        ScaledQuantity s = scaler.scale(qty, unit, from, to);

        assertThat(s.display()).isEqualTo(display);
        assertThat(s.approximate()).isEqualTo(approx);
    }

    @Test
    void noQuantityStaysNull() {
        assertThat(scaler.scale(null, null, 4, 8)).isNull();
    }

    @Test
    void valueIsUsableForMath() {
        assertThat(scaler.scale(new BigDecimal("1.5"), Unit.CUP, 2, 3).value()).isEqualByComparingTo("2.25");
    }

    @Test
    void servingsMustBePositive() {
        assertThatThrownBy(() -> scaler.scale(BigDecimal.ONE, Unit.G, 0, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
