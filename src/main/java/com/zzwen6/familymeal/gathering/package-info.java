/**
 * 临时聚餐（组队）：建一场、发邀请、应邀、按玩法点菜、确认菜单、开饭、散场。
 *
 * <p>数据边界（必须遵守）：
 * <ul>
 *   <li>聚餐不是家庭：参与者不成为 family_member，看不到家庭的菜谱库、日常菜单、库存与统计；</li>
 *   <li>聚餐里的菜以菜名快照保存（gathering_wish.dish_name / gathering_menu_item.dish_name），不直接引用家庭 dish 表；</li>
 *   <li>host_family_id 仅用于在发起人家庭日历上占位，不授予任何家庭权限；</li>
 *   <li>邀请通过 invite_token（小程序卡片/链接），散场（CLOSED）后邀请失效、本场只读；</li>
 *   <li>接口独立于 /api/families/{id}，统一在 /api/gatherings 下，入口是 {@link GatheringAccess}：
 *       只有发起人和本场参与者能读，发起人才能管理，队员只能改自己的应邀 / 意愿 / 认领 / 投票；</li>
 *   <li>聚餐通知单独按 gathering_id 存，不会出现在家庭通知里。</li>
 * </ul>
 */
package com.zzwen6.familymeal.gathering;
