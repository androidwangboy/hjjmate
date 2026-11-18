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
 * Lightbox overlay for inline images (wiki page body / chat answer body).
 *
 * <p>Two modes:
 * <ul>
 *   <li>Default (manual): the parent calls {@link attach} after each markdown
 *       render; used by the wiki page viewer.</li>
 *   <li>Delegate mode ({@code delegate=true}): a single document-level click
 *       listener plus a MutationObserver handle dynamically streamed chat
 *       messages. Images are scoped by {@code selector} (e.g. only markdown
 *       bodies inside assistant bubbles) so avatars/tool icons are excluded.</li>
 * </ul>
 *
 * <p>Element Plus ships {@code ElImageViewer} as a teleported fullscreen
 * overlay; the heavyweight scroll/zoom UI comes from upstream so this
 * component stays a thin coordinator.
 *
 * <p>修改人:祝奔；时间：2026-11-18T10:20:45.001Z；修改原因/内容：Wiki 图片的 src 会被 useWikiImageBlob 异步换成鉴权 blob URL，
 * 因此不再在 attach 时快照 src，而是在点击时实时读取当前 src；同时监听 wiki-image-loaded 事件刷新 URL，保证弹框显示的是可访问的 blob URL；代码来源：AI生成
 *
 * <p>修改人:祝奔；时间：2026-11-18T12:05:00.000Z；修改原因/内容：新增委托模式与图片选择器，使会话回答中的 markdown 图片也能点击放大缩小（与知识库一致）；代码来源：AI生成
 */
import { ref, onBeforeUnmount, onMounted } from 'vue'

const props = withDefaults(defineProps<{
  /** 需要纳入查看器的图片选择器；知识库默认全部 img，聊天传正文图片选择器 */
  selector?: string
  /** 委托模式：监听 document 上的点击与 DOM 变化，适合流式动态渲染的聊天消息 */
  delegate?: boolean
}>(), {
  selector: 'img',
  delegate: false,
})

const open = ref(false)
const urls = ref<string[]>([])
const activeIndex = ref(0)

// 修改人:祝奔；时间：2026-11-18T12:05:00.000Z；修改原因/内容：委托模式与知识库手动模式使用不同的绑定标记，避免共用 dataset key 互相干扰；代码来源：AI生成
const BOUND_FLAG = 'wikiLightboxBound'
const DELEGATE_BOUND_FLAG = 'chatLightboxBound'
let boundContainer: HTMLElement | null = null

function close() {
  open.value = false
}

/** 实时收集容器内所有匹配选择器的图片当前 src（含已被替换的 blob URL）。 */
function collectUrls(container: ParentNode | null): string[] {
  if (!container) return []
  return Array.from(container.querySelectorAll<HTMLImageElement>(props.selector))
    .map(img => img.getAttribute('src') || '')
    .filter((u): u is string => !!u && u.length > 0)
}

/** blob 图片加载完成后刷新 URL 列表；仅当弹框打开时更新，避免无谓计算。 */
function onWikiImageLoaded() {
  if (!open.value || !boundContainer) return
  urls.value = collectUrls(boundContainer)
}

window.addEventListener('wiki-image-loaded', onWikiImageLoaded)

/**
 * Walks all matching images in {@code container} and binds click → open
 * lightbox. Safe to call repeatedly: already-bound elements are skipped.
 *
 * @param container any HTMLElement that holds rendered markdown
 */
function attach(container: HTMLElement | null) {
  if (!container) return
  boundContainer = container
  const images = Array.from(container.querySelectorAll<HTMLImageElement>(props.selector))
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
        ? Array.from(boundContainer.querySelectorAll<HTMLImageElement>(props.selector))
        : images
      // 只保留有 src 的图片，并据此计算被点击图片的索引，保证索引与 url-list 严格对齐。
      const withSrc = current.filter(node => !!node.getAttribute('src'))
      urls.value = withSrc.map(node => node.getAttribute('src') as string)
      activeIndex.value = Math.max(0, withSrc.indexOf(img))
      open.value = true
    })
  })
}

// ===== 委托模式（聊天流式渲染） =====
// 修改人:祝奔；时间：2026-11-18T12:05:00.000Z；修改原因/内容：流式消息的图片会随 content_delta 动态插入，手动 attach 覆盖不到；改用 document 级事件委托 + 光标标记；代码来源：AI生成
let docClickHandler: ((event: MouseEvent) => void) | null = null
let delegateObserver: MutationObserver | null = null

/** 给匹配选择器的图片加放大光标，dataset 标记保证幂等。 */
function markZoomable(root: ParentNode) {
  root.querySelectorAll<HTMLImageElement>(props.selector).forEach((img) => {
    if (img.dataset[DELEGATE_BOUND_FLAG] === 'true') return
    img.dataset[DELEGATE_BOUND_FLAG] = 'true'
    img.style.cursor = 'zoom-in'
  })
}

function startDelegate() {
  markZoomable(document)
  // 修改人:祝奔；时间：2026-11-18T12:05:00.000Z；修改原因/内容：流式对话 DOM 变化极频繁，MutationObserver 只扫描新增节点而非全文档，避免每次 delta 全量查询；代码来源：AI生成
  delegateObserver = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      mutation.addedNodes.forEach((node) => {
        if (!(node instanceof Element)) return
        if (node instanceof HTMLImageElement && node.matches(props.selector)) {
          markZoomable(node.parentElement ?? document.body)
        } else {
          markZoomable(node)
        }
      })
    }
  })
  delegateObserver.observe(document.body, { childList: true, subtree: true })

  docClickHandler = (event: MouseEvent) => {
    const target = event.target
    if (!(target instanceof HTMLImageElement)) return
    // matches 支持后代选择器，可确保只处理选择器限定的正文图片，头像/图标不误入。
    if (!target.matches(props.selector)) return
    // 作用域取该图片所属的回答气泡：同一条回答内的图片可翻页浏览。
    const scope = target.closest('.msg-bubble') as HTMLElement | null
    if (!scope) return
    const imgs = Array.from(scope.querySelectorAll<HTMLImageElement>(props.selector))
      .filter(node => !!node.getAttribute('src'))
    if (imgs.length === 0) return
    event.preventDefault()
    event.stopPropagation()
    boundContainer = scope
    urls.value = imgs.map(node => node.getAttribute('src') as string)
    const idx = imgs.indexOf(target)
    activeIndex.value = idx >= 0 ? idx : 0
    open.value = true
  }
  // 捕获阶段处理，抢在 Markdown 里图片自带的点击行为之前。
  document.addEventListener('click', docClickHandler, true)
}

function stopDelegate() {
  if (docClickHandler) {
    document.removeEventListener('click', docClickHandler, true)
    docClickHandler = null
  }
  delegateObserver?.disconnect()
  delegateObserver = null
}

onMounted(() => {
  if (props.delegate) startDelegate()
})

onBeforeUnmount(() => {
  window.removeEventListener('wiki-image-loaded', onWikiImageLoaded)
  boundContainer = null
  stopDelegate()
})

defineExpose({ attach })
</script>
