import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * Global loading state — handles two layers:
 *  - Initial app bootstrap (B · brand centered) — full-screen, shown until Vue mounts
 *  - Route-level navigation (A · top progress + skeleton) — 200ms debounce to avoid flicker
 */
export const useGlobalLoadingStore = defineStore('globalLoading', () => {
  /** Route navigation loading (A) — controls top progress + skeleton in MainLayout */
  const isRouteLoading = ref(false)
  /** Initial bootstrap loading (B) — full-screen brand loader */
  const isInitialLoading = ref(true)

  let routeTimer: ReturnType<typeof setTimeout> | null = null
  let minVisibleTimer: ReturnType<typeof setTimeout> | null = null
  let routeLoadingStartAt = 0

  /**
   * Start route loading with 200ms debounce.
   * Navigations faster than 200ms never show the skeleton (prevents flash).
   */
  function startRouteLoading() {
    // Don't show route skeleton while the initial brand loader is still visible
    if (isInitialLoading.value) return
    // If already showing, don't restart debounce
    if (isRouteLoading.value) return
    if (routeTimer) clearTimeout(routeTimer)
    routeTimer = setTimeout(() => {
      routeTimer = null
      routeLoadingStartAt = Date.now()
      isRouteLoading.value = true
    }, 200)
  }

  /**
   * Stop route loading.
   * If the skeleton is already visible, keep it for at least 300ms total
   * to avoid a jarring flash when the chunk resolves just after becoming visible.
   * If it never became visible (cleared before 200ms), just cancel the timer.
   */
  function stopRouteLoading() {
    if (routeTimer) {
      clearTimeout(routeTimer)
      routeTimer = null
    }
    if (!isRouteLoading.value) return

    const elapsed = Date.now() - routeLoadingStartAt
    const minVisible = 300
    const remaining = minVisible - elapsed
    if (remaining > 0) {
      if (minVisibleTimer) clearTimeout(minVisibleTimer)
      minVisibleTimer = setTimeout(() => {
        minVisibleTimer = null
        isRouteLoading.value = false
      }, remaining)
    } else {
      if (minVisibleTimer) {
        clearTimeout(minVisibleTimer)
        minVisibleTimer = null
      }
      isRouteLoading.value = false
    }
  }

  /** Hide the initial brand loader (called once after app mounts) */
  function hideInitialLoading() {
    isInitialLoading.value = false
  }

  // Also hide initial loading after a safety timeout (e.g. HMR or error)
  // so the brand overlay never traps the user.
  setTimeout(() => {
    if (isInitialLoading.value) {
      // eslint-disable-next-line no-console
      console.warn('[GlobalLoading] initial loader safety timeout — force hiding')
      hideInitialLoading()
    }
  }, 8000)

  return {
    isRouteLoading,
    isInitialLoading,
    startRouteLoading,
    stopRouteLoading,
    hideInitialLoading,
  }
})
