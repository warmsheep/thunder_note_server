# 闪记 Web 用户端（thunder-note-web）

闪记 Web 用户端的前端工程。按当前决策，本工程与 `thunder-note-server` 同仓承载，构建产物由后端 Spring Boot 静态资源托管，与 Android 共享 `/api/...` 后端契约。

## 技术路线

- Vue 3 + Vite + Pinia + vue-router
- 同源访问后端：浏览器入口 `/web/`，API 默认 `/api/...`
- 不引入额外服务端，统一由 `thunder_note_server` 这一个 Spring Boot 服务承载页面与 API

## 目录约定

- `src/api/`：统一 `apiClient` 与各模块 API 封装
- `src/router/`：vue-router 路由
- `src/stores/`：Pinia store
- `src/views/`：页面级组件
- `src/components/`：可复用组件
- `src/styles/`：全局样式

## 本地开发

需要 Node 18+ 与 npm 9+。

```bash
cd thunder_note_server/frontend
npm install
npm run dev
```

dev 模式默认起在 `http://localhost:5173/web/`，并通过 Vite 代理把 `/api` 转发到 `http://localhost:8080`。本地需要先按 `thunder-note-server/README.md` 启动后端与中间件。

## 生产构建

```bash
cd thunder_note_server/frontend
npm install
npm run build
```

构建产物会输出到 `../src/main/resources/static/web/`，由后端打包到同一个 jar 中。后端启动后通过 `http://<host>:<port>/web/` 访问。

## 与后端的边界

- 浏览器请求 `/api/**` 仍由现有 controller 处理，不引入 `/api/v1`
- Web 静态资源与前端路由 fallback 仅匹配 `/web/**`，不会吞掉 `/api/**`、`/actuator/**`
- 登录字段使用 `username`，统一响应结构 `{ code, message, data, timestamp }`

## 维护规则

- 新增 API 调用前，先核对 `thunder-note-server` 真实 controller 与 `docs/API接口设计.md`
- 完成功能模块后，按项目永久规则执行：代码审查 → 安全审查 → 测试，三关全部通过才视为完成
- 完成后回写 `docs/完整开发计划.md` 中 `D1.6` 对应任务状态
