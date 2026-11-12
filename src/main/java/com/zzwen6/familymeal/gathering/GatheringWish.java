package com.zzwen6.familymeal.gathering;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 聚餐里的点菜/认领（预留）。菜名是快照，不关联家庭菜谱。 */
@Entity
@Table(name = "gathering_wish")
public class GatheringWish {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long gatheringId;
    @Column(nullable = false)
    private Long participantId;
    @Column(nullable = false, length = 64)
    private String dishName;
    @Column(nullable = false)
    private boolean bringBySelf;
    @Column(nullable = false)
    private int votes;
    private String note;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getGatheringId() { return gatheringId; }
    public Long getParticipantId() { return participantId; }
    public String getDishName() { return dishName; }
    public boolean isBringBySelf() { return bringBySelf; }
    public int getVotes() { return votes; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
