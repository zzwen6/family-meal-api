# family-meal-api · 家庭点餐后端 + 管理后台

「家里今晚吃什么」的协同工具：家人提前在小程序 / H5 里说想吃什么，掌厨统一看、定菜单，系统按菜谱自动生成采购清单，做好了一键通知开饭。**不是外卖，也不做支付。**

本仓库包含：

- **后端 API**：Java 21 + Spring Boot 3.5 + MySQL 8 + Redis，Flyway 管理表结构，Spring Security + JWT。
- **管理后台**：`admin-web/`（Vue 3 + Vite + Element Plus），构建后打进同一个 jar，访问 `/admin/`。
- **部署**：推到 `main` → GitHub Actions 构建镜像推到 GHCR → webhook 通知 NAS 上已有的 Watchtower 拉取重启（见 [docs/DEPLOY.md](docs/DEPLOY.md)）。

小程序 / H5 前端在另一个仓库：[zzwen6/family-meal-app](https://github.com/zzwen6/family-meal-app)。

## 第一版功能

| 模块 | 说明 |
| --- | --- |
| 登录 | 小程序 `wx.login` → `POST /api/auth/wx-login`（服务端 code2session）；用户名密码登录（管理后台、H5）；H5 注册可带邀请码直接入家。登录失败多次会被 Redis 限流锁定。 |
| 家庭与成员 | 角色：管理员 `ADMIN`（也能掌厨）、掌厨 `COOK`、成员 `MEMBER`、儿童 `CHILD`。邀请码入家；管理员可直接给孩子/老人建账号；每人可填过敏和忌口。 |
| 菜谱库 | 菜名、分类、难度、用时、默认份数、封面图、简介、小贴士；按步骤的做法；**主料**和**配料调味**分开维护，用量 + 单位 + 「家里常备」标记；详情可按份数换算。下架是软删除。 |
| 点餐意愿 | 某天某餐（早/午/晚），选菜谱里的菜或直接写「猪脚」；很想吃 / 都可以 / 别做这个；份数；「这餐不在家吃」。截止前可改，掌厨可替孩子点。 |
| 截止规则 | 管理员设置各餐截止时间（早餐按前一天）。`@Scheduled` 每分钟把过了截止的餐次标为已截止；掌厨可提前截止或重新开放。 |
| 掌厨确认菜单 | 一屏看全员意愿（谁没点、谁不在家、按菜汇总、过敏提醒），采纳 / 改菜 / 替代 / 加临时菜名，确认后自动生成采购清单。 |
| 采购清单 | 按「菜单份数 ÷ 菜谱默认份数」缩放用量，同名同单位合并并注明来自哪些菜；按单位取好读的数（2.67 罐 → 3 罐）；家里常备默认不买，掌厨可勾回；家人可勾「已买」；重新生成保留勾选。 |
| 开饭通知 | `POST /menus/{id}/ready` 写站内通知；微信订阅消息推送留了接口，标为 TODO（需要 AppID 和模板 ID）。 |
| 临时聚餐（预留） | 建好 `gathering*` 三张表和实体，与家庭数据隔离（不引用 family_member / dish），v1 不开放接口。 |

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
| GET | `/api/families/{fid}/notifications` | 站内通知 |
| GET | `/actuator/health` | 健康检查 |

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
| `WECHAT_DINNER_READY_TEMPLATE_ID` | 开饭提醒订阅消息模板（推送逻辑 TODO） |
| `CORS_ALLOWED_ORIGINS` | H5 前端来源，逗号分隔 |
| `APP_TIMEZONE` | 默认 `Asia/Shanghai`，截止时间按这个时区算 |

## 已知限制 / 后续

- 微信订阅消息推送未实现（`WechatNotifier` 里标了 TODO）；目前「开饭啦」只写站内通知。
- H5 无法使用微信网页授权（个人主体没有公众号），H5 用用户名密码 + 邀请码。
- 临时聚餐只建了表，接口和界面放到下一版。
- 没有微信支付（个人主体也不支持）。

## 部署

见 [docs/DEPLOY.md](docs/DEPLOY.md)。

## License

MIT
