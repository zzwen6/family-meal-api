package com.zzwen6.familymeal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 菜谱媒体上传、营养估算接口，以及库存自动增减（勾已买入账/撤回、用掉剩菜、开饭扣库存、幂等）。
 * 聚餐不进入营养和家庭库存。
 */
class MediaNutritionInventoryIntegrationTest extends IntegrationTestBase {

    private String register(String username, String nickname, String invite) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("username", username, "password", "secret-1", "nickname", nickname));
        if (invite != null) body.put("inviteCode", invite);
        Res r = post("/api/auth/register", null, body);
        assertThat(r.status()).as(r.body().toString()).isEqualTo(201);
        return r.body().get("token").asText();
    }

    private Map<String, Object> ing(String name, Object amount, String unit, boolean staple) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("amount", amount);
        m.put("unit", unit);
        m.put("staple", staple);
        return m;
    }

    private long createDish(String token, String base, String name, List<Map<String, Object>> ingredients) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("category", "家常");
        body.put("difficulty", "EASY");
        body.put("durationMinutes", 30);
        body.put("defaultServings", 2);
        body.put("ingredients", ingredients);
        Res r = post(base + "/dishes", token, body);
        assertThat(r.status()).as(r.body().toString()).isEqualTo(201);
        return r.body().get("id").asLong();
    }

    @Test
    void mediaUploadPermissionsAndRejection() throws Exception {
        String mom = register("mn-media-mom", "妈妈", null);
        long fid = post("/api/families", mom, Map.of("name", "媒体之家", "displayName", "妈妈")).body().get("id").asLong();
        String base = "/api/families/" + fid;
        String invite = get(base, mom).body().get("inviteCode").asText();
        String kid = register("mn-media-kid", "小宝", invite);
        long kidMid = memberId(get(base + "/members", mom).body(), "小宝");
        assertThat(put(base + "/members/" + kidMid, mom, Map.of("role", "CHILD")).status()).isEqualTo(200);
        // 普通成员
        Res join = post("/api/auth/register", null, Map.of("username", "mn-media-mem", "password", "secret-1",
                "nickname", "奶奶", "inviteCode", invite));
        assertThat(join.status()).isEqualTo(201);
        String grandma = join.body().get("token").asText();

        byte[] jpeg = jpegBytes(200);
        var ok = mvc.perform(MockMvcRequestBuilders.multipart(base + "/media")
                        .file(new MockMultipartFile("file", "cover.jpg", "image/jpeg", jpeg))
                        .header("Authorization", "Bearer " + mom)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        JsonNode uploaded = om.readTree(ok.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String url = uploaded.get("url").asText();
        assertThat(url).startsWith(base + "/media/");
        String stored = uploaded.get("storedName").asText();

        // 非掌厨不能上传
        var childUp = mvc.perform(MockMvcRequestBuilders.multipart(base + "/media")
                        .file(new MockMultipartFile("file", "c.jpg", "image/jpeg", jpeg))
                        .header("Authorization", "Bearer " + kid))
                .andReturn();
        assertThat(childUp.getResponse().getStatus()).isEqualTo(403);
        var memUp = mvc.perform(MockMvcRequestBuilders.multipart(base + "/media")
                        .file(new MockMultipartFile("file", "c.jpg", "image/jpeg", jpeg))
                        .header("Authorization", "Bearer " + grandma))
                .andReturn();
        assertThat(memUp.getResponse().getStatus()).isEqualTo(403);

        // 非法类型
        var bad = mvc.perform(MockMvcRequestBuilders.multipart(base + "/media")
                        .file(new MockMultipartFile("file", "x.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + mom))
                .andReturn();
        assertThat(bad.getResponse().getStatus()).isEqualTo(400);

        // 超限（测试配置图片上限 8KB）
        var huge = mvc.perform(MockMvcRequestBuilders.multipart(base + "/media")
                        .file(new MockMultipartFile("file", "big.jpg", "image/jpeg", jpegBytes(9000)))
                        .header("Authorization", "Bearer " + mom))
                .andReturn();
        assertThat(huge.getResponse().getStatus()).isEqualTo(400);

        // 成员和儿童可读；未登录 401
        var kidGet = mvc.perform(MockMvcRequestBuilders.get(base + "/media/" + stored)
                        .header("Authorization", "Bearer " + kid))
                .andReturn();
        assertThat(kidGet.getResponse().getStatus()).isEqualTo(200);
        assertThat(kidGet.getResponse().getContentType()).startsWith("image/jpeg");
        var memGet = mvc.perform(MockMvcRequestBuilders.get(base + "/media/" + stored)
                        .header("Authorization", "Bearer " + grandma))
                .andReturn();
        assertThat(memGet.getResponse().getStatus()).isEqualTo(200);
        var anon = mvc.perform(MockMvcRequestBuilders.get(base + "/media/" + stored)).andReturn();
        assertThat(anon.getResponse().getStatus()).isEqualTo(401);
        // query token 也可以
        var q = mvc.perform(MockMvcRequestBuilders.get(base + "/media/" + stored + "?access_token=" + kid)).andReturn();
        assertThat(q.getResponse().getStatus()).isEqualTo(200);

        // 写到菜谱封面
        long dishId = createDish(mom, base, "番茄炒蛋", List.of(ing("番茄", 2, "个", false), ing("鸡蛋", 3, "个", false)));
        Res upd = put(base + "/dishes/" + dishId, mom, Map.of(
                "name", "番茄炒蛋", "category", "家常", "difficulty", "EASY",
                "durationMinutes", 15, "defaultServings", 2, "coverUrl", url,
                "ingredients", List.of(ing("番茄", 2, "个", false), ing("鸡蛋", 3, "个", false)),
                "steps", List.of(Map.of("content", "炒一炒", "imageUrl", url))));
        assertThat(upd.status()).as(upd.body().toString()).isEqualTo(200);
        assertThat(upd.body().get("coverUrl").asText()).isEqualTo(url);
        assertThat(upd.body().get("steps").get(0).get("imageUrl").asText()).isEqualTo(url);
        // 营养估算：番茄、鸡蛋都在表里且有单件克数
        assertThat(upd.body().at("/nutrition/estimated").asBoolean()).isTrue();
        assertThat(upd.body().at("/nutrition/total/kcal").decimalValue()).isGreaterThan(BigDec("0"));

        // 手工覆盖优先
        Res ov = put(base + "/dishes/" + dishId, mom, Map.of(
                "name", "番茄炒蛋", "category", "家常", "difficulty", "EASY",
                "durationMinutes", 15, "defaultServings", 2, "coverUrl", url,
                "ingredients", List.of(ing("番茄", 2, "个", false)),
                "nutrition", Map.of("enabled", true, "kcal", 80, "protein", 5, "fat", 3, "carb", 6)));
        assertThat(ov.status()).as(ov.body().toString()).isEqualTo(200);
        assertThat(ov.body().at("/nutrition/override").asBoolean()).isTrue();
        assertThat(ov.body().at("/nutrition/total/kcal").decimalValue()).isEqualByComparingTo(BigDec("80"));
    }

    @Test
    void inventoryAutoAdjustIsIdempotentAndReadyDeductsOffsetOnly() throws Exception {
        String mom = register("mn-inv-mom", "妈妈", null);
        long fid = post("/api/families", mom, Map.of("name", "自动库存之家", "displayName", "妈妈")).body().get("id").asLong();
        String base = "/api/families/" + fid;
        String invite = get(base, mom).body().get("inviteCode").asText();
        String kid = register("mn-inv-kid", "小宝", invite);
        long kidMid = memberId(get(base + "/members", mom).body(), "小宝");
        assertThat(put(base + "/members/" + kidMid, mom, Map.of("role", "CHILD")).status()).isEqualTo(200);

        long dish = createDish(mom, base, "红烧猪脚",
                List.of(ing("猪脚", 1000, "g", false), ing("姜", 3, "片", false), ing("盐", 5, "g", true)));
        assertThat(post(base + "/inventory", mom, Map.of("name", "猪脚", "amount", 2000, "unit", "g")).status()).isEqualTo(201);
        assertThat(post(base + "/inventory", mom, Map.of("name", "姜", "amount", 10, "unit", "片")).status()).isEqualTo(201);
        Res lo = post(base + "/leftovers", mom, Map.of("name", "姜", "amount", 2, "unit", "片"));
        long leftoverId = lo.body().get("id").asLong();

        // 生成清单不改库存数量
        Res confirm = post(base + "/menus/confirm", mom, Map.of("mealDate", TODAY.toString(), "mealType", "DINNER",
                "items", List.of(Map.of("dishId", dish, "servings", 4)),
                "useLeftoverIds", List.of()));
        assertThat(confirm.status()).as(confirm.body().toString()).isEqualTo(200);
        long menuId = confirm.body().at("/menu/id").asLong();
        assertThat(invAmount(base, mom, "猪脚")).isEqualByComparingTo("2000");
        assertThat(invAmount(base, mom, "姜")).isEqualByComparingTo("10");
        // 4 人份：猪脚 2000 全额库存覆盖；姜 6 片，剩菜 2 + 库存 4
        JsonNode list = confirm.body().get("shoppingList");
        JsonNode pork = findBy(concat(list.get("buyItems"), list.get("stapleItems")), "name", "猪脚");
        assertThat(pork.get("toBuy").asBoolean()).isFalse();
        // 营养在已确认菜单上（只含有菜谱的菜）；盐 0 热量表内不列为未计入
        assertThat(confirm.body().at("/menu/nutrition/estimated").asBoolean()).isTrue();
        assertThat(confirm.body().at("/menu/nutrition/total/kcal").isNumber()).isTrue();

        // 用掉剩菜「姜」：名称对得上库存，扣 2 片 → 8；只扣一次
        Res used = post(base + "/leftovers/" + leftoverId + "/use", mom, Map.of("menuId", menuId));
        assertThat(used.body().get("used").asBoolean()).isTrue();
        assertThat(invAmount(base, mom, "姜")).isEqualByComparingTo("8");
        post(base + "/leftovers/" + leftoverId + "/use", mom, Map.of("menuId", menuId));
        assertThat(invAmount(base, mom, "姜")).isEqualByComparingTo("8");

        // 菜肴类剩菜对不上库存食材，只标记已用
        Res dishLo = post(base + "/leftovers", mom, Map.of("name", "红烧猪脚", "amount", 1, "unit", "碗", "menuId", menuId));
        assertThat(post(base + "/leftovers/" + dishLo.body().get("id").asLong() + "/use", mom, Map.of("menuId", menuId)).status()).isEqualTo(200);
        assertThat(invAmount(base, mom, "猪脚")).isEqualByComparingTo("2000");

        // 勾已买：盐默认不必买，把手改成要买再勾已买入账
        JsonNode salt = findBy(concat(list.get("buyItems"), list.get("stapleItems")), "name", "盐");
        long saltId = salt.get("id").asLong();
        assertThat(patch(base + "/shopping-items/" + saltId, mom, Map.of("toBuy", true)).status()).isEqualTo(200);
        assertThat(patch(base + "/shopping-items/" + saltId, kid, Map.of("purchased", true)).status()).isEqualTo(403);
        assertThat(patch(base + "/shopping-items/" + saltId, mom, Map.of("purchased", true)).status()).isEqualTo(200);
        assertThat(invAmount(base, mom, "盐")).isEqualByComparingTo("10"); // 4 人份 × 5g = 10g，常备但改成要买
        // 重复勾不双计
        assertThat(patch(base + "/shopping-items/" + saltId, mom, Map.of("purchased", true)).status()).isEqualTo(200);
        assertThat(invAmount(base, mom, "盐")).isEqualByComparingTo("10");
        // 取消撤回
        assertThat(patch(base + "/shopping-items/" + saltId, mom, Map.of("purchased", false)).status()).isEqualTo(200);
        assertThat(invAmount(base, mom, "盐")).isEqualByComparingTo("0");
        // 再勾一次又入账
        assertThat(patch(base + "/shopping-items/" + saltId, mom, Map.of("purchased", true)).status()).isEqualTo(200);
        assertThat(invAmount(base, mom, "盐")).isEqualByComparingTo("10");

        // 开饭：扣对照时来自库存的部分（猪脚 2000、姜 4），不与剩菜已扣的 2 片双扣
        Res ready = post(base + "/menus/" + menuId + "/ready", mom, null);
        assertThat(ready.status()).as(ready.body().toString()).isEqualTo(200);
        assertThat(invAmount(base, mom, "猪脚")).isEqualByComparingTo("0");
        assertThat(invAmount(base, mom, "姜")).isEqualByComparingTo("4"); // 10 - 2(剩菜) - 4(开饭)
        // 流水可见
        Res ledger = get(base + "/inventory/ledger?limit=20", mom);
        assertThat(ledger.status()).isEqualTo(200);
        assertThat(ledger.body()).isNotEmpty();
        // 生成清单仍不改数量：再看一遍库存不变
        assertThat(invAmount(base, mom, "猪脚")).isEqualByComparingTo("0");
    }

    private static long memberId(JsonNode members, String name) {
        return findBy(members, "displayName", name).get("id").asLong();
    }

    private java.math.BigDecimal invAmount(String base, String token, String name) throws Exception {
        JsonNode arr = get(base + "/inventory", token).body();
        for (JsonNode n : arr) if (name.equals(n.path("name").asText())) return n.get("amount").decimalValue();
        throw new AssertionError("没有库存 " + name + " in " + arr);
    }

    private static JsonNode findBy(JsonNode arr, String field, String value) {
        for (JsonNode n : arr) if (value.equals(n.path(field).asText())) return n;
        throw new AssertionError("未找到 " + field + "=" + value + " in " + arr);
    }

    private static JsonNode concat(JsonNode a, JsonNode b) {
        var arr = omArr();
        a.forEach(arr::add);
        b.forEach(arr::add);
        return arr;
    }

    private static com.fasterxml.jackson.databind.node.ArrayNode omArr() {
        return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
    }

    private static java.math.BigDecimal BigDec(String s) { return new java.math.BigDecimal(s); }

    private static byte[] jpegBytes(int size) {
        byte[] b = new byte[Math.max(size, 4)];
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        b[3] = (byte) 0xD9;
        return b;
    }
}
