<!-- 修改人:祝奔；时间：2026-11-26T18:10:47+08:00；修改原因/内容：新增右侧滑出式换肤与背景壁纸实时预览抽屉组件；代码来源：AI生成 -->
<template>
  <Teleport to="body">
    <Transition name="appearance-drawer-fade">
      <div
        v-if="themeStore.drawerOpen"
        class="appearance-backdrop"
        @click.self="themeStore.closeDrawer()"
      >
        <aside
          class="appearance-drawer"
          role="dialog"
          aria-modal="true"
          :aria-label="t('appearance.title')"
        >
          <header class="drawer-header">
            <div class="drawer-header__text">
              <h2 class="drawer-title">{{ t('appearance.title') }}</h2>
              <p class="drawer-subtitle">{{ t('appearance.subtitle') }}</p>
            </div>
            <button
              type="button"
              class="drawer-close"
              :title="t('appearance.close')"
              :aria-label="t('appearance.close')"
              @click="themeStore.closeDrawer()"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </header>

          <div class="drawer-body">
            <!-- 1. 整体风格预设 -->
            <section class="drawer-section">
              <div class="section-head">
                <h3 class="section-title">{{ t('appearance.sectionSkin') }}</h3>
                <span class="section-badge">{{ t(themeStore.activeSkin.nameKey) }}</span>
              </div>
              <div class="skin-grid">
                <button
                  v-for="skin in SKIN_PRESETS"
                  :key="skin.id"
                  type="button"
                  class="skin-card"
                  :class="{ active: themeStore.skinId === skin.id }"
                  @click="themeStore.setSkin(skin.id)"
                >
                  <div class="skin-preview" :style="{ background: skin.preview.bg }">
                    <span class="skin-preview__sidebar" :style="{ background: skin.preview.sidebar }">
                      <span class="skin-preview__dot" :style="{ background: skin.preview.primary }"></span>
                      <span class="skin-preview__bar" :style="{ background: skin.preview.text, opacity: 0.2 }"></span>
                      <span class="skin-preview__bar" :style="{ background: skin.preview.text, opacity: 0.14 }"></span>
                    </span>
                    <span class="skin-preview__main">
                      <span class="skin-preview__card" :style="{ background: skin.preview.surface }">
                        <span class="skin-preview__pill" :style="{ background: skin.preview.primary }"></span>
                        <span class="skin-preview__pill skin-preview__pill--short" :style="{ background: skin.preview.accent }"></span>
                      </span>
                    </span>
                    <span v-if="themeStore.skinId === skin.id" class="skin-check" aria-hidden="true">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                        <polyline points="20 6 9 17 4 12" />
                      </svg>
                    </span>
                  </div>
                  <div class="skin-meta">
                    <span class="skin-name">{{ t(skin.nameKey) }}</span>
                    <span class="skin-desc">{{ t(skin.descKey) }}</span>
                  </div>
                </button>
              </div>
            </section>

            <!-- 2. 明暗模式 -->
            <section class="drawer-section">
              <div class="section-head">
                <h3 class="section-title">{{ t('appearance.sectionMode') }}</h3>
              </div>
              <div class="mode-segmented" role="radiogroup" :aria-label="t('appearance.sectionMode')">
                <button
                  v-for="opt in modeOptions"
                  :key="opt.value"
                  type="button"
                  role="radio"
                  :aria-checked="themeStore.mode === opt.value"
                  class="mode-pill"
                  :class="{ active: themeStore.mode === opt.value }"
                  @click="themeStore.setMode(opt.value)"
                >
                  {{ opt.label }}
                </button>
              </div>
            </section>

            <!-- 3. 底部背景图 -->
            <section class="drawer-section">
              <div class="section-head">
                <h3 class="section-title">{{ t('appearance.sectionWallpaper') }}</h3>
                <span class="section-hint-inline">{{ t('appearance.wallpaperCountHint', { count: themeStore.customWallpapers.length }) }}</span>
              </div>

              <div class="wallpaper-grid">
                <!-- 无壁纸（纯色/渐变） -->
                <button
                  type="button"
                  class="wallpaper-item wallpaper-item--none"
                  :class="{ active: themeStore.wallpaperType === 'none' }"
                  @click="themeStore.clearWallpaper()"
                >
                  <div class="wallpaper-thumb wallpaper-thumb--none">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
                      <circle cx="12" cy="12" r="9" />
                      <line x1="5.6" y1="5.6" x2="18.4" y2="18.4" />
                    </svg>
                  </div>
                  <span class="wallpaper-label">{{ t('appearance.wallpaperNone') }}</span>
                </button>

                <!-- 内置壁纸 -->
                <button
                  v-for="wp in BUILTIN_WALLPAPERS"
                  :key="wp.id"
                  type="button"
                  class="wallpaper-item"
                  :class="{ active: themeStore.wallpaperType === 'builtin' && themeStore.wallpaperId === wp.id }"
                  @click="themeStore.selectBuiltinWallpaper(wp.id)"
                >
                  <div
                    class="wallpaper-thumb"
                    :style="{ background: wp.thumbnailBg }"
                  >
                    <img :src="withCtx(wp.path)" :alt="t(wp.nameKey)" loading="lazy" />
                  </div>
                  <span class="wallpaper-label">{{ t(wp.nameKey) }}</span>
                </button>

                <!-- 用户自定义上传壁纸 -->
                <div
                  v-for="item in themeStore.customWallpapers"
                  :key="item.id"
                  class="wallpaper-item wallpaper-item--custom"
                  :class="{ active: themeStore.wallpaperType === 'custom' && themeStore.wallpaperId === item.id }"
                >
                  <button
                    type="button"
                    class="wallpaper-select-btn"
                    :title="item.name"
                    @click="themeStore.selectCustomWallpaper(item.id)"
                  >
                    <div class="wallpaper-thumb">
                      <img v-if="item.thumbDataUrl" :src="item.thumbDataUrl" :alt="item.name" />
                    </div>
                    <span class="wallpaper-label">{{ item.name }}</span>
                  </button>
                  <button
                    type="button"
                    class="wallpaper-delete-btn"
                    :title="t('appearance.deleteWallpaper')"
                    :aria-label="t('appearance.deleteWallpaper')"
                    @click.stop="themeStore.deleteCustomWallpaper(item.id)"
                  >
                    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round">
                      <line x1="18" y1="6" x2="6" y2="18" />
                      <line x1="6" y1="6" x2="18" y2="18" />
                    </svg>
                  </button>
                </div>
              </div>

              <!-- 上传自定义图片 -->
              <div
                class="upload-dropzone"
                :class="{ 'is-dragging': isDragging, 'is-loading': themeStore.wallpaperLoading }"
                role="button"
                tabindex="0"
                @click="triggerFilePicker"
                @keydown.enter.prevent="triggerFilePicker"
                @keydown.space.prevent="triggerFilePicker"
                @dragover.prevent="isDragging = true"
                @dragleave.prevent="isDragging = false"
                @drop.prevent="onDropFile"
              >
                <input
                  ref="fileInputRef"
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  class="sr-only"
                  @change="onFileSelected"
                />
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                  <polyline points="17 8 12 3 7 8" />
                  <line x1="12" y1="3" x2="12" y2="15" />
                </svg>
                <div class="upload-dropzone__text">
                  <span class="upload-dropzone__title">
                    {{ themeStore.wallpaperLoading ? t('appearance.uploading') : t('appearance.uploadTitle') }}
                  </span>
                  <span class="upload-dropzone__hint">{{ t('appearance.uploadHint') }}</span>
                </div>
              </div>
            </section>

            <!-- 4. 画面融合与通透度微调 -->
            <section class="drawer-section">
              <div class="section-head">
                <h3 class="section-title">{{ t('appearance.sectionTuning') }}</h3>
              </div>

              <div class="preset-pills">
                <button
                  v-for="p in glassPresets"
                  :key="p.id"
                  type="button"
                  class="preset-pill"
                  :class="{ active: Math.abs(themeStore.surfaceOpacity - p.opacity) < 0.03 }"
                  @click="themeStore.setSurfaceOpacity(p.opacity)"
                >
                  {{ p.label }}
                </button>
              </div>

              <div class="slider-group" :class="{ 'is-muted': !themeStore.hasWallpaper }">
                <label class="slider-row">
                  <div class="slider-meta">
                    <span>{{ t('appearance.surfaceOpacity') }}</span>
                    <span class="slider-val">{{ Math.round(themeStore.surfaceOpacity * 100) }}%</span>
                  </div>
                  <input
                    type="range"
                    min="55"
                    max="98"
                    step="1"
                    :value="Math.round(themeStore.surfaceOpacity * 100)"
                    @input="themeStore.setSurfaceOpacity(Number(($event.target as HTMLInputElement).value) / 100)"
                  />
                </label>

                <label class="slider-row">
                  <div class="slider-meta">
                    <span>{{ t('appearance.wallpaperBlur') }}</span>
                    <span class="slider-val">{{ themeStore.wallpaperBlur }}px</span>
                  </div>
                  <input
                    type="range"
                    min="0"
                    max="24"
                    step="1"
                    :disabled="!themeStore.hasWallpaper"
                    :value="themeStore.wallpaperBlur"
                    @input="themeStore.setWallpaperBlur(Number(($event.target as HTMLInputElement).value))"
                  />
                </label>

                <label class="slider-row">
                  <div class="slider-meta">
                    <span>{{ t('appearance.scrimOpacity') }}</span>
                    <span class="slider-val">{{ Math.round(themeStore.scrimOpacity * 100) }}%</span>
                  </div>
                  <input
                    type="range"
                    min="0"
                    max="70"
                    step="1"
                    :disabled="!themeStore.hasWallpaper"
                    :value="Math.round(themeStore.scrimOpacity * 100)"
                    @input="themeStore.setScrimOpacity(Number(($event.target as HTMLInputElement).value) / 100)"
                  />
                </label>
              </div>
              <p v-if="!themeStore.hasWallpaper" class="tuning-empty-tip">{{ t('appearance.tuningNoWallpaperHint') }}</p>
            </section>
          </div>

          <footer class="drawer-footer">
            <button type="button" class="btn-reset" @click="onReset">
              {{ t('appearance.resetDefault') }}
            </button>
            <button type="button" class="btn-done" @click="themeStore.closeDrawer()">
              {{ t('appearance.done') }}
            </button>
          </footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { BUILTIN_WALLPAPERS, SKIN_PRESETS } from '@/config/skins'
