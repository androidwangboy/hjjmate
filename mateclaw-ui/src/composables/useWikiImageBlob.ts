import { onBeforeUnmount, onMounted } from 'vue'
import { fetchAuthenticatedBlob } from '@/api/index'
import { stripCtx } from '@/utils/appPaths'

// 修改人:祝奔；时间：2026-11-18T09:27:23.022Z；修改原因/内容：新增 Wiki 内嵌图片鉴权加载 composable，把 /hjjmate/wiki-images/{id}.{ext} 引用
// 通过带 JWT + X-Workspace-Id 的 /api/v1/wiki/images/{id} 请求换成 blob URL 显示；代码来源：AI生成

const WIKI_IMAGE_RE = /^\/(?:hjjmate\/)?wiki-images\/(\d+)\.[a-zA-Z0-9]+$/

/** 把 Wiki 图片引用（可能带 context 前缀）转成鉴权 API 路径。 */
export function wikiImageApiPath(src: string): string | null {
  try {
    const url = new URL(src, window.location.href)
    const match = WIKI_IMAGE_RE.exec(stripCtx(url.pathname))
    return match ? `/api/v1/wiki/images/${match[1]}` : null
  } catch {
    return null
  }
}

export function useWikiImageBlob() {
  const objectUrls = new Set<string>()
  let observer: MutationObserver | null = null

  async function loadImage(img: HTMLImageElement) {
    if (img.dataset.wikiImageLoaded === '1' || img.dataset.wikiImageLoading === '1') return
    const original = img.dataset.wikiImageSrc || img.getAttribute('src') || ''
    const apiPath = wikiImageApiPath(original)
    if (!apiPath) return
    img.dataset.wikiImageSrc = original
    img.dataset.wikiImageLoading = '1'
    try {
      const blob = await fetchAuthenticatedBlob(apiPath)
      const objectUrl = URL.createObjectURL(blob)
      objectUrls.add(objectUrl)
      img.src = objectUrl
      img.dataset.wikiImageLoaded = '1'
      // 修改人:祝奔；时间：2026-11-18T10:20:45.001Z；修改原因/内容：blob 异步替换 src 后通知灯箱刷新其 URL 列表，避免灯箱仍持有未鉴权的原始引用；代码来源：AI生成
      window.dispatchEvent(new CustomEvent('wiki-image-loaded', { detail: { img } }))
    } catch (e) {
      console.warn('[useWikiImageBlob] Failed to load wiki image:', original, e)
    } finally {
      delete img.dataset.wikiImageLoading
    }
  }

  function scan(root: ParentNode = document) {
    root.querySelectorAll<HTMLImageElement>('img[data-wiki-image]').forEach(loadImage)
  }

  onMounted(() => {
    scan()
    observer = new MutationObserver((mutations) => {
      for (const mutation of mutations) {
        mutation.addedNodes.forEach((node) => {
          if (node instanceof HTMLImageElement && node.matches('img[data-wiki-image]')) {
            loadImage(node)
          } else if (node instanceof HTMLElement) {
            scan(node)
          }
        })
      }
    })
    observer.observe(document.body, { childList: true, subtree: true })
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
    observer = null
    objectUrls.forEach((url) => URL.revokeObjectURL(url))
    objectUrls.clear()
  })

  return { scan }
}
