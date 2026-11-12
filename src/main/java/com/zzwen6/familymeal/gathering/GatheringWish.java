package com.zzwen6.familymeal.gathering;

import com.zzwen6.familymeal.common.TimeHolder;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 聚餐里的一道菜，含义随玩法不同：
 * HOST_COOK 客人想吃什么；VOTE 提议的菜（votes 为票数）；POTLUCK 认领自己带的菜（bringBySelf=true）。
 * 菜名是快照，不关联家庭菜谱。
 */
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

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = TimeHolder.now();
    }

    public Long getId() { return id; }
    public Long getGatheringId() { return gatheringId; }
    public void setGatheringId(Long gatheringId) { this.gatheringId = gatheringId; }
    public Long getParticipantId() { return participantId; }
    public void setParticipantId(Long participantId) { this.participantId = participantId; }
    public String getDishName() { return dishName; }
    public void setDishName(String dishName) { this.dishName = dishName; }
    public boolean isBringBySelf() { return bringBySelf; }
    public void setBringBySelf(boolean bringBySelf) { this.bringBySelf = bringBySelf; }
    public int getVotes() { return votes; }
    public void setVotes(int votes) { this.votes = votes; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
