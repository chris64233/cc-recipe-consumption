package com.chris64233.recipeconsumption.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 全系统统一的数量精度规则。
 *
 * <ul>
 *   <li>所有入库、库存、单据数量统一保留 {@value #SCALE} 位小数（{@link RoundingMode#HALF_UP}）；</li>
 *   <li>替代比例是 0~1 的小数，保留 {@value #RATIO_SCALE} 位；</li>
 *   <li>任何换算结果落库前必须经过 {@link #normalize(BigDecimal)}；</li>
 *   <li>数量比较一律使用 {@link BigDecimal#compareTo}，不依赖 scale。</li>
 * </ul>
 */
public final class Quantities {

    /** 数量精度：4 位小数。 */
    public static final int SCALE = 4;

    /** 比例精度：6 位小数（最多表示到百万分之一）。 */
    public static final int RATIO_SCALE = 6;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);

    private Quantities() {
    }

    /** 将任意数量规整到统一精度，null 视为 0。 */
    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** 将比例规整到比例精度。 */
    public static BigDecimal normalizeRatio(BigDecimal ratio) {
        if (ratio == null) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
        }
        return ratio.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /** 严格为正（> 0）。 */
    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    /** 非负（>= 0）。 */
    public static boolean isNonNegative(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) >= 0;
    }

    /** a 是否大于等于 b（按 compareTo 比较）。 */
    public static boolean gte(BigDecimal a, BigDecimal b) {
        return normalize(a).compareTo(normalize(b)) >= 0;
    }

    /** a 是否大于 b（按 compareTo 比较）。 */
    public static boolean gt(BigDecimal a, BigDecimal b) {
        return normalize(a).compareTo(normalize(b)) > 0;
    }

    /** a 与 b 在统一精度下是否相等。 */
    public static boolean eq(BigDecimal a, BigDecimal b) {
        return normalize(a).compareTo(normalize(b)) == 0;
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return normalize(normalize(a).add(normalize(b)));
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return normalize(normalize(a).subtract(normalize(b)));
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return normalize(a.multiply(b));
    }

    /**
     * 用统一精度做除法，避免除不尽时抛出 {@link ArithmeticException}。
     */
    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        if (!isPositive(b)) {
            throw new ArithmeticException("除数必须为正数");
        }
        return a.divide(b, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 校验业务入参数量：非 null、非负（或为正），落库时统一规整。
     *
     * @param field    字段名，用于异常信息
     * @param value    数量
     * @param positive true 表示必须严格大于 0
     * @return 规整后的数量
     */
    public static BigDecimal require(String field, BigDecimal value, boolean positive) {
        if (value == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        if (positive ? value.compareTo(BigDecimal.ZERO) <= 0 : value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(field + " 必须" + (positive ? "大于 0" : "大于等于 0") + ": " + value);
        }
        return normalize(value);
    }
}
