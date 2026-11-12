package com.zzwen6.familymeal;

import com.fasterxml.jackson.databind.JsonNode;
import com.zzwen6.familymeal.menu.MenuDeadlineJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** 主流程：点餐意愿 → 截止 → 掌厨确认菜单 → 采购清单（合并、缩放、常备品）→ 开饭通知，以及租户隔离与角色校验。 */
class MealFlowIntegrationTest extends IntegrationTestBase {

    @Autowired MenuDeadlineJob deadlineJob;

    private Map<String, Object> ing(String name, Object amount, String unit, boolean staple) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("amount", amount);
        m.put("unit", unit);
        m.put("staple", staple);
        return m;
    }

    private String register(String username, String nickname, String invite) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("username", username, "password", "secret-1", "nickname", nickname));
        if (invite != null) body.put("inviteCode", invite);
        Res r = post("/api/auth/register", null, body);
        assertThat(r.status()).as(r.body().toString()).isEqualTo(201);
        return r.body().get("token").asText();
    }

    @Test
    void wishToMenuToShoppingListHappyPath() throws Exception {
        // ---------- 建家、拉人 ----------
        String mom = register("flow-mom", "妈妈", null);
        Res fam = post("/api/families", mom, Map.of("name", "周家", "displayName", "妈妈"));
        assertThat(fam.status()).isEqualTo(201);
        long fid = fam.body().get("id").asLong();
        String invite = fam.body().get("inviteCode").asText();
        String base = "/api/families/" + fid;

        String dad = register("flow-dad", "爸爸", invite);
        String kid = register("flow-kid", "小宝", invite);
        String grandma = register("flow-grandma", "奶奶", invite);

        JsonNode members = get(base + "/members", mom).body();
        assertThat(members).hasSize(4);
        long dadMid = memberId(members, "爸爸");
        long kidMid = memberId(members, "小宝");
        assertThat(put(base + "/members/" + dadMid, mom, Map.of("role", "COOK")).status()).isEqualTo(200);
        assertThat(put(base + "/members/" + kidMid, mom, Map.of("role", "CHILD", "allergies", "花生")).status()).isEqualTo(200);
        // 孩子不能改别人角色
        assertThat(put(base + "/members/" + dadMid, kid, Map.of("role", "MEMBER")).status()).isEqualTo(403);

        // ---------- 菜谱：掌厨维护，孩子不能改 ----------
        Map<String, Object> trotters = new HashMap<>();
        trotters.put("name", "红烧猪脚");
        trotters.put("category", "硬菜");
        trotters.put("difficulty", "HARD");
        trotters.put("durationMinutes", 120);
        trotters.put("defaultServings", 2);
        trotters.put("steps", List.of(Map.of("content", "猪脚焯水去腥", "durationMinutes", 10),
                Map.of("content", "炒糖色后加生抽、姜片小火炖 90 分钟")));
        trotters.put("ingredients", List.of(ing("猪脚", 1000, "g", false)));
        trotters.put("seasonings", List.of(ing("冰糖", 30, "g", false), ing("姜", 3, "片", false),
                ing("生抽", 2, "勺", true), ing("盐", null, null, true)));
        Res d1 = post(base + "/dishes", dad, trotters);
        assertThat(d1.status()).as(d1.body().toString()).isEqualTo(201);
        long trotterId = d1.body().get("id").asLong();
        assertThat(d1.body().get("steps")).hasSize(2);
        assertThat(d1.body().get("seasonings")).hasSize(4);

        Map<String, Object> wings = new HashMap<>(trotters);
        wings.put("name", "可乐鸡腿");
        wings.put("category", "孩子爱吃");
        wings.put("difficulty", "EASY");
        wings.put("durationMinutes", 40);
        wings.put("steps", List.of(Map.of("content", "鸡腿划刀，加可乐、生抽焖 25 分钟")));
        wings.put("ingredients", List.of(ing("鸡腿", 4, "只", false), ing("可乐", 1, "罐", false)));
        wings.put("seasonings", List.of(ing("姜", 2, "片", false), ing("生抽", 1, "勺", true)));
        long wingId = post(base + "/dishes", dad, wings).body().get("id").asLong();

        assertThat(post(base + "/dishes", kid, wings).status()).isEqualTo(403);

        // 份量换算：4 人份 = 默认 2 人份的两倍
        Res scaled = get(base + "/dishes/" + trotterId + "?servings=4", kid);
        assertThat(scaled.body().at("/ingredients/0/amount").decimalValue()).isEqualByComparingTo("2000");
        assertThat(scaled.body().at("/seasonings/0/amount").decimalValue()).isEqualByComparingTo("60");

        // ---------- 点餐意愿 ----------
        String today = TODAY.toString();
        Res w1 = post(base + "/wishes", mom, Map.of("mealDate", today, "mealType", "DINNER", "dishId", trotterId, "intensity", "WANT"));
        assertThat(w1.status()).as(w1.body().toString()).isEqualTo(201);
        Res w2 = post(base + "/wishes", kid, Map.of("mealDate", today, "mealType", "DINNER", "dishId", wingId, "intensity", "WANT", "servings", 2));
        assertThat(w2.status()).isEqualTo(201);
        long kidWishId = w2.body().get("id").asLong();
        assertThat(post(base + "/wishes", kid, Map.of("mealDate", today, "mealType", "DINNER", "freeText", "番茄炒蛋", "intensity", "OK")).status()).isEqualTo(201);
        assertThat(post(base + "/wishes", grandma, Map.of("mealDate", today, "mealType", "DINNER", "notEatingHome", true)).status()).isEqualTo(201);
        // 孩子不能替别人点
        assertThat(post(base + "/wishes", kid, Map.of("mealDate", today, "mealType", "DINNER", "dishId", wingId, "memberId", dadMid)).status()).isEqualTo(403);
        // 空意愿被拒绝
        assertThat(post(base + "/wishes", kid, Map.of("mealDate", today, "mealType", "DINNER")).status()).isEqualTo(400);

        // 截止前可以改
        Res edit = put(base + "/wishes/" + kidWishId, kid, Map.of("mealDate", today, "mealType", "DINNER", "dishId", wingId, "intensity", "WANT", "servings", 1));
        assertThat(edit.status()).isEqualTo(200);
        assertThat(edit.body().get("servings").asInt()).isEqualTo(1);

        Res board = get(base + "/menus/board?date=" + today + "&mealType=DINNER", dad);
        assertThat(board.status()).isEqualTo(200);
        assertThat(board.body().get("headcount").asInt()).isEqualTo(3); // 奶奶不在家
        assertThat(board.body().get("votes")).hasSize(3);
        assertThat(board.body().get("canCook").asBoolean()).isTrue();
        assertThat(board.body().at("/menu/status").asText()).isEqualTo("OPEN");
        JsonNode dadStatus = findBy(board.body().get("members"), "displayName", "爸爸");
        assertThat(dadStatus.get("submitted").asBoolean()).isFalse();

        // ---------- 截止 ----------
        setTime(TODAY.atTime(16, 30));
        Res late = put(base + "/wishes/" + kidWishId, kid, Map.of("mealDate", today, "mealType", "DINNER", "dishId", wingId, "servings", 3));
        assertThat(late.status()).isEqualTo(409);
        assertThat(late.body().get("code").asText()).isEqualTo("WISH_CLOSED");
        assertThat(deadlineJob.closeExpired()).isGreaterThanOrEqualTo(1);
        assertThat(get(base + "/menus/board?date=" + today + "&mealType=DINNER", kid).body().at("/menu/status").asText())
                .isEqualTo("LOCKED");

        // ---------- 掌厨确认菜单：采纳猪脚和鸡腿，番茄炒蛋改为替代菜 ----------
        Map<String, Object> custom = new HashMap<>();
        custom.put("customName", "西红柿鸡蛋汤");
        custom.put("note", "番茄炒蛋改成汤");
        Res confirm = post(base + "/menus/confirm", dad, Map.of("mealDate", today, "mealType", "DINNER",
                "items", List.of(Map.of("dishId", trotterId, "servings", 4), Map.of("dishId", wingId, "servings", 4), custom),
                "note", "今晚 6 点半开饭"));
        assertThat(confirm.status()).as(confirm.body().toString()).isEqualTo(200);
        assertThat(confirm.body().at("/menu/status").asText()).isEqualTo("CONFIRMED");
        assertThat(confirm.body().at("/menu/items/0/source").asText()).isEqualTo("WISH");
        assertThat(confirm.body().at("/menu/items/2/name").asText()).isEqualTo("西红柿鸡蛋汤");
        long menuId = confirm.body().at("/menu/id").asLong();

        JsonNode list = confirm.body().get("shoppingList");
        JsonNode buy = list.get("buyItems");
        assertThat(findBy(buy, "name", "猪脚").get("amount").decimalValue()).isEqualByComparingTo("2000");
        assertThat(findBy(buy, "name", "鸡腿").get("amount").decimalValue()).isEqualByComparingTo("8");
        JsonNode ginger = findBy(buy, "name", "姜");
        assertThat(ginger.get("amount").decimalValue()).isEqualByComparingTo("10"); // 3*2 + 2*2 合并
        assertThat(ginger.get("sourceDishes").asText()).contains("红烧猪脚").contains("可乐鸡腿");
        // 常备品默认不买
        JsonNode soy = findBy(list.get("stapleItems"), "name", "生抽");
        assertThat(soy.get("amount").decimalValue()).isEqualByComparingTo("6");
        assertThat(soy.get("toBuy").asBoolean()).isFalse();
        JsonNode salt = findBy(list.get("stapleItems"), "name", "盐");
        assertThat(salt.has("amount")).isFalse(); // 适量

        // 孩子不能改买不买；掌厨可以把生抽勾回采购
        long soyId = soy.get("id").asLong();
        assertThat(patch(base + "/shopping-items/" + soyId, kid, Map.of("toBuy", true)).status()).isEqualTo(403);
        assertThat(patch(base + "/shopping-items/" + soyId, dad, Map.of("toBuy", true)).body().get("toBuy").asBoolean()).isTrue();
        long porkId = findBy(buy, "name", "猪脚").get("id").asLong();
        assertThat(patch(base + "/shopping-items/" + porkId, kid, Map.of("purchased", true)).status()).isEqualTo(200);

        // 重新生成保留勾选
        Res regen = post(base + "/menus/" + menuId + "/shopping-list/regenerate", dad, null);
        assertThat(regen.status()).isEqualTo(200);
        assertThat(findBy(regen.body().get("buyItems"), "name", "生抽").get("toBuy").asBoolean()).isTrue();
        assertThat(findBy(regen.body().get("buyItems"), "name", "猪脚").get("purchased").asBoolean()).isTrue();
        assertThat(regen.body().get("purchasedCount").asInt()).isEqualTo(1);

        // 菜单确认后不能再改意愿（掌厨也要先重新开放）
        Res afterConfirm = post(base + "/wishes", dad, Map.of("mealDate", today, "mealType", "DINNER", "dishId", wingId));
        assertThat(afterConfirm.status()).isEqualTo(409);
        assertThat(afterConfirm.body().get("code").asText()).isEqualTo("MENU_CONFIRMED");

        // ---------- 租户隔离 ----------
        String stranger = register("flow-stranger", "邻居", null);
        long otherFid = post("/api/families", stranger, Map.of("name", "隔壁家")).body().get("id").asLong();
        assertThat(get(base + "/dishes", stranger).status()).isEqualTo(403);
        assertThat(get(base + "/menus/" + menuId + "/shopping-list", stranger).status()).isEqualTo(403);
        assertThat(patch("/api/families/" + otherFid + "/shopping-items/" + porkId, stranger, Map.of("purchased", false)).status())
                .isEqualTo(404);
        assertThat(get("/api/families/" + otherFid + "/dishes/" + trotterId, stranger).status()).isEqualTo(404);

        // ---------- 开饭 ----------
        assertThat(post(base + "/menus/" + menuId + "/ready", kid, null).status()).isEqualTo(403);
        Res ready = post(base + "/menus/" + menuId + "/ready", dad, null);
        assertThat(ready.status()).isEqualTo(200);
        assertThat(ready.body().get("status").asText()).isEqualTo("READY");
        JsonNode notes = get(base + "/notifications", kid).body();
        assertThat(notes.get(0).get("type").asText()).isEqualTo("DINNER_READY");
        assertThat(notes.get(0).get("wechatStatus").asText()).isEqualTo("SKIPPED");
        assertThat(notes.get(1).get("type").asText()).isEqualTo("MENU_CONFIRMED");

        // 日历
        Res cal = get(base + "/menus?from=" + today + "&to=" + TODAY.plusDays(6), mom);
        assertThat(cal.body()).hasSize(1);
        assertThat(cal.body().at("/0/wishCount").asInt()).isEqualTo(4);
    }

    @Test
    void cookCanReopenAndAdminRulesChangeDeadline() throws Exception {
        String admin = adminToken();
        long fid = get("/api/families", admin).body().at("/0/id").asLong();
        String base = "/api/families/" + fid;
        Res upd = put(base, admin, Map.of("dinnerDeadline", "08:00"));
        assertThat(upd.status()).isEqualTo(200);
        assertThat(upd.body().get("dinnerDeadline").asText()).isEqualTo("08:00");

        String invite = get(base, admin).body().get("inviteCode").asText();
        String member = register("rule-member", "表哥", invite);
        String tomorrow = TODAY.plusDays(1).toString();
        // 9:00 时已过今天晚餐 08:00 的截止
        Res closed = post(base + "/wishes", member, Map.of("mealDate", TODAY.toString(), "mealType", "DINNER", "freeText", "火锅"));
        assertThat(closed.status()).isEqualTo(409);
        // 明天的还能点
        assertThat(post(base + "/wishes", member, Map.of("mealDate", tomorrow, "mealType", "DINNER", "freeText", "火锅")).status())
                .isEqualTo(201);

        // 管理员（可掌厨）重新开放今天晚餐 1 小时
        // 普通成员不能重新开放
        assertThat(post(base + "/menus/reopen", member, Map.of("mealDate", TODAY.toString(), "mealType", "DINNER")).status())
                .isEqualTo(403);
        Res reopen = post(base + "/menus/reopen", admin, Map.of("mealDate", TODAY.toString(), "mealType", "DINNER"));
        assertThat(reopen.status()).as(reopen.body().toString()).isEqualTo(200);
        assertThat(reopen.body().get("status").asText()).isEqualTo("OPEN");
        assertThat(reopen.body().get("deadline").asText()).isEqualTo(TODAY + "T10:00:00");
        assertThat(post(base + "/wishes", member, Map.of("mealDate", TODAY.toString(), "mealType", "DINNER", "freeText", "火锅")).status())
                .isEqualTo(201);
        assertThat(put(base, member, Map.of("dinnerDeadline", "20:00")).status()).isEqualTo(403);
        put(base, admin, Map.of("dinnerDeadline", "16:00"));
    }

    private static long memberId(JsonNode members, String name) {
        return findBy(members, "displayName", name).get("id").asLong();
    }

    private static JsonNode findBy(JsonNode arr, String field, String value) {
        for (JsonNode n : arr) if (value.equals(n.path(field).asText())) return n;
        throw new AssertionError("未找到 " + field + "=" + value + " in " + arr);
    }
}
