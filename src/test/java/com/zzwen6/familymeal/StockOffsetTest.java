package com.zzwen6.familymeal;

import com.zzwen6.familymeal.shopping.StockOffset;
import com.zzwen6.familymeal.shopping.StockOffset.Kind;
import com.zzwen6.familymeal.shopping.StockOffset.PoolItem;
import com.zzwen6.familymeal.shopping.StockOffset.Result;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockOffsetTest {
    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test
    void fullyCoveredByInventory() {
        Result r = StockOffset.apply("猪脚", bd("2000"), "g",
                List.of(new PoolItem(Kind.INVENTORY, "猪 脚", bd("2000"), "克")));
        assertThat(r.fullyCovered()).isTrue();
        assertThat(r.remaining()).isEqualByComparingTo("0");
        assertThat(r.note()).contains("家里已有").contains("库存");
    }

    @Test
    void partialUsesLeftoverFirstThenInventory() {
        Result r = StockOffset.apply("姜", bd("10"), "片", List.of(
                new PoolItem(Kind.LEFTOVER, "姜", bd("2"), "片"),
                new PoolItem(Kind.INVENTORY, "姜", bd("3"), "片")));
        assertThat(r.fullyCovered()).isFalse();
        assertThat(r.remaining()).isEqualByComparingTo("5");
        assertThat(r.note()).contains("剩菜").contains("库存").contains("还需买");
    }

    @Test
    void unitMismatchDoesNotChangeAmount() {
        Result r = StockOffset.apply("盐", bd("5"), "g",
                List.of(new PoolItem(Kind.INVENTORY, "盐", bd("1"), "袋")));
        assertThat(r.fullyCovered()).isFalse();
        assertThat(r.remaining()).isEqualByComparingTo("5");
        assertThat(r.note()).contains("单位不同");
    }

    @Test
    void noMatchLeavesNeededUnchanged() {
        Result r = StockOffset.apply("鸡腿", bd("8"), "个", List.of());
        assertThat(r.applied()).isFalse();
        assertThat(r.remaining()).isEqualByComparingTo("8");
        assertThat(r.note()).isNull();
    }
}
