<template>
  <div class="mc-page-shell agent-api-page">
    <div class="mc-page-frame">
      <div class="mc-page-inner">
        <div class="mc-page-header agent-api-header">
          <div class="agent-api-title-wrap">
            <button class="btn-secondary back-button" @click="router.push('/agents')">← {{ t('common.back', '返回') }}</button>
            <div>
              <div class="mc-page-kicker">{{ t('agentApi.kicker', 'EXPERT API') }}</div>
              <h1 class="mc-page-title">{{ t('agentApi.title', '专家 API 控制台') }}</h1>
              <p class="mc-page-desc">{{ t('agentApi.desc', '发布、鉴权并观测外部系统对该专家的调用。') }}</p>
            </div>
          </div>
          <span v-if="publication" class="api-status" :class="publication.enabled ? 'is-on' : 'is-off'">
            {{ publication.enabled ? t('agentApi.status.published', '已发布') : t('agentApi.status.unpublished', '未发布') }}
          </span>
        </div>

        <div v-if="loading" class="mc-surface-card api-loading">{{ t('common.loading', '加载中…') }}</div>
        <div v-else class="api-console-layout">
          <aside class="api-console-nav mc-surface-card">
            <div class="api-nav-agent">
              <span class="api-nav-agent__icon">🤖</span>
              <div><strong>{{ agentName || `Agent #${agentId}` }}</strong><small>API Console</small></div>
            </div>
            <button v-for="item in navItems" :key="item.key" class="api-nav-item" :class="{ active: activeSection === item.key }" @click="activeSection = item.key">
              <span>{{ item.icon }}</span>{{ item.label }}
            </button>
          </aside>

          <main class="api-console-main">
            <section v-if="activeSection === 'overview'">
              <div class="section-heading"><div><h2>{{ t('agentApi.overview.title', '概览') }}</h2><p>{{ t('agentApi.overview.subtitle', '先完成发布，再把快速开始代码交给外部系统。') }}</p></div><button class="btn-primary" @click="activeSection = 'settings'">{{ t('agentApi.overview.settings', '发布设置') }}</button></div>
              <div class="api-stat-grid">
                <div class="api-stat-card"><span>{{ t('agentApi.stats.calls', '今日调用') }}</span><strong>{{ publication?.todayCalls ?? stats?.totalCalls ?? 0 }}</strong></div>
                <div class="api-stat-card"><span>{{ t('agentApi.stats.success', '成功率') }}</span><strong>{{ formatPercent(publication?.successRate ?? stats?.successRate ?? 0) }}</strong></div>
                <div class="api-stat-card"><span>{{ t('agentApi.stats.latency', '平均耗时') }}</span><strong>{{ publication?.averageLatencyMs ?? stats?.averageLatencyMs ?? 0 }}ms</strong></div>
                <div class="api-stat-card"><span>{{ t('agentApi.stats.keys', '有效 Key') }}</span><strong>{{ publication?.keyCount ?? keys.length }}</strong></div>
              </div>
              <div class="quick-start-card mc-surface-card">
                <div class="quick-start-head"><div><span class="eyebrow">{{ t('agentApi.quickStart.eyebrow', 'QUICK START') }}</span><h3>{{ t('agentApi.quickStart.title', '三步接入专家') }}</h3><p>{{ t('agentApi.quickStart.desc', '开启 API、创建 Key、复制示例。复杂调用再进入文档和测试。') }}</p></div><span class="quick-start-step">1 · 2 · 3</span></div>
                <div class="quick-start-steps"><span class="done">✓ {{ publication?.enabled ? t('agentApi.quickStart.published', 'API 已发布') : t('agentApi.quickStart.publish', '开启 API') }}</span><span :class="{ done: keys.length > 0 }">{{ keys.length > 0 ? '✓' : '2' }} {{ t('agentApi.quickStart.key', '创建 Key') }}</span><span>3 {{ t('agentApi.quickStart.call', '开始调用') }}</span></div>
                <div class="code-toolbar"><span>{{ selectedProtocol === 'rest' ? 'MateClaw REST' : 'OpenAI compatible' }}</span><button class="copy-btn" @click="copyExample">{{ t('agentApi.actions.copy', '复制示例') }}</button></div>
                <pre class="api-code"><code>{{ quickStartCode }}</code></pre>
              </div>
              <div class="overview-grid">
                <div class="mc-surface-card overview-card"><h3>{{ t('agentApi.overview.endpoints', '调用入口') }}</h3><div class="endpoint-row"><span class="method post">POST</span><code>/api/v1/open/agent/chat</code><button @click="copyText('/api/v1/open/agent/chat')">⧉</button></div><div class="endpoint-row"><span class="method post">POST</span><code>/api/v1/open/agent/stream</code><button @click="copyText('/api/v1/open/agent/stream')">⧉</button></div><div class="endpoint-row"><span class="method post">POST</span><code>/v1/chat/completions</code><button @click="copyText('/v1/chat/completions')">⧉</button></div></div>
                <div class="mc-surface-card overview-card"><h3>{{ t('agentApi.overview.capabilities', '已支持能力') }}</h3><div class="capability-list"><span>✓ {{ t('agentApi.capabilities.sync', '同步响应') }}</span><span>✓ {{ t('agentApi.capabilities.stream', 'SSE 流式') }}</span><span>✓ {{ t('agentApi.capabilities.async', '异步任务') }}</span><span>✓ {{ t('agentApi.capabilities.multimodal', '文件与多模态') }}</span><span>✓ {{ t('agentApi.capabilities.memory', '多轮记忆隔离') }}</span></div></div>
              </div>
            </section>

            <section v-else-if="activeSection === 'keys'">
              <div class="section-heading"><div><h2>{{ t('agentApi.keys.title', '凭证') }}</h2><p>{{ t('agentApi.keys.subtitle', '每个外部系统使用独立 Key，撤销不会影响其他调用方。') }}</p></div><button class="btn-primary" @click="showCreateKey = true">＋ {{ t('agentApi.keys.create', '创建 Key') }}</button></div>
              <div class="mc-surface-card table-card"><table><thead><tr><th>{{ t('agentApi.keys.name', '名称') }}</th><th>Key</th><th>{{ t('agentApi.keys.status', '状态') }}</th><th>{{ t('agentApi.keys.lastUsed', '最后使用') }}</th><th></th></tr></thead><tbody><tr v-for="key in keys" :key="key.id"><td><strong>{{ key.name }}</strong><small>{{ formatDate(key.createTime) }}</small></td><td><code>{{ key.keyPrefix }}…</code></td><td><span class="table-status" :class="{ on: key.enabled }">{{ key.enabled ? t('agentApi.status.active', '有效') : t('agentApi.status.revoked', '已撤销') }}</span></td><td>{{ formatDate(key.lastUsedAt) }}</td><td><button v-if="key.enabled" class="text-danger" @click="revokeKey(key)">{{ t('agentApi.actions.revoke', '撤销') }}</button></td></tr><tr v-if="keys.length === 0"><td colspan="5" class="empty-row">{{ t('agentApi.keys.empty', '还没有 API Key。') }}</td></tr></tbody></table></div>
              <div class="security-note">🔐 {{ t('agentApi.keys.once', 'Key 明文只在创建成功时显示一次，请立即保存。') }}</div>
            </section>

            <section v-else-if="activeSection === 'docs'">
              <div class="section-heading"><div><h2>{{ t('agentApi.docs.title', '文档') }}</h2><p>{{ t('agentApi.docs.subtitle', '固定使用专家模型别名 expert，Key 决定调用哪位专家。') }}</p></div><button class="btn-secondary" @click="activeSection = 'test'">{{ t('agentApi.docs.test', '在线测试') }}</button></div>
              <div class="protocol-switch"><button :class="{ active: selectedProtocol === 'rest' }" @click="selectedProtocol = 'rest'">MateClaw REST</button><button :class="{ active: selectedProtocol === 'openai' }" @click="selectedProtocol = 'openai'">OpenAI compatible</button></div>
              <div class="docs-grid"><div class="mc-surface-card docs-copy"><span class="method-label">POST</span><code>{{ selectedProtocol === 'rest' ? '/api/v1/open/agent/chat' : '/v1/chat/completions' }}</code><h3>{{ t('agentApi.docs.request', '请求要求') }}</h3><ul v-if="selectedProtocol === 'rest'"><li><code>conversationId</code>：外部会话 ID，必填</li><li><code>endUserId</code>：外部用户 ID，必填</li><li><code>message</code>：用户问题，或传 contentParts</li></ul><ul v-else><li><code>model</code> 必须为 <code>expert</code></li><li><code>user</code> 映射为 endUserId</li><li><code>conversation_id</code> 必填</li></ul><p class="docs-note">{{ t('agentApi.docs.authNote', '使用 Authorization: Bearer mak_… 认证。') }}</p></div><div class="mc-surface-card docs-code"><div class="code-toolbar"><span>{{ t('agentApi.docs.example', '请求示例') }}</span><button class="copy-btn" @click="copyExample">{{ t('agentApi.actions.copy', '复制') }}</button></div><pre class="api-code"><code>{{ quickStartCode }}</code></pre></div></div>
            </section>

            <section v-else-if="activeSection === 'logs'">
              <div class="section-heading"><div><h2>{{ t('agentApi.logs.title', '调用日志') }}</h2><p>{{ t('agentApi.logs.subtitle', '只记录调用元数据，不保存完整 prompt 和答案正文。') }}</p></div><button class="btn-secondary" @click="loadLogs">↻ {{ t('common.refresh', '刷新') }}</button></div>
              <div class="mc-surface-card table-card"><table><thead><tr><th>Request ID</th><th>{{ t('agentApi.logs.protocol', '协议') }}</th><th>{{ t('agentApi.logs.status', '状态') }}</th><th>{{ t('agentApi.logs.latency', '耗时') }}</th><th>{{ t('agentApi.logs.time', '时间') }}</th></tr></thead><tbody><tr v-for="log in logs" :key="log.requestId"><td><code>{{ log.requestId }}</code></td><td>{{ log.protocol }} · {{ log.mode }}</td><td><span class="table-status" :class="{ on: log.status === 'completed' }">{{ log.status }}</span></td><td>{{ log.latencyMs ?? 0 }}ms</td><td>{{ formatDate(log.createTime) }}</td></tr><tr v-if="logs.length === 0"><td colspan="5" class="empty-row">{{ t('agentApi.logs.empty', '暂无调用记录。') }}</td></tr></tbody></table></div>
            </section>

            <section v-else-if="activeSection === 'stats'">
              <div class="section-heading"><div><h2>{{ t('agentApi.stats.title', '统计') }}</h2><p>{{ t('agentApi.stats.subtitle', '观察调用量、成功率、耗时和 Token 使用。') }}</p></div></div>
              <div class="api-stat-grid api-stat-grid--large"><div class="api-stat-card"><span>{{ t('agentApi.stats.total', '总调用') }}</span><strong>{{ stats?.totalCalls ?? 0 }}</strong></div><div class="api-stat-card"><span>{{ t('agentApi.stats.successCalls', '成功调用') }}</span><strong>{{ stats?.successfulCalls ?? 0 }}</strong></div><div class="api-stat-card"><span>{{ t('agentApi.stats.inputTokens', '输入 Token') }}</span><strong>{{ stats?.inputTokens ?? 0 }}</strong></div><div class="api-stat-card"><span>{{ t('agentApi.stats.outputTokens', '输出 Token') }}</span><strong>{{ stats?.outputTokens ?? 0 }}</strong></div></div>
            </section>

            <section v-else-if="activeSection === 'settings'">
              <div class="section-heading"><div><h2>{{ t('agentApi.settings.title', '设置') }}</h2><p>{{ t('agentApi.settings.subtitle', '发布开关和默认治理会作用于该专家的所有 API Key。') }}</p></div></div>
              <div v-if="publication" class="mc-surface-card settings-card"><label class="settings-toggle"><input v-model="settings.enabled" type="checkbox"><span><strong>{{ t('agentApi.settings.publish', '发布专家 API') }}</strong><small>{{ t('agentApi.settings.publishHint', '关闭后所有外部请求立即拒绝。') }}</small></span></label><div class="settings-grid"><label>{{ t('agentApi.settings.rpm', '每分钟请求数') }}<input v-model.number="settings.requestsPerMinute" type="number" min="1"></label><label>{{ t('agentApi.settings.concurrent', '并发数') }}<input v-model.number="settings.concurrentLimit" type="number" min="1"></label><label>{{ t('agentApi.settings.daily', '每日额度') }}<input v-model.number="settings.dailyQuota" type="number" min="1"></label><label>{{ t('agentApi.settings.timeout', '超时秒数') }}<input v-model.number="settings.timeoutSeconds" type="number" min="1"></label></div><label class="settings-toggle settings-toggle--sub"><input v-model="settings.webhookEnabled" type="checkbox"><span><strong>{{ t('agentApi.settings.webhook', '允许异步 Webhook') }}</strong><small>{{ t('agentApi.settings.webhookHint', '调用方仍需在请求中提供 callbackUrl。') }}</small></span></label><button class="btn-primary" :disabled="savingSettings" @click="saveSettings">{{ savingSettings ? t('common.saving', '保存中…') : t('common.save', '保存设置') }}</button></div>
            </section>

            <section v-else-if="activeSection === 'test'">
              <div class="section-heading"><div><h2>{{ t('agentApi.test.title', '在线测试') }}</h2><p>{{ t('agentApi.test.subtitle', '使用当前登录身份测试，不消耗外部 Key。专家已有工具仍可能执行。') }}</p></div></div>
              <div class="test-layout"><div class="mc-surface-card test-form"><label>{{ t('agentApi.test.endUser', '外部用户 ID') }}<input v-model="testForm.endUserId" /></label><label>{{ t('agentApi.test.conversation', '会话 ID') }}<input v-model="testForm.conversationId" /></label><label>{{ t('agentApi.test.message', '测试问题') }}<textarea v-model="testForm.message" rows="6"></textarea></label><button class="btn-primary" :disabled="testing" @click="runTest">{{ testing ? t('agentApi.test.running', '执行中…') : t('agentApi.test.send', '发送测试') }}</button></div><div class="mc-surface-card test-result"><div class="code-toolbar"><span>{{ t('agentApi.test.result', '响应结果') }}</span><span v-if="testResult" class="table-status on">completed</span></div><pre v-if="testResult" class="test-output">{{ testResult }}</pre><div v-else class="test-placeholder">{{ t('agentApi.test.empty', '发送请求后，响应会显示在这里。') }}</div></div></div>
            </section>
          </main>
        </div>
      </div>
    </div>

    <div v-if="showCreateKey" class="modal-overlay" @click.self="showCreateKey = false"><div class="modal small-modal"><div class="modal-header"><h2>{{ t('agentApi.keys.createTitle', '创建 API Key') }}</h2><button class="modal-close" @click="showCreateKey = false">×</button></div><div class="modal-body"><label class="form-label">{{ t('agentApi.keys.name', '名称') }}<input v-model="newKeyName" class="form-input" :placeholder="t('agentApi.keys.namePlaceholder', '例如：CRM 系统')"></label><p class="form-hint">{{ t('agentApi.keys.createHint', '明文只显示一次。建议每个外部系统单独创建。') }}</p></div><div class="modal-footer"><button class="btn-secondary" @click="showCreateKey = false">{{ t('common.cancel', '取消') }}</button><button class="btn-primary" :disabled="creatingKey || !newKeyName.trim()" @click="createKey">{{ creatingKey ? t('common.saving', '创建中…') : t('agentApi.keys.create', '创建 Key') }}</button></div></div></div>
    <div v-if="createdPlaintext" class="modal-overlay"><div class="modal small-modal"><div class="modal-header"><h2>{{ t('agentApi.keys.createdTitle', 'Key 已创建') }}</h2></div><div class="modal-body"><p class="form-hint">{{ t('agentApi.keys.once', 'Key 明文只在创建成功时显示一次，请立即保存。') }}</p><pre class="api-code api-code--secret">{{ createdPlaintext }}</pre><button class="btn-secondary" @click="copyText(createdPlaintext)">{{ t('agentApi.actions.copy', '复制 Key') }}</button></div><div class="modal-footer"><button class="btn-primary" @click="createdPlaintext = ''">{{ t('common.done', '我已保存') }}</button></div></div></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { agentApiManagement } from '@/api'
