<template>
  <el-config-provider :locale="elementLocale">
    <GlobalTopProgress :visible="globalLoading.isRouteLoading" />
    <router-view />
    <!-- Mounted once at the app root so mcConfirm() can pop a dialog
         from anywhere without each caller wiring its own host. -->
    <McConfirmHost />
    <!-- Single global file-preview dialog. Attachment cards and generated-file
         links open it via the previewBus window event, so both user-uploaded
         and AI-generated docx/xlsx/pdf preview in-place. -->
    <FilePreviewDialog global />
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, watchEffect, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import en from 'element-plus/es/locale/lang/en'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { currentLocale } from '@/i18n'
import { useThemeStore } from '@/stores/useThemeStore'
import { useSystemSettingsStore } from '@/stores/useSystemSettingsStore'
import { useGlobalWikilinkClick } from '@/composables/useGlobalWikilinkClick'
import { useGlobalFileDownloadClick } from '@/composables/useGlobalFileDownloadClick'
import { useGlobalGeneratedImageBlob } from '@/composables/useGlobalGeneratedImageBlob'
import McConfirmHost from '@/components/common/McConfirmHost.vue'
import FilePreviewDialog from '@/components/chat/preview/FilePreviewDialog.vue'
import GlobalTopProgress from '@/components/common/GlobalTopProgress.vue'
import { useGlobalLoadingStore } from '@/stores/useGlobalLoadingStore'

// Initialize theme — applies .dark class to <html> immediately
useThemeStore()

const globalLoading = useGlobalLoadingStore()

// Hide the inline initial loader (B) once Vue is mounted.
// Keep it visible for at least 600ms total to avoid a flash on fast loads.
const INLINE_LOADER_MIN_MS = 600
const INLINE_LOADER_FADE_MS = 400
const inlineLoaderStart = Date.now()
onMounted(() => {
  const elapsed = Date.now() - inlineLoaderStart
  const wait = Math.max(0, INLINE_LOADER_MIN_MS - elapsed)
  setTimeout(() => {
    const el = document.getElementById('app-initial-loader')
    if (el) {
      el.classList.add('ail--hidden')
      setTimeout(() => {
        el.style.display = 'none'
      }, INLINE_LOADER_FADE_MS)
    }
    globalLoading.hideInitialLoading()
  }, wait)
})

// Load runtime settings (streamEnabled / debugMode) so the chat flow honors
// them. localStorage cache makes them available instantly; this refreshes
// from the backend in the background.
useSystemSettingsStore().load()

// Global click delegator for [[wikilinks]] rendered into chat / docs /
// memory surfaces. WikiPageViewer's own postprocess handles in-wiki
// clicks (those carry data-slug); this catches everything else.
useGlobalWikilinkClick()

// Global click delegator for tool-generated file download links
// (`/api/v1/files/...`). Downloads via authenticated fetch → blob so an
// expired/missing file degrades to a toast instead of a full-page navigation
// to the backend's 404 JSON, which would otherwise replace the whole SPA.
useGlobalFileDownloadClick()
useGlobalGeneratedImageBlob()

const { t } = useI18n()

watchEffect(() => {
  document.title = t('app.title')
})

const elementLocale = computed(() => (currentLocale.value === 'en-US' ? en : zhCn))
</script>
