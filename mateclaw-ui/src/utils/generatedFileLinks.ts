/**
 * Rewrites bare generated-file download URLs in assistant text into
 * `[filename](url)` markdown links, so the chat renders the file name instead
 * of the raw UUID URL.
 *
 * The backend does the same rewrite before persisting the final answer; this
 * client-side pass covers the live-streamed bubble, whose text arrived as raw
 * deltas before persistence. File names come from `metadata.generatedFiles`,
 * which the server extracts from tool results during the same turn.
 */

import { ctxPath } from '@/utils/appPaths'

// context path（如 /hjjmate）作为可选前缀参与匹配，兼容带 /hjjmate 前缀的
// 相对链接与根路径部署（ctxPath=''）两种形态；绝对 URL 的前缀会被
// `https?:...` 那段吞掉，无需额外处理。
const escapeRegExp = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const CTX_OPT = ctxPath ? `(?:${escapeRegExp(ctxPath)})?` : ''
const GENERATED_URL_RE = new RegExp(
  `(\\]\\(|<)?(?:https?:\\/\\/[^\\s)\\]<>]+)?${CTX_OPT}\\/api\\/v1\\/files\\/generated\\/([a-zA-Z0-9-]+)`,
  'g',
)
const GENERATED_ID_RE = new RegExp(
  `${CTX_OPT}\\/api\\/v1\\/files\\/generated\\/([a-zA-Z0-9-]+)`,
)

export interface GeneratedFileRef {
  name?: string
  url?: string
}

/** Accept only browser-safe external URLs or absolute same-origin paths. */
export function isSafeFileUrl(value: unknown): value is string {
  if (typeof value !== 'string' || !value || /[\u0000-\u001f\u007f]/.test(value)) return false
  if (value.startsWith('/')) return !value.startsWith('//')
  if (!/^https?:\/\//i.test(value)) return false
  try {
    const url = new URL(value)
    return (url.protocol === 'http:' || url.protocol === 'https:') && !!url.hostname
  } catch {
    return false
  }
}

/** Build an id → display-name map from `metadata.generatedFiles`. */
export function buildGeneratedFileNameMap(files: unknown): Map<string, string> {
  const names = new Map<string, string>()
  if (!Array.isArray(files)) return names
  for (const f of files as GeneratedFileRef[]) {
    const m = GENERATED_ID_RE.exec(String(f?.url || ''))
    if (m && f?.name) names.set(m[1], String(f.name))
  }
  return names
}

/**
 * Wrap bare generated-file URLs whose id has a known name into
 * `[name](url)`. URLs already serving as a markdown link destination
 * (preceded by `](`) or angle-bracket autolinks (`<url>`) are left as-is.
 */
export function linkifyGeneratedFileUrls(text: string, names: Map<string, string>): string {
  if (!text || !names.size || !text.includes('/api/v1/files/generated/')) return text
  return text.replace(GENERATED_URL_RE, (full, prefix: string | undefined, id: string) => {
    if (prefix) return full
    const name = names.get(id)
    if (!name) return full
    return `[${name.replace(/[[\]]/g, '')}](${full})`
  })
}