import { copyToClipboard } from '@/utils/clipboard'
import { mcConfirm } from '@/components/common/useConfirm'
import { mcToast } from '@/composables/useMcToast'
import type { AgentApiKey, AgentApiLog, AgentApiPublication, AgentApiStats } from '@/types'
import { buildQuickStart, type ApiProtocol } from './AgentApiConsole/apiConsoleLogic'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const agentId = String(route.params.id)
const agentName = ref('')
const loading = ref(true)
const activeSection = ref('overview')
const selectedProtocol = ref<ApiProtocol>('rest')
const publication = ref<AgentApiPublication | null>(null)
const keys = ref<AgentApiKey[]>([])
const stats = ref<AgentApiStats | null>(null)
const logs = ref<AgentApiLog[]>([])
const lastPlaintext = ref('')
const createdPlaintext = ref('')
const showCreateKey = ref(false)
const newKeyName = ref('')
const creatingKey = ref(false)
const savingSettings = ref(false)
const testing = ref(false)
const testResult = ref('')
const settings = reactive({ enabled: false, requestsPerMinute: 60, concurrentLimit: 4, dailyQuota: 10000, timeoutSeconds: 120, webhookEnabled: false })
const testForm = reactive({ endUserId: 'external-user-1', conversationId: `console-test-${Date.now()}`, message: '请介绍一下你能做什么。' })

