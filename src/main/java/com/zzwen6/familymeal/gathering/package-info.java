/**
 * 临时聚餐（组队）——v1 只预留实体与表结构，不开放接口。
 *
 * <p>数据边界（后续实现时必须遵守）：
 * <ul>
 *   <li>聚餐不是家庭：参与者不成为 family_member，看不到家庭的菜谱库、日常菜单、库存与统计；</li>
 *   <li>聚餐里的菜以菜名快照保存（gathering_wish.dish_name），不直接引用家庭 dish 表；</li>
 *   <li>host_family_id 仅用于在发起人家庭日历上占位，不授予任何家庭权限；</li>
 *   <li>邀请通过 invite_token（小程序卡片/链接），散场（CLOSED）后队员自动失效。</li>
 * </ul>
 */
package com.zzwen6.familymeal.gathering;
