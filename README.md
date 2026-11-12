# family-meal-api · 家庭点餐后端 + 管理后台

「家里今晚吃什么」的协同工具：家人提前在小程序 / H5 里说想吃什么，掌厨统一看、定菜单，系统按菜谱自动生成采购清单，做好了一键通知开饭；约朋友来吃饭时可以发起一场「临时聚餐」，按主人掌厨 / 众筹投票 / 各带一道三种玩法点菜。**不是外卖，也不做支付。**

本仓库包含：

- **后端 API**：Java 21 + Spring Boot 3.5 + MySQL 8 + Redis，Flyway 管理表结构，Spring Security + JWT。
- **管理后台**：`admin-web/`（Vue 3 + Vite + Element Plus），构建后打进同一个 jar，访问 `/admin/`。
- **部署**：推到 `main` → GitHub Actions 构建镜像推到 GHCR → webhook 通知 NAS 上已有的 Watchtower 拉取重启（见 [docs/DEPLOY.md](docs/DEPLOY.md)）。

小程序 / H5 前端在另一个仓库：[zzwen6/family-meal-app](https://github.com/zzwen6/family-meal-app)。

## 功能

| 模块 | 说明 |
| --- | --- |
| 登录 | 小程序 `wx.login` → `POST /api/auth/wx-login`（服务端 code2session）；用户名密码登录（管理后台、H5）；H5 注册可带邀请码直接入家。登录失败多次会被 Redis 限流锁定。 |
| 家庭与成员 | 角色：管理员 `ADMIN`（也能掌厨）、掌厨 `COOK`、成员 `MEMBER`、儿童 `CHILD`。邀请码入家；管理员可直接给孩子/老人建账号，并把儿童绑定监护人（默认操作者，之后可改）。儿童账号权限在服务端收紧：不能改规则、轮换、库存、剩菜、菜谱、上传媒体、确认/重开菜单、开饭、勾选已买、成员角色、邀请码，也不能发起或管理聚餐（仍可作为客人被邀请）。儿童可以查看菜谱封面、步骤图和视频，以及营养估算。 |
| 菜谱库 | 菜名、分类、难度、用时、默认份数、封面图（可上传，也可填外链）、简介、小贴士；按步骤的做法，步骤可配图和短视频；**主料**和**配料调味**分开维护，用量 + 单位 + 「家里常备」标记；详情可按份数换算，并给出整道菜和每人份的**营养估算**（按用料对照内置食材表，或掌厨手工覆盖）。下架是软删除。 |
| 点餐意愿 | 某天某餐（早/午/晚），选菜谱里的菜或直接写「猪脚」；很想吃 / 都可以 / 别做这个；份数；「这餐不在家吃」。截止前可改；掌厨/管理员或该孩子的监护人（即使是普通成员）可以代孩子点。 |
| 截止规则 | 管理员设置各餐截止时间（早餐按前一天）。`@Scheduled` 每分钟把过了截止的餐次标为已截止；掌厨可提前截止或重新开放。 |
| 掌厨确认菜单 | 一屏看全员意愿（谁没点、谁不在家、按菜汇总、过敏提醒），采纳 / 改菜 / 替代 / 加临时菜名，确认后自动生成采购清单。 |
| 采购清单 | 按「菜单份数 ÷ 菜谱默认份数」缩放用量，同名同单位合并并注明来自哪些菜；按单位取好读的数（2.67 罐 → 3 罐）；家里常备默认不买，掌厨可勾回；家人（儿童除外）可勾「已买」，勾选后把购买量入账到家庭库存，取消则撤回这次入账；重新生成保留勾选，且不会把本清单已入账的量再算成「家里已有」。 |
| 开饭通知 | `POST /menus/{id}/ready` 写站内通知，并给绑定了微信的家人发**订阅消息**「开饭提醒」（配置见下文；未配置时跳过，站内通知照常）。 |
| 掌厨轮换 | 管理员设定参与轮换的家人和适用餐次（默认晚餐），可生成本周/未来 N 天安排（按「谁做得少、较久没做、再按顺序」填空档，不覆盖手工指定）。今晚概况和工作台显示指定掌厨；可临时改派、对调。被排到的人不必先改成 COOK 角色。没有安排时权限与以前一样；有指定时，指定掌厨和管理员可确认，其他掌厨可代做（记录实际确认人并标明「代做」）。 |
| 吃腻与心愿公平 | 默认看最近 14 天、同一道菜出现 2 次即提示「可能吃腻了」（窗口和阈值可在家庭规则里改）。统计每位成员 WANT 被纳入确认菜单的比例，较久没被选上会提示。AVOID 的菜若被放进菜单会警告对应成员。只统计本家庭已确认/已开饭的日常菜单，**不含聚餐**。提示由后端计算，在概况、确认预览和点意愿页展示，不强制拦截。 |
| 库存与剩菜 | 家庭库存由管理员/掌厨手工维护，也可由采购已买、用掉剩菜、开饭自动增减，库存页可看最近流水。剩菜可在用餐后或随时登记。确认菜单和生成采购清单时，按规范化名称对照库存和未用完剩菜，只计算建议购买量（全额则默认不买并注明「家里已有」，部分则减少数量），**生成时不改库存数量**。勾选已买会把该项购买量合并入库存；用掉能匹配到库存食材的剩菜会扣库存；开饭时按生成清单时记下的「家里已有」部分扣一次。聚餐不进入家庭库存。详见下文规则。 |
| 儿童简化点餐 | 儿童登录后使用更大图文和口语化流程（我想吃 / 不吃这个 / 今晚不在家）。家长可在「家人与孩子」里帮孩子点餐。儿童进不了管理后台（仅 ADMIN/COOK）。 |
| 周菜单模板 | 管理员和掌厨可维护「一周各天各餐」的模板，或把过去一周已确认的日常菜单另存为模板。应用到某周时写入计划/预填，不把菜单标为已确认；已确认或已开饭的餐次永不覆盖，已有计划默认也不覆盖（可显式 overwrite）。日历、今晚概况和工作台能看到计划，与指定掌厨、聚餐占位共存。 |
| 餐后评价与统计 | 菜单开饭（READY）后，成员（含儿童）可为这餐打 1～5 分，可写一句话；每人每餐一次，可改；监护人可代评。统计按 from/to（默认本周）：每天吃了什么和谁掌厨（安排 + 实际确认人）、常做的菜（次数/上次/平均分）、常点的菜与选中比例、心愿兑现与掌厨次数、窗口内重复较高的菜。只统计本家庭日常菜单，**不含聚餐**。 |

| 临时聚餐 | 约朋友 / 亲戚吃饭：发起一场 → 小程序卡片或链接邀请 → 应邀（来不来、带几个人、忌口）→ 按三种玩法点菜 → 发起人确认菜单 → 开饭 → 散场；可「复用上次那波人」。和家庭数据完全隔离，详见下一节。 |

## 临时聚餐

一场聚餐是独立于家庭的「小组」：参与者只属于这一场，**不会成为 family_member**。接口都在 `/api/gatherings` 下，和 `/api/families/{id}` 完全分开。

### 流程

1. **发起**（任意登录用户）：标题、日期、餐次、地点、玩法、点菜截止时间（可选）、自己这边几个人。可选「在家庭日历上占位」（`hostFamilyId`，只能选自己所在的家），**只用于占位，不授予任何家庭权限**。系统生成随机 `inviteToken`，发起人自动成为参与者（RSVP=YES）。
2. **邀请**：小程序里分享卡片（`pages/gathering/join?token=`），H5 复制链接。对方登录后先看预览（标题、日期、主人昵称，不含其他参与者和家庭信息），再决定是否加入。散场或 token 无效时不能加入。发起人也可以给没有账号的人（比如老人）代登记。
3. **应邀**：来 / 待定 / 不来、一共来几个人（含自己）、忌口（只有发起人和本人能看到）。截止后、菜单确认后都能改人数，散场后不能。
4. **按玩法点菜**（截止前、菜单确认前；三种玩法的接口行为不同）：

   | 玩法 | `POST /wishes` 的含义 | 防重复 | 发起人怎么定 |
   | --- | --- | --- | --- |
   | `HOST_COOK` 主人掌厨 | 「我想吃」 | 同一人不重复点同一道 | 看「几个人想吃」汇总（`tally`）定菜单，**维护采购清单** |
   | `VOTE` 众筹投票 | 「提议一道菜」（自动投自己一票），其他人 `POST /wishes/{id}/vote` 投票 | 同名菜只能有一条提议；一人一菜一票（数据库唯一约束兜底） | 按票数排序确认，也可维护采购清单 |
   | `POTLUCK` 各带一道 | 「我来带」认领（`bringBySelf=true`） | 同名菜只能一人认领 | 看谁带什么、谁还没认领（`unclaimed`），确认后菜单显示「谁带」；不需要统一采购 |

5. **确认菜单**（发起人，状态 → `CONFIRMED`）：采纳意愿 / 提议 / 认领，或加自己准备的菜。份数留空 = 按出席总人数（RSVP=YES 的 headcount 之和），**人数变了自动跟着变**；详情里 `headcountChanged` 会提示「确认时 X 人，现在 Y 人」。确认后队员不能再改菜，发起人可「重新开放」。截止后队员不能再改意愿 / 投票 / 认领，发起人仍可确认。
6. **采购清单**（主人掌厨 / 众筹投票）：聚餐的菜是菜名快照，不能套用家庭菜谱的用料，所以由发起人手填。填了「几人份」（`baseServings`）的项按出席总人数实时换算（沿用家庭采购清单的取整规则，例如 300g/2 人份 → 3 人 450g）。
7. **开饭**：给本场 RSVP=YES 的参与者写聚餐通知，并发微信订阅消息（与家庭开饭共用同一个模板）。
8. **散场**（只有发起人，状态 → `CLOSED`）：邀请失效，不能再加入或修改；历史可查看。数据只留在这一场，**不进任何家庭统计**。
9. **复用这波人**：发起人从自己发起的任意一场（含已散场）新建，参与者拷过去、RSVP 置为 `MAYBE` 等重新应邀，新 token、新日期 / 标题，意愿和菜单不拷。

### 数据隔离

- 只有发起人和本场参与者能读这场（`GatheringAccess`），其他人一律 403（不存在的聚餐也是 403，避免枚举）。
- 发起人才能改信息、确认、开饭、散场、维护采购、移除参与者；队员只能改自己的应邀、意愿 / 提议 / 认领、投票。
- 聚餐详情不返回任何家庭信息（`hostFamilyId` 只给发起人自己看），菜名是快照，不引用家庭 `dish` 表；客人访问发起人家庭的菜谱 / 意愿 / 成员 / 菜单 / 通知全部 403（集成测试逐个覆盖）。
- 聚餐通知按 `gathering_id` 存（V2 迁移给 `notification` 加了 `gathering_id`，与 `family_id` 互斥，有 CHECK 约束），家庭通知接口查不到。
- 家庭日历占位：`GET /api/families/{fid}/gatherings?from=&to=` 只给家庭成员返回标题、日期、餐次、状态、发起人，家人不会因此成为参与者、看不了详情。小程序「今晚」和管理后台日历都会显示「这餐有一场临时聚餐」。
- 表结构增量放在 `V2__gathering.sql`（投票、确认菜单、聚餐采购、通知扩展），V1 未改动。儿童监护、周模板与计划、餐后评价在 `V4__child_template_stats.sql`。菜谱步骤媒体、营养覆盖、库存流水和采购入账标记在 `V5__media_nutrition_inventory_ledger.sql`。不改已发布的 V1–V4。

### 租户隔离与权限

所有家庭数据接口都是 `/api/families/{familyId}/...`，每个请求先查「当前用户在这个家里的成员身份」，不是成员一律 403；写操作再按角色判断（菜谱和确认菜单要掌厨/管理员，规则和成员要管理员）。JWT 只放用户 ID，不放家庭和角色，角色变化立即生效。

### 统一错误格式

```json
{ "code": "WISH_CLOSED", "message": "已过点餐截止时间", "status": 409, "path": "/api/families/1/wishes", "timestamp": "...", "fieldErrors": {} }
```

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/wx-login` | `{ code, nickname? }` 小程序登录 |
| POST | `/api/auth/login` / `/api/auth/register` | 用户名密码登录 / H5 注册 |
| GET | `/api/auth/me` | 当前用户和所在家庭 |
| GET/POST | `/api/families` · `/api/families/join` | 我的家庭 / 建家 / 邀请码入家 |
| GET/PUT | `/api/families/{fid}` | 家庭信息与规则（截止时间、吃腻窗口/阈值） |
| GET/POST/PUT/DELETE | `/api/families/{fid}/members[/{mid}]` | 成员 |
| GET/POST/PUT/DELETE | `/api/families/{fid}/dishes[/{id}]` | 菜谱（`?servings=` 换算；详情含营养估算；写入可带步骤图/视频和营养覆盖） |
| POST | `/api/families/{fid}/media` | 上传菜谱图片或短视频（仅管理员和掌厨；返回本 API 可访问的 URL） |
| GET | `/api/families/{fid}/media/{filename}` | 下载媒体（家庭成员含儿童可读；`Authorization: Bearer` 或查询参数 `access_token`） |
| GET/POST/PUT/DELETE | `/api/families/{fid}/wishes[/{id}]` · `/wishes/mine` | 点餐意愿（`memberId` 可代孩子点；`/wishes/mine?memberId=` 监护人可查看孩子的意愿） |
| GET | `/api/families/{fid}/menus/board?date=&mealType=` | 某餐概览（含计划菜、开饭后的评价、本餐营养估算） |
| GET | `/api/families/{fid}/menus?from=&to=` | 日历 |
| POST | `/api/families/{fid}/menus/confirm` | 确认菜单并生成采购清单（可带 `useLeftoverIds` 用掉剩菜） |
| POST | `/api/families/{fid}/menus/confirm-preview` | 确认前预览吃腻 / 忌口 / 公平提示，不写库 |
| POST | `/api/families/{fid}/menus/{id}/lock` · `/menus/reopen` · `/menus/{id}/ready` | 截止 / 重新开放 / 开饭 |
| GET/POST | `/api/families/{fid}/menus/{id}/shopping-list[/regenerate\|/items]` | 采购清单（生成时对照库存和剩菜） |
| PATCH/DELETE | `/api/families/{fid}/shopping-items/{itemId}` | 勾选买不买、已买 |
| GET/PUT | `/api/families/{fid}/cook-rotation` | 轮换设置（启用、适用餐次、参与成员及顺序；仅管理员可改） |
| GET | `/api/families/{fid}/cook-assignments?from=&to=` | 一段日期内的掌厨安排 |
| PUT | `/api/families/{fid}/cook-assignments` | 指定 / 改派 / 取消某餐掌厨（`memberId` 为空即取消） |
| POST | `/api/families/{fid}/cook-assignments/generate` | 生成本周或 `from`～`to` / `days` 天安排，不覆盖手工指定 |
| POST | `/api/families/{fid}/cook-assignments/swap` | 对调两餐的指定掌厨 |
| GET | `/api/families/{fid}/hints?date=&mealType=&dishId=&name=` | 吃腻 / 公平 / 剩菜等提示（点意愿时可带菜） |
| GET/POST/PUT/DELETE | `/api/families/{fid}/inventory[/{id}]` | 家庭库存（成员只读，管理员和掌厨可改） |
| GET | `/api/families/{fid}/inventory/ledger` | 库存流水（默认最近 50 条；成员只读） |
| GET/POST/PUT/DELETE | `/api/families/{fid}/leftovers[/{id}]` · `POST .../leftovers/{id}/use` | 剩菜登记与用掉 |
| GET | `/api/families/{fid}/notifications` | 站内通知（只有家庭通知） |
| GET/POST/PUT/DELETE | `/api/families/{fid}/week-templates[/{id}]` | 周菜单模板（掌厨/管理员可写；`POST .../from-week` 另存上周；`POST .../{id}/apply` 应用到某周） |
| GET | `/api/families/{fid}/meal-plans?from=&to=` | 某段日期的预填计划 |
| GET/POST | `/api/families/{fid}/menus/{menuId}/ratings` | 餐后评价（开饭后提交/修改；可带 `memberId` 代评） |
| GET | `/api/families/{fid}/stats?from=&to=` | 统计看板（默认本周一到周日；不含聚餐） |
| GET | `/api/families/{fid}/gatherings?from=&to=` | 家庭日历上的临时聚餐占位 |
| GET | `/api/wechat/subscribe-config` | 小程序订阅「开饭提醒」用的模板 ID（未配置时 `enabled=false`） |
| GET | `/actuator/health` | 健康检查 |

### 临时聚餐接口

| 方法 | 路径 | 谁能调 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/gatherings` | 登录用户 | 我发起 / 参加的聚餐（含已散场） |
| POST | `/api/gatherings` | 登录用户 | 发起一场 |
| GET | `/api/gatherings/invite/{token}` | 登录用户 | 邀请预览（少量信息） |
| POST | `/api/gatherings/invite/{token}/join` | 登录用户 | 应邀加入（已在就更新应邀）；散场后 409 |
| GET | `/api/gatherings/{id}` | 参与者 | 详情：参与者与 RSVP、意愿 / 提议 / 认领、汇总、菜单 |
| PUT | `/api/gatherings/{id}` | 发起人 | 改标题、日期、餐次、地点、截止、占位家庭、备注（玩法不能改） |
| POST | `/api/gatherings/{id}/copy` | 发起人 | 复用这波人新建一场 |
| PUT | `/api/gatherings/{id}/me` | 参与者 | 改自己的 RSVP、人数、忌口、称呼 |
| POST / DELETE | `/api/gatherings/{id}/participants[/{pid}]` | 发起人 | 代登记没有账号的客人 / 移除参与者 |
| POST / PUT / DELETE | `/api/gatherings/{id}/wishes[/{wishId}]` | 参与者（只能改删自己的；发起人可删任意） | 想吃 / 提议 / 认领 |
| POST / DELETE | `/api/gatherings/{id}/wishes/{wishId}/vote` | 参与者 | 投票 / 取消（仅 VOTE） |
| POST | `/api/gatherings/{id}/confirm` | 发起人 | 确认（或修改）菜单 |
| POST | `/api/gatherings/{id}/reopen` | 发起人 | 菜单定了后重新开放点菜 |
| POST | `/api/gatherings/{id}/ready` | 发起人 | 开饭：聚餐通知 + 订阅消息 |
| POST | `/api/gatherings/{id}/close` | 发起人 | 散场 |
| GET | `/api/gatherings/{id}/notifications` | 参与者 | 本场通知 |
| GET | `/api/gatherings/{id}/shopping` | 参与者 | 采购清单（按出席人数换算；POTLUCK 为 409） |
| POST / PUT / PATCH / DELETE | `/api/gatherings/{id}/shopping/items[/{itemId}]` | 发起人 | 加 / 改 / 勾已买 / 删采购项 |

常见错误码：`GATHERING_CLOSED`（已散场）、`GATHERING_CONFIRMED`（菜单已定）、`WISH_CLOSED`（过了截止）、`DUPLICATE_WISH` / `DUPLICATE_DISH` / `ALREADY_CLAIMED`（各玩法的防重复）、`ALREADY_VOTED`、`MODE_MISMATCH`（玩法不支持这个操作）、`ALREADY_READY`。

## 本地开发

需要 JDK 21、Maven 3.9、Node 20+、MySQL 8、Redis 7。

```bash
# 1. 起数据库（也可以用自己的）
docker run -d --name fm-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=family_meal -e MYSQL_USER=family_meal -e MYSQL_PASSWORD=family_meal mysql:8.0 \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
docker run -d --name fm-redis -p 6379:6379 redis:7-alpine

# 2. 启动后端（表结构由 Flyway 自动创建）
export SPRING_DATASOURCE_PASSWORD=family_meal
export ADMIN_PASSWORD=admin123          # 首次启动自动建管理员 admin 和默认家庭
export WECHAT_MOCK_ENABLED=true         # 没有 AppID 时让小程序/开发者工具也能登录（仅开发）
mvn spring-boot:run

# 3. 管理后台开发（热更新，/api 代理到 8080）
cd admin-web && npm install && npm run dev   # http://localhost:5174/admin/

# 4. 打包（先构建后台，再打 jar；jar 里就带 /admin/）
(cd admin-web && npm run build) && mvn -DskipTests package
java -jar target/family-meal-api.jar
```

### 测试

```bash
mvn test
```

集成测试用 Testcontainers 启动真实 MySQL 8 和 Redis（需要本机能跑 Docker），覆盖：登录 / 错误格式 / 限流、微信 mock 登录、邀请码注册，以及「点餐 → 截止 → 确认菜单 → 采购清单（缩放、合并、常备品、保留勾选）→ 开饭通知」主流程、角色校验和跨家庭隔离。`DailyEnhanceIntegrationTest` 覆盖掌厨轮换（生成、手工改派/对调、不覆盖手工指定、无安排时旧权限、指定后确认与代做）、吃腻与公平（窗口内重复菜、WANT 长期未选中、AVOID 警告、聚餐不计入）、库存与剩菜（全额/部分对照、用掉剩菜后扣匹配的库存、成员只读）。

`ChildTemplateStatsIntegrationTest` 覆盖：儿童不能改菜谱 / 规则 / 确认菜单 / 轮换 / 库存 / 剩菜 / 发起聚餐（403），能给自己点餐；监护人（MEMBER）可代孩子点，非监护普通成员不能；把掌厨改成儿童后权限立即收紧。周模板可保存、从上周另存、应用到未来一周预填且不覆盖已确认菜单，工作台能带出计划。统计次数和兑现比例正确，聚餐不计入，评价后平均分正确，未开饭不能评。

`NutritionServiceTest` 覆盖已知食材（含别名）换算、未知食材和「适量」跳过、份数缩放、手工覆盖优先、盐等调味热量记 0。`MediaNutritionInventoryIntegrationTest` 覆盖：掌厨可上传、非法类型和超限被拒、普通成员和儿童不能上传但可读、未登录 401、`access_token` 查询参数可读、封面写入菜谱、营养覆盖接口，以及库存新规则（勾已买入账、取消撤回、重复勾不双计、用掉剩菜扣库存、开饭只扣「家里已有」部分且不与剩菜双扣、儿童勾已买 403、生成清单不改库存数量）。`StockOffsetTest` 覆盖对照计算，以及重新生成时把本清单已入账的购买量从库存池里扣除，避免刚买的被当成「家里已有」。

`GatheringIntegrationTest` 覆盖临时聚餐：创建、邀请预览、应邀、三种玩法主路径（想吃汇总 / 提议 + 一人一票 / 认领防重复）、截止、确认、人数变化后份量和采购换算、开饭通知不进家庭、散场后不能加入和修改、复用上波人、家庭日历占位，以及客人访问发起人家庭菜谱 / 意愿 / 成员等全部 403、非参与者 403。`WechatNotifierTest` 用 mock HTTP 验证订阅消息：未配置跳过、access_token 获取与 Redis 缓存（提前 5 分钟过期）、token 失效重取、请求体与字段映射、失败返回 FAILED 不抛异常。

如果本机 Docker 不能做端口映射，可以自己起好库再复用：

```bash
FM_TEST_MYSQL_URL="jdbc:mysql://127.0.0.1:3306/family_meal_test?allowPublicKeyRetrieval=true&useSSL=false" \
FM_TEST_MYSQL_USER=root FM_TEST_MYSQL_PASSWORD=root FM_TEST_REDIS_PORT=6379 mvn test
```

> 测试会清空所连的库（Flyway clean），不要指向正式数据库。

## 配置（环境变量）

完整列表见 [.env.example](.env.example) 和 `src/main/resources/application.yml`。

| 变量 | 说明 |
| --- | --- |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | MySQL |
| `SPRING_DATA_REDIS_HOST` / `_PORT` / `_PASSWORD` | Redis |
| `JWT_SECRET` | 至少 32 字节；不设会用临时密钥（重启后需重新登录） |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` / `ADMIN_FAMILY_NAME` | 首次启动的管理员与默认家庭 |
| `WECHAT_APPID` / `WECHAT_SECRET` | 小程序登录（个人主体即可） |
| `WECHAT_MOCK_ENABLED` | 未配置 AppID 时允许 mock 登录，仅开发 |
| `WECHAT_DINNER_READY_TEMPLATE_ID` | 开饭提醒订阅消息模板 ID；和 AppID / Secret 都配了才推送 |
| `WECHAT_TPL_TITLE_FIELD` / `WECHAT_TPL_CONTENT_FIELD` / `WECHAT_TPL_TIME_FIELD` | 模板字段映射，默认 `thing1` / `thing2` / `time3`，见下文 |
| `WECHAT_MINIPROGRAM_STATE` | 点消息跳转的小程序版本：`formal`（默认）/ `trial` / `developer` |
| `CORS_ALLOWED_ORIGINS` | H5 前端来源，逗号分隔 |
| `APP_TIMEZONE` | 默认 `Asia/Shanghai`，截止时间按这个时区算 |
| `MEDIA_PATH` | 本地媒体目录，默认 `./data/media`；Compose 里固定为容器内 `/data/media`，由宿主机 `MEDIA_DIR` 挂载 |
| `MEDIA_IMAGE_MAX_BYTES` / `MEDIA_VIDEO_MAX_BYTES` | 图片和视频大小上限，默认 5MB / 30MB |
| `MEDIA_MAX_FILE` / `MEDIA_MAX_REQUEST` | Spring  multipart 上限，默认 30MB / 32MB |

## 微信订阅消息（开饭提醒）

家里「开饭啦」和临时聚餐「开饭啦」都会给有微信 openid 的接收人发一条订阅消息（家庭发给全家，聚餐只发给本场 RSVP=YES 的人）；「菜单已确认」只写站内通知，不占用订阅次数。

1. 小程序后台 → **功能 → 订阅消息** → 公共模板库里选一个「开饭 / 用餐 / 预约提醒」类的**一次性订阅**模板，记下模板 ID 和字段名（模板详情里形如 `thing1`、`thing2`、`time3`）。
2. 后端配置 `WECHAT_APPID`、`WECHAT_SECRET`、`WECHAT_DINNER_READY_TEMPLATE_ID`，按你选的模板填字段映射：

   | 变量 | 默认 | 填什么 |
   | --- | --- | --- |
   | `WECHAT_TPL_TITLE_FIELD` | `thing1` | 放标题（「开饭啦」）的字段 |
   | `WECHAT_TPL_CONTENT_FIELD` | `thing2` | 放内容（「11月12日晚餐做好了，快来吃！」）的字段 |
   | `WECHAT_TPL_TIME_FIELD` | `time3` | 放发送时间（`yyyy-MM-dd HH:mm`）的字段 |

   模板里没有的字段把对应变量设为空即可。`thing` 类字段微信限制 20 个字，超出会自动截断（`phrase` 限 5 个字）。
3. 小程序里用户点「🔔 提醒我开饭」（今晚页、聚餐详情页）时调用 `wx.requestSubscribeMessage` 拿一次性授权（模板 ID 从 `GET /api/wechat/subscribe-config` 取），授权一次能收一条。H5 不调用微信 API。

实现：`WechatNotifier` 用 appid + secret 取 `access_token`，缓存在 Redis `fm:wx:access_token`，比微信给的有效期提前 5 分钟过期；token 失效（40001 / 42001）时清缓存重取一次；逐个 openid 调 `POST /cgi-bin/message/subscribe/send`。用户没授权（43101）或其他失败只记日志，至少一人成功记 `SENT`，全部失败记 `FAILED`，**不会影响开饭流程和站内通知**。三项没配齐时直接记 `SKIPPED`。

## 吃腻、公平与库存对照规则

- **默认窗口 14 天、吃腻阈值 2 次**。管理员可在家庭规则里把窗口改成 1～60 天（常用 7 / 14 / 21），阈值 1～20。只统计本家庭状态为已确认 / 已开饭的日常菜单，按 `dish_id` 或规范化菜名匹配；临时聚餐的菜单、意愿和采购**不进入**吃腻、公平、轮换、营养统计和家庭库存。
- **名称规范化**：去掉空格（含全角空格）、全角转半角、忽略大小写。单位会把「克/g」「毫升/ml」等常见别名视为相同；单位不同则不自动扣数量，只提示核对。
- **采购对照（生成清单仍不改库存）**：生成或重新生成清单时，按名称匹配**未用完剩菜（优先，可能先过期）和库存**，只计算建议购买量，**不改库存数量**。这样反复生成不会提前扣库存，也不会和后面的「勾已买入账 / 开饭扣已有」打架。全额覆盖则该项默认 `to_buy=false` 并注明「家里已有」；部分覆盖则减少数量并注明用了多少。对照结果（剩菜用量、库存用量）会记在采购项上，开饭时只按「库存用量」扣。重新生成时，会先从库存池里扣除**本清单已经入账**的购买量，避免刚勾已买的食材又被算成「家里已有」并从待买列表消失，也避免开饭时把刚买的量再扣掉。家里常备（staple）仍然默认不买。用户手工改过的「买不买」（`manual_to_buy`）和「已买」在重新生成时会保留；未手工改过的「买不买」会按常备和当前对照结果重算。
- **勾选已买**：`purchased=true` 时，把该项购买量**增加**到家庭库存（同名同单位合并，没有则新建），并记下入账数量，只入账一次。取消勾选则按当时记下的数量撤回（库存不会无故变成大负数，最低到 0）。重复勾选不会双计。儿童不能勾已买。
- **用掉剩菜**：确认菜单时若选了对应菜，或请求里带了 `useLeftoverIds`，或事后点「用掉」，该份剩菜标记为已用，不再参与对照。若剩菜名称能匹配到库存食材，再从库存扣对应数量（扣到 0 即可，低于 0 时置 0 并记流水）；对得上菜名但对不上库存食材的菜肴类剩菜，只标记已用，不强行扣库存。每份剩菜只扣一次。
- **开饭扣库存**：菜单变为 READY 时，按各采购项在生成时记下的「家里已有 / 来自库存」数量扣一次（不是整份采购量，也不含已经由剩菜覆盖的部分），用菜单上的 `stock_deducted` 标记保证不重复扣。之后重开再开饭不会再扣。这样与「用掉剩菜」不会对同一份量扣两次。
- **手工改库存**仍然可以，与自动流水共存。库存页可查看最近流水（采购入账、撤回、用掉剩菜、开饭扣减、手工调整）。

表结构增量在 `V3__daily_enhance.sql`（轮换、对照）和 `V5__media_nutrition_inventory_ledger.sql`（入账标记、开饭标记、库存流水）。V1 / V2 / V4 未改动。

## 菜谱媒体与营养估算

- **上传**：管理员和掌厨可 `POST /api/families/{fid}/media`（字段名 `file`）上传本家庭菜谱的图片或短视频。文件存在可配置的本地目录（`MEDIA_PATH`，Compose 挂到宿主机 `MEDIA_DIR`），文件名是不可猜测的 UUID。只接受 jpeg / png / webp / gif 和 mp4 / webm，并用文件头校验，不只看后缀。默认图片 ≤ 5MB、视频 ≤ 30MB。返回本 API 可访问的 URL：`/api/families/{fid}/media/{uuid.ext}`。未实现对象存储，未配置时一律走本地目录。
- **访问**：家庭成员（含儿童、普通成员）可读，未登录不能看。校验登录：请求头 `Authorization: Bearer`，或 GET 查询参数 `access_token`（方便小程序 `<image>` / `<video>` 和 H5 的 `src`）。目录不可遍历。替换步骤/封面或更新菜谱时会删除本家庭不再引用的旧文件；软删除的菜谱会保留文件。
- **营养估算**：按菜谱用料对照内置表 `nutrition/foods.json`（约 129 种常见中式食材，每 100g 的热量、蛋白质、脂肪、碳水化合物，含别名；数据是**中国食物成分表风格的约数**，不是检测值，也不是官方认证）。名称经规范化后精确或别名匹配（如「鸡胸」→「鸡胸肉」）。单位换算与现有用量规则对齐：克 / 千克 / 斤 / 两 / 毫升（按 1g/ml）/ 勺 / 茶匙，以及表里为「个」等提供的大约克重；不能换算或写「适量」且无重量的用料不计入，并在详情列出「未计入：xx」。盐、味精、胡椒、花椒、八角、桂皮、香叶等按 0 热量处理，避免把热量算飞。掌厨可手工覆盖整道菜（按默认份数）的营养，覆盖后以手工为准，并按当前份数缩放。已确认菜单会汇总本餐估算，只含有菜谱的菜；手写菜没有用料则跳过并注明。临时聚餐不进入营养统计。前端和接口都会标明这是估算。

## 已知限制 / 后续

- 订阅消息是一次性授权：用户每点一次「提醒我开饭」只能收一条；服务端不记录授权次数，没授权的人微信会返回 43101（记日志跳过）。
- H5 无法使用微信网页授权（个人主体没有公众号），H5 用用户名密码 + 邀请码；H5 也收不到微信订阅消息，只有站内通知。
- 临时聚餐的采购清单需要发起人手填（菜名是快照，不套用家庭菜谱用料）；聚餐不做 AA 收款。
- 没有微信支付（个人主体也不支持）。
- 营养数字是按用料和内置近似表估算的，找不到的食材、不能换算的单位和手写菜会跳过；不要把它当成检测报告或健康宣传依据。
- 菜谱媒体只存在本地目录，没有做对象存储（如腾讯云 COS）；请把 `MEDIA_DIR` 挂到 NAS 持久目录并定期备份。软删除的菜谱不会立刻删文件。

## 部署

见 [docs/DEPLOY.md](docs/DEPLOY.md)。

## License

MIT