const navItems = computed(() => [
  { key: 'overview', icon: '▦', label: t('agentApi.nav.overview', '概览') },
  { key: 'keys', icon: '⚿', label: t('agentApi.nav.keys', '凭证') },
  { key: 'docs', icon: '▤', label: t('agentApi.nav.docs', '文档') },
  { key: 'logs', icon: '◷', label: t('agentApi.nav.logs', '调用日志') },
  { key: 'stats', icon: '∿', label: t('agentApi.nav.stats', '统计') },
  { key: 'settings', icon: '⚙', label: t('agentApi.nav.settings', '设置') },
  { key: 'test', icon: '▷', label: t('agentApi.nav.test', '在线测试') },
])

const quickStartCode = computed(() => buildQuickStart(selectedProtocol.value, lastPlaintext.value || '<YOUR_API_KEY>', agentId))

onMounted(async () => {
  try {
    await Promise.all([loadPublication(), loadKeys(), loadStats()])
  } finally {
    loading.value = false
  }
})

async function loadPublication() {
  const res: any = await agentApiManagement.getPublication(agentId)
  publication.value = res.data
  if (publication.value) Object.assign(settings, publication.value)
}

async function loadKeys() {
  const res: any = await agentApiManagement.listKeys(agentId)
  keys.value = res.data || []
}

