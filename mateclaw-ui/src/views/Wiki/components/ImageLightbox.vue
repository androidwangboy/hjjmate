<template>
  <el-image-viewer
    v-if="open"
    :url-list="urls"
    :initial-index="activeIndex"
    :hide-on-click-modal="true"
    :teleported="true"
    @close="close"
  />
</template>

<script setup lang="ts">
/**
 * Lightbox overlay for inline wiki page images.
 *
 * <p>The component is mounted once per wiki page view and exposes a
 * single imperative entry point — {@link attach} — that the parent
 * calls after a markdown render lands. attach() walks every <img>
 * inside the supplied container, attaches a click handler that opens
 * the lightbox at the image's index, applies the cursor cue, and
 * marks the element as bound so subsequent attach() calls are
 * idempotent.
 *
 * <p>Element Plus ships {@code ElImageViewer} as a teleported
 * fullscreen overlay; the heavyweight scroll/zoom UI comes from
 * upstream so this component stays a thin coordinator.
 *
 * <p>修改人:祝奔；时间：2026-11-18T10:20:45.001Z；修改原因/内容：Wiki 图片的 src 会被 useWikiImageBlob 异步换成鉴权 blob URL，
 * 因此不再在 attach 时快照 src，而是在点击时实时读取当前 src；同时监听 wiki-image-loaded 事件刷新 URL，保证弹框显示的是可访问的 blob URL；代码来源：AI生成
 */
import { ref, onBeforeUnmount } from 'vue'

const open = ref(false)
const urls = ref<string[]>([])
const activeIndex = ref(0)

const BOUND_FLAG = 'wikiLightboxBound'
let boundContainer: HTMLElement | null = null

function close() {
  open.value = false
}

/** 实时收集容器内所有 img 的当前 src（含已被替换的 blob URL）。 */
function collectUrls(container: HTMLElement | null): string[] {
  if (!container) return []
  return Array.from(container.querySelectorAll('img'))
    .map(img => img.getAttribute('src') || '')
    .filter((u): u is string => !!u && u.length > 0)
}

/** blob 图片加载完成后刷新 URL 列表；仅当弹框打开时更新，避免无谓计算。 */
function onWikiImageLoaded() {
  if (!open.value || !boundContainer) return
  urls.value = collectUrls(boundContainer)
}

window.addEventListener('wiki-image-loaded', onWikiImageLoaded)
onBeforeUnmount(() => {
  window.removeEventListener('wiki-image-loaded', onWikiImageLoaded)
  boundContainer = null
})

/**
 * Walks all <img> elements in {@code container} and binds click → open
 * lightbox. Safe to call repeatedly: already-bound elements are skipped.
 *
 * @param container any HTMLElement that holds rendered markdown
 */
function attach(container: HTMLElement | null) {
  if (!container) return
  boundContainer = container
  const images = Array.from(container.querySelectorAll('img'))
  if (images.length === 0) return

  urls.value = collectUrls(container)

  images.forEach((img) => {
    if (img.dataset[BOUND_FLAG] === 'true') return
    img.dataset[BOUND_FLAG] = 'true'
    img.style.cursor = 'zoom-in'
    img.addEventListener('click', (event) => {
      event.preventDefault()
      // 点击时按当前 DOM 顺序实时取 src —— 此时 blob 通常已加载完成，
      // 拿到的是可访问的 blob URL 而非无鉴权的 /hjjmate/wiki-images/... 引用。
      const current = boundContainer
        ? Array.from(boundContainer.querySelectorAll('img'))
        : images
      // 只保留有 src 的图片，并据此计算被点击图片的索引，保证索引与 url-list 严格对齐。
      const withSrc = current.filter(node => !!node.getAttribute('src'))
      urls.value = withSrc.map(node => node.getAttribute('src') as string)
      activeIndex.value = Math.max(0, withSrc.indexOf(img))
      open.value = true
    })
  })
}

defineExpose({ attach })
</script>
