package com.zzwen6.familymeal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 儿童简化点餐与监护、周菜单模板、统计与餐后评价。
 * 聚餐数据不得进入模板和统计。
 */
class ChildTemplateStatsIntegrationTest extends IntegrationTestBase {

    private Map<String, Object> dish(String name, String category) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("category", category);
        m.put("difficulty", "EASY");
        m.put("durationMinutes", 20);
        m.put("defaultServings", 2);
        m.put("steps", List.of(Map.of("content", "炒一下")));
        m.put("ingredients", List.of(Map.of("name", "主料", "amount", 100, "unit", "g", "staple", false)));
        m.put("seasonings", List.of());
        return m;
    }

    private String register(String username, String nickname, String invite) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("username", username, "password", "secret-1", "nickname", nickname));
        if (invite != null) body.put("inviteCode", invite);
        Res r = post("/api/auth/register", null, body);
        assertThat(r.status()).as(r.body().toString()).isEqualTo(201);
        return r.body().get("token").asText();
    }

    private static long mid(JsonNode members, String name) {
        return findBy(members, "displayName", name).get("id").asLong();
    }

    private static JsonNode findBy(JsonNode arr, String field, String value) {
        for (JsonNode n : arr) if (value.equals(n.path(field).asText())) return n;
        throw new AssertionError("未找到 " + field + "=" + value + " in " + arr);
    }

    @Test
    void childPermissionsAndGuardianProxyWish() throws Exception {
        String mom = register("cts-mom", "妈妈", null);
        Res fam = post("/api/families", mom, Map.of("name", "童家", "displayName", "妈妈"));
        assertThat(fam.status()).isEqualTo(201);
        long fid = fam.body().get("id").asLong();
        String invite = fam.body().get("inviteCode").asText();
        String base = "/api/families/" + fid;

        String dad = register("cts-dad", "爸爸", invite);
        String aunt = register("cts-aunt", "阿姨", invite);
        String uncle = register("cts-uncle", "叔叔", invite);
        String kid = register("cts-kid", "豆豆", invite);

        JsonNode members = get(base + "/members", mom).body();
        long dadMid = mid(members, "爸爸");
        long auntMid = mid(members, "阿姨");
        long uncleMid = mid(members, "叔叔");
        long kidMid = mid(members, "豆豆");
        long momMid = mid(members, "妈妈");

        assertThat(put(base + "/members/" + dadMid, mom, Map.of("role", "COOK")).status()).isEqualTo(200);
        // 先把豆豆设为儿童并指定阿姨为监护人
        Res child = put(base + "/members/" + kidMid, mom, Map.of("role", "CHILD", "guardianMemberId", auntMid));
        assertThat(child.status()).as(child.body().toString()).isEqualTo(200);
        assertThat(child.body().get("guardianMemberId").asLong()).isEqualTo(auntMid);
        assertThat(child.body().get("guardianName").asText()).isEqualTo("阿姨");

        // 不指定监护人时默认绑定操作者（管理员）
        String babyTok = register("cts-baby", "小小", invite);
        long babyMid = mid(get(base + "/members", mom).body(), "小小");
        Res baby = put(base + "/members/" + babyMid, mom, Map.of("role", "CHILD"));
        assertThat(baby.status()).isEqualTo(200);
        assertThat(baby.body().get("guardianMemberId").asLong()).isEqualTo(momMid);

        // 创建儿童账号也默认监护人
        Res created = post(base + "/members", mom, Map.of(
                "displayName", "贝贝", "role", "CHILD", "username", "cts-bb", "password", "secret-1"));
        assertThat(created.status()).as(created.body().toString()).isEqualTo(201);
        assertThat(created.body().get("guardianMemberId").asLong()).isEqualTo(momMid);

        // ---- 儿童不能改规则 / 菜谱 / 确认菜单 / 轮换 / 库存 / 剩菜 / 发起聚餐 ----
        assertThat(put(base, kid, Map.of("dinnerDeadline", "20:00")).status()).isEqualTo(403);
        assertThat(post(base + "/dishes", kid, dish("番茄炒蛋", "家常")).status()).isEqualTo(403);
        long eggId = post(base + "/dishes", dad, dish("番茄炒蛋", "家常")).body().get("id").asLong();
        long soupId = post(base + "/dishes", dad, dish("紫菜汤", "汤")).body().get("id").asLong();
        assertThat(put(base + "/cook-rotation", kid, Map.of("enabled", true)).status()).isEqualTo(403);
        assertThat(post(base + "/inventory", kid, Map.of("name", "鸡蛋")).status()).isEqualTo(403);
        assertThat(post(base + "/leftovers", kid, Map.of("name", "剩饭")).status()).isEqualTo(403);
        assertThat(post("/api/gatherings", kid, Map.of(
                "title", "周末小聚", "mealDate", TODAY.plusDays(3).toString(), "mealType", "DINNER",
                "mode", "HOST_COOK", "hostFamilyId", fid)).status()).isEqualTo(403);
        assertThat(post(base + "/week-templates", kid, Map.of("name", "随便", "items", List.of())).status()).isEqualTo(403);
        assertThat(post("/api/families", kid, Map.of("name", "豆豆自己的家")).status()).isEqualTo(403);

        // 儿童能给自己点餐
        String today = TODAY.toString();
        Res selfWish = post(base + "/wishes", kid, Map.of(
                "mealDate", today, "mealType", "DINNER", "dishId", eggId, "intensity", "WANT"));
        assertThat(selfWish.status()).as(selfWish.body().toString()).isEqualTo(201);
        assertThat(selfWish.body().get("memberId").asLong()).isEqualTo(kidMid);

        // 儿童不能替别人点
        assertThat(post(base + "/wishes", kid, Map.of(
                "mealDate", today, "mealType", "DINNER", "dishId", soupId, "memberId", dadMid)).status()).isEqualTo(403);

        // 监护人（MEMBER）能代孩子点；非监护普通成员不能
        Res auntWish = post(base + "/wishes", aunt, Map.of(
                "mealDate", today, "mealType", "LUNCH", "dishId", soupId, "intensity", "WANT", "memberId", kidMid));
        assertThat(auntWish.status()).as(auntWish.body().toString()).isEqualTo(201);
        assertThat(auntWish.body().get("memberId").asLong()).isEqualTo(kidMid);

        assertThat(post(base + "/wishes", uncle, Map.of(
                "mealDate", today, "mealType", "LUNCH", "dishId", eggId, "memberId", kidMid)).status()).isEqualTo(403);

        // 掌厨仍可代点
        assertThat(post(base + "/wishes", dad, Map.of(
                "mealDate", today, "mealType", "BREAKFAST", "dishId", eggId, "memberId", kidMid)).status()).isEqualTo(201);

        // 儿童不能确认菜单
        assertThat(post(base + "/menus/confirm", kid, Map.of(
                "mealDate", today, "mealType", "DINNER",
                "items", List.of(Map.of("dishId", eggId, "servings", 2)))).status()).isEqualTo(403);

        // 把掌厨改成儿童后，权限立即收紧
        assertThat(put(base + "/members/" + dadMid, mom, Map.of("role", "CHILD", "guardianMemberId", momMid)).status())
                .isEqualTo(200);
        assertThat(post(base + "/dishes", dad, dish("红烧肉", "硬菜")).status()).isEqualTo(403);
        assertThat(post(base + "/menus/confirm", dad, Map.of(
                "mealDate", today, "mealType", "DINNER",
                "items", List.of(Map.of("dishId", eggId, "servings", 2)))).status()).isEqualTo(403);
        assertThat(post("/api/gatherings", dad, Map.of(
                "title", "再约一顿", "mealDate", TODAY.plusDays(4).toString(), "mealType", "DINNER",
                "mode", "HOST_COOK")).status()).isEqualTo(403);
    }

    @Test
    void weekTemplateSaveFromPastAndApplyWithoutOverwritingConfirmed() throws Exception {
        String cook = register("cts-tpl-cook", "掌厨周", null);
        Res fam = post("/api/families", cook, Map.of("name", "周模板家", "displayName", "掌厨周"));
        long fid = fam.body().get("id").asLong();
        String base = "/api/families/" + fid;
        long riceId = post(base + "/dishes", cook, dish("蛋炒饭", "主食")).body().get("id").asLong();
        long greenId = post(base + "/dishes", cook, dish("青菜", "素菜")).body().get("id").asLong();
        long soupId = post(base + "/dishes", cook, dish("冬瓜汤", "汤")).body().get("id").asLong();

        // 回到上周（周一 11-02）确认两天的菜单，再跳回今天另存为模板
        setTime(TODAY.minusDays(10).atTime(9, 0)); // 11-02 周一
        String mon = TODAY.minusDays(10).toString();
        String tue = TODAY.minusDays(9).toString();
        Res c1 = post(base + "/menus/confirm", cook, Map.of("mealDate", mon, "mealType", "DINNER",
                "items", List.of(Map.of("dishId", riceId, "servings", 3), Map.of("dishId", greenId, "servings", 3))));
        assertThat(c1.status()).as(c1.body().toString()).isEqualTo(200);
        Res c2 = post(base + "/menus/confirm", cook, Map.of("mealDate", tue, "mealType", "LUNCH",
                "items", List.of(Map.of("dishId", soupId, "servings", 2))));
        assertThat(c2.status()).as(c2.body().toString()).isEqualTo(200);

        setTime(TODAY.atTime(9, 0));
        Res saved = post(base + "/week-templates/from-week", cook, Map.of(
                "weekStart", mon, "name", "上周家常"));
        assertThat(saved.status()).as(saved.body().toString()).isEqualTo(201);
        assertThat(saved.body().get("name").asText()).isEqualTo("上周家常");
        assertThat(saved.body().get("items").size()).isGreaterThanOrEqualTo(3);
        long tid = saved.body().get("id").asLong();

        // 也可以手工保存模板
        Res manual = post(base + "/week-templates", cook, Map.of("name", "清淡一周", "items", List.of(
                Map.of("dayOfWeek", 1, "mealType", "DINNER", "dishId", soupId, "servings", 2),
                Map.of("dayOfWeek", 3, "mealType", "DINNER", "customName", "白粥", "servings", 2)
        )));
        assertThat(manual.status()).as(manual.body().toString()).isEqualTo(201);

        // 应用到未来一周（11-16 周一），应预填且不把菜单标为已确认
        String nextMon = TODAY.plusDays(4).toString(); // 11-16
        Res apply = post(base + "/week-templates/" + tid + "/apply", cook, Map.of("weekStart", nextMon));
        assertThat(apply.status()).as(apply.body().toString()).isEqualTo(200);
        assertThat(apply.body().get("appliedSlots").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(apply.body().get("itemsWritten").asInt()).isGreaterThanOrEqualTo(3);

        Res plans = get(base + "/meal-plans?from=" + nextMon + "&to=" + TODAY.plusDays(10), cook);
        assertThat(plans.status()).isEqualTo(200);
        assertThat(plans.body().size()).isGreaterThanOrEqualTo(3);

        Res board = get(base + "/menus/board?date=" + nextMon + "&mealType=DINNER", cook);
        assertThat(board.status()).isEqualTo(200);
        assertThat(board.body().get("planItems").size()).isGreaterThanOrEqualTo(2);
        assertThat(board.body().path("menu").isMissingNode() || board.body().path("menu").isNull()
                || !"CONFIRMED".equals(board.body().path("menu").path("status").asText())).isTrue();

        // 确认周一晚餐后，再次应用（即使 overwrite）也不能覆盖已确认菜单
        Res confirm = post(base + "/menus/confirm", cook, Map.of("mealDate", nextMon, "mealType", "DINNER",
                "items", List.of(Map.of("dishId", riceId, "servings", 4))));
        assertThat(confirm.status()).as(confirm.body().toString()).isEqualTo(200);
        assertThat(confirm.body().at("/menu/items").size()).isEqualTo(1);
        assertThat(confirm.body().at("/menu/items/0/dishId").asLong()).isEqualTo(riceId);

        Res apply2 = post(base + "/week-templates/" + tid + "/apply", cook,
                Map.of("weekStart", nextMon, "overwrite", true));
        assertThat(apply2.status()).as(apply2.body().toString()).isEqualTo(200);
        assertThat(apply2.body().get("skippedConfirmed").asInt()).isGreaterThanOrEqualTo(1);

        Res menuAfter = get(base + "/menus/board?date=" + nextMon + "&mealType=DINNER", cook);
        assertThat(menuAfter.body().at("/menu/status").asText()).isEqualTo("CONFIRMED");
        assertThat(menuAfter.body().at("/menu/items").size()).isEqualTo(1);
        assertThat(menuAfter.body().at("/menu/items/0/dishId").asLong()).isEqualTo(riceId);
    }

    @Test
    void statsRatingsExcludeGatheringAndRequireReady() throws Exception {
        String mom = register("cts-st-mom", "统计妈", null);
        Res fam = post("/api/families", mom, Map.of("name", "统计之家", "displayName", "统计妈"));
        long fid = fam.body().get("id").asLong();
        String invite = fam.body().get("inviteCode").asText();
        String base = "/api/families/" + fid;
        String dad = register("cts-st-dad", "统计爸", invite);
        String kid = register("cts-st-kid", "统计娃", invite);
        JsonNode members = get(base + "/members", mom).body();
        long dadMid = mid(members, "统计爸");
        long kidMid = mid(members, "统计娃");
        long momMid = mid(members, "统计妈");
        assertThat(put(base + "/members/" + dadMid, mom, Map.of("role", "COOK")).status()).isEqualTo(200);
        assertThat(put(base + "/members/" + kidMid, mom, Map.of("role", "CHILD")).status()).isEqualTo(200);

        long tofuId = post(base + "/dishes", dad, dish("麻婆豆腐", "硬菜")).body().get("id").asLong();
        long fishId = post(base + "/dishes", dad, dish("清蒸鱼", "硬菜")).body().get("id").asLong();

        String today = TODAY.toString();
        String yest = TODAY.minusDays(1).toString();

        // 昨天：妈想吃豆腐（兑现），娃想吃鱼（未兑现），确认只有豆腐
        setTime(TODAY.minusDays(1).atTime(9, 0));
        assertThat(post(base + "/wishes", mom, Map.of("mealDate", yest, "mealType", "DINNER",
                "dishId", tofuId, "intensity", "WANT")).status()).isEqualTo(201);
        assertThat(post(base + "/wishes", kid, Map.of("mealDate", yest, "mealType", "DINNER",
                "dishId", fishId, "intensity", "WANT")).status()).isEqualTo(201);
        Res yestConfirm = post(base + "/menus/confirm", dad, Map.of("mealDate", yest, "mealType", "DINNER",
                "items", List.of(Map.of("dishId", tofuId, "servings", 3))));
        assertThat(yestConfirm.status()).as(yestConfirm.body().toString()).isEqualTo(200);
        long yestMenuId = yestConfirm.body().at("/menu/id").asLong();

        // 今天：两人都想吃豆腐，确认豆腐+鱼并开饭
        setTime(TODAY.atTime(9, 0));
        assertThat(post(base + "/wishes", mom, Map.of("mealDate", today, "mealType", "DINNER",
                "dishId", tofuId, "intensity", "WANT")).status()).isEqualTo(201);
        assertThat(post(base + "/wishes", kid, Map.of("mealDate", today, "mealType", "DINNER",
                "dishId", tofuId, "intensity", "WANT")).status()).isEqualTo(201);
        Res todayConfirm = post(base + "/menus/confirm", dad, Map.of("mealDate", today, "mealType", "DINNER",
                "items", List.of(Map.of("dishId", tofuId, "servings", 3), Map.of("dishId", fishId, "servings", 3))));
        assertThat(todayConfirm.status()).as(todayConfirm.body().toString()).isEqualTo(200);
        long todayMenuId = todayConfirm.body().at("/menu/id").asLong();

        // 未开饭不能评
        assertThat(post(base + "/menus/" + todayMenuId + "/ratings", mom, Map.of("score", 5)).status()).isEqualTo(409);

        // 把昨天的也开饭，方便统计平均分（开饭不校验是否过去）
        assertThat(post(base + "/menus/" + yestMenuId + "/ready", dad, null).status()).isEqualTo(200);
        assertThat(post(base + "/menus/" + todayMenuId + "/ready", dad, null).status()).isEqualTo(200);

        // 评价：今天豆腐+鱼这餐，妈 5 分、娃 3 分；昨天只有豆腐，妈 4 分
        assertThat(post(base + "/menus/" + todayMenuId + "/ratings", mom, Map.of("score", 5, "comment", "好吃")).status())
                .isEqualTo(200);
        assertThat(post(base + "/menus/" + todayMenuId + "/ratings", kid, Map.of("score", 3)).status()).isEqualTo(200);
        // 监护人可代孩子改评（管理员也是监护人，默认绑定）
        Res proxy = post(base + "/menus/" + todayMenuId + "/ratings", mom, Map.of("score", 4, "memberId", kidMid));
        assertThat(proxy.status()).as(proxy.body().toString()).isEqualTo(200);
        assertThat(proxy.body().get("score").asInt()).isEqualTo(4);
        assertThat(proxy.body().get("memberId").asLong()).isEqualTo(kidMid);

        // 非监护普通成员不能代评：把统计爸改成 MEMBER 再试昨天的？爸是 COOK 可以代。另建一位普通成员。
        String other = register("cts-st-other", "邻居哥", invite);
        assertThat(post(base + "/menus/" + todayMenuId + "/ratings", other, Map.of("score", 1, "memberId", kidMid)).status())
                .isEqualTo(403);

        assertThat(post(base + "/menus/" + yestMenuId + "/ratings", mom, Map.of("score", 4)).status()).isEqualTo(200);

        // 聚餐：同样叫麻婆豆腐，不应进入统计
        Res g = post("/api/gatherings", mom, Map.of(
                "title", "朋友聚餐", "mealDate", today, "mealType", "DINNER", "mode", "HOST_COOK"));
        assertThat(g.status()).as(g.body().toString()).isEqualTo(201);
        long gid = g.body().get("id").asLong();
        assertThat(post("/api/gatherings/" + gid + "/wishes", mom, Map.of("dishName", "麻婆豆腐")).status()).isIn(200, 201);
        assertThat(post("/api/gatherings/" + gid + "/confirm", mom, Map.of(
                "items", List.of(Map.of("dishName", "麻婆豆腐", "servings", 4)))).status()).isEqualTo(200);

        Res stats = get(base + "/stats?from=" + yest + "&to=" + today, mom);
        assertThat(stats.status()).as(stats.body().toString()).isEqualTo(200);
        JsonNode body = stats.body();

        // 两天日常菜单，没有第三场聚餐
        assertThat(body.get("days").size()).isEqualTo(2);
        for (JsonNode d : body.get("days")) {
            assertThat(d.get("plannedOnly").asBoolean()).isFalse();
            assertThat(d.get("status").asText()).isEqualTo("READY");
        }

        JsonNode tofu = findBy(body.get("frequentDishes"), "name", "麻婆豆腐");
        assertThat(tofu.get("times").asInt()).isEqualTo(2); // 昨天+今天，聚餐不算
        // 昨天 4 分（1 人），今天 (5+4)/2=4.5，平均 (4 + 5 + 4) / 3 = 4.333...
        assertThat(tofu.get("averageScore").asDouble()).isEqualTo((4 + 5 + 4) / 3.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(tofu.get("lastCooked").asText()).isEqualTo(today);

        JsonNode fish = findBy(body.get("frequentDishes"), "name", "清蒸鱼");
        assertThat(fish.get("times").asInt()).isEqualTo(1);
        assertThat(fish.get("averageScore").asDouble()).isEqualTo((5 + 4) / 2.0, org.assertj.core.data.Offset.offset(0.01));

        // WANT：豆腐被点 3 次（妈昨+今、娃今）全兑现；鱼被点 1 次（娃昨）未兑现
        JsonNode tofuWish = findBy(body.get("frequentWishes"), "name", "麻婆豆腐");
        assertThat(tofuWish.get("wantCount").asInt()).isEqualTo(3);
        assertThat(tofuWish.get("selectedCount").asInt()).isEqualTo(3);
        assertThat(tofuWish.get("selectedRate").asDouble()).isEqualTo(1.0, org.assertj.core.data.Offset.offset(0.01));
        JsonNode fishWish = findBy(body.get("frequentWishes"), "name", "清蒸鱼");
        assertThat(fishWish.get("wantCount").asInt()).isEqualTo(1);
        assertThat(fishWish.get("selectedCount").asInt()).isEqualTo(0);

        JsonNode momFair = findBy(body.get("fairness"), "displayName", "统计妈");
        assertThat(momFair.get("wantCount").asInt()).isEqualTo(2);
        assertThat(momFair.get("fulfilledCount").asInt()).isEqualTo(2);
        assertThat(momFair.get("fulfillmentRate").asDouble()).isEqualTo(1.0, org.assertj.core.data.Offset.offset(0.01));
        JsonNode kidFair = findBy(body.get("fairness"), "displayName", "统计娃");
        assertThat(kidFair.get("wantCount").asInt()).isEqualTo(2);
        assertThat(kidFair.get("fulfilledCount").asInt()).isEqualTo(1);
        assertThat(kidFair.get("fulfillmentRate").asDouble()).isEqualTo(0.5, org.assertj.core.data.Offset.offset(0.01));
        // 两餐都是爸爸确认
        JsonNode dadFair = findBy(body.get("fairness"), "displayName", "统计爸");
        assertThat(dadFair.get("confirmedCookCount").asInt()).isEqualTo(2);
    }
}
