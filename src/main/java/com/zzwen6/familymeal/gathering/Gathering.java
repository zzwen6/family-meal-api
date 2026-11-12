package com.zzwen6.familymeal.gathering;

import com.zzwen6.familymeal.common.Enums.MealType;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 一场临时聚餐（预留）。 */
@Entity
@Table(name = "gathering")
public class Gathering {
    public enum Mode { HOST_COOK, VOTE, POTLUCK }
    public enum Status { OPEN, CONFIRMED, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long hostUserId;
    private Long hostFamilyId;
    @Column(nullable = false, length = 64)
    private String title;
    @Column(nullable = false)
    private LocalDate mealDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MealType mealType;
    private String location;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Mode mode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;
    @Column(nullable = false, length = 32)
    private String inviteToken;
    private LocalDateTime deadlineAt;
    private LocalDateTime closedAt;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getHostUserId() { return hostUserId; }
    public Long getHostFamilyId() { return hostFamilyId; }
    public String getTitle() { return title; }
    public LocalDate getMealDate() { return mealDate; }
    public MealType getMealType() { return mealType; }
    public String getLocation() { return location; }
    public Mode getMode() { return mode; }
    public Status getStatus() { return status; }
    public String getInviteToken() { return inviteToken; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
