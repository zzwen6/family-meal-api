package com.zzwen6.familymeal.hint;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class HintDtos {
    private HintDtos() {}

    public enum HintType { FATIGUE, FAIRNESS, AVOID, LEFTOVER }

    /**
     * 结构化提示：后端是权威来源。
     * type 决定语义；dish / member / leftover 等字段按类型选用；message 是可直接展示的中文文案。
     */
    public record HintView(
            HintType type,
            Long dishId,
            String dishName,
            Long memberId,
            String memberName,
            String message,
            Integer count,
            Integer total,
            BigDecimal ratio,
            Long leftoverId,
            LocalDate expiresOn
    ) {
        public static HintView fatigue(Long dishId, String dishName, int count, int windowDays) {
            return new HintView(HintType.FATIGUE, dishId, dishName, null, null,
                    "「" + dishName + "」最近 " + windowDays + " 天吃过 " + count + " 次，可能吃腻了",
                    count, windowDays, null, null, null);
        }

        public static HintView fairness(Long memberId, String memberName, int fulfilled, int wants, int windowDays) {
            String msg = fulfilled == 0
                    ? memberName + "最近 " + windowDays + " 天点了 " + wants + " 次想吃，还没有被选上"
                    : memberName + "的想吃被选上的比例较低（" + fulfilled + "/" + wants + "）";
            BigDecimal ratio = wants == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(fulfilled).divide(BigDecimal.valueOf(wants), 2, java.math.RoundingMode.HALF_UP);
            return new HintView(HintType.FAIRNESS, null, null, memberId, memberName, msg,
                    fulfilled, wants, ratio, null, null);
        }

        public static HintView avoid(Long dishId, String dishName, Long memberId, String memberName) {
            return new HintView(HintType.AVOID, dishId, dishName, memberId, memberName,
                    memberName + "不想吃「" + dishName + "」", null, null, null, null, null);
        }

        public static HintView leftover(Long leftoverId, String name, LocalDate expiresOn) {
            String msg = expiresOn == null
                    ? "有剩菜「" + name + "」可以先吃掉"
                    : "有剩菜「" + name + "」可以先吃掉（大约保鲜到 " + expiresOn + "）";
            return new HintView(HintType.LEFTOVER, null, name, null, null, msg, null, null, null, leftoverId, expiresOn);
        }
    }
}
