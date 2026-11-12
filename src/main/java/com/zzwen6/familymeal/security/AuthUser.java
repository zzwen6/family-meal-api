package com.zzwen6.familymeal.security;

/** JWT 里携带的当前登录用户。家庭和角色不放进 token，每次请求按 familyId 查成员关系。 */
public record AuthUser(Long userId, boolean systemAdmin) {}
