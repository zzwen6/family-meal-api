package com.zzwen6.familymeal;

import com.zzwen6.familymeal.dish.Amounts;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmountsTest {
    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test
    void scalesProportionally() {
        assertThat(Amounts.scale(bd("1000"), 4, 2, "g")).isEqualByComparingTo("2000");
        assertThat(Amounts.scale(bd("30"), 3, 2, "g")).isEqualByComparingTo("45");
        assertThat(Amounts.scale(null, 4, 2, "g")).isNull();
    }

    @Test
    void roundsToFriendlyNumbersByUnit() {
        // 8 人份 / 默认 3 人份 × 1 罐 = 2.67 罐 → 买 3 罐
        assertThat(Amounts.scale(bd("1"), 8, 3, "罐")).isEqualByComparingTo("3");
        // 1000g × 8/3 = 2666.67g → 2667g
        assertThat(Amounts.scale(bd("1000"), 8, 3, "g")).isEqualByComparingTo("2667");
        // 2 勺 × 8/3 = 5.33 勺 → 5.5 勺
        assertThat(Amounts.scale(bd("2"), 8, 3, "勺")).isEqualByComparingTo("5.5");
        // 小数量的克数保留小数
        assertThat(Amounts.scale(bd("1.5"), 3, 2, "g")).isEqualByComparingTo("2.25");
        // 没有单位就保留两位
        assertThat(Amounts.scale(bd("1"), 2, 3, null)).isEqualByComparingTo("0.67");
    }
}
