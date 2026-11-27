<template>
  <!-- 修改人:祝奔；时间：2026-11-26T17:56:28+08:00；修改原因/内容：将登录页精修改版为冰蓝磨砂玻璃风格，统一品牌区样式并补充输入框线性图标；代码来源：AI生成 -->
  <div class="login-page" :style="loginPageStyle">
    <div class="login-center">
      <div class="login-logo">
        <img :src="appLogo" alt="HUJIA Mate" class="logo-image" />
        <h1 class="logo-title">HUJIA&nbsp;<span class="logo-title-highlight">Mate</span></h1>
        <p class="logo-tagline">{{ t('login.tagline') }}</p>
      </div>

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="input-wrap">
          <span class="input-icon" aria-hidden="true">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
          </span>
          <input
            v-model="form.username"
            type="text"
            class="form-input form-input--with-icon"
            :placeholder="t('login.placeholders.username')"
            :aria-label="t('login.fields.username')"
            autocomplete="username"
            required
          />
        </div>

        <div class="input-wrap">
          <span class="input-icon" aria-hidden="true">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
              <rect x="4" y="11" width="16" height="10" rx="2" />
              <path d="M8 11V7a4 4 0 0 1 8 0v4" />
            </svg>
          </span>
          <input
            v-model="form.password"
            :type="showPassword ? 'text' : 'password'"
            class="form-input form-input--with-icon form-input--has-eye"
            :placeholder="t('login.placeholders.password')"
            :aria-label="t('login.fields.password')"
            autocomplete="current-password"
            required
          />
          <button
            type="button"
            class="eye-btn"
            :aria-label="showPassword ? 'Hide password' : 'Show password'"
            @click="showPassword = !showPassword"
          >
            <svg v-if="!showPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
            <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
              <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
              <line x1="1" y1="1" x2="23" y2="23" />
            </svg>
          </button>
        </div>

        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

        <button type="submit" class="login-btn" :disabled="loading">
          <span v-if="!loading" class="login-btn-label">{{ t('login.signIn') }}</span>
          <span v-else class="loading-dots">
            <span></span><span></span><span></span>
          </span>
        </button>
      </form>

      <!-- SSO providers (rendered only when the backend reports enabled providers) -->
      <template v-if="ssoProviders.length > 0">
        <div class="sso-divider">或使用</div>
        <div class="sso-buttons">
          <button
            v-for="p in ssoProviders"
            :key="p.id"
            type="button"
            class="sso-btn"
            :disabled="loading"
            @click="handleSsoLogin(p.id)"
          >
            {{ p.displayName }} 登录
          </button>
        </div>
      </template>

      <!-- SSO bind dialog (link-only mode: user must bind to an existing account) -->
      <div v-if="bindDialog.visible" class="bind-dialog">
        <div class="bind-dialog-content">
          <h3 class="bind-title">首次使用 {{ bindDialog.provider }} 登录</h3>
          <p class="bind-desc">请绑定你的 HUJIA Mate 账号</p>
          <input v-model="bindDialog.username" type="text" class="form-input" placeholder="HUJIA Mate 用户名" autocomplete="username" />
          <input v-model="bindDialog.password" type="password" class="form-input" placeholder="HUJIA Mate 密码" autocomplete="current-password" />
          <div v-if="bindDialog.error" class="error-msg">{{ bindDialog.error }}</div>
          <button class="login-btn" :disabled="loading" @click="handleBind">绑定</button>
          <button class="bind-cancel" @click="cancelBind">取消</button>
        </div>
      </div>

      <i18n-t v-if="defaultCredentials" keypath="login.hint" tag="p" class="login-hint">
        <template #username><code>{{ defaultCredentials.username }}</code></template>
        <template #password><code>{{ defaultCredentials.password }}</code></template>
      </i18n-t>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { authApi, ssoApi } from '@/api/index'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import { useSystemSettingsStore } from '@/stores/useSystemSettingsStore'
