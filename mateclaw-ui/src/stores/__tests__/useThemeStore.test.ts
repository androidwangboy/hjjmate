// 修改人:祝奔；时间：2026-11-26T18:10:47+08:00；修改原因/内容：新增useThemeStore换肤、壁纸选择、参数钳制、持久化及重置单元测试；代码来源：AI生成
import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useThemeStore } from '../useThemeStore'
import { APPEARANCE_STORAGE_KEY } from '@/utils/wallpaperStorage'

describe('useThemeStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    document.documentElement.className = ''
    delete document.documentElement.dataset.skin
    document.documentElement.removeAttribute('style')
  })

  it('defaults to medical-ice light skin without wallpaper', () => {
    const store = useThemeStore()
    expect(store.skinId).toBe('medical-ice')
    expect(store.mode).toBe('light')
    expect(store.isDark).toBe(false)
    expect(store.wallpaperType).toBe('none')
    expect(store.hasWallpaper).toBe(false)
    expect(document.documentElement.dataset.skin).toBe('medical-ice')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('switches skin and syncs recommended color mode + DOM dataset', () => {
    const store = useThemeStore()
    store.setSkin('deep-ocean')

    expect(store.skinId).toBe('deep-ocean')
    expect(store.mode).toBe('dark')
    expect(store.isDark).toBe(true)
    expect(document.documentElement.dataset.skin).toBe('deep-ocean')
    expect(document.documentElement.classList.contains('dark')).toBe(true)

    const persisted = JSON.parse(localStorage.getItem(APPEARANCE_STORAGE_KEY) || '{}')
    expect(persisted.skinId).toBe('deep-ocean')
  })

  it('supports switching skin without overriding mode when syncRecommendedMode is false', () => {
    const store = useThemeStore()
    store.setMode('light')
    store.setSkin('slate-ink', { syncRecommendedMode: false })

    expect(store.skinId).toBe('slate-ink')
    expect(store.mode).toBe('light')
    expect(store.isDark).toBe(false)
  })

  it('selects and clears built-in wallpaper and toggles has-wallpaper class', () => {
    const store = useThemeStore()
    store.selectBuiltinWallpaper('medical-desk')

    expect(store.wallpaperType).toBe('builtin')
    expect(store.wallpaperId).toBe('medical-desk')
    expect(store.hasWallpaper).toBe(true)
    expect(store.resolvedWallpaperUrl).toContain('/images/login/hujiamate-login-bg-pc-new.png')
    expect(document.documentElement.classList.contains('has-wallpaper')).toBe(true)

    store.clearWallpaper()
    expect(store.wallpaperType).toBe('none')
    expect(store.hasWallpaper).toBe(false)
    expect(document.documentElement.classList.contains('has-wallpaper')).toBe(false)
  })

  it('clamps surface opacity, blur, and scrim opacity and writes CSS variables', () => {
    const store = useThemeStore()
    store.setSurfaceOpacity(0.1)
    store.setWallpaperBlur(99)
    store.setScrimOpacity(-1)

    expect(store.surfaceOpacity).toBe(0.55)
    expect(store.wallpaperBlur).toBe(24)
    expect(store.scrimOpacity).toBe(0)
    expect(document.documentElement.style.getPropertyValue('--mc-surface-alpha')).toBe('0.55')
    expect(document.documentElement.style.getPropertyValue('--mc-wallpaper-blur')).toBe('24px')
    expect(document.documentElement.style.getPropertyValue('--mc-wallpaper-scrim-alpha')).toBe('0')
  })

  it('resets back to default medical-ice appearance', () => {
    const store = useThemeStore()
    store.setSkin('warm-sand')
    store.selectBuiltinWallpaper('warm-silk')
    store.setSurfaceOpacity(0.7)
    store.setWallpaperBlur(12)
    store.setScrimOpacity(0.45)

    store.resetAppearance()
    expect(store.skinId).toBe('medical-ice')
    expect(store.mode).toBe('light')
    expect(store.wallpaperType).toBe('none')
    expect(store.surfaceOpacity).toBe(0.84)
    expect(store.wallpaperBlur).toBe(2)
    expect(store.scrimOpacity).toBe(0.18)
  })
})