import { useThemeStore, type ThemeMode } from '@/stores/useThemeStore'
import { withCtx } from '@/utils/appPaths'
import { WallpaperValidationError } from '@/utils/wallpaperStorage'
import { mcToast } from '@/composables/useMcToast'

const { t } = useI18n()
const themeStore = useThemeStore()

const fileInputRef = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)

const modeOptions = computed<{ value: ThemeMode; label: string }[]>(() => [
  { value: 'light', label: t('nav.themeLight') },
  { value: 'dark', label: t('nav.themeDark') },
  { value: 'system', label: t('nav.themeSystem') },
])

const glassPresets = computed(() => [
  { id: 'crystal', label: t('appearance.glassCrystal'), opacity: 0.72 },
  { id: 'frosted', label: t('appearance.glassFrosted'), opacity: 0.84 },
  { id: 'solid', label: t('appearance.glassSolid'), opacity: 0.95 },
])

function triggerFilePicker() {
  if (themeStore.wallpaperLoading) return
  fileInputRef.value?.click()
}

async function handleImageFile(file: File | undefined) {
  if (!file) return
  try {
    await themeStore.uploadCustomWallpaper(file)
    mcToast.success(t('appearance.uploadSuccess'))
  } catch (err) {
    if (err instanceof WallpaperValidationError) {
      if (err.code === 'unsupported_type') {
        mcToast.error(t('appearance.errorUnsupportedType'))
        return
      }
      if (err.code === 'file_too_large') {
        mcToast.error(t('appearance.errorFileTooLarge'))
        return
      }
    }
    mcToast.error(t('appearance.errorUploadFailed'))
  }
}

