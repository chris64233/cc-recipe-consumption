package com.chris64233.recipeconsumption.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 统一数量精度规则。
 * 所有实物/标准当量数量保留 4 位小数，运算后立即归整，四舍五入（HALF_UP）；
 * 换算系数、替代比例保留 6 位小数。所有比较只使用 compareTo，禁止使用 equals。
 */
public final class QuantityMath {

    /** 数量精度（位） */
    public static final int SCALE = 4;

    /** 系数/比例精度（位） */
    public static final int RATIO_SCALE = 6;

    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private QuantityMath() {
    }

    /** 归整为统一数量精度。null 视为 0。 */
    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return value.setScale(SCALE, ROUNDING);
    }

    /** 归整换算系数/比例。 */
    public static BigDecimal normalizeRatio(BigDecimal value) {
        return value.setScale(RATIO_SCALE, ROUNDING);
    }

    /** 数量加法（结果归整） */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return normalize(nz(a).add(nz(b)));
    }

    /** 数量减法（结果归整） */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return normalize(nz(a).subtract(nz(b)));
    }

    /** 数量乘法（结果归整） */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return normalize(nz(a).multiply(nz(b)));
    }

    /** 按换算系数把实物数量折算为标准当量（结果归整） */
    public static BigDecimal toEquivalent(BigDecimal qty, BigDecimal conversionRatio) {
        return normalize(nz(qty).multiply(nz(conversionRatio)));
    }

    public static boolean isPositive(BigDecimal value) {
        return nz(value).compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isNonNegative(BigDecimal value) {
        return nz(value).compareTo(BigDecimal.ZERO) >= 0;
    }

    /** 数量相等（统一精度下 compareTo == 0） */
    public static boolean eq(BigDecimal a, BigDecimal b) {
        return normalize(a).compareTo(normalize(b)) == 0;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
