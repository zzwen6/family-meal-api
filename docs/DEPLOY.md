# 部署到 NAS（GHCR + Watchtower）

和 opds-library 用同一套流程：

```
git push main ──► GitHub Actions「Release images」
                    ├─ 构建镜像（管理后台 + Spring Boot jar）
                    ├─ 推到 ghcr.io/zzwen6/family-meal-api:{latest, 版本号, 短 SHA}
                    └─ curl -H "Authorization: Bearer $DEPLOY_WEBHOOK_TOKEN" $DEPLOY_WEBHOOK_URL
                                   │
NAS 上 opds-library 那套 Compose 里的 Watchtower（127.0.0.1:8081，经 nginx 反代）
                    └─ 拉取所有带 com.centurylinklabs.watchtower.enable=true 标签的容器的新镜像并重建
```

本仓库的 `docker-compose.yml` **只启动 API 容器**，不包含 MySQL、Redis，也不包含 Watchtower。MySQL 和 Redis 请指向你已经独立部署的实例。NAS 上已经有 Watchtower，开第二个会抢同一个 webhook / 端口并重复更新；已有的 Watchtower 设置了 `WATCHTOWER_LABEL_ENABLE=true`，会更新本项目带 `com.centurylinklabs.watchtower.enable=true` 标签的 `api` 容器。

## 1. GitHub Actions secrets（一次性）

在 GitHub 打开 `zzwen6/family-meal-api` → **Settings → Secrets and variables → Actions → New repository secret**，添加两项，值和 opds-library 仓库里的一样：

| Name | Value |
| --- | --- |
| `DEPLOY_WEBHOOK_URL` | Watchtower 更新地址，通常是 `https://gitlab.six666.qzz.io/v1/update` |
| `DEPLOY_WEBHOOK_TOKEN` | NAS 上 Watchtower 的 `WATCHTOWER_HTTP_API_TOKEN`（即 opds-library `.env` 里的 `DEPLOY_WEBHOOK_TOKEN`） |

没配这两项时，Actions 仍会构建并推送镜像，只是最后一步打印「未配置，跳过通知 NAS」并正常结束；配好后下一次推送就会自动部署。webhook 返回非 2xx 时该步骤会失败，方便发现问题。

也可以用 gh：

```bash
gh secret set DEPLOY_WEBHOOK_URL -R zzwen6/family-meal-api --body "https://gitlab.six666.qzz.io/v1/update"
gh secret set DEPLOY_WEBHOOK_TOKEN -R zzwen6/family-meal-api   # 交互输入 token
```

## 2. NAS 上首次启动

```bash
# 登录 GHCR（如果 opds-library 已经登录过可以跳过；镜像若为私有必须登录）
# token 用 GitHub 的 Personal Access Token（classic），勾 read:packages
echo "<你的 PAT>" | docker login ghcr.io -u zzwen6 --password-stdin

mkdir -p /volume1/docker/family-meal && cd /volume1/docker/family-meal
# 放入本仓库的 docker-compose.yml 和 .env.example
cp .env.example .env
# 先在已有 MySQL 上建好空库 family_meal 和账号（字符集 utf8mb4），表由 Flyway 启动时创建
vi .env     # 填写 MYSQL_HOST/PORT/USER/PASSWORD、REDIS_HOST/PORT/PASSWORD、JWT_SECRET、ADMIN_PASSWORD、WECHAT_APPID/SECRET、CORS_ALLOWED_ORIGINS
# MYSQL_HOST / REDIS_HOST：库在 NAS 本机上用 host.docker.internal；在其他容器里填容器名或 IP，必要时把 api 加入同一 Docker 网络

docker compose pull
docker compose up -d
docker compose logs -f api      # 看到 Started FamilyMealApplication 即可
```

- 默认宿主机端口 `8090`（`HTTP_PORT`），因为 8080 已被 opds-library 占用。
- 管理后台：`http://<NAS>:8090/admin/`，用 `.env` 里的 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 登录。
- 健康检查：`http://<NAS>:8090/actuator/health`。
- 数据在你独立部署的 MySQL 和 Redis 里，按原有方式备份即可（MySQL 建议用 `mysqldump`）。

Watchtower 从容器内的 `/config.json` 读 GHCR 登录信息（opds-library 的 Compose 已挂载 `/root/.docker/config.json`）。所以在 NAS 上用 root（群晖一般是 `sudo docker login`）登录一次 ghcr.io，Watchtower 就能拉本项目的镜像。GHCR 包首次推送后如果是私有，也可以在 GitHub → Packages → family-meal-api → Package settings 里改成 Public，这样不登录也能拉。

## 3. 对外访问（小程序必须 HTTPS）

微信小程序只允许请求已备案、HTTPS 的「request 合法域名」。在 NAS 的 nginx / 反向代理里给 `api` 容器（`http://127.0.0.1:8090`）配一个 HTTPS 域名，例如 `https://meal.example.com`，然后：

1. 小程序后台「开发管理 → 开发设置 → 服务器域名」里把它加到 **request 合法域名**；
2. 前端 `family-meal-app` 的 `.env` 里设 `VITE_API_BASE=https://meal.example.com`；
3. 如果 H5 和 API 不同域，把 H5 的域名加到后端 `CORS_ALLOWED_ORIGINS`。

## 4. 手动触发 / 回滚

- 手动重新部署：GitHub → Actions → Release images → **Run workflow**。
- 回滚：在 `.env` 里把 `API_IMAGE` 固定到某个版本或短 SHA，例如 `ghcr.io/zzwen6/family-meal-api:0.1.0`，再 `docker compose up -d`。（固定 tag 后 Watchtower 只会在该 tag 更新时才动。）
- 发新版本号：改 `pom.xml` 里的 `<version>`，镜像会多打一个对应版本的 tag。

## 5. 本地构建镜像（可选）

Dockerfile 默认用国内镜像源（阿里云 Maven、npmmirror），适合在 NAS 上直接构建：

```bash
docker build -t family-meal-api:local .
# .env 里设 API_IMAGE=family-meal-api:local
```

GitHub Actions 在海外，会通过 `--build-arg MAVEN_MIRROR=https://repo.maven.apache.org/maven2 NPM_REGISTRY=https://registry.npmjs.org/` 换回官方源。
