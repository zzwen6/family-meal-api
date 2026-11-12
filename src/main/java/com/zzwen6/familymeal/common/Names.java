package com.zzwen6.familymeal.common;

import java.util.Map;

/** 名称与单位规范化：去空白、全角转半角、忽略大小写，用于吃腻匹配和库存对照。 */
public final class Names {
    private Names() {}

    private static final Map<String, String> UNIT_ALIASES = Map.ofEntries(
            Map.entry("g", "g"), Map.entry("克", "g"), Map.entry("公克", "g"),
            Map.entry("kg", "kg"), Map.entry("千克", "kg"), Map.entry("公斤", "kg"),
            Map.entry("ml", "ml"), Map.entry("毫升", "ml"),
            Map.entry("l", "l"), Map.entry("升", "l"),
            Map.entry("勺", "勺"), Map.entry("汤匙", "勺"),
            Map.entry("茶匙", "茶匙")
    );

    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == '\u3000') continue;
            if (c >= 0xFF01 && c <= 0xFF5E) c = (char) (c - 0xFEE0);
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    public static boolean same(String a, String b) {
        return normalize(a).equals(normalize(b));
    }

    /** 单位规范化，并把常见别名（克/g、毫升/ml）视为同一单位。 */
    public static String normalizeUnit(String unit) {
        String n = normalize(unit);
        return UNIT_ALIASES.getOrDefault(n, n);
    }

    public static boolean sameUnit(String a, String b) {
        return normalizeUnit(a).equals(normalizeUnit(b));
    }

    /** 单位兼容：相同，或其中一侧未填单位。 */
    public static boolean compatibleUnit(String a, String b) {
        String na = normalizeUnit(a);
        String nb = normalizeUnit(b);
        return na.isEmpty() || nb.isEmpty() || na.equals(nb);
    }
}