async function loadStats() {
  const res: any = await agentApiManagement.stats(agentId)
  stats.value = res.data
}

async function loadLogs() {
  const res: any = await agentApiManagement.listLogs(agentId, { page: 1, size: 50 })
  logs.value = res.data || []
}

async function saveSettings() {
  savingSettings.value = true
  try {
    const res: any = await agentApiManagement.updatePublication(agentId, { ...settings, modelAlias: 'expert' })
    publication.value = res.data
    mcToast.success(t('agentApi.messages.saved', 'API 设置已保存'))
  } catch (e: any) {
    mcToast.error(e?.message || t('agentApi.messages.saveFailed', 'API 设置保存失败'))
  } finally {
    savingSettings.value = false
  }
}

async function createKey() {
  creatingKey.value = true
  try {
    const res: any = await agentApiManagement.createKey(agentId, { name: newKeyName.value.trim() })
    lastPlaintext.value = res.data?.plaintext || ''
    createdPlaintext.value = lastPlaintext.value
    showCreateKey.value = false
    newKeyName.value = ''
    await loadKeys()
    mcToast.success(t('agentApi.messages.keyCreated', 'API Key 已创建'))
  } catch (e: any) {
    mcToast.error(e?.message || t('agentApi.messages.keyFailed', 'API Key 创建失败'))
  } finally {
    creatingKey.value = false
  }
}

