<template>
  <div class="login-page">
    <div class="login-center">
      <div class="login-logo">
        <img src="/logo/hjjmate_logo.png" alt="HjjMate" class="logo-image" />
        <h1 class="logo-title">Hjj<span class="logo-title-highlight">Mate</span></h1>
      </div>

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="input-wrap">
          <input
            v-model="form.username"
            type="text"
            class="form-input"
            :placeholder="t('login.placeholders.username')"
            :aria-label="t('login.fields.username')"
            autocomplete="username"
            required
          />
        </div>

        <div class="input-wrap">
          <input
            v-model="form.password"
            :type="showPassword ? 'text' : 'password'"
            class="form-input form-input--has-eye"
            :placeholder="t('login.placeholders.password')"
            :aria-label="t('login.fields.password')"
            autocomplete="current-password"
            required
          />
          <button type="button" class="eye-btn" @click="showPassword = !showPassword">
            <svg v-if="!showPassword" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
              <line x1="1" y1="1" x2="23" y2="23"/>
            </svg>
          </button>
        </div>

        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

        <button type="submit" class="login-btn" :disabled="loading">
          <span v-if="!loading">{{ t('login.signIn') }}</span>
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
          <p class="bind-desc">请绑定你的 HjjMate 账号</p>
          <input v-model="bindDialog.username" type="text" class="form-input" placeholder="HjjMate 用户名" autocomplete="username" />
          <input v-model="bindDialog.password" type="password" class="form-input" placeholder="HjjMate 密码" autocomplete="current-password" />
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
.login-page {
  position: relative;
  isolation: isolate;
  width: 100%;
  height: 100dvh;
  min-height: 100vh;
  min-height: 100svh;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: clamp(16px, 4vh, 48px) clamp(20px, 6vw, 112px);
  overflow: hidden;
  background-color: #071f24;
  background-image:
    linear-gradient(
      90deg,
      rgba(4, 31, 34, 0.06) 0%,
      rgba(4, 31, 34, 0.08) 46%,
      rgba(3, 22, 27, 0.25) 64%,
      rgba(2, 15, 21, 0.70) 100%
    ),
    url('/images/login/hjjmate-login-bg-pc.png');
  background-position: center;
  background-size: cover;
  background-repeat: no-repeat;
}

/* A quiet vignette keeps the image atmospheric while preserving form contrast. */
.login-page::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background: linear-gradient(
    180deg,
    rgba(1, 16, 20, 0.16) 0%,
    transparent 28%,
    transparent 72%,
    rgba(1, 14, 18, 0.24) 100%
  );
}

.login-center {
  position: relative;
  z-index: 1;
  width: min(100%, 420px);
  max-width: 420px;
  max-height: calc(100dvh - 32px);
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 24px;
  padding: clamp(24px, 4vh, 42px) clamp(24px, 3vw, 40px);
  border: 1px solid rgba(224, 244, 236, 0.18);
  border-radius: 24px;
  background: linear-gradient(145deg, rgba(15, 61, 64, 0.80), rgba(3, 25, 30, 0.91));
  box-shadow:
    0 28px 80px rgba(0, 10, 14, 0.42),
    0 8px 24px rgba(0, 16, 20, 0.22),
    inset 0 1px 0 rgba(255, 255, 255, 0.10);
  backdrop-filter: blur(18px) saturate(120%);
  -webkit-backdrop-filter: blur(18px) saturate(120%);
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: rgba(190, 225, 215, 0.30) transparent;
  animation: fadeUp 0.6s ease-out both;
}

.login-center::before {
  content: '';
  position: absolute;
  top: 0;
  left: 28px;
  right: 28px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(244, 184, 145, 0.75), transparent);
  pointer-events: none;
}

.login-center::-webkit-scrollbar {
  width: 4px;
}

.login-center::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(190, 225, 215, 0.30);
}

/* Logo */
.login-logo {
  text-align: center;
}

.logo-image {
  display: block;
  margin: 0 auto 14px;
  width: 88px;
  height: 88px;
  object-fit: contain;
  filter: drop-shadow(0 8px 24px rgba(238, 153, 111, 0.34));
  animation: breathe 3.5s ease-in-out infinite;
}

.logo-title {
  font-size: 36px;
  font-weight: 800;
  color: #f3f8f5;
  margin: 0;
  letter-spacing: -0.04em;
}

.logo-title-highlight {
  color: #f0a17d;
}

