package io.github.lorencmarkja.tradelens.economics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Presentation helpers: values are rounded to cents (HALF_UP) only at the edge of the system. */
public final class Money {

    private Money() {
    }

    public static BigDecimal round(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static String display(BigDecimal value) {
        return value == null ? null : round(value).toPlainString();
    }

    /** Trims insignificant zeros of stored inputs, e.g. 140.000000 -> "140", 140.5 -> "140.5". */
    public static String input(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }
}