// 修改人:祝奔；时间：2026-11-26T17:47:28+08:00；修改原因/内容：引入withCtx以兼容部署前缀加载新登录背景图，并移除原brandTags；代码来源：AI生成
import { appLogo, withCtx } from '@/utils/appPaths'

interface SsoProvider { id: string; displayName: string }

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
// Default credentials are a local-development convenience, not production UI.
const defaultCredentials = import.meta.env.DEV
  ? { username: 'admin', password: 'admin123' }
  : null
const workspaceStore = useWorkspaceStore()
const systemSettingsStore = useSystemSettingsStore()
const loading = ref(false)
const showPassword = ref(false)
const errorMsg = ref('')
const form = reactive({ username: '', password: '' })

const ssoProviders = ref<SsoProvider[]>([])

const bindDialog = reactive({
  visible: false,
  bindToken: '',
  provider: '',
  username: '',
  password: '',
  error: '',
})

// 修改人:祝奔；时间：2026-11-26T17:47:28+08:00；修改原因/内容：注入新登录页背景图片CSS变量并移除原AI数智协同标签配置；代码来源：AI生成
const loginPageStyle = {
  '--login-bg-url': `url('${withCtx('/images/login/hujiamate-login-bg-pc-new.png')}')`,
}

// Load enabled SSO providers on mount so the button only shows when configured.
onMounted(async () => {
  try {
    const res: any = await ssoApi.providers()
    ssoProviders.value = (res.data || res) as SsoProvider[]
  } catch {
    // SSO not enabled or unreachable — password login still works.
  }

  // Detect SSO callback: /login?sso=callback&code=xxx&provider=feishu
  const query = route.query
  if (query.sso === 'callback' && query.code && query.provider) {
    await handleSsoCallback(String(query.provider), String(query.code), String(query.state || ''))
  }
})

/** Shared login-success flow: write localStorage + navigate. */
async function applyLogin(data: { token: string; id: string | number; username: string; role: string }) {
  localStorage.setItem('token', data.token)
  localStorage.setItem('userId', String(data.id || '1'))
  localStorage.setItem('username', data.username)
  localStorage.setItem('role', data.role || 'user')
  systemSettingsStore.load()
  try {
    await workspaceStore.fetchWorkspaces()
  } catch {
    /* default-deny is fine; router guard will still steer */
  }
  const target = workspaceStore.can('view:dashboard') ? '/dashboard' : '/chat'
  router.push(target)
}

async function handleLogin() {
  if (!form.username || !form.password) return
  loading.value = true
  errorMsg.value = ''
  try {
    const res: any = await authApi.login(form)
    const data = res.data || res
    await applyLogin(data)
  } catch (e: any) {
    errorMsg.value = typeof e === 'string' ? e : t('login.failed')
  } finally {
    loading.value = false
  }
}

/** Redirect to the IdP authorization page. */
async function handleSsoLogin(providerId: string) {
  loading.value = true
  errorMsg.value = ''
  try {
    const res: any = await ssoApi.authorize(providerId)
    const data = res.data || res
    if (data.authorizeUrl) {
      window.location.href = data.authorizeUrl
    }
  } catch (e: any) {
    errorMsg.value = typeof e === 'string' ? e : 'SSO 授权失败'
    loading.value = false
  }
}

/** Handle the OAuth2 callback (code → JWT). */
async function handleSsoCallback(provider: string, code: string, state: string) {
  loading.value = true
  errorMsg.value = ''
  try {
    const res: any = await ssoApi.callback(provider, code, state)
    const data = res.data || res

    // link-only mode: backend returns { bindRequired: true, bindToken, provider, displayName }
    if (data.bindRequired) {
      bindDialog.visible = true
      bindDialog.bindToken = data.bindToken || ''
      bindDialog.provider = data.provider || provider
      bindDialog.error = ''
      return
    }

    // Success: loginResponse carries the JWT
    await applyLogin(data.loginResponse)
    // Clean the query params so a refresh doesn't replay the callback.
    router.replace({ path: '/login' })
  } catch (e: any) {
    errorMsg.value = typeof e === 'string' ? e : 'SSO 登录失败'
  } finally {
    loading.value = false
  }
}

