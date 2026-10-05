package com.tabletop.recipe.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns "1 1/2 ч. л. соли" into quantity=1.5, unit=TSP, name="соли".
 * Never throws: anything it cannot understand stays in the name, and raw text is stored anyway.
 * Plain Java on purpose: no Spring, easy to unit-test.
 */
public final class IngredientParser {

    private static final Map<Character, String> UNICODE_FRACTIONS = Map.of(
            '½', "1/2", '⅓', "1/3", '⅔', "2/3", '¼', "1/4", '¾', "3/4", '⅛', "1/8");

    // number: "2", "1.5", "1,5", "1/2", "1 1/2"
    // order matters: the regex takes the first alternative that matches
    private static final String NUMBER = "\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:[.,]\\d+)?";
    // optional range: "2-3", "2–3"
    private static final Pattern LEADING_QUANTITY = Pattern.compile(
            "^(" + NUMBER + ")(?:\\s*[-–—]\\s*(" + NUMBER + "))?\\s*");

    private static final Set<String> TO_TASTE = Set.of("по вкусу", "to taste", "optional", "по желанию");

    /** Aliases sorted longest first, so "ст. л." wins over "с". */
    private static final List<Map.Entry<String, Unit>> ALIASES = buildAliases();

    public ParsedIngredient parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Ingredient text is empty");
        }
        String text = normalize(raw);
        String note = null;

        // "(about 300 g)" and everything after the first comma go to note
        Matcher paren = Pattern.compile("\\(([^)]*)\\)").matcher(text);
        List<String> notes = new ArrayList<>();
        while (paren.find()) notes.add(paren.group(1).trim());
        text = paren.replaceAll("").replaceAll("\\s{2,}", " ").trim();
        int comma = text.indexOf(',');
        if (comma > 0 && !Character.isDigit(text.charAt(comma - 1))) {
            notes.add(text.substring(comma + 1).trim());
            text = text.substring(0, comma).trim();
        }
        for (String phrase : TO_TASTE) {
            if (text.toLowerCase(Locale.ROOT).endsWith(" " + phrase)) {
                notes.add(phrase);
                text = text.substring(0, text.length() - phrase.length()).trim();
            }
        }
        notes.removeIf(String::isBlank);
        if (!notes.isEmpty()) note = String.join("; ", notes);

        BigDecimal quantity = null;
        Matcher q = LEADING_QUANTITY.matcher(text);
        if (q.find()) {
            quantity = toNumber(q.group(1));
            if (q.group(2) != null) {
                // "2-3 cloves": keep the lower bound for math, the range for the human
                note = join(q.group(1) + "–" + q.group(2), note);
            }
            text = text.substring(q.end());
        }

        Unit unit = null;
        if (quantity != null) {
            for (Map.Entry<String, Unit> alias : ALIASES) {
                String a = alias.getKey();
                if (startsWithWord(text, a)) {
                    unit = alias.getValue();
                    text = text.substring(a.length()).replaceFirst("^\\.", "").trim();
                    break;
                }
            }
            if (text.toLowerCase(Locale.ROOT).startsWith("of ")) text = text.substring(3);
        }

        String name = text.trim();
        if (name.isEmpty()) name = raw.trim();   // e.g. "200 g" alone: better than an empty name
        return new ParsedIngredient(quantity, unit, name, note);
    }

    private static String normalize(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.trim().toCharArray()) {
            String fraction = UNICODE_FRACTIONS.get(c);
            if (fraction != null) {
                // "1½" -> "1 1/2"
                if (!sb.isEmpty() && Character.isDigit(sb.charAt(sb.length() - 1))) sb.append(' ');
                sb.append(fraction);
            } else {
                sb.append(c);
            }
        }
        // "200g" -> "200 g"
        return sb.toString().replaceAll("(\\d)([^\\d\\s.,/\\-–—])", "$1 $2").replaceAll("\\s+", " ");
    }

    static BigDecimal toNumber(String s) {
        s = s.trim().replace(',', '.');
        String[] parts = s.split("\\s+");
        BigDecimal total = BigDecimal.ZERO;
        for (String part : parts) {
            if (part.contains("/")) {
                String[] f = part.split("/");
                total = total.add(new BigDecimal(f[0]).divide(new BigDecimal(f[1]), 3, RoundingMode.HALF_UP));
            } else {
                total = total.add(new BigDecimal(part));
            }
        }
        return total.stripTrailingZeros();
    }

    /** Alias must be followed by end of text, a space or a dot: "г" matches "г муки", not "горчицы". */
    private static boolean startsWithWord(String text, String alias) {
        if (!text.regionMatches(true, 0, alias, 0, alias.length())) return false;
        if (text.length() == alias.length()) return true;
        char next = text.charAt(alias.length());
        return Character.isWhitespace(next) || (next == '.' && !alias.endsWith("."));
    }

    private static String join(String a, String b) {
        return b == null ? a : a + "; " + b;
    }

    private static List<Map.Entry<String, Unit>> buildAliases() {
        List<Map.Entry<String, Unit>> list = new ArrayList<>();
        for (Unit u : Unit.values()) for (String a : u.aliases()) list.add(Map.entry(a, u));
        list.sort((x, y) -> y.getKey().length() - x.getKey().length());
        return List.copyOf(list);
    }
}
