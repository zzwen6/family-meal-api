package com.zzwen6.familymeal.gathering;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 聚餐参与者（预留）：可以是登录用户，也可以只是一个称呼；只在本场有效。 */
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
    @Column(nullable = false)
    private int headcount = 1;
    private String dietaryNotes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Rsvp rsvp;
    @Column(nullable = false)
    private LocalDateTime joinedAt;

    public Long getId() { return id; }
    public Long getGatheringId() { return gatheringId; }
    public Long getUserId() { return userId; }
    public String getGuestName() { return guestName; }
    public int getHeadcount() { return headcount; }
    public String getDietaryNotes() { return dietaryNotes; }
    public Rsvp getRsvp() { return rsvp; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
}