async function onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  await handleImageFile(file)
}

async function onDropFile(event: DragEvent) {
  isDragging.value = false
  const file = event.dataTransfer?.files?.[0]
  await handleImageFile(file)
}

function onReset() {
  themeStore.resetAppearance()
  mcToast.success(t('appearance.resetSuccess'))
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && themeStore.drawerOpen) {
    themeStore.closeDrawer()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.appearance-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2100;
  display: flex;
  justify-content: flex-end;
  background: rgba(15, 23, 42, 0.14);
}

.appearance-drawer {
  width: min(392px, 94vw);
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--mc-panel-top, rgba(255, 255, 255, 0.96));
  backdrop-filter: blur(22px) saturate(135%);
  -webkit-backdrop-filter: blur(22px) saturate(135%);
  border-left: 1px solid var(--mc-border);
  box-shadow: -16px 0 48px rgba(15, 23, 42, 0.16);
  color: var(--mc-text-primary);
}

.drawer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 20px 14px;
  border-bottom: 1px solid var(--mc-border-light);
}

.drawer-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--mc-text-primary);
}

.drawer-subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--mc-text-secondary);
}

.drawer-close {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  border: 1px solid var(--mc-border-light);
  background: var(--mc-bg-muted);
  color: var(--mc-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s ease;
}

.drawer-close:hover {
  background: var(--mc-bg-hover);
  color: var(--mc-text-primary);
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.drawer-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.section-title {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--mc-text-primary);
}

.section-badge {
  font-size: 11px;
  font-weight: 600;
  color: var(--mc-primary);
  background: var(--mc-primary-bg);
  padding: 2px 8px;
  border-radius: 999px;
}

.section-hint-inline {
  font-size: 11px;
  color: var(--mc-text-tertiary);
}

/* Skin grid */
.skin-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.skin-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
  border-radius: 14px;
  border: 1.5px solid var(--mc-border-light);
  background: var(--mc-bg-elevated);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.skin-card:hover {
  border-color: var(--mc-border-strong);
  transform: translateY(-1px);
}

.skin-card.active {
  border-color: var(--mc-primary);
  box-shadow: 0 0 0 3px rgba(var(--mc-primary-rgb), 0.14);
}

.skin-preview {
  position: relative;
  height: 58px;
  border-radius: 9px;
  padding: 6px;
  display: flex;
  gap: 5px;
  overflow: hidden;
  border: 1px solid rgba(128, 128, 128, 0.14);
}

.skin-preview__sidebar {
  width: 28%;
  border-radius: 5px;
  padding: 4px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.skin-preview__dot {
  width: 7px;
  height: 7px;
  border-radius: 3px;
}

.skin-preview__bar {
  height: 3px;
  border-radius: 2px;
}

.skin-preview__main {
  flex: 1;
  display: flex;
  align-items: stretch;
}

.skin-preview__card {
  flex: 1;
  border-radius: 5px;
  padding: 6px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.skin-preview__pill {
  height: 6px;
  width: 58%;
  border-radius: 999px;
}

.skin-preview__pill--short {
  width: 36%;
  opacity: 0.78;
}

.skin-check {
  position: absolute;
  right: 5px;
  bottom: 5px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--mc-primary);
  color: #ffffff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.skin-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 2px;
}

.skin-name {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--mc-text-primary);
}

.skin-desc {
  font-size: 11px;
  color: var(--mc-text-tertiary);
  line-height: 1.35;
}

/* Mode segmented */
.mode-segmented {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  padding: 4px;
  border-radius: 12px;
  background: var(--mc-bg-muted);
  border: 1px solid var(--mc-border-light);
}

.mode-pill {
  height: 32px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--mc-text-secondary);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.mode-pill:hover {
  color: var(--mc-text-primary);
}

.mode-pill.active {
  background: var(--mc-bg-elevated);
  color: var(--mc-primary);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}

/* Wallpaper grid */
.wallpaper-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.wallpaper-item {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 4px;
  border-radius: 11px;
  border: 1.5px solid var(--mc-border-light);
  background: var(--mc-bg-elevated);
  cursor: pointer;
  text-align: center;
  min-width: 0;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.wallpaper-item:hover {
  border-color: var(--mc-border-strong);
}

.wallpaper-item.active {
  border-color: var(--mc-primary);
  box-shadow: 0 0 0 2px rgba(var(--mc-primary-rgb), 0.16);
}

.wallpaper-select-btn {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  min-width: 0;
  width: 100%;
}

.wallpaper-thumb {
  width: 100%;
  aspect-ratio: 16 / 10;
  border-radius: 7px;
  overflow: hidden;
  background: var(--mc-bg-sunken);
  display: flex;
  align-items: center;
  justify-content: center;
}

.wallpaper-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.wallpaper-thumb--none {
  color: var(--mc-text-tertiary);
  background: linear-gradient(135deg, var(--mc-bg-muted), var(--mc-bg-sunken));
}

.wallpaper-label {
  font-size: 11px;
  color: var(--mc-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding: 0 2px 2px;
}

.wallpaper-delete-btn {
  position: absolute;
  top: 7px;
  right: 7px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: none;
  background: rgba(15, 23, 42, 0.65);
  color: #ffffff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease, background-color 0.15s ease;
}

.wallpaper-item--custom:hover .wallpaper-delete-btn,
.wallpaper-delete-btn:focus-visible {
  opacity: 1;
}

.wallpaper-delete-btn:hover {
  background: var(--mc-danger, #c0392b);
}

/* Upload dropzone */
.upload-dropzone {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 12px;
  border: 1.5px dashed var(--mc-border-strong);
  background: var(--mc-bg-muted);
  color: var(--mc-text-secondary);
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease, color 0.15s ease;
}

.upload-dropzone:hover,
.upload-dropzone.is-dragging {
  border-color: var(--mc-primary);
  background: var(--mc-primary-bg);
  color: var(--mc-primary);
}

.upload-dropzone.is-loading {
  opacity: 0.7;
  cursor: wait;
}

.upload-dropzone__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.upload-dropzone__title {
  font-size: 12.5px;
  font-weight: 600;
}

.upload-dropzone__hint {
  font-size: 11px;
  color: var(--mc-text-tertiary);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}

/* Tuning */
.preset-pills {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.preset-pill {
  height: 30px;
  border-radius: 9px;
  border: 1px solid var(--mc-border-light);
  background: var(--mc-bg-elevated);
  color: var(--mc-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.preset-pill:hover {
  border-color: var(--mc-border-strong);
  color: var(--mc-text-primary);
}

.preset-pill.active {
  border-color: var(--mc-primary);
  background: var(--mc-primary-bg);
  color: var(--mc-primary);
  font-weight: 600;
}

.slider-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  background: var(--mc-bg-muted);
  border: 1px solid var(--mc-border-light);
}

.slider-group.is-muted .slider-row:nth-child(n + 2) {
  opacity: 0.45;
}

.slider-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.slider-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--mc-text-secondary);
}

.slider-val {
  font-family: var(--mc-font-mono);
  font-size: 11.5px;
  color: var(--mc-text-primary);
  font-weight: 600;
}

.slider-row input[type='range'] {
  width: 100%;
  accent-color: var(--mc-primary);
  cursor: pointer;
}

.slider-row input[type='range']:disabled {
  cursor: not-allowed;
}

.tuning-empty-tip {
  margin: 0;
  font-size: 11px;
  color: var(--mc-text-tertiary);
}

/* Footer */
.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid var(--mc-border-light);
  background: var(--mc-bg-muted);
}

.btn-reset,
.btn-done {
  height: 36px;
  padding: 0 14px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-reset {
  border: 1px solid var(--mc-border);
  background: var(--mc-bg-elevated);
  color: var(--mc-text-secondary);
}

.btn-reset:hover {
  background: var(--mc-bg-hover);
  color: var(--mc-text-primary);
}

.btn-done {
  border: none;
  background: var(--mc-primary);
  color: #ffffff;
  padding-inline: 20px;
}

.btn-done:hover {
  background: var(--mc-primary-hover);
}

.appearance-drawer-fade-enter-active,
.appearance-drawer-fade-leave-active {
  transition: opacity 0.2s ease;
}

.appearance-drawer-fade-enter-active .appearance-drawer,
.appearance-drawer-fade-leave-active .appearance-drawer {
  transition: transform 0.22s ease;
}

.appearance-drawer-fade-enter-from,
.appearance-drawer-fade-leave-to {
  opacity: 0;
}

.appearance-drawer-fade-enter-from .appearance-drawer,
.appearance-drawer-fade-leave-to .appearance-drawer {
  transform: translateX(100%);
}
</style>
