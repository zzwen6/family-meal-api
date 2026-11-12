# syntax=docker/dockerfile:1
#
# 家庭点餐后端 + 管理后台，一个镜像。
# 默认使用国内镜像源（方便在 NAS / 国内机器本地构建）；GitHub Actions 里会用 build-arg 换回官方源。
#
#   docker build -t family-meal-api:local .
#   docker build --build-arg MAVEN_MIRROR=https://repo.maven.apache.org/maven2 \
#                --build-arg NPM_REGISTRY=https://registry.npmjs.org/ -t family-meal-api:local .

# ---------- 1. 构建管理后台（Vue 3 + Vite） ----------
FROM node:20-alpine AS admin
ARG NPM_REGISTRY=https://registry.npmmirror.com/
WORKDIR /build/admin-web
COPY admin-web/package.json admin-web/package-lock.json ./
RUN npm config set registry "${NPM_REGISTRY}" && npm ci --no-audit --no-fund
COPY admin-web/ ./
# vite.config.js 的 outDir 指向 ../src/main/resources/static/admin
RUN mkdir -p /build/src/main/resources/static && npm run build

# ---------- 2. 构建 Spring Boot jar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MAVEN_MIRROR=https://maven.aliyun.com/repository/public
WORKDIR /build
RUN mkdir -p /root/.m2 && printf '%s\n' \
    '<settings><mirrors><mirror>' \
    '<id>build-mirror</id><mirrorOf>central</mirrorOf>' \
    "<url>${MAVEN_MIRROR}</url>" \
    '</mirror></mirrors></settings>' > /root/.m2/settings.xml
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2/repository mvn -B -q dependency:go-offline
COPY src ./src
COPY --from=admin /build/src/main/resources/static/admin ./src/main/resources/static/admin
# 测试依赖 Docker（Testcontainers），镜像构建里跳过；请在本地 / CI 单独运行 mvn test
RUN --mount=type=cache,target=/root/.m2/repository mvn -B -q -DskipTests package \
    && cp target/family-meal-api.jar /build/app.jar

# ---------- 3. 运行时 ----------
FROM eclipse-temurin:21-jre
ARG PUID=1000
ARG PGID=1000
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SERVER_PORT=8080
# 以非 root 用户运行；基础镜像自带 ubuntu(1000) 用户时先移除，避免 UID 冲突
RUN set -eux; \
    if getent passwd ubuntu >/dev/null; then userdel -r ubuntu || true; fi; \
    if ! getent group "${PGID}" >/dev/null; then groupadd -g "${PGID}" app; fi; \
    useradd -u "${PUID}" -g "${PGID}" -M -d /app -s /usr/sbin/nologin app; \
    mkdir -p /app /data/media && chown "${PUID}:${PGID}" /app /data/media
WORKDIR /app
COPY --from=build --chown=${PUID}:${PGID} /build/app.jar /app/app.jar
USER ${PUID}:${PGID}
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/actuator/health" >/dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
