// 修改人:祝奔；时间：2026-11-26T18:10:47+08:00；修改原因/内容：升级useThemeStore为统一外观状态中心，管理皮肤预设、底部壁纸、磨砂玻璃参数并保持现有明暗接口兼容；代码来源：AI生成
import { acceptHMRUpdate, defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import {
  DEFAULT_SKIN_ID,
  getBuiltinWallpaper,
  getSkinPreset,
  resolveBuiltinWallpaperUrl,
  type SkinId,
} from '@/config/skins'
import {
  clampNumber,
  DEFAULT_APPEARANCE_CONFIG,
  defaultAppearanceStorage,
  sanitizeAppearanceConfig,
  type AppearanceConfig,
  type CustomWallpaperMeta,
  type WallpaperType,
} from '@/utils/wallpaperStorage'

export type ThemeMode = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'mateclaw-theme'

export const useThemeStore = defineStore('theme', () => {
  function getInitialMode(): ThemeMode {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
    } catch { /* ignore */ }
    // 默认医疗冰蓝皮肤开箱采用通透浅色模式
    return 'light'
  }

  function resolveIsDark(targetMode: ThemeMode): boolean {
    if (targetMode === 'dark') return true
    if (targetMode === 'light') return false
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
  }

  const initialAppearance = sanitizeAppearanceConfig(defaultAppearanceStorage.loadConfig())

  const mode = ref<ThemeMode>(getInitialMode())
  const isDark = ref(resolveIsDark(mode.value))
  const skinId = ref<SkinId>(initialAppearance.skinId)
  const wallpaperType = ref<WallpaperType>(initialAppearance.wallpaperType)
  const wallpaperId = ref<string>(initialAppearance.wallpaperId)
  const surfaceOpacity = ref<number>(initialAppearance.surfaceOpacity)
  const wallpaperBlur = ref<number>(initialAppearance.wallpaperBlur)
  const scrimOpacity = ref<number>(initialAppearance.scrimOpacity)

  const customWallpapers = ref<CustomWallpaperMeta[]>([])
  const customWallpaperObjectUrl = ref<string>('')
  const wallpaperLoading = ref<boolean>(false)
  const drawerOpen = ref<boolean>(false)

  const activeSkin = computed(() => getSkinPreset(skinId.value))

  const resolvedWallpaperUrl = computed(() => {
    if (wallpaperType.value === 'builtin') {
      return resolveBuiltinWallpaperUrl(wallpaperId.value)
    }
    if (wallpaperType.value === 'custom') {
      return customWallpaperObjectUrl.value
    }
    return ''
  })

  const hasWallpaper = computed(() => wallpaperType.value !== 'none' && Boolean(resolvedWallpaperUrl.value))

  function persistAppearance() {
    const config: AppearanceConfig = sanitizeAppearanceConfig({
      skinId: skinId.value,
      wallpaperType: wallpaperType.value,
      wallpaperId: wallpaperId.value,
      surfaceOpacity: surfaceOpacity.value,
      wallpaperBlur: wallpaperBlur.value,
      scrimOpacity: scrimOpacity.value,
    })
    defaultAppearanceStorage.saveConfig(config)
  }

  function revokeCustomObjectUrl() {
    if (customWallpaperObjectUrl.value && typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') {
      URL.revokeObjectURL(customWallpaperObjectUrl.value)
    }
    customWallpaperObjectUrl.value = ''
  }

  function applyAppearanceToDom() {
    if (typeof document === 'undefined') return
    const root = document.documentElement
    root.dataset.skin = skinId.value
    root.classList.toggle('dark', isDark.value)
    root.classList.toggle('has-wallpaper', hasWallpaper.value)

    const panelAlpha = Math.min(0.98, Number((surfaceOpacity.value + 0.04).toFixed(2)))
    const cardAlpha = Math.min(0.96, Number((surfaceOpacity.value + 0.02).toFixed(2)))
    root.style.setProperty('--mc-wallpaper-blur', `${wallpaperBlur.value}px`)
    root.style.setProperty('--mc-wallpaper-scrim-alpha', String(scrimOpacity.value))
    root.style.setProperty('--mc-surface-alpha', String(surfaceOpacity.value))
    root.style.setProperty('--mc-panel-alpha', String(panelAlpha))
    root.style.setProperty('--mc-card-alpha', String(cardAlpha))
    root.style.setProperty('--mc-glass-blur', hasWallpaper.value ? '18px' : '12px')
  }

  function setMode(newMode: ThemeMode) {
    mode.value = newMode
    isDark.value = resolveIsDark(newMode)
    try { localStorage.setItem(STORAGE_KEY, newMode) } catch { /* ignore */ }
  }

  function toggle() {
    setMode(isDark.value ? 'light' : 'dark')
  }

  function setSkin(id: SkinId, options?: { syncRecommendedMode?: boolean }) {
    skinId.value = id
    const preset = getSkinPreset(id)
    if (options?.syncRecommendedMode !== false) {
      setMode(preset.recommendedMode)
    }
    persistAppearance()
  }

  function selectBuiltinWallpaper(id: string) {
    const preset = getBuiltinWallpaper(id)
    if (!preset) return
    revokeCustomObjectUrl()
    wallpaperType.value = 'builtin'
    wallpaperId.value = preset.id
    persistAppearance()
  }

  function clearWallpaper() {
    revokeCustomObjectUrl()
    wallpaperType.value = 'none'
    wallpaperId.value = ''
    persistAppearance()
  }

  async function activateCustomWallpaperBlob(id: string): Promise<boolean> {
    const blob = await defaultAppearanceStorage.loadCustomWallpaperBlob(id)
    if (!blob) return false
    revokeCustomObjectUrl()
    if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
      customWallpaperObjectUrl.value = URL.createObjectURL(blob)
    }
    wallpaperType.value = 'custom'
    wallpaperId.value = id
    persistAppearance()
    return true
  }

  async function loadCustomWallpapers() {
    customWallpapers.value = await defaultAppearanceStorage.listCustomWallpapers()
  }

  async function initWallpaper() {
    await loadCustomWallpapers()
    if (wallpaperType.value === 'custom' && wallpaperId.value) {
      const restored = await activateCustomWallpaperBlob(wallpaperId.value)
      if (!restored) {
        clearWallpaper()
      }
    }
  }

  async function uploadCustomWallpaper(file: File): Promise<CustomWallpaperMeta> {
    wallpaperLoading.value = true
    try {
      const meta = await defaultAppearanceStorage.saveCustomWallpaper(file)
      await loadCustomWallpapers()
      await activateCustomWallpaperBlob(meta.id)
      return meta
    } finally {
      wallpaperLoading.value = false
    }
  }

  async function selectCustomWallpaper(id: string) {
    const ok = await activateCustomWallpaperBlob(id)
    if (!ok) {
      clearWallpaper()
    }
  }

  async function deleteCustomWallpaper(id: string) {
    await defaultAppearanceStorage.deleteCustomWallpaper(id)
    await loadCustomWallpapers()
    if (wallpaperType.value === 'custom' && wallpaperId.value === id) {
      clearWallpaper()
    }
  }

  function setSurfaceOpacity(value: number) {
    surfaceOpacity.value = Number(clampNumber(value, 0.55, 0.98, DEFAULT_APPEARANCE_CONFIG.surfaceOpacity).toFixed(2))
    persistAppearance()
  }

  function setWallpaperBlur(value: number) {
    wallpaperBlur.value = Math.round(clampNumber(value, 0, 24, DEFAULT_APPEARANCE_CONFIG.wallpaperBlur))
    persistAppearance()
  }

  function setScrimOpacity(value: number) {
    scrimOpacity.value = Number(clampNumber(value, 0, 0.7, DEFAULT_APPEARANCE_CONFIG.scrimOpacity).toFixed(2))
    persistAppearance()
  }

  function resetAppearance() {
    revokeCustomObjectUrl()
    skinId.value = DEFAULT_SKIN_ID
    wallpaperType.value = DEFAULT_APPEARANCE_CONFIG.wallpaperType
    wallpaperId.value = DEFAULT_APPEARANCE_CONFIG.wallpaperId
    surfaceOpacity.value = DEFAULT_APPEARANCE_CONFIG.surfaceOpacity
    wallpaperBlur.value = DEFAULT_APPEARANCE_CONFIG.wallpaperBlur
    scrimOpacity.value = DEFAULT_APPEARANCE_CONFIG.scrimOpacity
    setMode('light')
    persistAppearance()
  }

  function openDrawer() {
    drawerOpen.value = true
  }

  function closeDrawer() {
    drawerOpen.value = false
  }

  function toggleDrawer() {
    drawerOpen.value = !drawerOpen.value
  }

  watch(
    [isDark, skinId, wallpaperType, wallpaperId, surfaceOpacity, wallpaperBlur, scrimOpacity, resolvedWallpaperUrl],
    applyAppearanceToDom,
    { immediate: true, flush: 'sync' },
  )

  const mql = typeof window !== 'undefined' ? window.matchMedia?.('(prefers-color-scheme: dark)') : undefined
  mql?.addEventListener('change', () => {
    if (mode.value === 'system') {
      isDark.value = mql.matches
    }
  })

  return {
    mode,
    isDark,
    skinId,
    activeSkin,
    wallpaperType,
    wallpaperId,
    surfaceOpacity,
    wallpaperBlur,
    scrimOpacity,
    customWallpapers,
    wallpaperLoading,
    drawerOpen,
    hasWallpaper,
    resolvedWallpaperUrl,
    setMode,
    toggle,
    setSkin,
    selectBuiltinWallpaper,
    clearWallpaper,
    initWallpaper,
    loadCustomWallpapers,
    uploadCustomWallpaper,
    selectCustomWallpaper,
    deleteCustomWallpaper,
    setSurfaceOpacity,
    setWallpaperBlur,
    setScrimOpacity,
    resetAppearance,
    openDrawer,
    closeDrawer,
    toggleDrawer,
  }
})

// Enable HMR for this store: editing it during `pnpm dev` patches the live
// store instead of requiring a full page reload. Stripped from prod builds.
if (import.meta.hot) {
  import.meta.hot.accept(acceptHMRUpdate(useThemeStore, import.meta.hot))
}