/** Submit the bind form (link-only mode). */
async function handleBind() {
  if (!bindDialog.username || !bindDialog.password) return
  loading.value = true
  bindDialog.error = ''
  try {
    const res: any = await ssoApi.bind(bindDialog.bindToken, bindDialog.username, bindDialog.password)
    const data = res.data || res
    bindDialog.visible = false
    await applyLogin(data)
  } catch (e: any) {
    bindDialog.error = typeof e === 'string' ? e : '绑定失败，请检查用户名和密码'
  } finally {
    loading.value = false
  }
}

function cancelBind() {
  bindDialog.visible = false
  bindDialog.bindToken = ''
  bindDialog.username = ''
  bindDialog.password = ''
  bindDialog.error = ''
}
</script>

<style scoped>
/* 修改人:祝奔；时间：2026-11-26T17:56:28+08:00；修改原因/内容：重构登录页为冰蓝磨砂玻璃设计体系，提取统一CSS变量并移除全局深色遮罩与冗余动画；代码来源：AI生成 */
.login-page {
  --login-font-sans: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  --login-page-bg: #e6f2fc;
  --login-page-pad-right: 6%;
  --login-card-width: 400px;
  --login-card-bg: rgba(242, 248, 255, 0.82);
  --login-card-blur: blur(24px) saturate(130%);
  --login-card-border: rgba(255, 255, 255, 0.85);
  --login-card-radius: 24px;
  --login-card-padding: 40px 36px;
  --login-card-shadow:
    0 16px 48px rgba(35, 102, 156, 0.16),
    inset 0 1px 0 rgba(255, 255, 255, 0.95);

  --login-brand-primary: #173b5c;
  --login-brand-accent: #079eaa;
  --login-tagline-color: #557c98;

  --login-input-height: 48px;
  --login-input-radius: 10px;
  --login-input-bg: rgba(255, 255, 255, 0.65);
  --login-input-bg-focus: rgba(255, 255, 255, 0.95);
  --login-input-border: #c9dceb;
  --login-input-border-focus: #168edb;
  --login-input-focus-ring: 0 0 0 3px rgba(22, 142, 219, 0.12);
  --login-input-text: #173b5c;
  --login-input-placeholder: #8099ac;
  --login-input-icon: #6c8da5;
  --login-input-gap: 14px;
  --login-transition: 200ms ease;

  --login-btn-height: 48px;
  --login-btn-radius: 10px;
  --login-btn-bg: linear-gradient(110deg, #168edb 0%, #08b5a5 100%);
  --login-btn-text: #ffffff;
  --login-btn-letter-spacing: 4px;
  --login-btn-shadow: 0 6px 18px rgba(22, 142, 219, 0.22);

  --login-error-bg: rgba(239, 68, 68, 0.08);
  --login-error-border: rgba(220, 38, 38, 0.26);
  --login-error-text: #b91c1c;

  --login-divider-text: #6c8da5;
  --login-divider-line: rgba(148, 184, 212, 0.45);
  --login-secondary-bg: rgba(255, 255, 255, 0.65);
  --login-secondary-hover-bg: rgba(255, 255, 255, 0.92);
  --login-hint-text: #557c98;
  --login-hint-code-bg: rgba(22, 142, 219, 0.10);
  --login-hint-code-text: #173b5c;
  --login-scrollbar: rgba(108, 141, 165, 0.32);

  position: relative;
  width: 100%;
  height: 100dvh;
  min-height: 100vh;
  min-height: 100svh;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: clamp(16px, 4vh, 48px) var(--login-page-pad-right) clamp(16px, 4vh, 48px) clamp(20px, 4vw, 48px);
  overflow: hidden;
  font-family: var(--login-font-sans);
  background-color: var(--login-page-bg);
  background-image: var(--login-bg-url, url('/images/login/hujiamate-login-bg-pc-new.png'));
  background-position: center;
  background-size: cover;
  background-repeat: no-repeat;
}

.login-center {
  position: relative;
  z-index: 1;
  width: min(100%, var(--login-card-width));
  max-width: var(--login-card-width);
  max-height: calc(100dvh - 32px);
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 28px;
  padding: var(--login-card-padding);
  border: 1px solid var(--login-card-border);
  border-radius: var(--login-card-radius);
  background: var(--login-card-bg);
  box-shadow: var(--login-card-shadow);
  backdrop-filter: var(--login-card-blur);
  -webkit-backdrop-filter: var(--login-card-blur);
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: var(--login-scrollbar) transparent;
}

.login-center::-webkit-scrollbar {
  width: 4px;
}

.login-center::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: var(--login-scrollbar);
}

