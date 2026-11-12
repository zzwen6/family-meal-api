package com.zzwen6.familymeal.auth;

import com.zzwen6.familymeal.common.BaseTimeEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class AppUser extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64)
    private String nickname;
    private String avatarUrl;
    private String wxOpenid;
    private String wxUnionid;
    private String username;
    private String passwordHash;
    @Column(nullable = false)
    private boolean systemAdmin;

    public Long getId() { return id; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getWxOpenid() { return wxOpenid; }
    public void setWxOpenid(String wxOpenid) { this.wxOpenid = wxOpenid; }
    public String getWxUnionid() { return wxUnionid; }
    public void setWxUnionid(String wxUnionid) { this.wxUnionid = wxUnionid; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isSystemAdmin() { return systemAdmin; }
    public void setSystemAdmin(boolean systemAdmin) { this.systemAdmin = systemAdmin; }
}
