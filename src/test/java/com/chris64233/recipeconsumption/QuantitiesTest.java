package com.chris64233.recipeconsumption;

import com.chris64233.recipeconsumption.support.Quantities;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 统一精度规则：4 位数量、6 位比例、HALF_UP、除零保护、入参校验。
 */
class QuantitiesTest {

    @Test
    void normalizesToFourDecimalsHalfUp() {
        assertThat(Quantities.normalize(new BigDecimal("1.23455")))
                .isEqualByComparingTo("1.2346");
        assertThat(Quantities.normalize(new BigDecimal("1.23454")))
                .isEqualByComparingTo("1.2345");
        assertThat(Quantities.normalize(null)).isEqualByComparingTo("0");
    }

    @Test
    void divisionUsesFixedScale() {
        assertThat(Quantities.divide(new BigDecimal("1"), new BigDecimal("3")))
                .isEqualByComparingTo("0.3333");
        assertThatThrownBy(() -> Quantities.divide(BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void requireRejectsNullZeroAndNegative() {
        assertThatThrownBy(() -> Quantities.require("q", null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quantities.require("q", BigDecimal.ZERO, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quantities.require("q", new BigDecimal("-1"), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Quantities.require("q", new BigDecimal("2"), false)).isEqualByComparingTo("2.0000");
    }

    @Test
    void comparisonsAreScaleInsensitive() {
        assertThat(Quantities.eq(new BigDecimal("1.0"), new BigDecimal("1.0000"))).isTrue();
        assertThat(Quantities.gte(new BigDecimal("1.0001"), new BigDecimal("1.0000"))).isTrue();
    }
}
