package com.zzwen6.familymeal;

import com.zzwen6.familymeal.common.Names;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamesTest {
    @Test
    void normalizesSpacesCaseAndFullWidth() {
        assertThat(Names.normalize(" 猪　脚 ")).isEqualTo("猪脚");
        assertThat(Names.same("Soy Sauce", "soy  sauce")).isTrue();
        assertThat(Names.same("番茄炒蛋", "番　茄炒蛋")).isTrue();
        assertThat(Names.normalize("ＡＢＣ１２３")).isEqualTo("abc123");
        assertThat(Names.same("猪肉", "猪脚")).isFalse();
    }

    @Test
    void unitAliasesAndCompatibility() {
        assertThat(Names.sameUnit("g", "克")).isTrue();
        assertThat(Names.sameUnit("ml", "毫升")).isTrue();
        assertThat(Names.compatibleUnit("g", "")).isTrue();
        assertThat(Names.compatibleUnit("g", "片")).isFalse();
        assertThat(Names.normalizeUnit("千克")).isEqualTo("kg");
    }
}