/* Form */
.login-form {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.form-input {
  width: 100%;
  padding: 14px 16px;
  border: 1.5px solid rgba(214, 239, 231, 0.20);
  border-radius: 12px;
  font-size: 15px;
  color: #f2f8f5;
  background: rgba(1, 20, 24, 0.34);
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
}

.form-input::placeholder {
  color: rgba(214, 233, 226, 0.54);
}

.form-input--has-eye {
  padding-right: 44px;
}

.form-input:focus {
  border-color: #eea17c;
  background: rgba(2, 25, 29, 0.56);
  box-shadow: 0 0 0 3px rgba(217, 109, 70, 0.20), 0 10px 24px rgba(0, 12, 15, 0.16);
}

.eye-btn {
  position: absolute;
  right: 12px;
  width: 28px;
  height: 28px;
  border: none;
  background: none;
  cursor: pointer;
  color: rgba(207, 229, 222, 0.64);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
}

.eye-btn:hover {
  color: #f0a17d;
}

/* Error */
.error-msg {
  padding: 10px 14px;
  background: rgba(128, 40, 34, 0.34);
  border: 1px solid rgba(255, 166, 150, 0.48);
  border-radius: 10px;
  font-size: 13px;
  color: #ffd9d0;
}

/* Button */
.login-btn {
  width: 100%;
  padding: 12px;
  background: linear-gradient(135deg, var(--mc-primary), var(--mc-primary-hover));
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  margin-top: 4px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(217, 119, 87, 0.3);
}

.login-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

/* SSO buttons */
.sso-divider {
  text-align: center;
  font-size: 12px;
  color: rgba(207, 229, 222, 0.62);
  margin: 4px 0;
  position: relative;
}
.sso-divider::before,
.sso-divider::after {
  content: '';
  display: inline-block;
  width: 30%;
  height: 1px;
  background: rgba(207, 229, 222, 0.20);
  vertical-align: middle;
  margin: 0 8px;
}
.sso-buttons {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}
.sso-btn {
  width: 100%;
  padding: 11px;
  background: rgba(255, 255, 255, 0.06);
  color: #edf6f1;
  border: 1.5px solid rgba(214, 239, 231, 0.20);
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  height: 44px;
}
.sso-btn:hover:not(:disabled) {
  border-color: rgba(240, 161, 125, 0.82);
  color: #f0a17d;
  background: rgba(217, 109, 70, 0.12);
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
  color: #f3f8f5;
  margin: 0;
}
.bind-desc {
  font-size: 13px;
  color: rgba(207, 229, 222, 0.68);
  margin: 0;
}
.bind-cancel {
  width: 100%;
  padding: 8px;
  background: none;
  color: rgba(207, 229, 222, 0.68);
  border: none;
  font-size: 13px;
  cursor: pointer;
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
  background: white;
  border-radius: 50%;
  animation: bounce 1.2s infinite;
}

.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-5px); }
}

/* Hint */
.login-hint {
  text-align: center;
  font-size: 12px;
  color: rgba(207, 229, 222, 0.62);
  margin: 0;
  opacity: 0.9;
}

.login-hint :deep(code) {
  background: rgba(217, 109, 70, 0.16);
  padding: 1px 6px;
  border-radius: 4px;
  color: #f3b08e;
  font-size: 12px;
}

/* Breathing animation */
@keyframes breathe {
  0%, 100% {
    transform: scale(1);
    filter: drop-shadow(0 6px 20px rgba(217, 119, 87, 0.3));
  }
  50% {
    transform: scale(1.06);
    filter: drop-shadow(0 8px 28px rgba(217, 119, 87, 0.45));
  }
}

/* Entrance animation */
@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Tablet: keep the right-side composition while reducing the edge inset. */
@media (max-width: 900px) {
  .login-page {
    padding-right: clamp(20px, 4vw, 48px);
  }
}

/* Mobile: center the card so the desktop artwork is not over-cropped. */
@media (max-width: 767px) {
  .login-page {
    justify-content: center;
    padding: 16px;
    background-position: 32% center;
  }

  .login-page::after {
    background: linear-gradient(
      180deg,
      rgba(1, 16, 20, 0.26) 0%,
      rgba(1, 16, 20, 0.10) 40%,
      rgba(1, 14, 18, 0.54) 100%
    );
  }

  .login-center {
    width: min(100%, 420px);
    padding: 28px 22px;
    border-radius: 20px;
  }
}

/* Short screens: keep the normal desktop view scroll-free and make the
   card compact before falling back to its internal overflow as a last resort. */
@media (max-height: 720px) {
  .login-page {
    padding-block: 12px;
  }

  .login-center {
    gap: 18px;
    padding-block: 20px;
  }

  .logo-image {
    width: 70px;
    height: 70px;
    margin-bottom: 8px;
  }

  .logo-title {
    font-size: 30px;
  }

  .login-form {
    gap: 10px;
  }

  .form-input {
    padding-block: 11px;
  }

  .login-btn {
    height: 44px;
  }
}

@media (max-width: 480px) {
  .login-page {
    padding: 12px;
  }

  .login-center {
    padding-inline: 20px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-center,
  .logo-image {
    animation: none;
  }
}
</style>