async function revokeKey(key: AgentApiKey) {
  const confirmed = await mcConfirm({
    title: t('agentApi.keys.revokeTitle', '撤销 API Key'),
    message: t('agentApi.keys.revokeConfirm', { name: key.name }, `确认撤销「${key.name}」？`),
    tone: 'danger',
  })
  if (!confirmed) return
  try {
    await agentApiManagement.revokeKey(agentId, key.id)
    await loadKeys()
    mcToast.success(t('agentApi.messages.revoked', 'API Key 已撤销'))
  } catch (e: any) {
    mcToast.error(e?.message || t('agentApi.messages.revokeFailed', 'API Key 撤销失败'))
  }
}

async function runTest() {
  testing.value = true
  testResult.value = ''
  try {
    const res: any = await agentApiManagement.test(agentId, { ...testForm })
    testResult.value = res.data?.content || JSON.stringify(res.data, null, 2)
  } catch (e: any) {
    testResult.value = e?.message || t('agentApi.test.failed', '测试失败')
  } finally {
    testing.value = false
  }
}

async function copyExample() {
  await copyText(quickStartCode.value)
}

async function copyText(value: string) {
  try {
    await copyToClipboard(value)
    mcToast.success(t('agentApi.messages.copied', '已复制'))
  } catch {
    mcToast.error(t('agentApi.messages.copyFailed', '复制失败'))
  }
}

