package com.tabletop.recipe.domain;

import java.util.*;

/**
 * Canonical units. The database stores the key ("g", "tsp"); the frontend shows it in the user's language.
 * Aliases cover how people actually write units in Russian and English recipes.
 */
public enum Unit {
    G("g", Kind.MASS, "г", "гр", "грамм", "грамма", "граммов", "g", "gr", "gram", "grams"),
    KG("kg", Kind.MASS, "кг", "килограмм", "килограмма", "kg", "kilogram", "kilograms"),
    ML("ml", Kind.VOLUME, "мл", "миллилитр", "миллилитра", "миллилитров", "ml", "milliliter", "milliliters", "millilitre"),
    L("l", Kind.VOLUME, "л", "литр", "литра", "литров", "l", "liter", "liters", "litre", "litres"),
    TSP("tsp", Kind.SPOON, "ч. л.", "ч.л.", "чл", "чайная ложка", "чайные ложки", "чайных ложек",
            "tsp", "teaspoon", "teaspoons"),
    TBSP("tbsp", Kind.SPOON, "ст. л.", "ст.л.", "стл", "столовая ложка", "столовые ложки", "столовых ложек",
            "tbsp", "tablespoon", "tablespoons"),
    CUP("cup", Kind.SPOON, "стакан", "стакана", "стаканов", "cup", "cups"),
    PCS("pcs", Kind.COUNT, "шт", "шт.", "штука", "штуки", "штук", "pc", "pcs", "piece", "pieces"),
    CLOVE("clove", Kind.COUNT, "зубчик", "зубчика", "зубчиков", "clove", "cloves"),
    PINCH("pinch", Kind.COUNT, "щепотка", "щепотки", "щепоток", "pinch", "pinches"),
    PACK("pack", Kind.COUNT, "пачка", "пачки", "пачек", "упаковка", "упаковки", "упаковок",
            "pack", "packs", "package", "packages"),
    CAN("can", Kind.COUNT, "банка", "банки", "банок", "can", "cans"),
    OZ("oz", Kind.MASS, "унция", "унции", "унций", "oz", "ounce", "ounces"),
    LB("lb", Kind.MASS, "фунт", "фунта", "фунтов", "lb", "lbs", "pound", "pounds");

    /** How a quantity in this unit is rounded after scaling. */
    public enum Kind { MASS, VOLUME, SPOON, COUNT }

    private final String key;
    private final Kind kind;
    private final List<String> aliases;

    Unit(String key, Kind kind, String... aliases) {
        this.key = key;
        this.kind = kind;
        this.aliases = List.of(aliases);
    }

    public String key() { return key; }
    public Kind kind() { return kind; }
    List<String> aliases() { return aliases; }

    public static Optional<Unit> fromKey(String key) {
        if (key == null) return Optional.empty();
        for (Unit u : values()) if (u.key.equals(key)) return Optional.of(u);
        return Optional.empty();
    }
}
