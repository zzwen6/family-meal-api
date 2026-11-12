package com.zzwen6.familymeal.gathering;

import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.gathering.Gathering.Mode;
import com.zzwen6.familymeal.gathering.Gathering.Status;
import com.zzwen6.familymeal.gathering.GatheringParticipant.Rsvp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class GatheringDtos {
    private GatheringDtos() {}

    // ---------------- 请求 ----------------

    /**
     * 发起一场聚餐。hostFamilyId 可选，只用于在这个家庭的日历上占位（必须是自己所在的家庭），不授予任何家庭权限。
     * headcount / dietaryNotes 是发起人自己这一方的人数和忌口。
     */
    public record CreateRequest(@NotBlank(message = "请填写聚餐标题") @Size(max = 64) String title,
                                @NotNull(message = "请选择日期") LocalDate mealDate,
                                @NotNull(message = "请选择餐次") MealType mealType,
                                @Size(max = 128) String location,
                                @NotNull(message = "请选择玩法") Mode mode,
                                LocalDateTime deadlineAt,
                                Long hostFamilyId,
                                @Size(max = 500) String note,
                                @Min(1) @Max(50) Integer headcount,
                                @Size(max = 255) String dietaryNotes) {}

    /** 发起人修改基本信息（整体覆盖；玩法创建后不能改）。 */
    public record UpdateRequest(@NotBlank(message = "请填写聚餐标题") @Size(max = 64) String title,
                                @NotNull LocalDate mealDate,
                                @NotNull MealType mealType,
                                @Size(max = 128) String location,
                                LocalDateTime deadlineAt,
                                Long hostFamilyId,
                                @Size(max = 500) String note) {}

    /** 复用「上次那波人」：从一场已有聚餐新建，参与者拷过来、RSVP 置为 MAYBE 等重新应邀。mode 为空沿用原玩法。 */
    public record CopyRequest(@NotBlank(message = "请填写聚餐标题") @Size(max = 64) String title,
                              @NotNull(message = "请选择日期") LocalDate mealDate,
                              @NotNull(message = "请选择餐次") MealType mealType,
                              @Size(max = 128) String location,
                              Mode mode,
                              LocalDateTime deadlineAt,
                              Long hostFamilyId,
                              @Size(max = 500) String note) {}

    /** 通过邀请加入；rsvp 默认 YES，guestName 默认用昵称。 */
    public record JoinRequest(Rsvp rsvp,
                              @Min(1) @Max(20) Integer headcount,
                              @Size(max = 255) String dietaryNotes,
                              @Size(max = 64) String guestName) {}

    /** 改自己的应邀：来不来、带几个人、忌口、称呼。 */
    public record RsvpRequest(@NotNull(message = "请选择来不来") Rsvp rsvp,
                              @Min(1) @Max(20) Integer headcount,
                              @Size(max = 255) String dietaryNotes,
                              @Size(max = 64) String guestName) {}

    /** 发起人代填一位没有账号的客人（比如家里老人）。 */
    public record AddGuestRequest(@NotBlank(message = "请填写称呼") @Size(max = 64) String guestName,
                                  Rsvp rsvp,
                                  @Min(1) @Max(20) Integer headcount,
                                  @Size(max = 255) String dietaryNotes) {}

    /** 一道菜：HOST_COOK 是「想吃」，VOTE 是「提议」，POTLUCK 是「我带」。 */
    public record WishRequest(@NotBlank(message = "请填写菜名") @Size(max = 64) String dishName,
                              @Size(max = 255) String note) {}

    /**
     * 确认菜单中的一道菜：wishId（采纳某条意愿/提议/认领）或 dishName 至少填一个。
     * servings 为空表示「按出席总人数」，人数变化时自动跟着变；bringParticipantId 为空时，
     * 各带一道模式下默认是认领人，其他模式表示主人准备。
     */
    public record ConfirmItem(Long wishId,
                              @Size(max = 64) String dishName,
                              @Min(1) @Max(200) Integer servings,
                              Long bringParticipantId,
                              @Size(max = 255) String note) {}

    public record ConfirmRequest(@NotEmpty(message = "至少确认一道菜") @Valid List<ConfirmItem> items) {}

    public record ReopenRequest(LocalDateTime deadlineAt) {}

    /** baseServings 不为空时，amount 表示 baseServings 人份的用量，按出席总人数换算。 */
    public record ShoppingItemRequest(@NotBlank(message = "名称不能为空") @Size(max = 64) String name,
                                      @DecimalMin("0") BigDecimal amount,
                                      @Size(max = 16) String unit,
                                      @Min(1) @Max(200) Integer baseServings,
                                      @Size(max = 64) String forDish,
                                      @Size(max = 255) String note) {}

    public record PurchasedRequest(@NotNull Boolean purchased) {}

    // ---------------- 响应 ----------------

    /** 我的聚餐列表里的一项。 */
    public record GatheringSummary(Long id, String title, LocalDate mealDate, MealType mealType, String location,
                                   Mode mode, Status status, String hostName, boolean host, Rsvp myRsvp,
                                   int headcount, int participantCount, LocalDateTime readyAt) {}

    /**
     * 邀请预览：只给决定要不要加入所需的最少信息，不含其他参与者和家庭信息。
     * gatheringId 只在已经加入时返回。
     */
    public record InvitePreview(String title, LocalDate mealDate, MealType mealType, String location, Mode mode,
                                Status status, String hostName, LocalDateTime deadlineAt, boolean joinable,
                                boolean joined, Long gatheringId, String reason) {}

    /** dietaryNotes 只对发起人和本人可见。 */
    public record ParticipantView(Long id, String name, Rsvp rsvp, int headcount, String dietaryNotes,
                                  boolean host, boolean me, boolean guest, LocalDateTime joinedAt) {}

    public record WishView(Long id, Long participantId, String participantName, String dishName, String note,
                           boolean bringBySelf, int votes, boolean votedByMe, boolean mine, boolean editable,
                           LocalDateTime createdAt) {}

    /** 主人掌厨：同一道菜有几个人想吃。 */
    public record DishTally(String dishName, int count, List<String> by) {}

    /** servings 是实际份数；autoServings=true 表示按出席总人数自动换算。 */
    public record MenuItemView(Long id, String dishName, int servings, boolean autoServings, Long wishId,
                               Long bringParticipantId, String bringByName, String note) {}

    public record ShoppingItemView(Long id, String name, BigDecimal amount, String unit, Integer baseServings,
                                   BigDecimal scaledAmount, String forDish, String note, boolean purchased) {}

    /** headcount 是当前出席总人数，scaledAmount 已按它换算好；人数一变，下次读取就是新的用量。 */
    public record ShoppingView(Long gatheringId, int headcount, List<ShoppingItemView> items, int total,
                               int purchasedCount) {}

    public record GatheringDetail(Long id, String title, LocalDate mealDate, MealType mealType, String location,
                                  String note, Mode mode, Status status, String hostName, boolean host,
                                  Long hostFamilyId, String inviteToken, LocalDateTime deadlineAt,
                                  boolean canEditWishes, int headcount, Integer confirmedHeadcount,
                                  boolean headcountChanged, int yesCount, int maybeCount, int noCount,
                                  LocalDateTime confirmedAt, LocalDateTime readyAt, LocalDateTime closedAt,
                                  LocalDateTime createdAt, Long sourceGatheringId, ParticipantView me,
                                  List<ParticipantView> participants, List<WishView> wishes,
                                  List<DishTally> tally, List<String> unclaimed, List<MenuItemView> menu) {}

    /** 家庭日历上的占位：只有标题、时间、发起人，用来提醒「这餐有一场临时聚餐」。 */
    public record FamilySlotView(Long id, String title, LocalDate mealDate, MealType mealType, Status status,
                                 String hostName, boolean joined) {}
}
