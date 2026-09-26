package com.chris64233.recipeconsumption.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class QuantityMathTest {

    @Test
    void normalize_uses_four_scale_half_up() {
        assertThat(QuantityMath.normalize(new BigDecimal("1.23455")))
                .isEqualByComparingTo(new BigDecimal("1.2346"));
        assertThat(QuantityMath.normalize(new BigDecimal("1.23454")))
                .isEqualByComparingTo(new BigDecimal("1.2345"));
    }

    @Test
    void multiplication_and_equivalent_are_normalized() {
        assertThat(QuantityMath.multiply(new BigDecimal("2"), new BigDecimal("2.00005")))
                .isEqualByComparingTo(new BigDecimal("4.0001"));
        assertThat(QuantityMath.toEquivalent(new BigDecimal("12.5"), new BigDecimal("0.8")))
                .isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    void nulls_treated_as_zero() {
        assertThat(QuantityMath.normalize(null)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(QuantityMath.add(null, new BigDecimal("1.5")))
                .isEqualByComparingTo(new BigDecimal("1.5000"));
    }

    @Test
    void equality_is_value_based_not_scale_based() {
        assertThat(QuantityMath.eq(new BigDecimal("1.2"), new BigDecimal("1.20000"))).isTrue();
    }
}
