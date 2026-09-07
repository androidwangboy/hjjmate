/**
 * 应用部署前缀（context path）工具。
 *
 * 当应用以 `server.servlet.context-path=/hjjmate`（前端 Vite `base=/hjjmate/`）
 * 部署时，所有发给本后端的绝对路径请求（/api、/logo、/icons、/skill-assets
 * 以及后端返回的同源下载/资源链接）都必须带 `/hjjmate` 前缀，否则会被
 * nginx / Spring 404。
 *
 * `import.meta.env.BASE_URL` 跟随 Vite base：
 *   - base=`/hjjmate/`  → ctxPath=`/hjjmate`，withCtx('/api/v1/...') 会加前缀；
 *   - base=`/`（传统根路径部署）→ ctxPath=``，withCtx 为原样透传。
 */

/** Vite base，恒以 `/` 结尾（`/hjjmate/` 或 `/`）。 */
export const appBase: string = import.meta.env.BASE_URL

/** context path，不含结尾斜杠；根路径部署时为 ''。 */
export const ctxPath: string =
  appBase.length > 1 ? appBase.replace(/\/+$/, '') : ''

/**
 * 给根相对路径补上 context 前缀。协议相对（`//`）、绝对 URL、以及已经带
 * 前缀的路径原样返回。
 */
export function withCtx(path: string): string {
  if (!path || path[0] !== '/' || path.startsWith('//') || !ctxPath) return path
  return path === ctxPath || path.startsWith(`${ctxPath}/`)
    ? path
    : `${ctxPath}${path}`
}

/** 去掉 context 前缀，返回纯净的根相对路径（供正则匹配等使用）。 */
export function stripCtx(path: string): string {
  if (ctxPath && path.startsWith(`${ctxPath}/`)) {
    return path.slice(ctxPath.length)
  }
  return path === ctxPath ? '/' : path
}

/** 静态品牌 logo 地址。 */
export const appLogo = `${ctxPath}/logo/hjjmate_logo.png`
