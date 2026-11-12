package com.zzwen6.familymeal.gathering;

import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.common.TimeHolder;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 一场临时聚餐：独立于家庭，只属于发起人和本场参与者。 */
@Entity
@Table(name = "gathering")
public class Gathering {
    /** HOST_COOK 主人掌厨 / VOTE 众筹投票 / POTLUCK 各带一道 */
    public enum Mode { HOST_COOK, VOTE, POTLUCK }
    /** OPEN 应邀点菜中 / CONFIRMED 菜单已定 / CLOSED 已散场 */
    public enum Status { OPEN, CONFIRMED, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long hostUserId;
    /** 只用于在发起人家庭日历上占位，不授予任何家庭权限。 */
    private Long hostFamilyId;
    @Column(nullable = false, length = 64)
    private String title;
    @Column(nullable = false)
    private LocalDate mealDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MealType mealType;
    private String location;
    private String note;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Mode mode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;
    @Column(nullable = false, length = 32)
    private String inviteToken;
    private LocalDateTime deadlineAt;
    private LocalDateTime confirmedAt;
    private Integer confirmedHeadcount;
    private LocalDateTime readyAt;
    private Long sourceGatheringId;
    private LocalDateTime closedAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = TimeHolder.now();
    }

    public Long getId() { return id; }
    public Long getHostUserId() { return hostUserId; }
    public void setHostUserId(Long hostUserId) { this.hostUserId = hostUserId; }
    public Long getHostFamilyId() { return hostFamilyId; }
    public void setHostFamilyId(Long hostFamilyId) { this.hostFamilyId = hostFamilyId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getMealDate() { return mealDate; }
    public void setMealDate(LocalDate mealDate) { this.mealDate = mealDate; }
    public MealType getMealType() { return mealType; }
    public void setMealType(MealType mealType) { this.mealType = mealType; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getInviteToken() { return inviteToken; }
    public void setInviteToken(String inviteToken) { this.inviteToken = inviteToken; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(LocalDateTime deadlineAt) { this.deadlineAt = deadlineAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public Integer getConfirmedHeadcount() { return confirmedHeadcount; }
    public void setConfirmedHeadcount(Integer confirmedHeadcount) { this.confirmedHeadcount = confirmedHeadcount; }
    public LocalDateTime getReadyAt() { return readyAt; }
    public void setReadyAt(LocalDateTime readyAt) { this.readyAt = readyAt; }
    public Long getSourceGatheringId() { return sourceGatheringId; }
    public void setSourceGatheringId(Long sourceGatheringId) { this.sourceGatheringId = sourceGatheringId; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public boolean isClosed() { return status == Status.CLOSED; }
}
