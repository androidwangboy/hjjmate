# MateClaw Web UI

Web 控制台。默认以 `/hjjmate` 前缀部署：构建产物由后端以静态资源形式托管，
访问路径前缀与后端 `server.servlet.context-path` 保持一致，可经 nginx
`http://<ip>/hjjmate` 对外访问；也支持根路径部署。

## 本地开发

```bash
npm install && npm run dev    # http://localhost:5173
```
后端服务配置 server.servlet.context-path: /
dev 服务器已代理 `/` → `http://localhost:18088`（含 WebSocket），
后端开发模式在 `/` context-path 下工作时前端无需跨域。

## 生产构建

```bash
npm install -g pnpm@10 --silent
pnpm install --frozen-lockfile
# 默认按 /hjjmate 打包并输出到后端 static，访问 http://<ip>:18088/hjjmate/
pnpm exec vite build --outDir ../mateclaw-server/src/main/resources/static --emptyOutDir
```

根路径部署（后端 `application.yml` 需将 `server.servlet.context-path` 置空）：

```bash
VITE_BASE_URL=/ pnpm exec vite build --outDir ../mateclaw-server/src/main/resources/static --emptyOutDir
```

## 路径前缀约定

| 环节 | 位置 |
| --- | --- |
| 后端 context path | `mateclaw-server/src/main/resources/application.yml` → `server.servlet.context-path: /hjjmate` |
| 前端资源 base | `vite.config.ts` → `base: process.env.VITE_BASE_URL \|\| '/hjjmate/'` |
| HTML 内静态资源 | `index.html` 使用 `%BASE_URL%`（logo/favicon） |
| dev 代理 | `vite.config.ts`：`/hjjmate` → `http://localhost:18088`（保留 `/api`、`/skill-assets` 代理以兼容根路径部署） |

> 前端不硬编码路径前缀：统一通过 `src/utils/appPaths.ts`（`ctxPath` / `withCtx` /
> `stripCtx` / `appLogo`）推导。任何 root-relative 路径（`/api/...`、`/icons/...`、
> `/logo/...`、`/login`、WebSocket、SSE）都必须经 `withCtx`/`appLogo` 输出，
> 才能在 `/hjjmate` 与根路径两种部署间无缝切换。

## nginx 部署示例

MateClaw server 已把本 UI 打包进静态资源，并开启 REST/WebSocket/SSE，
所以一个 `location` 即可（**保留前缀转发，禁止 rewrite 去掉 `/hjjmate`**）：

```nginx
server {
    listen 80;
    server_name your-domain-or-ip;

    location /hjjmate/ {
        proxy_pass http://127.0.0.1:18088/hjjmate/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # SSE 流式输出
        proxy_buffering off;

        # WebSocket（/hjjmate/api/v1/chat/ws 等）
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
    }
}
```

若前端由独立静态服务器托管（未打进后端 static），拆成两个 location：

```nginx
location /hjjmate/ {
    alias /data/mateclaw-ui/dist/;          # 产物目录
    try_files $uri $uri/ /hjjmate/index.html; # SPA fallback
}

location /hjjmate/api/ {
    proxy_pass http://127.0.0.1:18088;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Upgrade $http_upgrade;      # WebSocket
    proxy_set_header Connection "upgrade";
    proxy_buffering off;                         # SSE
    proxy_read_timeout 3600s;
}
```
