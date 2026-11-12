package com.zzwen6.familymeal.gathering;

import com.zzwen6.familymeal.common.TimeHolder;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 聚餐参与者：可以是登录用户，也可以只是主人代填的一个称呼；只在本场有效，不是家庭成员。 */
@Entity
@Table(name = "gathering_participant")
public class GatheringParticipant {
    public enum Rsvp { YES, NO, MAYBE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long gatheringId;
    private Long userId;
    @Column(nullable = false, length = 64)
    private String guestName;
    /** 算上自己一共来几个人（带家属就 >1）。 */
    @Column(nullable = false)
    private int headcount = 1;
    private String dietaryNotes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Rsvp rsvp;
    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    void prePersist() {
        if (joinedAt == null) joinedAt = TimeHolder.now();
    }

    public Long getId() { return id; }
    public Long getGatheringId() { return gatheringId; }
    public void setGatheringId(Long gatheringId) { this.gatheringId = gatheringId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public int getHeadcount() { return headcount; }
    public void setHeadcount(int headcount) { this.headcount = headcount; }
    public String getDietaryNotes() { return dietaryNotes; }
    public void setDietaryNotes(String dietaryNotes) { this.dietaryNotes = dietaryNotes; }
    public Rsvp getRsvp() { return rsvp; }
    public void setRsvp(Rsvp rsvp) { this.rsvp = rsvp; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
}
