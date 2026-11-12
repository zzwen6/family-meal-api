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
| 家庭与成员 | 角色：管理员 `ADMIN`（也能掌厨）、掌厨 `COOK`、成员 `MEMBER`、儿童 `CHILD`。邀请码入家；管理员可直接给孩子/老人建账号；每人可填过敏和忌口。 |
| 菜谱库 | 菜名、分类、难度、用时、默认份数、封面图、简介、小贴士；按步骤的做法；**主料**和**配料调味**分开维护，用量 + 单位 + 「家里常备」标记；详情可按份数换算。下架是软删除。 |
| 点餐意愿 | 某天某餐（早/午/晚），选菜谱里的菜或直接写「猪脚」；很想吃 / 都可以 / 别做这个；份数；「这餐不在家吃」。截止前可改，掌厨可替孩子点。 |
| 截止规则 | 管理员设置各餐截止时间（早餐按前一天）。`@Scheduled` 每分钟把过了截止的餐次标为已截止；掌厨可提前截止或重新开放。 |
| 掌厨确认菜单 | 一屏看全员意愿（谁没点、谁不在家、按菜汇总、过敏提醒），采纳 / 改菜 / 替代 / 加临时菜名，确认后自动生成采购清单。 |
| 采购清单 | 按「菜单份数 ÷ 菜谱默认份数」缩放用量，同名同单位合并并注明来自哪些菜；按单位取好读的数（2.67 罐 → 3 罐）；家里常备默认不买，掌厨可勾回；家人可勾「已买」；重新生成保留勾选。 |
| 开饭通知 | `POST /menus/{id}/ready` 写站内通知，并给绑定了微信的家人发**订阅消息**「开饭提醒」（配置见下文；未配置时跳过，站内通知照常）。 |
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
- 表结构增量放在 `V2__gathering.sql`（投票、确认菜单、聚餐采购、通知扩展），V1 未改动。

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
| GET/PUT | `/api/families/{fid}` | 家庭信息与规则（截止时间） |
| GET/POST/PUT/DELETE | `/api/families/{fid}/members[/{mid}]` | 成员 |
| GET/POST/PUT/DELETE | `/api/families/{fid}/dishes[/{id}]` | 菜谱（`?servings=` 换算） |
| GET/POST/PUT/DELETE | `/api/families/{fid}/wishes[/{id}]` · `/wishes/mine` | 点餐意愿 |
| GET | `/api/families/{fid}/menus/board?date=&mealType=` | 某餐概览（掌厨工作台） |
| GET | `/api/families/{fid}/menus?from=&to=` | 日历 |
| POST | `/api/families/{fid}/menus/confirm` | 确认菜单并生成采购清单 |
| POST | `/api/families/{fid}/menus/{id}/lock` · `/menus/reopen` · `/menus/{id}/ready` | 截止 / 重新开放 / 开饭 |
| GET/POST | `/api/families/{fid}/menus/{id}/shopping-list[/regenerate\|/items]` | 采购清单 |
| PATCH/DELETE | `/api/families/{fid}/shopping-items/{itemId}` | 勾选买不买、已买 |
| GET | `/api/families/{fid}/notifications` | 站内通知（只有家庭通知） |
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

集成测试用 Testcontainers 启动真实 MySQL 8 和 Redis（需要本机能跑 Docker），覆盖：登录 / 错误格式 / 限流、微信 mock 登录、邀请码注册，以及「点餐 → 截止 → 确认菜单 → 采购清单（缩放、合并、常备品、保留勾选）→ 开饭通知」主流程、角色校验和跨家庭隔离。

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

## 已知限制 / 后续

- 订阅消息是一次性授权：用户每点一次「提醒我开饭」只能收一条；服务端不记录授权次数，没授权的人微信会返回 43101（记日志跳过）。
- H5 无法使用微信网页授权（个人主体没有公众号），H5 用用户名密码 + 邀请码；H5 也收不到微信订阅消息，只有站内通知。
- 临时聚餐的采购清单需要发起人手填（菜名是快照，不套用家庭菜谱用料）；聚餐不做 AA 收款。
- 没有微信支付（个人主体也不支持）。

## 部署

见 [docs/DEPLOY.md](docs/DEPLOY.md)。

## License

MIT
