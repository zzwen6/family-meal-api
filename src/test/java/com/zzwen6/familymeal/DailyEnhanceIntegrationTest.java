package com.zzwen6.familymeal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** 家庭日常增强：掌厨轮换、吃腻与心愿公平、库存与剩菜对照采购。聚餐不进入这些统计。 */
class DailyEnhanceIntegrationTest extends IntegrationTestBase {

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
    void cookRotationGenerateAssignSwapAndPermissions() throws Exception {
        String mom = register("de-rot-mom", "妈妈", null);
        long fid = post("/api/families", mom, Map.of("name", "轮换之家", "displayName", "妈妈")).body().get("id").asLong();
        String base = "/api/families/" + fid;
        String invite = get(base, mom).body().get("inviteCode").asText();
        String dad = register("de-rot-dad", "爸爸", invite);
        String aunt = register("de-rot-aunt", "姑姑", invite);
        JsonNode members = get(base + "/members", mom).body();
        long dadMid = memberId(members, "爸爸");
        long auntMid = memberId(members, "姑姑");
        long momMid = memberId(members, "妈妈");
        // 姑姑是掌厨，爸爸保持普通成员（被排到也不改 Role）
        assertThat(put(base + "/members/" + auntMid, mom, Map.of("role", "COOK")).status()).isEqualTo(200);

        // 默认没有安排
        Res empty = get(base + "/cook-rotation", mom);
        assertThat(empty.status()).isEqualTo(200);
        assertThat(empty.body().get("enabled").asBoolean()).isFalse();
        assertThat(get(base + "/cook-assignments?from=" + TODAY + "&to=" + TODAY.plusDays(6), mom).body()).isEmpty();

        // 普通成员不能改轮换设置
        assertThat(put(base + "/cook-rotation", dad, Map.of("enabled", true, "mealTypes", List.of("DINNER"),
                "memberIds", List.of(dadMid, auntMid))).status()).isEqualTo(403);

        Res rot = put(base + "/cook-rotation", mom, Map.of("enabled", true, "mealTypes", List.of("DINNER"),
                "memberIds", List.of(dadMid, auntMid)));
        assertThat(rot.status()).as(rot.body().toString()).isEqualTo(200);
        assertThat(rot.body().get("members")).hasSize(2);
        assertThat(rot.body().at("/members/0/memberId").asLong()).isEqualTo(dadMid);

        // 生成本周晚餐安排：次数都是 0，按顺序爸爸、姑姑轮流
        Res gen = post(base + "/cook-assignments/generate", mom, Map.of("from", TODAY.toString(), "to", TODAY.plusDays(6).toString()));
        assertThat(gen.status()).as(gen.body().toString()).isEqualTo(200);
        assertThat(gen.body()).hasSize(7);
        assertThat(gen.body().at("/0/memberName").asText()).isEqualTo("爸爸");
        assertThat(gen.body().at("/0/source").asText()).isEqualTo("AUTO");
        assertThat(gen.body().at("/1/memberName").asText()).isEqualTo("姑姑");
        assertThat(gen.body().at("/2/memberName").asText()).isEqualTo("爸爸");

        // 手工把第 3 天改派给妈妈，再生成不覆盖
        String day3 = TODAY.plusDays(2).toString();
        Res manual = put(base + "/cook-assignments", mom, Map.of("mealDate", day3, "mealType", "DINNER", "memberId", momMid));
        assertThat(manual.status()).as(manual.body().toString()).isEqualTo(200);
        assertThat(manual.body().get("source").asText()).isEqualTo("MANUAL");
        assertThat(manual.body().get("memberName").asText()).isEqualTo("妈妈");

        Res gen2 = post(base + "/cook-assignments/generate", mom, Map.of("from", TODAY.toString(), "to", TODAY.plusDays(6).toString()));
        assertThat(findByDate(gen2.body(), day3).get("memberName").asText()).isEqualTo("妈妈");
        assertThat(findByDate(gen2.body(), day3).get("source").asText()).isEqualTo("MANUAL");
        assertThat(findByDate(gen2.body(), TODAY.toString()).get("memberName").asText()).isEqualTo("爸爸");

        // 对调今天和明天
        String day2 = TODAY.plusDays(1).toString();
        Res swap = post(base + "/cook-assignments/swap", mom, Map.of(
                "date1", TODAY.toString(), "mealType1", "DINNER",
                "date2", day2, "mealType2", "DINNER"));
        assertThat(swap.status()).as(swap.body().toString()).isEqualTo(200);
        assertThat(findByDate(swap.body(), TODAY.toString()).get("memberName").asText()).isEqualTo("姑姑");
        assertThat(findByDate(swap.body(), day2).get("memberName").asText()).isEqualTo("爸爸");
        assertThat(findByDate(swap.body(), TODAY.toString()).get("source").asText()).isEqualTo("MANUAL");

        // 改回来：今天仍指定爸爸，方便后面测确认权限
        assertThat(put(base + "/cook-assignments", mom, Map.of("mealDate", TODAY.toString(), "mealType", "DINNER", "memberId", dadMid))
                .status()).isEqualTo(200);

        long dishId = createDish(mom, base, "番茄炒蛋", List.of(ing("番茄", 2, "个", false)));

        // 午餐没有安排：旧权限 —— 普通成员不能确认，掌厨可以
        Res lunchDeny = post(base + "/menus/confirm", dad, Map.of("mealDate", TODAY.toString(), "mealType", "LUNCH",
                "items", List.of(Map.of("dishId", dishId, "servings", 2))));
        assertThat(lunchDeny.status()).isEqualTo(403);
        Res lunchOk = post(base + "/menus/confirm", aunt, Map.of("mealDate", TODAY.toString(), "mealType", "LUNCH",
                "items", List.of(Map.of("dishId", dishId, "servings", 2))));
        assertThat(lunchOk.status()).as(lunchOk.body().toString()).isEqualTo(200);
        assertThat(lunchOk.body().at("/menu/confirmedByName").asText()).isEqualTo("姑姑");
        assertThat(lunchOk.body().at("/menu/proxyConfirm").asBoolean()).isFalse();

        // 今晚指定爸爸：爸爸（MEMBER）可以确认；记录实际确认人
        Res board = get(base + "/menus/board?date=" + TODAY + "&mealType=DINNER", dad);
        assertThat(board.body().get("canCook").asBoolean()).isFalse();
        assertThat(board.body().get("canRunKitchen").asBoolean()).isTrue();
        assertThat(board.body().at("/assignedCook/displayName").asText()).isEqualTo("爸爸");

        Res dadConfirm = post(base + "/menus/confirm", dad, Map.of("mealDate", TODAY.toString(), "mealType", "DINNER",
                "items", List.of(Map.of("dishId", dishId, "servings", 3))));
        assertThat(dadConfirm.status()).as(dadConfirm.body().toString()).isEqualTo(200);
        assertThat(dadConfirm.body().at("/menu/confirmedByName").asText()).isEqualTo("爸爸");
        assertThat(dadConfirm.body().at("/menu/proxyConfirm").asBoolean()).isFalse();
        assertThat(dadConfirm.body().at("/menu/assignedMemberName").asText()).isEqualTo("爸爸");

        // 重新开放后，姑姑（其他掌厨）可代为确认，界面应标明代做；管理员也可以
        long dinnerId = dadConfirm.body().at("/menu/id").asLong();
        assertThat(post(base + "/menus/" + dinnerId + "/reopen", dad, null).status()).isEqualTo(200);
        Res proxy = post(base + "/menus/confirm", aunt, Map.of("mealDate", TODAY.toString(), "mealType", "DINNER",
                "items", List.of(Map.of("dishId", dishId, "servings", 3))));
        assertThat(proxy.status()).as(proxy.body().toString()).isEqualTo(200);
        assertThat(proxy.body().at("/menu/confirmedByName").asText()).isEqualTo("姑姑");
        assertThat(proxy.body().at("/menu/assignedMemberName").asText()).isEqualTo("爸爸");
        assertThat(proxy.body().at("/menu/proxyConfirm").asBoolean()).isTrue();

        Res board2 = get(base + "/menus/board?date=" + TODAY + "&mealType=DINNER", mom);
        assertThat(board2.body().get("proxyConfirm").asBoolean()).isTrue();
        assertThat(board2.body().get("canRunKitchen").asBoolean()).isTrue(); // 管理员
    }

