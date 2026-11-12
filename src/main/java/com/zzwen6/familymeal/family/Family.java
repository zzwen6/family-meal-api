package com.zzwen6.familymeal.family;

import com.zzwen6.familymeal.common.BaseTimeEntity;
import com.zzwen6.familymeal.common.Enums.MealType;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "family")
public class Family extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64)
    private String name;
    @Column(nullable = false, length = 16)
    private String inviteCode;
    @Column(nullable = false)
    private LocalTime breakfastDeadline = LocalTime.of(21, 0);
    @Column(nullable = false)
    private LocalTime lunchDeadline = LocalTime.of(10, 0);
    @Column(nullable = false)
    private LocalTime dinnerDeadline = LocalTime.of(16, 0);
    /** 吃腻统计窗口（天），默认 14。 */
    @Column(nullable = false)
    private int fatigueWindowDays = 14;
    /** 同一道菜在窗口内出现次数达到该值则提示吃腻，默认 2。 */
    @Column(nullable = false)
    private int fatigueThreshold = 2;
    @Column(nullable = false)
    private Long createdBy;

    /** 某天某餐的默认点餐截止时间：早餐按前一天，午餐/晚餐按当天。 */
    public LocalDateTime deadlineFor(LocalDate date, MealType type) {
        return switch (type) {
            case BREAKFAST -> date.minusDays(1).atTime(breakfastDeadline);
            case LUNCH -> date.atTime(lunchDeadline);
            case DINNER -> date.atTime(dinnerDeadline);
        };
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    public LocalTime getBreakfastDeadline() { return breakfastDeadline; }
    public void setBreakfastDeadline(LocalTime t) { this.breakfastDeadline = t; }
    public LocalTime getLunchDeadline() { return lunchDeadline; }
    public void setLunchDeadline(LocalTime t) { this.lunchDeadline = t; }
    public LocalTime getDinnerDeadline() { return dinnerDeadline; }
    public void setDinnerDeadline(LocalTime t) { this.dinnerDeadline = t; }
    public int getFatigueWindowDays() { return fatigueWindowDays; }
    public void setFatigueWindowDays(int fatigueWindowDays) { this.fatigueWindowDays = fatigueWindowDays; }
    public int getFatigueThreshold() { return fatigueThreshold; }
    public void setFatigueThreshold(int fatigueThreshold) { this.fatigueThreshold = fatigueThreshold; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