/* Brand */
.login-logo {
  text-align: center;
}

.logo-image {
  display: block;
  margin: 0 auto 14px;
  width: 76px;
  height: 76px;
  object-fit: contain;
}

.logo-title {
  font-family: var(--login-font-sans);
  font-size: 32px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--login-brand-primary);
  margin: 0;
  letter-spacing: -0.8px;
}

.logo-title-highlight {
  color: var(--login-brand-accent);
}

.logo-tagline {
  margin: 10px 0 0;
  font-family: var(--login-font-sans);
  font-size: 14px;
  font-weight: 400;
  line-height: 1.5;
  letter-spacing: 1px;
  color: var(--login-tagline-color);
}

/* Form */
.login-form {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--login-input-gap);
}

.input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 14px;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--login-input-icon);
  pointer-events: none;
  transition: color var(--login-transition);
}

.input-wrap:focus-within .input-icon {
  color: var(--login-input-border-focus);
}

.form-input {
  width: 100%;
  height: var(--login-input-height);
  padding: 0 14px;
  border: 1px solid var(--login-input-border);
  border-radius: var(--login-input-radius);
  font-family: var(--login-font-sans);
  font-size: 14px;
  color: var(--login-input-text);
  background: var(--login-input-bg);
  outline: none;
  box-sizing: border-box;
  transition:
    border-color var(--login-transition),
    background-color var(--login-transition),
    box-shadow var(--login-transition);
}

.form-input--with-icon {
  padding-left: 42px;
}

.form-input--has-eye {
  padding-right: 42px;
}

.form-input::placeholder {
  color: var(--login-input-placeholder);
}

.form-input:focus {
  border-color: var(--login-input-border-focus);
  background: var(--login-input-bg-focus);
  box-shadow: var(--login-input-focus-ring);
}

.form-input:-webkit-autofill,
.form-input:-webkit-autofill:hover,
.form-input:-webkit-autofill:focus {
  -webkit-text-fill-color: var(--login-input-text);
  box-shadow: 0 0 0 1000px rgba(255, 255, 255, 0.92) inset;
  transition: background-color 9999s ease-in-out 0s;
}

.eye-btn {
  position: absolute;
  right: 10px;
  width: 30px;
  height: 30px;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--login-input-icon);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  transition: color var(--login-transition), background-color var(--login-transition);
}

.eye-btn:hover {
  color: var(--login-input-border-focus);
  background: rgba(22, 142, 219, 0.08);
}

/* Error */
.error-msg {
  padding: 10px 14px;
  background: var(--login-error-bg);
  border: 1px solid var(--login-error-border);
  border-radius: var(--login-input-radius);
  font-size: 13px;
  color: var(--login-error-text);
}

/* Primary submit button */
.login-btn {
  width: 100%;
  height: var(--login-btn-height);
  padding: 0 16px;
  margin-top: 8px;
  background: var(--login-btn-bg);
  color: var(--login-btn-text);
  border: none;
  border-radius: var(--login-btn-radius);
  font-family: var(--login-font-sans);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: var(--login-btn-letter-spacing);
  box-shadow: var(--login-btn-shadow);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition:
    transform var(--login-transition),
    filter var(--login-transition),
    box-shadow var(--login-transition),
    opacity var(--login-transition);
}

.login-btn-label {
  padding-left: var(--login-btn-letter-spacing);
}