    @Test
    void fatigueFairnessAvoidAndGatheringExcluded() throws Exception {
        String mom = register("de-hint-mom", "妈妈", null);
        long fid = post("/api/families", mom, Map.of("name", "提示之家", "displayName", "妈妈")).body().get("id").asLong();
        String base = "/api/families/" + fid;
        String invite = get(base, mom).body().get("inviteCode").asText();
        String kid = register("de-hint-kid", "小宝", invite);
        JsonNode members = get(base + "/members", mom).body();
        long kidMid = memberId(members, "小宝");

        long pork = createDish(mom, base, "红烧肉", List.of(ing("五花肉", 500, "g", false)));
        long egg = createDish(mom, base, "番茄炒蛋", List.of(ing("番茄", 2, "个", false)));
        long cilantro = createDish(mom, base, "香菜拌豆腐", List.of(ing("香菜", 1, "把", false)));

        // 窗口内两餐都做了红烧肉，小宝的番茄炒蛋都没被选上
        String d1 = TODAY.minusDays(10).toString();
        String d2 = TODAY.minusDays(3).toString();
        setTime(TODAY.minusDays(10).atTime(9, 0));
        assertThat(post(base + "/wishes", kid, Map.of("mealDate", d1, "mealType", "DINNER", "dishId", egg, "intensity", "WANT")).status()).isEqualTo(201);
        assertThat(post(base + "/menus/confirm", mom, Map.of("mealDate", d1, "mealType", "DINNER",
                "items", List.of(Map.of("dishId", pork, "servings", 2)))).status()).isEqualTo(200);

        setTime(TODAY.minusDays(3).atTime(9, 0));
        assertThat(post(base + "/wishes", kid, Map.of("mealDate", d2, "mealType", "DINNER", "dishId", egg, "intensity", "WANT")).status()).isEqualTo(201);
        assertThat(post(base + "/menus/confirm", mom, Map.of("mealDate", d2, "mealType", "DINNER",
                "items", List.of(Map.of("dishId", pork, "servings", 2)))).status()).isEqualTo(200);

        setTime(TODAY.atTime(9, 0));
        // 聚餐确认同名菜，不应计入吃腻
        Map<String, Object> gBody = new HashMap<>();
        gBody.put("title", "同学局");
        gBody.put("mealDate", TODAY.toString());
        gBody.put("mealType", "DINNER");
        gBody.put("mode", "HOST_COOK");
        gBody.put("hostFamilyId", fid);
        Res g = post("/api/gatherings", mom, gBody);
        assertThat(g.status()).as(g.body().toString()).isEqualTo(201);
        long gid = g.body().get("id").asLong();
        Res gConfirm = post("/api/gatherings/" + gid + "/confirm", mom,
                Map.of("items", List.of(Map.of("dishName", "红烧肉", "servings", 4))));
        assertThat(gConfirm.status()).as(gConfirm.body().toString()).isEqualTo(200);

        Res hints = get(base + "/hints?date=" + TODAY + "&mealType=DINNER&dishId=" + pork, mom);
        assertThat(hints.status()).isEqualTo(200);
        JsonNode fatigue = findHint(hints.body(), "FATIGUE", "红烧肉");
        assertThat(fatigue.get("count").asInt()).isEqualTo(2);
        assertThat(fatigue.get("message").asText()).contains("吃过 2 次").contains("可能吃腻了");

        Res board = get(base + "/menus/board?date=" + TODAY + "&mealType=DINNER", mom);
        assertThat(findHint(board.body().get("hints"), "FATIGUE", "红烧肉").get("count").asInt()).isEqualTo(2);
        JsonNode fair = findHintByType(board.body().get("hints"), "FAIRNESS");
        assertThat(fair.get("memberName").asText()).isEqualTo("小宝");
        assertThat(fair.get("count").asInt()).isEqualTo(0);
        assertThat(fair.get("total").asInt()).isEqualTo(2);
        assertThat(fair.get("message").asText()).contains("还没有被选上");

        // 今晚小宝忌口香菜，确认预览和确认后都要警告
        assertThat(post(base + "/wishes", kid, Map.of("mealDate", TODAY.toString(), "mealType", "DINNER",
                "dishId", cilantro, "intensity", "AVOID")).status()).isEqualTo(201);
        Res preview = post(base + "/menus/confirm-preview", mom, Map.of("mealDate", TODAY.toString(), "mealType", "DINNER",
                "items", List.of(Map.of("dishId", cilantro, "servings", 2), Map.of("dishId", pork, "servings", 2))));
        assertThat(preview.status()).as(preview.body().toString()).isEqualTo(200);
        JsonNode avoid = findHint(preview.body().get("hints"), "AVOID", "香菜拌豆腐");
        assertThat(avoid.get("memberName").asText()).isEqualTo("小宝");
        assertThat(avoid.get("message").asText()).contains("不想吃");

        Res confirm = post(base + "/menus/confirm", mom, Map.of("mealDate", TODAY.toString(), "mealType", "DINNER",
                "items", List.of(Map.of("dishId", cilantro, "servings", 2))));
        assertThat(confirm.status()).isEqualTo(200);
        assertThat(findHint(confirm.body().get("hints"), "AVOID", "香菜拌豆腐").get("memberId").asLong()).isEqualTo(kidMid);

        // 点意愿页针对红烧肉也能看到吃腻
        Res wishHint = get(base + "/hints?date=" + TODAY + "&mealType=DINNER&name=" + url("红烧肉"), mom);
        // name 用在 query，中文直接拼接即可（测试 MockMvc 接受）
        wishHint = get(base + "/hints?date=" + TODAY + "&mealType=DINNER&dishId=" + pork, kid);
        assertThat(findHint(wishHint.body(), "FATIGUE", "红烧肉").get("count").asInt()).isEqualTo(2);
    }