function formatPercent(value: number) {
  return `${Number(value || 0).toFixed(1)}%`
}

function formatDate(value?: string | null) {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}
</script>

<style scoped>
.agent-api-header { align-items: flex-start; }
.agent-api-title-wrap { display:flex; align-items:flex-start; gap:14px; }
.back-button { margin-top:4px; white-space:nowrap; }
.api-status, .table-status { display:inline-flex; align-items:center; border-radius:999px; padding:5px 10px; font-size:12px; font-weight:700; }
.api-status.is-on, .table-status.on { color:#166534; background:#dcfce7; }
.api-status.is-off { color:#92400e; background:#fef3c7; }
.api-console-layout { display:grid; grid-template-columns:190px minmax(0,1fr); gap:18px; align-items:start; }
.api-console-nav { padding:12px; position:sticky; top:18px; }
.api-nav-agent { display:flex; gap:9px; align-items:center; padding:10px 8px 14px; border-bottom:1px solid var(--mc-border,#e5e7eb); margin-bottom:8px; }
.api-nav-agent__icon { font-size:26px; }
.api-nav-agent strong { display:block; font-size:13px; color:var(--mc-text,#172033); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:118px; }
.api-nav-agent small { color:var(--mc-text-tertiary,#98a2b3); font-size:10px; }
.api-nav-item { width:100%; border:0; background:transparent; text-align:left; padding:9px 10px; border-radius:7px; color:var(--mc-text-secondary,#667085); cursor:pointer; display:flex; gap:9px; align-items:center; font-size:13px; }
.api-nav-item:hover, .api-nav-item.active { background:#eff6ff; color:#1d4ed8; font-weight:650; }
.api-console-main { min-width:0; }
.section-heading { display:flex; justify-content:space-between; gap:12px; align-items:flex-start; margin-bottom:16px; }
.section-heading h2 { margin:0; font-size:22px; color:var(--mc-text,#172033); }
.section-heading p { margin:5px 0 0; color:var(--mc-text-tertiary,#667085); font-size:13px; }
.api-stat-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:10px; margin-bottom:16px; }
.api-stat-card { background:var(--mc-surface,#fff); border:1px solid var(--mc-border,#e5e7eb); border-radius:10px; padding:14px; }
.api-stat-card span { display:block; color:#667085; font-size:12px; }
.api-stat-card strong { display:block; margin-top:7px; color:#172033; font-size:24px; letter-spacing:-.03em; }
.quick-start-card { padding:18px; }
.quick-start-head { display:flex; justify-content:space-between; gap:16px; }
.eyebrow { color:#2563eb; letter-spacing:.12em; font-size:10px; font-weight:800; }
.quick-start-head h3 { margin:5px 0 4px; font-size:18px; }
.quick-start-head p { margin:0; color:#667085; font-size:12px; }
.quick-start-step { color:#2563eb; font-weight:800; font-size:13px; }
.quick-start-steps { display:flex; gap:18px; margin:17px 0 14px; color:#667085; font-size:12px; }
.quick-start-steps .done { color:#15803d; }
.code-toolbar { display:flex; align-items:center; justify-content:space-between; color:#667085; font-size:11px; margin-bottom:6px; }
.copy-btn { border:0; background:transparent; color:#2563eb; cursor:pointer; font-size:11px; }
.api-code { margin:0; border-radius:8px; background:#0f172a; color:#d1fae5; padding:14px; overflow:auto; font:12px/1.65 ui-monospace,SFMono-Regular,Consolas,monospace; white-space:pre-wrap; word-break:break-word; }
.api-code--secret { margin:12px 0; font-size:13px; }
.overview-grid { display:grid; grid-template-columns:1fr 1fr; gap:14px; margin-top:14px; }
.overview-card { padding:16px; }
.overview-card h3 { margin:0 0 13px; font-size:15px; }
.endpoint-row { display:flex; align-items:center; gap:7px; padding:8px 0; border-top:1px solid #eef0f3; font-size:11px; }
.endpoint-row code { flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.endpoint-row button { border:0; background:transparent; color:#2563eb; cursor:pointer; }
.method { font-size:9px; font-weight:800; color:#15803d; background:#dcfce7; padding:3px 5px; border-radius:4px; }
.capability-list { display:grid; grid-template-columns:1fr 1fr; gap:9px; color:#475467; font-size:12px; }
.table-card { overflow:auto; }
table { width:100%; border-collapse:collapse; font-size:12px; }
th, td { padding:12px 14px; border-bottom:1px solid #eef0f3; text-align:left; white-space:nowrap; }
th { color:#667085; font-size:11px; font-weight:650; background:#fafbfc; }
td strong, td small { display:block; } td small { margin-top:3px; color:#98a2b3; font-size:10px; }
td code { font-size:11px; color:#475467; }
.text-danger { border:0; background:transparent; color:#dc2626; cursor:pointer; }
.empty-row { color:#98a2b3; text-align:center; padding:30px; }
.security-note { color:#667085; font-size:12px; padding:12px 2px; }
.protocol-switch { display:flex; gap:5px; margin-bottom:12px; }
.protocol-switch button { border:1px solid #d0d5dd; background:#fff; color:#667085; border-radius:7px; padding:8px 12px; cursor:pointer; font-size:12px; }
.protocol-switch button.active { border-color:#93c5fd; color:#1d4ed8; background:#eff6ff; font-weight:650; }
.docs-grid, .test-layout { display:grid; grid-template-columns:1fr 1.1fr; gap:14px; }
.docs-copy, .docs-code, .test-form, .test-result, .settings-card { padding:16px; }
.method-label { color:#15803d; font-weight:800; font-size:11px; margin-right:8px; }
.docs-copy > code { color:#344054; font-size:12px; }
.docs-copy h3 { margin:22px 0 8px; font-size:14px; }
.docs-copy ul { margin:0; padding-left:19px; color:#667085; font-size:12px; line-height:1.9; }
.docs-note { color:#2563eb; font-size:12px; }
.settings-toggle { display:flex; gap:10px; align-items:flex-start; padding-bottom:18px; border-bottom:1px solid #eef0f3; }
.settings-toggle input { margin-top:3px; }
.settings-toggle strong, .settings-toggle small { display:block; } .settings-toggle small { color:#667085; margin-top:4px; font-size:11px; }
.settings-toggle--sub { border:0; padding-top:18px; }
.settings-grid { display:grid; grid-template-columns:1fr 1fr; gap:14px; padding-top:18px; }
.settings-grid label, .test-form label { display:flex; flex-direction:column; gap:6px; color:#475467; font-size:12px; }
.settings-grid input, .test-form input, .test-form textarea { border:1px solid #d0d5dd; border-radius:7px; padding:8px 10px; font:inherit; background:#fff; }
.settings-card > .btn-primary { margin-top:8px; }
.test-form { display:flex; flex-direction:column; gap:13px; }
.test-form textarea { resize:vertical; }
.test-result { min-height:300px; }
.test-output { white-space:pre-wrap; color:#344054; font:13px/1.6 ui-monospace,Consolas,monospace; }
.test-placeholder { color:#98a2b3; font-size:12px; padding:60px 10px; text-align:center; }
.api-loading { padding:40px; text-align:center; color:#667085; }
@media (max-width: 900px) { .api-console-layout { grid-template-columns:1fr; } .api-console-nav { position:static; display:flex; gap:5px; overflow:auto; } .api-nav-agent { display:none; } .api-nav-item { width:auto; white-space:nowrap; } .api-stat-grid { grid-template-columns:repeat(2,1fr); } .overview-grid, .docs-grid, .test-layout { grid-template-columns:1fr; } }
</style>