.login-btn:hover:not(:disabled) {
  filter: brightness(1.06);
  transform: translateY(-1px);
}

.login-btn:active:not(:disabled) {
  transform: translateY(0);
}

.login-btn:disabled {
  opacity: 0.72;
  cursor: not-allowed;
}

/* SSO buttons */
.sso-divider {
  text-align: center;
  font-size: 12px;
  color: var(--login-divider-text);
  margin: 0;
  position: relative;
}

.sso-divider::before,
.sso-divider::after {
  content: '';
  display: inline-block;
  width: 28%;
  height: 1px;
  background: var(--login-divider-line);
  vertical-align: middle;
  margin: 0 10px;
}

.sso-buttons {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.sso-btn {
  width: 100%;
  height: 44px;
  padding: 0 14px;
  background: var(--login-secondary-bg);
  color: var(--login-brand-primary);
  border: 1px solid var(--login-input-border);
  border-radius: var(--login-input-radius);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition:
    border-color var(--login-transition),
    background-color var(--login-transition),
    color var(--login-transition);
}

.sso-btn:hover:not(:disabled) {
  border-color: var(--login-input-border-focus);
  color: var(--login-input-border-focus);
  background: var(--login-secondary-hover-bg);
}

.sso-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

/* Bind dialog */
.bind-dialog {
  width: 100%;
}

.bind-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bind-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--login-brand-primary);
  margin: 0;
}

.bind-desc {
  font-size: 13px;
  color: var(--login-tagline-color);
  margin: 0;
}

.bind-cancel {
  width: 100%;
  padding: 8px;
  background: none;
  color: var(--login-tagline-color);
  border: none;
  font-size: 13px;
  cursor: pointer;
  transition: color var(--login-transition);
}

.bind-cancel:hover {
  color: var(--login-brand-primary);
}

/* Loading */
.loading-dots {
  display: flex;
  gap: 5px;
  align-items: center;
}

.loading-dots span {
  width: 6px;
  height: 6px;
  background: var(--login-btn-text);
  border-radius: 50%;
  animation: bounce 1.2s infinite;
}

.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-5px); }
}

/* Local dev hint (hidden in production) */
.login-hint {
  text-align: center;
  font-size: 12px;
  color: var(--login-hint-text);
  margin: -6px 0 0;
}

.login-hint :deep(code) {
  background: var(--login-hint-code-bg);
  padding: 2px 6px;
  border-radius: 5px;
  color: var(--login-hint-code-text);
  font-size: 12px;
}

/* Desktop wide screens (2560×1440) and compact laptops (1366×768) */
@media (max-width: 1440px) {
  .login-page {
    background-position: 36% center;
  }
}

/* Tablet and below: center the glass card. */
@media (max-width: 900px) {
  .login-page {
    justify-content: center;
    padding: clamp(16px, 4vh, 32px) clamp(20px, 4vw, 48px);
  }
}

/* Mobile: center the card cleanly. */
@media (max-width: 767px) {
  .login-page {
    justify-content: center;
    padding: 16px;
    background-position: 30% center;
  }

  .login-center {
    width: min(100%, var(--login-card-width));
    padding: 32px 24px;
    border-radius: 20px;
  }
}

/* Short desktop viewports (e.g. 1366×768 with browser chrome) */
@media (max-height: 700px) {
  .login-page {
    padding-block: 12px;
  }

  .login-center {
    gap: 20px;
    padding: 28px 32px;
  }

  .logo-image {
    width: 64px;
    height: 64px;
    margin-bottom: 10px;
  }

  .logo-title {
    font-size: 28px;
  }

  .logo-tagline {
    margin-top: 8px;
  }

  .login-btn {
    margin-top: 4px;
  }
}

@media (max-width: 480px) {
  .login-page {
    padding: 12px;
  }

  .login-center {
    padding: 28px 20px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .form-input,
  .input-icon,
  .eye-btn,
  .login-btn,
  .sso-btn,
  .bind-cancel {
    transition: none;
  }
}
</style>
