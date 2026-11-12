package com.zzwen6.familymeal.rotation;

import jakarta.persistence.*;

@Entity
@Table(name = "cook_rotation_member")
public class CookRotationMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rotation_id")
    private CookRotation rotation;
    @Column(nullable = false)
    private Long memberId;
    @Column(nullable = false)
    private int sortOrder;

    public Long getId() { return id; }
    public CookRotation getRotation() { return rotation; }
    public void setRotation(CookRotation rotation) { this.rotation = rotation; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
