package com.zzwen6.familymeal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 临时聚餐：创建 → 邀请 → 应邀 → 三种玩法 → 确认 → 人数变化影响份量/采购 → 开饭 → 散场 → 复用这波人，
 * 以及最重要的数据隔离（客人看不到发起人家庭的任何数据、非参与者 403、家庭日历只占位）。
 */
class GatheringIntegrationTest extends IntegrationTestBase {

    private String register(String username, String nickname) throws Exception {
        Res r = post("/api/auth/register", null, Map.of("username", username, "password", "secret-1", "nickname", nickname));
        assertThat(r.status()).as(r.body().toString()).isEqualTo(201);
        return r.body().get("token").asText();
    }

    private Map<String, Object> createBody(String title, String mode, Long familyId) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        m.put("mealDate", TODAY.plusDays(1).toString());
        m.put("mealType", "DINNER");
        m.put("location", "我家");
        m.put("mode", mode);
        m.put("deadlineAt", TODAY + "T18:00:00");
        if (familyId != null) m.put("hostFamilyId", familyId);
        return m;
    }

    @Test
    void hostCookFlowWithIsolationHeadcountCloseAndCopy() throws Exception {
        // ---------- 发起人有一个家，家里有菜谱、意愿、成员 ----------
        String host = register("g1-host", "王姐");
        long fid = post("/api/families", host, Map.of("name", "王家", "displayName", "王姐")).body().get("id").asLong();
        String fbase = "/api/families/" + fid;
        Map<String, Object> dish = new HashMap<>(Map.of("name", "家传红烧肉", "category", "硬菜", "difficulty", "MEDIUM",
                "durationMinutes", 60, "defaultServings", 2));
        dish.put("ingredients", List.of(Map.of("name", "五花肉", "amount", 500, "unit", "g", "staple", false)));
        assertThat(post(fbase + "/dishes", host, dish).status()).isEqualTo(201);
        assertThat(post(fbase + "/wishes", host, Map.of("mealDate", TODAY.toString(), "mealType", "DINNER",
                "freeText", "家里的秘密菜", "intensity", "WANT")).status()).isEqualTo(201);

        // hostFamilyId 只能是自己的家
        String outsider = register("g1-outsider", "路人");
        long outsiderFid = post("/api/families", outsider, Map.of("name", "路人家")).body().get("id").asLong();
        assertThat(post("/api/gatherings", host, createBody("蹭别人家日历", "HOST_COOK", outsiderFid)).status()).isEqualTo(403);
        // 未登录不能建
        assertThat(post("/api/gatherings", null, createBody("x", "HOST_COOK", null)).status()).isEqualTo(401);

        // ---------- 创建 ----------
        Res created = post("/api/gatherings", host, createBody("周五老同学聚餐", "HOST_COOK", fid));
        assertThat(created.status()).as(created.body().toString()).isEqualTo(201);
        long gid = created.body().get("id").asLong();
        String token = created.body().get("inviteToken").asText();
        String gbase = "/api/gatherings/" + gid;
        assertThat(token).hasSizeGreaterThanOrEqualTo(20);
        assertThat(created.body().get("status").asText()).isEqualTo("OPEN");
        assertThat(created.body().get("host").asBoolean()).isTrue();
        assertThat(created.body().get("hostFamilyId").asLong()).isEqualTo(fid);
        assertThat(created.body().get("participants")).hasSize(1);
        assertThat(created.body().at("/me/rsvp").asText()).isEqualTo("YES");

        // ---------- 邀请预览：要登录；只有少量信息 ----------
        assertThat(get("/api/gatherings/invite/" + token, null).status()).isEqualTo(401);
        String guest = register("g1-guest", "老李");
        assertThat(get(gbase, guest).status()).isEqualTo(403); // 还没加入
        Res preview = get("/api/gatherings/invite/" + token, guest);
        assertThat(preview.status()).isEqualTo(200);
        assertThat(preview.body().get("title").asText()).isEqualTo("周五老同学聚餐");
        assertThat(preview.body().get("hostName").asText()).isEqualTo("王姐");
        assertThat(preview.body().get("joinable").asBoolean()).isTrue();
        assertThat(preview.body().get("joined").asBoolean()).isFalse();
        assertThat(preview.body().has("participants")).isFalse();
        assertThat(preview.body().has("hostFamilyId")).isFalse();
        assertThat(preview.body().has("gatheringId")).isFalse();
        assertThat(preview.body().has("inviteToken")).isFalse();
        assertThat(get("/api/gatherings/invite/not-a-real-token", guest).status()).isEqualTo(404);

        // ---------- 应邀：带 1 个家属，忌口 ----------
        Res joined = post("/api/gatherings/invite/" + token + "/join", guest,
                Map.of("rsvp", "YES", "headcount", 2, "dietaryNotes", "不吃香菜"));
        assertThat(joined.status()).as(joined.body().toString()).isEqualTo(200);
        assertThat(joined.body().get("headcount").asInt()).isEqualTo(3);
        assertThat(joined.body().get("host").asBoolean()).isFalse();
        assertThat(joined.body().has("hostFamilyId")).isFalse(); // 家庭信息不给客人
        // 再点一次邀请不会重复加入
        assertThat(post("/api/gatherings/invite/" + token + "/join", guest, null).body().get("participants")).hasSize(2);
        // 客人看不到别人的忌口，主人能看到
        String guest2 = register("g1-guest2", "小张");
        post("/api/gatherings/invite/" + token + "/join", guest2, Map.of("rsvp", "MAYBE", "dietaryNotes", "海鲜过敏"));
        JsonNode g2View = get(gbase, guest2).body();
        assertThat(findBy(g2View.get("participants"), "name", "老李").has("dietaryNotes")).isFalse();
        assertThat(findBy(get(gbase, host).body().get("participants"), "name", "小张").get("dietaryNotes").asText()).isEqualTo("海鲜过敏");
        assertThat(g2View.get("maybeCount").asInt()).isEqualTo(1);

        // ---------- 数据隔离：客人不是家庭成员，看不到家庭的任何东西 ----------
        assertThat(get("/api/families", guest).body()).isEmpty();
        assertThat(get(fbase, guest).status()).isEqualTo(403);
        assertThat(get(fbase + "/dishes", guest).status()).isEqualTo(403);
        assertThat(get(fbase + "/members", guest).status()).isEqualTo(403);
        assertThat(get(fbase + "/wishes?date=" + TODAY + "&mealType=DINNER", guest).status()).isEqualTo(403);
        assertThat(get(fbase + "/menus/board?date=" + TODAY + "&mealType=DINNER", guest).status()).isEqualTo(403);
        assertThat(get(fbase + "/menus?from=" + TODAY + "&to=" + TODAY.plusDays(6), guest).status()).isEqualTo(403);
        assertThat(get(fbase + "/notifications", guest).status()).isEqualTo(403);
        assertThat(get(fbase + "/gatherings?from=" + TODAY + "&to=" + TODAY.plusDays(6), guest).status()).isEqualTo(403);
        // 聚餐详情里也没有家庭菜谱的内容
        assertThat(get(gbase, guest).body().toString()).doesNotContain("家传红烧肉").doesNotContain("家里的秘密菜").doesNotContain("王家");

        // 非参与者 403（包括不存在的聚餐，避免枚举）
        assertThat(get(gbase, outsider).status()).isEqualTo(403);
        assertThat(get(gbase + "/shopping", outsider).status()).isEqualTo(403);
        assertThat(post(gbase + "/wishes", outsider, Map.of("dishName", "偷偷点")).status()).isEqualTo(403);
        assertThat(get("/api/gatherings/999999", outsider).status()).isEqualTo(403);
        assertThat(get(gbase + "/notifications", outsider).status()).isEqualTo(403);

        // ---------- 主人掌厨：客人提交想吃 ----------
        Res w1 = post(gbase + "/wishes", guest, Map.of("dishName", "水煮鱼", "note", "少辣"));
        assertThat(w1.status()).as(w1.body().toString()).isEqualTo(201);
        assertThat(post(gbase + "/wishes", guest2, Map.of("dishName", "水煮鱼")).status()).isEqualTo(201);
        assertThat(post(gbase + "/wishes", guest, Map.of("dishName", " 水煮鱼 ")).status()).isEqualTo(409); // 同一人不重复
        Res w3 = post(gbase + "/wishes", guest2, Map.of("dishName", "拍黄瓜"));
        JsonNode tally = w3.body().get("tally");
        assertThat(tally.get(0).get("dishName").asText()).isEqualTo("水煮鱼");
        assertThat(tally.get(0).get("count").asInt()).isEqualTo(2);
        assertThat(tally.get(0).get("by")).hasSize(2);
        long cucumberWish = findBy(w3.body().get("wishes"), "dishName", "拍黄瓜").get("id").asLong();
        // 只能改/删自己的
        assertThat(put(gbase + "/wishes/" + cucumberWish, guest, Map.of("dishName", "拍蒜")).status()).isEqualTo(403);
        assertThat(delete(gbase + "/wishes/" + cucumberWish, guest).status()).isEqualTo(403);
        // 投票只在众筹模式
        assertThat(post(gbase + "/wishes/" + cucumberWish + "/vote", guest, null).body().get("code").asText()).isEqualTo("MODE_MISMATCH");
        // 队员不能管理
        assertThat(post(gbase + "/confirm", guest, Map.of("items", List.of(Map.of("dishName", "火锅")))).status()).isEqualTo(403);
        assertThat(post(gbase + "/close", guest, null).status()).isEqualTo(403);
        assertThat(post(gbase + "/shopping/items", guest, Map.of("name", "鱼")).status()).isEqualTo(403);

        // ---------- 截止后客人不能再改意愿，主人仍可确认 ----------
        setTime(TODAY.atTime(18, 30));
        Res late = post(gbase + "/wishes", guest, Map.of("dishName", "小龙虾"));
        assertThat(late.status()).isEqualTo(409);
        assertThat(late.body().get("code").asText()).isEqualTo("WISH_CLOSED");
        assertThat(get(gbase, guest).body().get("canEditWishes").asBoolean()).isFalse();

        long fishWish = findBy(w3.body().get("wishes"), "dishName", "水煮鱼").get("id").asLong();
        Res confirm = post(gbase + "/confirm", host, Map.of("items", List.of(
                Map.of("wishId", fishWish), Map.of("dishName", "可乐鸡翅", "servings", 6))));
        assertThat(confirm.status()).as(confirm.body().toString()).isEqualTo(200);
        assertThat(confirm.body().get("status").asText()).isEqualTo("CONFIRMED");
        JsonNode menu = confirm.body().get("menu");
        assertThat(menu).hasSize(2);
        assertThat(menu.get(0).get("dishName").asText()).isEqualTo("水煮鱼");
        assertThat(menu.get(0).get("servings").asInt()).isEqualTo(3); // 按出席人数：王姐 1 + 老李 2
        assertThat(menu.get(0).get("autoServings").asBoolean()).isTrue();
        assertThat(menu.get(1).get("servings").asInt()).isEqualTo(6);
        assertThat(confirm.body().get("confirmedHeadcount").asInt()).isEqualTo(3);

        // 采购：主人手填，500g 是 2 人份 → 3 人 750g；不换算的项保持原样
        Res s1 = post(gbase + "/shopping/items", host, Map.of("name", "草鱼", "amount", 500, "unit", "g", "baseServings", 2, "forDish", "水煮鱼"));
        assertThat(s1.status()).as(s1.body().toString()).isEqualTo(201);
        Res s2 = post(gbase + "/shopping/items", host, Map.of("name", "啤酒", "amount", 6, "unit", "罐"));
        JsonNode fish = findBy(s2.body().get("items"), "name", "草鱼");
        assertThat(s2.body().get("headcount").asInt()).isEqualTo(3);
        assertThat(fish.get("scaledAmount").decimalValue()).isEqualByComparingTo("750");
        assertThat(findBy(s2.body().get("items"), "name", "啤酒").get("scaledAmount").decimalValue()).isEqualByComparingTo("6");
        long fishItem = fish.get("id").asLong();
        assertThat(patch(gbase + "/shopping/items/" + fishItem, host, Map.of("purchased", true)).body().get("purchasedCount").asInt()).isEqualTo(1);
        // 客人能看采购清单（知道主人准备了什么），但不能改
        assertThat(get(gbase + "/shopping", guest).status()).isEqualTo(200);
        assertThat(patch(gbase + "/shopping/items/" + fishItem, guest, Map.of("purchased", false)).status()).isEqualTo(403);

        // ---------- 人数变化：老李改成带 3 个人，小张也来 → 份量和采购跟着变 ----------
        Res rs = put(gbase + "/me", guest, Map.of("rsvp", "YES", "headcount", 4, "dietaryNotes", "不吃香菜"));
        assertThat(rs.status()).as(rs.body().toString()).isEqualTo(200);
        put(gbase + "/me", guest2, Map.of("rsvp", "YES", "headcount", 1));
        JsonNode after = get(gbase, host).body();
        assertThat(after.get("headcount").asInt()).isEqualTo(6);
        assertThat(after.get("headcountChanged").asBoolean()).isTrue();
        assertThat(after.at("/menu/0/servings").asInt()).isEqualTo(6);
        assertThat(after.at("/menu/1/servings").asInt()).isEqualTo(6); // 固定份数不变
        assertThat(findBy(get(gbase + "/shopping", host).body().get("items"), "name", "草鱼").get("scaledAmount").decimalValue())
                .isEqualByComparingTo("1500");
        // 菜单确认后不能再改意愿
        setTime(TODAY.atTime(9, 0));
        assertThat(post(gbase + "/wishes", guest, Map.of("dishName", "小龙虾")).body().get("code").asText()).isEqualTo("GATHERING_CONFIRMED");

        // ---------- 开饭：只发聚餐通知，不进家庭通知 ----------
        int familyNoticesBefore = get(fbase + "/notifications", host).body().size();
        Res ready = post(gbase + "/ready", host, null);
        assertThat(ready.status()).isEqualTo(200);
        assertThat(ready.body().has("readyAt")).isTrue();
        JsonNode gNotes = get(gbase + "/notifications", guest).body();
        assertThat(gNotes.get(0).get("type").asText()).isEqualTo("GATHERING_READY");
        assertThat(gNotes.get(0).get("wechatStatus").asText()).isEqualTo("SKIPPED"); // 测试环境没配订阅消息
        assertThat(gNotes.get(1).get("type").asText()).isEqualTo("GATHERING_CONFIRMED");
        assertThat(get(fbase + "/notifications", host).body().size()).isEqualTo(familyNoticesBefore);

        // ---------- 家庭日历占位：家人看到「有一场聚餐」，但看不了详情 ----------
        String invite = get(fbase, host).body().get("inviteCode").asText();
        Res sisterReg = post("/api/auth/register", null, Map.of("username", "g1-sister", "password", "secret-1",
                "nickname", "妹妹", "inviteCode", invite));
        String sister = sisterReg.body().get("token").asText();
        JsonNode slots = get(fbase + "/gatherings?from=" + TODAY + "&to=" + TODAY.plusDays(6), sister).body();
        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).get("title").asText()).isEqualTo("周五老同学聚餐");
        assertThat(slots.get(0).get("mealDate").asText()).isEqualTo(TODAY.plusDays(1).toString());
        assertThat(slots.get(0).get("mealType").asText()).isEqualTo("DINNER");
        assertThat(slots.get(0).get("hostName").asText()).isEqualTo("王姐");
        assertThat(slots.get(0).get("joined").asBoolean()).isFalse();
        assertThat(slots.get(0).has("participants")).isFalse();
        assertThat(get(gbase, sister).status()).isEqualTo(403); // 家人不因此成为聚餐参与者
        assertThat(get(fbase + "/gatherings?from=" + TODAY + "&to=" + TODAY.plusDays(6), host).body().get(0).get("joined").asBoolean()).isTrue();
        // 聚餐不混进家庭日常统计：日历只有家里自己的那餐
        JsonNode cal = get(fbase + "/menus?from=" + TODAY + "&to=" + TODAY.plusDays(6), host).body();
        assertThat(cal.toString()).doesNotContain("水煮鱼");

        // ---------- 散场 ----------
        Res closed = post(gbase + "/close", host, null);
        assertThat(closed.body().get("status").asText()).isEqualTo("CLOSED");
        assertThat(closed.body().has("inviteToken")).isFalse();
        Res latecomer = post("/api/gatherings/invite/" + token + "/join", outsider, Map.of("rsvp", "YES"));
        assertThat(latecomer.status()).isEqualTo(409);
        assertThat(latecomer.body().get("code").asText()).isEqualTo("GATHERING_CLOSED");
        assertThat(get("/api/gatherings/invite/" + token, outsider).body().get("joinable").asBoolean()).isFalse();
        assertThat(put(gbase + "/me", guest, Map.of("rsvp", "NO")).status()).isEqualTo(409);
        assertThat(post(gbase + "/confirm", host, Map.of("items", List.of(Map.of("dishName", "火锅")))).status()).isEqualTo(409);
        assertThat(post(gbase + "/shopping/items", host, Map.of("name", "葱")).status()).isEqualTo(409);
        assertThat(post(gbase + "/close", host, null).status()).isEqualTo(409);
        // 历史可查看
        assertThat(get(gbase, guest).status()).isEqualTo(200);
        assertThat(get(gbase + "/shopping", guest).body().get("items")).hasSize(2);
        JsonNode guestList = get("/api/gatherings", guest).body();
        assertThat(findBy(guestList, "title", "周五老同学聚餐").get("status").asText()).isEqualTo("CLOSED");

        // ---------- 复用上次那波人 ----------
        assertThat(post(gbase + "/copy", guest, Map.of("title", "x", "mealDate", TODAY.plusDays(8).toString(), "mealType", "LUNCH")).status())
                .isEqualTo(403);
        Res copy = post(gbase + "/copy", host, Map.of("title", "下周再聚", "mealDate", TODAY.plusDays(8).toString(), "mealType", "LUNCH"));
        assertThat(copy.status()).as(copy.body().toString()).isEqualTo(201);
        assertThat(copy.body().get("id").asLong()).isNotEqualTo(gid);
        assertThat(copy.body().get("inviteToken").asText()).isNotEqualTo(token);
        assertThat(copy.body().get("status").asText()).isEqualTo("OPEN");
        assertThat(copy.body().get("mode").asText()).isEqualTo("HOST_COOK");
        assertThat(copy.body().get("sourceGatheringId").asLong()).isEqualTo(gid);
        assertThat(copy.body().get("participants")).hasSize(3);
        assertThat(findBy(copy.body().get("participants"), "name", "老李").get("rsvp").asText()).isEqualTo("MAYBE");
        assertThat(findBy(copy.body().get("participants"), "name", "老李").get("headcount").asInt()).isEqualTo(4);
        assertThat(findBy(copy.body().get("participants"), "name", "王姐").get("rsvp").asText()).isEqualTo("YES");
        assertThat(copy.body().get("wishes")).isEmpty();
        assertThat(copy.body().get("menu")).isEmpty();
        long copyId = copy.body().get("id").asLong();
        // 老李在新的一场里，可以重新应邀
        assertThat(put("/api/gatherings/" + copyId + "/me", guest, Map.of("rsvp", "YES", "headcount", 2)).body().get("headcount").asInt())
                .isEqualTo(3);
        assertThat(get("/api/gatherings", guest).body()).hasSize(2);
    }

    @Test
    void voteModeOnePersonOneVote() throws Exception {
        String host = register("g2-host", "阿强");
        String a = register("g2-a", "阿美");
        String b = register("g2-b", "阿伟");
        Res created = post("/api/gatherings", host, createBody("周末火锅局", "VOTE", null));
        long gid = created.body().get("id").asLong();
        String token = created.body().get("inviteToken").asText();
        String gbase = "/api/gatherings/" + gid;
        post("/api/gatherings/invite/" + token + "/join", a, null);
        post("/api/gatherings/invite/" + token + "/join", b, Map.of("rsvp", "YES"));

        // 提议（自己自动一票），同名菜不能重复提议
        Res p1 = post(gbase + "/wishes", a, Map.of("dishName", "毛肚"));
        assertThat(p1.status()).isEqualTo(201);
        long tripe = findBy(p1.body().get("wishes"), "dishName", "毛肚").get("id").asLong();
        assertThat(findBy(p1.body().get("wishes"), "dishName", "毛肚").get("votes").asInt()).isEqualTo(1);
        assertThat(findBy(p1.body().get("wishes"), "dishName", "毛肚").get("votedByMe").asBoolean()).isTrue();
        Res dup = post(gbase + "/wishes", b, Map.of("dishName", "毛肚"));
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.body().get("code").asText()).isEqualTo("DUPLICATE_DISH");
        long duck = findBy(post(gbase + "/wishes", b, Map.of("dishName", "鸭血")).body().get("wishes"), "dishName", "鸭血").get("id").asLong();

        // 投票：一人一菜一票
        assertThat(post(gbase + "/wishes/" + tripe + "/vote", b, null).status()).isEqualTo(200);
        Res again = post(gbase + "/wishes/" + tripe + "/vote", b, null);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.body().get("code").asText()).isEqualTo("ALREADY_VOTED");
        assertThat(post(gbase + "/wishes/" + tripe + "/vote", a, null).body().get("code").asText()).isEqualTo("ALREADY_VOTED");
        Res hv = post(gbase + "/wishes/" + tripe + "/vote", host, null);
        JsonNode list = hv.body().get("wishes");
        assertThat(list.get(0).get("dishName").asText()).isEqualTo("毛肚"); // 按票数排序
        assertThat(list.get(0).get("votes").asInt()).isEqualTo(3);
        // 取消投票
        Res un = delete(gbase + "/wishes/" + tripe + "/vote", host);
        assertThat(findBy(un.body().get("wishes"), "dishName", "毛肚").get("votes").asInt()).isEqualTo(2);
        assertThat(delete(gbase + "/wishes/" + tripe + "/vote", host).status()).isEqualTo(404);
        // 非参与者不能投
        String stranger = register("g2-stranger", "陌生人");
        assertThat(post(gbase + "/wishes/" + tripe + "/vote", stranger, null).status()).isEqualTo(403);

        // 发起人按票数确认
        Res confirm = post(gbase + "/confirm", host, Map.of("items", List.of(Map.of("wishId", tripe), Map.of("wishId", duck))));
        assertThat(confirm.status()).isEqualTo(200);
        assertThat(confirm.body().at("/menu/0/dishName").asText()).isEqualTo("毛肚");
        assertThat(confirm.body().at("/menu/0/servings").asInt()).isEqualTo(3);
        // 定了之后不能再投
        assertThat(post(gbase + "/wishes/" + duck + "/vote", a, null).body().get("code").asText()).isEqualTo("GATHERING_CONFIRMED");
        // 发起人可以重新开放
        assertThat(post(gbase + "/reopen", host, null).body().get("status").asText()).isEqualTo("OPEN");
        assertThat(post(gbase + "/wishes/" + duck + "/vote", a, null).status()).isEqualTo(200);
        // 众筹也能维护采购
        assertThat(get(gbase + "/shopping", a).status()).isEqualTo(200);
        assertThat(get("/api/gatherings", a).body().get(0).get("mode").asText()).isEqualTo("VOTE");
    }

    @Test
    void potluckClaimsAndConfirm() throws Exception {
        String host = register("g3-host", "Lily");
        String a = register("g3-a", "Tom");
        String b = register("g3-b", "Jerry");
        Res created = post("/api/gatherings", host, createBody("楼下野餐", "POTLUCK", null));
        long gid = created.body().get("id").asLong();
        String token = created.body().get("inviteToken").asText();
        String gbase = "/api/gatherings/" + gid;
        post("/api/gatherings/invite/" + token + "/join", a, Map.of("headcount", 2));
        post("/api/gatherings/invite/" + token + "/join", b, null);
        // 主人代填一位没账号的客人
        Res added = post(gbase + "/participants", host, Map.of("guestName", "外婆", "headcount", 1));
        assertThat(added.status()).isEqualTo(201);
        assertThat(findBy(added.body().get("participants"), "name", "外婆").get("guest").asBoolean()).isTrue();
        assertThat(post(gbase + "/participants", a, Map.of("guestName", "我朋友")).status()).isEqualTo(403);

        Res c1 = post(gbase + "/wishes", a, Map.of("dishName", "卤牛肉", "note", "我自己卤"));
        assertThat(c1.status()).isEqualTo(201);
        JsonNode claim = findBy(c1.body().get("wishes"), "dishName", "卤牛肉");
        assertThat(claim.get("bringBySelf").asBoolean()).isTrue();
        assertThat(claim.get("participantName").asText()).isEqualTo("Tom");
        Res taken = post(gbase + "/wishes", b, Map.of("dishName", "卤牛肉"));
        assertThat(taken.status()).isEqualTo(409);
        assertThat(taken.body().get("code").asText()).isEqualTo("ALREADY_CLAIMED");
        Res c2 = post(gbase + "/wishes", b, Map.of("dishName", "水果拼盘"));
        // 还没认领的人（RSVP=YES）
        assertThat(c2.body().get("unclaimed").toString()).contains("Lily").contains("外婆").doesNotContain("Tom").doesNotContain("Jerry");
        long beefWish = claim.get("id").asLong();
        long fruitWish = findBy(c2.body().get("wishes"), "dishName", "水果拼盘").get("id").asLong();
        // 不能改别人的认领
        assertThat(put(gbase + "/wishes/" + beefWish, b, Map.of("dishName", "卤猪蹄")).status()).isEqualTo(403);
        // 改自己的
        assertThat(put(gbase + "/wishes/" + fruitWish, b, Map.of("dishName", "西瓜")).status()).isEqualTo(200);

        // 各带一道不需要统一采购
        assertThat(get(gbase + "/shopping", host).body().get("code").asText()).isEqualTo("MODE_MISMATCH");
        assertThat(post(gbase + "/wishes/" + beefWish + "/vote", a, null).body().get("code").asText()).isEqualTo("MODE_MISMATCH");

        Res confirm = post(gbase + "/confirm", host, Map.of("items", List.of(
                Map.of("wishId", beefWish), Map.of("wishId", fruitWish), Map.of("dishName", "饮料", "note", "主人准备"))));
        assertThat(confirm.status()).as(confirm.body().toString()).isEqualTo(200);
        JsonNode menu = confirm.body().get("menu");
        assertThat(menu.get(0).get("bringByName").asText()).isEqualTo("Tom");
        assertThat(menu.get(1).get("dishName").asText()).isEqualTo("西瓜");
        assertThat(menu.get(1).get("bringByName").asText()).isEqualTo("Jerry");
        assertThat(menu.get(2).has("bringByName")).isFalse();
        assertThat(menu.get(0).get("servings").asInt()).isEqualTo(5); // Lily1 + Tom2 + Jerry1 + 外婆1

        // 主人移除一位参与者，他的认领一并清掉
        long jerry = findBy(confirm.body().get("participants"), "name", "Jerry").get("id").asLong();
        Res removed = delete(gbase + "/participants/" + jerry, host);
        assertThat(removed.status()).isEqualTo(200);
        assertThat(removed.body().get("participants")).hasSize(3);
        assertThat(removed.body().get("wishes")).hasSize(1);
        assertThat(get(gbase, b).status()).isEqualTo(403);

        assertThat(post(gbase + "/ready", a, null).status()).isEqualTo(403);
        assertThat(post(gbase + "/ready", host, null).status()).isEqualTo(200);
        assertThat(post(gbase + "/ready", host, null).body().get("code").asText()).isEqualTo("ALREADY_READY");
    }

    @Test
    void subscribeConfigIsDisabledWithoutTemplate() throws Exception {
        String u = register("g4-user", "某人");
        Res r = get("/api/wechat/subscribe-config", u);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("enabled").asBoolean()).isFalse();
        assertThat(r.body().has("dinnerReadyTemplateId")).isFalse();
        assertThat(get("/api/wechat/subscribe-config", null).status()).isEqualTo(401);
    }

    private static JsonNode findBy(JsonNode arr, String field, String value) {
        for (JsonNode n : arr) if (value.equals(n.path(field).asText())) return n;
        throw new AssertionError("未找到 " + field + "=" + value + " in " + arr);
    }
}
