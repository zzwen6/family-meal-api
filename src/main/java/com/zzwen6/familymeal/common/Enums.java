package com.zzwen6.familymeal.common;

/** 领域枚举集中放在这里，方便前后端对照。 */
public final class Enums {
    private Enums() {}

    /** 家庭角色：ADMIN 管理员（也可掌厨）、COOK 掌厨、MEMBER 成员、CHILD 儿童 */
    public enum Role {
        ADMIN, COOK, MEMBER, CHILD;
        public boolean canCook() { return this == ADMIN || this == COOK; }
        public boolean isAdmin() { return this == ADMIN; }
    }

    public enum MealType { BREAKFAST, LUNCH, DINNER }

    /** 意愿强弱：很想吃 / 都可以 / 今晚别做这个 */
    public enum Intensity { WANT, OK, AVOID }

    public enum Difficulty { EASY, MEDIUM, HARD }

    /** MAIN 主料 / SEASONING 配料与调味 */
    public enum IngredientKind { MAIN, SEASONING }

    /** OPEN 可点 / LOCKED 已截止 / CONFIRMED 已确认菜单 / READY 已开饭 */
    public enum MenuStatus { OPEN, LOCKED, CONFIRMED, READY }

    public enum MenuItemSource { WISH, COOK_ADDED, SUBSTITUTE }

    public enum NotificationType { MENU_CONFIRMED, DINNER_READY }

    public enum WechatStatus { SKIPPED, SENT, FAILED }
}
