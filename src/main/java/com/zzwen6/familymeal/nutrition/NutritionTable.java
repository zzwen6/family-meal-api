package com.zzwen6.familymeal.nutrition;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzwen6.familymeal.common.Names;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内置常见中式食材每 100g 营养参考（中国食物成分表风格的约数，不是检测值或官方认证）。
 * 名称经 {@link Names} 规范化后匹配，别名（如「鸡胸 / 鸡胸肉」）指向同一条目。
 */
@Component
public class NutritionTable {
    private static final Logger log = LoggerFactory.getLogger(NutritionTable.class);
    private final Map<String, FoodEntry> byName = new HashMap<>();

    public NutritionTable(ObjectMapper om) {
        try (InputStream in = new ClassPathResource("nutrition/foods.json").getInputStream()) {
            List<JsonNode> rows = om.readValue(in, new TypeReference<>() {});
            for (JsonNode n : rows) {
                String name = n.path("name").asText(null);
                if (name == null || name.isBlank()) continue;
                FoodEntry entry = new FoodEntry(
                        name.trim(),
                        om.convertValue(n.get("aliases"), new TypeReference<List<String>>() {}),
                        dec(n, "kcal"), dec(n, "protein"), dec(n, "fat"), dec(n, "carb"),
                        n.hasNonNull("pieceGrams") ? n.get("pieceGrams").decimalValue() : null,
                        n.path("zeroEnergy").asBoolean(false));
                index(name, entry);
                if (entry.aliases() != null) {
                    for (String a : entry.aliases()) index(a, entry);
                }
            }
            log.info("loaded {} nutrition food keys", byName.size());
        } catch (Exception e) {
            throw new IllegalStateException("无法加载营养参考表 nutrition/foods.json", e);
        }
    }

    private void index(String name, FoodEntry entry) {
        String key = Names.normalize(name);
        if (!key.isEmpty()) byName.putIfAbsent(key, entry);
    }

    private static BigDecimal dec(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? BigDecimal.ZERO : v.decimalValue();
    }

    public Optional<FoodEntry> find(String ingredientName) {
        String key = Names.normalize(ingredientName);
        if (key.isEmpty()) return Optional.empty();
        FoodEntry exact = byName.get(key);
        if (exact != null) return Optional.of(exact);
        // 较长的别名/标准名包含关系：如「鸡胸肉丝」含「鸡胸肉」
        FoodEntry best = null;
        int bestLen = 0;
        for (var e : byName.entrySet()) {
            String k = e.getKey();
            if (k.length() >= 2 && key.contains(k) && k.length() > bestLen) {
                best = e.getValue();
                bestLen = k.length();
            }
        }
        return Optional.ofNullable(best);
    }
}
