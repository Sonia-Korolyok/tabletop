package com.tabletop.recipe.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngredientParserTest {

    private final IngredientParser parser = new IngredientParser();

    @ParameterizedTest(name = "\"{0}\" -> {1} {2} {3}")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            200 г муки                 | 200  | G     | муки
            200 г. муки                | 200  | G     | муки
            200g flour                 | 200  | G     | flour
            1,5 кг картофеля           | 1.5  | KG    | картофеля
            1.5 kg potatoes            | 1.5  | KG    | potatoes
            1/2 ч. л. соли             | 0.5  | TSP   | соли
            1 1/2 cups of milk         | 1.5  | CUP   | milk
            1½ cups flour              | 1.5  | CUP   | flour
            ½ tsp salt                 | 0.5  | TSP   | salt
            2 ст.л. сахара             | 2    | TBSP  | сахара
            3 столовые ложки масла     | 3    | TBSP  | масла
            2 tbsp olive oil           | 2    | TBSP  | olive oil
            2 яйца                     | 2    | -     | яйца
            3 large eggs               | 3    | -     | large eggs
            1 банка томатов            | 1    | CAN   | томатов
            400 мл молока              | 400  | ML    | молока
            2 горчицы                  | 2    | -     | горчицы
            """)
    void parsesQuantityUnitAndName(String raw, BigDecimal quantity, Unit unit, String name) {
        ParsedIngredient p = parser.parse(raw);

        assertThat(p.quantity()).isEqualByComparingTo(quantity);
        assertThat(p.unit()).isEqualTo(unit);
        assertThat(p.name()).isEqualTo(name);
    }

    @Test
    void lineWithoutQuantity() {
        ParsedIngredient p = parser.parse("соль по вкусу");

        assertThat(p.quantity()).isNull();
        assertThat(p.unit()).isNull();
        assertThat(p.name()).isEqualTo("соль");
        assertThat(p.note()).isEqualTo("по вкусу");
    }

    @Test
    void rangeKeepsLowerBoundAndRememberRangeInNote() {
        ParsedIngredient p = parser.parse("2-3 зубчика чеснока");

        assertThat(p.quantity()).isEqualByComparingTo("2");
        assertThat(p.unit()).isEqualTo(Unit.CLOVE);
        assertThat(p.name()).isEqualTo("чеснока");
        assertThat(p.note()).isEqualTo("2–3");
    }

    @Test
    void parenthesesAndTextAfterCommaGoToNote() {
        ParsedIngredient p = parser.parse("1 луковица (крупная), мелко нарезать");

        assertThat(p.quantity()).isEqualByComparingTo("1");
        assertThat(p.name()).isEqualTo("луковица");
        assertThat(p.note()).isEqualTo("крупная; мелко нарезать");
    }

    @Test
    void unitOnlyLineKeepsRawTextAsName() {
        assertThat(parser.parse("200 g").name()).isEqualTo("200 g");
    }

    @Test
    void emptyLineIsRejected() {
        assertThatThrownBy(() -> parser.parse("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
