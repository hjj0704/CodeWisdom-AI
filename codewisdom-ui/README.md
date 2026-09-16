# CodeWisdom UI（Vue3）

阶段 10 前端：Vue3 + Vite + Element Plus + Pinia + Axios。

## 开发（本机不启中间件）

1. 确认 ECS 网关可达：`http://47.93.158.48:8080`
2. 复制环境变量：`cp .env.example .env.development`（默认已指向 ECS）
3. 安装依赖：`npm install`
4. 启动：`npm run dev` → 浏览器打开 http://localhost:5173

Vite 会把 `/api/*` 代理到 `VITE_API_PROXY_TARGET`（默认 47.93.158.48:8080）。

## 构建

```bash
npm run build
```

产物在 `dist/`，可部署到 Nginx（T-1104）。

## 当前页面

| 路由 | 任务 | 说明 |
|---|---|---|
| `/import` | T-1002 | Git / ZIP 导入，调用 `project-resource` API |
