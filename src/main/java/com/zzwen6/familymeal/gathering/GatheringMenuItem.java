package com.zzwen6.familymeal.gathering;

import jakarta.persistence.*;

/** 发起人确认的一道菜。servings 为空表示「按出席总人数」，人数变化时自动跟着变。 */
@Entity
@Table(name = "gathering_menu_item")
public class GatheringMenuItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long gatheringId;
    @Column(nullable = false, length = 64)
    private String dishName;
    private Integer servings;
    private Long wishId;
    /** 各带一道：由哪位参与者带来；为空表示主人准备。 */
    @Column(name = "bring_participant_id")
    private Long bringParticipantId;
    private String note;
    @Column(nullable = false)
    private int sortOrder;

    public Long getId() { return id; }
    public Long getGatheringId() { return gatheringId; }
    public void setGatheringId(Long gatheringId) { this.gatheringId = gatheringId; }
    public String getDishName() { return dishName; }
    public void setDishName(String dishName) { this.dishName = dishName; }
    public Integer getServings() { return servings; }
    public void setServings(Integer servings) { this.servings = servings; }
    public Long getWishId() { return wishId; }
    public void setWishId(Long wishId) { this.wishId = wishId; }
    public Long getBringParticipantId() { return bringParticipantId; }
    public void setBringParticipantId(Long bringParticipantId) { this.bringParticipantId = bringParticipantId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
