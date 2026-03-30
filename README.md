# Thunder Note Server

*为闪记提供认证、内容、消息、文件与最小同步闭环的后端服务。*

---

## 目录

- [简介](#简介)
- [特性](#特性)
- [当前实现状态](#当前实现状态)
- [架构](#架构)
- [快速开始](#快速开始)
- [Docker 部署](#docker-部署)
- [运行配置](#运行配置)
- [开发指南](#开发指南)
- [项目结构](#项目结构)
- [常见问题和解答](#常见问题和解答)

---

## 简介

`thunder-note-server` 是 Thunder Note 当前真实后端实现，面向 Android Java 客户端提供统一 API、认证鉴权、内容存储和文件上传能力。

当前后端基于 `Spring Boot 3`、`Java 21`、`MyBatis-Plus`、`PostgreSQL`、`Redis`、`Flyway`、`JWT` 和 `MinIO` 构建，采用 `controller -> service -> mapper/entity` 的常见分层方式。

这个仓库描述的是当前已经落地的服务端事实，不等同于文档中的完整目标态。当前真实接口前缀为 `/api/...`，不是历史文档中常见的 `/api/v1/...`。

## 特性

- 用户认证：支持注册、登录、刷新令牌、登出、密码修改和手势锁密文备份。
- 用户资料：支持资料读取、资料更新、头像更新和联系人关系管理。
- 闪记主线：支持闪记创建、编辑、删除、搜索、置顶、隐藏和收集箱语义。
- 消息能力：支持闪记消息、联系人会话、消息删除、批量删除、复合卡片与 SSE 流式消息入口。
- 合集能力：支持合集列表、创建、编辑和删除。
- 收藏能力：支持消息收藏列表与新增/取消收藏。
- 文件能力：支持文件上传与下载，兼容图片、视频、音频、PDF 等主线媒体类型。
- 同步闭环：提供 `bootstrap`、`pull`、`push` 三个基础同步接口，支撑当前最小联调链路。
- 数据迁移：使用 `Flyway` 管理数据库 schema，迁移脚本是当前数据库事实来源。
- 统一响应：大多数 JSON 接口使用 `{ code, message, data, timestamp }` 响应结构。

## 当前实现状态

- 当前仓库是已投入联调使用的后端，而不是纯设计骨架。
- 当前接口真实前缀为 `/api/...`，不是 `/api/v1/...`。
- 当前登录请求使用 `username` 字段，不是邮箱登录。
- `sync` 已形成最小可用闭环，但还不能等同于“完整离线同步协议”。
- 当前已实现主模块包括 `auth`、`user`、`flashnote`、`message`、`collection`、`favorite`、`file`、`sync`、`search`。
- `system`、`admin` 等能力在当前后端中仍未形成真实实现。

## 架构

### 技术栈

- `Java 21`
- `Spring Boot 3.2.12`
- `MyBatis-Plus 3.5.6`
- `PostgreSQL`
- `Redis`
- `Flyway`
- `JWT`
- `MinIO`

### 分层结构

- `controller`：定义真实 HTTP 接口和请求入口。
- `service`：承载业务逻辑。
- `mapper/entity`：承载数据库映射与持久化。
- `dto`：承载接口请求和响应对象。
- `common`：统一响应、异常、安全配置与通用能力。

### 当前主要接口分组

- `/api/auth`：认证、登出、密码与手势锁备份。
- `/api/users`：资料、头像、联系人与好友请求。
- `/api/flash-notes`：闪记列表、搜索、创建、编辑、删除、置顶与隐藏。
- `/api/messages`：消息列表、发送、删除、批量删除、清空收集箱与消息流。
- `/api/collections`：合集 CRUD。
- `/api/favorites`：消息收藏列表与收藏管理。
- `/api/files`：上传与下载。
- `/api/sync`：`bootstrap`、`pull`、`push`。

## 快速开始

### 环境要求

- `JDK 21`
- `Maven 3.9+`
- `PostgreSQL 14+` 或兼容版本
- `Redis`
- `MinIO`

### 1. 准备依赖服务

默认本地配置如下：

- PostgreSQL：`localhost:15432`
- Redis：`localhost:6379`
- MinIO：`http://localhost:9000`

数据库默认连接名为 `thunder_note`，Flyway 会在应用启动时自动执行迁移。

### 2. 启动服务

```bash
mvn clean compile
mvn spring-boot:run
```

默认启动端口为 `8080`。

### 3. 运行测试

```bash
mvn test
```

## Docker 部署

当前仓库已经提供 `Dockerfile` 和 `docker-compose.yml` 示例，推荐优先使用 `Docker Compose` 启动完整本地环境。

如果你希望把端口、密码、JWT 密钥等配置从 `compose` 文件中抽离出来，仓库还提供了 `.env.docker.example` 模板。

### 方式一：使用 Docker Compose 一键启动

仓库内已包含：

- `Dockerfile`
- `docker-compose.yml`
- `.env.docker.example`

其中 `docker-compose.yml` 会同时启动：

- `postgres`
- `redis`
- `minio`
- `minio-init`
- `server`

#### 0. 准备环境变量文件

```bash
cp .env.docker.example .env
```

建议至少修改：

- `NOTE_JWT_SECRET`
- `POSTGRES_PASSWORD`
- `MINIO_ROOT_PASSWORD`

如果本机端口已被占用，也可以在 `.env` 里调整：

- `SERVER_PORT`
- `POSTGRES_HOST_PORT`
- `REDIS_HOST_PORT`
- `MINIO_API_HOST_PORT`
- `MINIO_CONSOLE_HOST_PORT`

#### 1. 启动完整环境

```bash
docker compose up -d --build
```

#### 2. 检查容器状态

```bash
docker compose ps
```

正常情况下应看到以下服务：

- `thunder-note-postgres`
- `thunder-note-redis`
- `thunder-note-minio`
- `thunder-note-server`

`minio-init` 是一次性初始化容器，用于自动创建 `thunder-note` bucket，执行完成后退出属于正常现象。

此外，`compose` 现在已经包含 `healthcheck`：

- `postgres` 会等待数据库真正可连接
- `redis` 会等待 `PING` 返回成功
- `minio` 会等待健康检查接口可用
- `server` 会等待 `/actuator/health` 返回成功

`server` 只会在 PostgreSQL、Redis、MinIO 真正就绪后再启动。

#### 3. 验证服务

```bash
curl http://localhost:8080/actuator/health
```

#### 4. 停止环境

```bash
docker compose down
```

如果还需要同时删除持久化卷：

```bash
docker compose down -v
```

### 方式二：分别启动中间件和应用容器

### 0. 先启动外部依赖中间件

当前服务依赖以下中间件：

- PostgreSQL：主数据库
- Redis：缓存与会话相关能力
- MinIO：对象存储

如果本机还没有这些服务，可以先用 Docker 启动。

#### PostgreSQL

```bash
docker run -d \
  --name thunder-note-postgres \
  -e POSTGRES_DB=thunder_note \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 15432:5432 \
  -v thunder-note-postgres-data:/var/lib/postgresql/data \
  postgres:17
```

说明：

- 容器内端口为 `5432`
- 宿主机映射为 `15432`
- 这与当前 `application.yml` 的默认数据库端口保持一致

#### Redis

```bash
docker run -d \
  --name thunder-note-redis \
  -p 6379:6379 \
  -v thunder-note-redis-data:/data \
  redis:7-alpine redis-server --appendonly yes
```

#### MinIO

```bash
docker run -d \
  --name thunder-note-minio \
  -p 9000:9000 \
  -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  -v thunder-note-minio-data:/data \
  minio/minio server /data --console-address ":9001"
```

启动后可通过 `http://localhost:9001` 打开 MinIO Console，使用下面的默认账号登录：

- 用户名：`minioadmin`
- 密码：`minioadmin`

然后手动创建一个 bucket：`thunder-note`。

#### 检查中间件状态

```bash
docker ps
```

应至少看到以下三个容器处于运行状态：

- `thunder-note-postgres`
- `thunder-note-redis`
- `thunder-note-minio`

### 1. 先打包应用

```bash
mvn clean package -DskipTests
```

打包完成后，应用产物通常位于 `target/thunder-note-server-0.0.1-SNAPSHOT.jar`。

### 2. 在仓库根目录创建 `Dockerfile`

示例内容如下：

```dockerfile
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/thunder-note-server-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### 3. 构建镜像

```bash
docker build -t thunder-note-server:local .
```

### 4. 启动容器

如果你的 PostgreSQL、Redis、MinIO 都运行在宿主机 Docker 中，可以直接通过环境变量覆盖默认配置：

```bash
docker run -d \
  --name thunder-note-server \
  -p 8080:8080 \
  -e NOTE_DB_HOST=host.docker.internal \
  -e NOTE_DB_PORT=15432 \
  -e NOTE_DB_NAME=thunder_note \
  -e NOTE_DB_USERNAME=postgres \
  -e NOTE_DB_PASSWORD=postgres \
  -e NOTE_REDIS_HOST=host.docker.internal \
  -e NOTE_REDIS_PORT=6379 \
  -e NOTE_MINIO_ENDPOINT=http://host.docker.internal:9000 \
  -e NOTE_MINIO_ACCESS_KEY=minioadmin \
  -e NOTE_MINIO_SECRET_KEY=minioadmin \
  -e NOTE_MINIO_BUCKET=thunder-note \
  -e NOTE_JWT_SECRET=replace-with-a-random-secret \
  -e NOTE_CORS_ORIGINS=http://localhost:* \
  thunder-note-server:local
```

Linux 环境下如果 `host.docker.internal` 不可用，可以改成宿主机实际 IP，或在运行时增加：

```bash
--add-host=host.docker.internal:host-gateway
```

### 5. 使用 `.env` 文件启动

仓库提供了 `.env.example` 可作为模板。复制后按实际环境修改：

```bash
cp .env.example .env
```

然后通过 `--env-file` 启动：

```bash
docker run -d \
  --name thunder-note-server \
  --env-file .env \
  -p 8080:8080 \
  --add-host=host.docker.internal:host-gateway \
  thunder-note-server:local
```

如果你的 `.env` 使用的是仓库默认模板，建议至少确认以下字段：

- `NOTE_DB_HOST=host.docker.internal`
- `NOTE_DB_PORT=15432`
- `NOTE_REDIS_HOST=host.docker.internal`
- `NOTE_MINIO_ENDPOINT=http://host.docker.internal:9000`
- `NOTE_MINIO_BUCKET=thunder-note`
- `NOTE_JWT_SECRET` 已替换为随机密钥

### 6. 验证服务

启动成功后，可先检查健康接口：

```bash
curl http://localhost:8080/actuator/health
```

如果返回健康状态，再继续使用 Android 客户端或其他调用方联调。

### 7. 可选：一键清理测试环境

如果只是本地测试，停止并删除容器可以使用：

```bash
docker rm -f thunder-note-server thunder-note-postgres thunder-note-redis thunder-note-minio
```

如果还需要删除持久化数据卷：

```bash
docker volume rm thunder-note-postgres-data thunder-note-redis-data thunder-note-minio-data
```

## 运行配置

主要配置位于 `src/main/resources/application.yml`，支持通过环境变量覆盖：

- `SERVER_PORT`
- `NOTE_DB_HOST`
- `NOTE_DB_PORT`
- `NOTE_DB_NAME`
- `NOTE_DB_USERNAME`
- `NOTE_DB_PASSWORD`
- `NOTE_REDIS_HOST`
- `NOTE_REDIS_PORT`
- `NOTE_MINIO_ENDPOINT`
- `NOTE_MINIO_ACCESS_KEY`
- `NOTE_MINIO_SECRET_KEY`
- `NOTE_MINIO_BUCKET`
- `NOTE_JWT_SECRET`
- `NOTE_CORS_ORIGINS`
- `NOTE_LOG_LEVEL`

示例：

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:postgresql://localhost:15432/thunder_note --spring.datasource.username=postgres --spring.datasource.password=postgres"
```

## 开发指南

- 真实接口事实优先看 `controller`，不要直接按照旧文档中的 `/api/v1/...` 推断。
- 数据库结构事实优先看 `src/main/resources/db/migration/` 中的 Flyway 脚本。
- 修改接口时，除了核对 controller 和 service，也应同步关注 Android 端 `data/remote` 是否发生漂移。
- 默认统一响应结构为 `{ code, message, data, timestamp }`，不要绕开 `ApiResponse` 与全局异常处理。
- 当前测试已具备一定基础，但仍不能假设所有主线都已被完整覆盖，提交前建议至少执行一次编译和相关测试。

## 项目结构

```text
thunder-note-server/
├── src/main/java/com/flashnote/
│   ├── auth/
│   ├── collection/
│   ├── common/
│   ├── favorite/
│   ├── file/
│   ├── flashnote/
│   ├── message/
│   ├── sync/
│   ├── user/
│   └── FlashNoteApplication.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
└── src/test/java/
```

## 常见问题和解答

<!-- 预留 -->