    @Test
    void inventoryAndLeftoverOffsetShoppingAndPermissions() throws Exception {
        String mom = register("de-inv-mom", "妈妈", null);
        long fid = post("/api/families", mom, Map.of("name", "库存之家", "displayName", "妈妈")).body().get("id").asLong();
        String base = "/api/families/" + fid;
        String invite = get(base, mom).body().get("inviteCode").asText();
        String kid = register("de-inv-kid", "小宝", invite);

        long trotter = createDish(mom, base, "红烧猪脚", List.of(ing("猪脚", 1000, "g", false), ing("姜", 3, "片", false)));

        // 成员只读库存，不能改
        assertThat(get(base + "/inventory", kid).status()).isEqualTo(200);
        assertThat(post(base + "/inventory", kid, Map.of("name", "猪脚", "amount", 2000, "unit", "g")).status()).isEqualTo(403);
        Res inv = post(base + "/inventory", mom, Map.of("name", "猪　脚", "amount", 2000, "unit", "克"));
        assertThat(inv.status()).as(inv.body().toString()).isEqualTo(201);

        // 剩菜姜 2 片，部分扣减
        Res lo = post(base + "/leftovers", mom, Map.of("name", "姜", "amount", 2, "unit", "片"));
        assertThat(lo.status()).as(lo.body().toString()).isEqualTo(201);
        long leftoverId = lo.body().get("id").asLong();
        assertThat(post(base + "/leftovers", kid, Map.of("name", "剩饭", "amount", 1, "unit", "碗")).status()).isEqualTo(403);

        Res board = get(base + "/menus/board?date=" + TODAY + "&mealType=DINNER", mom);
        assertThat(findHintByType(board.body().get("hints"), "LEFTOVER").get("dishName").asText()).isEqualTo("姜");
        assertThat(board.body().get("leftovers")).hasSize(1);

        // 4 人份：猪脚 2000g 全额扣完；姜 6 片扣 2 剩 4
        Res confirm = post(base + "/menus/confirm", mom, Map.of("mealDate", TODAY.toString(), "mealType", "DINNER",
                "items", List.of(Map.of("dishId", trotter, "servings", 4)),
                "useLeftoverIds", List.of(leftoverId)));
        assertThat(confirm.status()).as(confirm.body().toString()).isEqualTo(200);
        JsonNode list = confirm.body().get("shoppingList");
        JsonNode pork = findBy(concat(list.get("buyItems"), list.get("stapleItems")), "name", "猪脚");
        assertThat(pork.get("toBuy").asBoolean()).isFalse();
        assertThat(pork.get("amount").decimalValue()).isEqualByComparingTo("0");
        assertThat(pork.get("note").asText()).contains("家里已有");
        JsonNode ginger = findBy(list.get("buyItems"), "name", "姜");
        assertThat(ginger.get("toBuy").asBoolean()).isTrue();
        assertThat(ginger.get("amount").decimalValue()).isEqualByComparingTo("4");
        assertThat(ginger.get("note").asText()).contains("剩菜").contains("还需买");

        // 确认时勾选用掉剩菜后，剩菜已用，重新生成不再扣（姜回到 6）
        assertThat(get(base + "/leftovers?used=false", mom).body()).isEmpty();
        long menuId = confirm.body().at("/menu/id").asLong();
        Res regen = post(base + "/menus/" + menuId + "/shopping-list/regenerate", mom, null);
        assertThat(regen.status()).isEqualTo(200);
        JsonNode ginger2 = findBy(regen.body().get("buyItems"), "name", "姜");
        assertThat(ginger2.get("amount").decimalValue()).isEqualByComparingTo("6");
        assertThat(ginger2.path("note").isMissingNode() || ginger2.path("note").isNull()
                || ginger2.get("note").asText().isBlank()).isTrue();
        // 猪脚库存仍在（对照不改库存数量），仍然全额扣完
        JsonNode pork2 = findBy(concat(regen.body().get("buyItems"), regen.body().get("stapleItems")), "name", "猪脚");
        assertThat(pork2.get("toBuy").asBoolean()).isFalse();
        assertThat(get(base + "/inventory", mom).body().get(0).get("amount").decimalValue()).isEqualByComparingTo("2000");

        // 用掉剩菜接口本身：再登记一份，用掉后不参与对照
        Res lo2 = post(base + "/leftovers", mom, Map.of("name", "猪脚", "amount", 500, "unit", "g", "menuId", menuId));
        assertThat(lo2.status()).isEqualTo(201);
        long lo2id = lo2.body().get("id").asLong();
        // 未用完时部分扣减：库存 2000 + 剩菜 500 仍盖过 2000，全额
        Res regenMid = post(base + "/menus/" + menuId + "/shopping-list/regenerate", mom, null);
        JsonNode porkMid = findBy(concat(regenMid.body().get("buyItems"), regenMid.body().get("stapleItems")), "name", "猪脚");
        assertThat(porkMid.get("toBuy").asBoolean()).isFalse();
        assertThat(post(base + "/leftovers/" + lo2id + "/use", mom, Map.of("menuId", menuId)).body().get("used").asBoolean()).isTrue();
        // 用掉后只剩库存 2000，仍全额覆盖
        Res regen3 = post(base + "/menus/" + menuId + "/shopping-list/regenerate", mom, null);
        assertThat(findBy(concat(regen3.body().get("buyItems"), regen3.body().get("stapleItems")), "name", "猪脚")
                .get("toBuy").asBoolean()).isFalse();
    }

