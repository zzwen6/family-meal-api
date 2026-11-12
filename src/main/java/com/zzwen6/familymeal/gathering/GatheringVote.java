package com.zzwen6.familymeal.gathering;

import com.zzwen6.familymeal.common.TimeHolder;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 众筹投票的一票：同一个参与者对同一道菜只能投一次（数据库唯一约束兜底）。 */
@Entity
@Table(name = "gathering_vote")
public class GatheringVote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long gatheringId;
    @Column(nullable = false)
    private Long wishId;
    @Column(nullable = false)
    private Long participantId;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected GatheringVote() {}

    public GatheringVote(Long gatheringId, Long wishId, Long participantId) {
        this.gatheringId = gatheringId;
        this.wishId = wishId;
        this.participantId = participantId;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = TimeHolder.now();
    }

    public Long getId() { return id; }
    public Long getGatheringId() { return gatheringId; }
    public Long getWishId() { return wishId; }
    public Long getParticipantId() { return participantId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