    @Test
    void familyRulesAcceptFatigueWindow() throws Exception {
        String mom = register("de-rule-mom", "妈妈", null);
        long fid = post("/api/families", mom, Map.of("name", "规则之家", "displayName", "妈妈")).body().get("id").asLong();
        String base = "/api/families/" + fid;
        Res upd = put(base, mom, Map.of("fatigueWindowDays", 7, "fatigueThreshold", 3));
        assertThat(upd.status()).as(upd.body().toString()).isEqualTo(200);
        assertThat(upd.body().get("fatigueWindowDays").asInt()).isEqualTo(7);
        assertThat(upd.body().get("fatigueThreshold").asInt()).isEqualTo(3);
        assertThat(put(base, mom, Map.of("fatigueWindowDays", 0)).status()).isEqualTo(400);
    }

    private static long memberId(JsonNode members, String name) {
        return findBy(members, "displayName", name).get("id").asLong();
    }

    private static JsonNode findBy(JsonNode arr, String field, String value) {
        for (JsonNode n : arr) if (value.equals(n.path(field).asText())) return n;
        throw new AssertionError("未找到 " + field + "=" + value + " in " + arr);
    }

    private static JsonNode findByDate(JsonNode arr, String date) {
        for (JsonNode n : arr) if (date.equals(n.path("mealDate").asText()) && "DINNER".equals(n.path("mealType").asText())) return n;
        throw new AssertionError("未找到日期 " + date + " in " + arr);
    }

    private static JsonNode findHint(JsonNode arr, String type, String dishName) {
        for (JsonNode n : arr) {
            if (type.equals(n.path("type").asText()) && n.path("dishName").asText().contains(dishName)) return n;
        }
        throw new AssertionError("未找到提示 " + type + "/" + dishName + " in " + arr);
    }

    private static JsonNode findHintByType(JsonNode arr, String type) {
        for (JsonNode n : arr) if (type.equals(n.path("type").asText())) return n;
        throw new AssertionError("未找到提示类型 " + type + " in " + arr);
    }

    private static JsonNode concat(JsonNode a, JsonNode b) {
        com.fasterxml.jackson.databind.node.ArrayNode arr = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        a.forEach(arr::add);
        b.forEach(arr::add);
        return arr;
    }

    private static String url(String s) {
        return s;
    }
}
