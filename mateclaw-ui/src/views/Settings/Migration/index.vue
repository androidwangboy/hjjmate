<template>
  <div class="settings-section migration-section">
    <div class="section-header">
      <h2 class="section-title">{{ t('settings.migration.title') }}</h2>
      <p class="section-desc">{{ t('settings.migration.desc') }}</p>
    </div>

    <!-- 非破坏性保证 -->
    <div class="settings-card safety-card">
      <div class="safety-badge">
        <span class="safety-icon">🛡️</span>
        <div>
          <div class="safety-title">{{ t('settings.migration.safetyTitle') }}</div>
          <ul class="safety-list">
            <li>{{ t('settings.migration.safety1') }}</li>
            <li>{{ t('settings.migration.safety2') }}</li>
            <li>{{ t('settings.migration.safety3') }}</li>
          </ul>
        </div>
      </div>
    </div>

    <!-- 导出 -->
    <div class="settings-card">
      <h3 class="card-title">{{ t('settings.migration.exportTitle') }}</h3>
      <p class="card-hint">{{ t('settings.migration.exportHint') }}</p>

      <div class="module-grid">
        <label v-for="m in modules" :key="m.value" class="module-item">
          <input v-model="selectedModules" type="checkbox" :value="m.value" />
          <span>{{ m.label }}</span>
        </label>
      </div>

      <div class="setting-item">
        <div class="setting-info">
          <div class="setting-label">{{ t('settings.migration.pickAgents') }}</div>
          <div class="setting-hint">{{ t('settings.migration.pickAgentsHint') }}</div>
        </div>
        <div class="setting-control">
          <select v-model="selectedAgent" class="form-input">
            <option value="">{{ t('settings.migration.allAgents') }}</option>
            <option v-for="a in agents" :key="a.id" :value="a.name">{{ a.name }}</option>
          </select>
        </div>
      </div>

      <label class="inline-check">
        <input v-model="includeKb" type="checkbox" />
        <span>{{ t('settings.migration.includeKb') }}</span>
      </label>
      <label class="inline-check">
        <input v-model="includePages" type="checkbox" />
        <span>{{ t('settings.migration.includePages') }}</span>
      </label>
      <label class="inline-check">
        <input v-model="includeMemory" type="checkbox" />
        <span>{{ t('settings.migration.includeMemory') }}</span>
      </label>

      <div class="form-actions">
        <button class="btn-primary" :disabled="exporting" @click="doExport">
          {{ exporting ? t('common.loading') : t('settings.migration.exportBtn') }}
        </button>
      </div>
    </div>

    <!-- 导入 -->
    <div class="settings-card">
      <h3 class="card-title">{{ t('settings.migration.importTitle') }}</h3>
      <p class="card-hint">{{ t('settings.migration.importHint') }}</p>

      <div class="setting-item">
        <div class="setting-info">
          <div class="setting-label">{{ t('settings.migration.conflictPolicy') }}</div>
          <div class="setting-hint">{{ t('settings.migration.conflictPolicyHint') }}</div>
        </div>
        <div class="setting-control">
          <select v-model="onConflict" class="form-input">
            <option value="rename">{{ t('settings.migration.policyRename') }}</option>
            <option value="skip">{{ t('settings.migration.policySkip') }}</option>
            <option value="fail">{{ t('settings.migration.policyFail') }}</option>
          </select>
        </div>
      </div>

      <p class="card-hint">{{ t('settings.migration.importScopeHint') }}</p>

      <div class="file-drop" @click="pickFile" @dragover.prevent @drop.prevent="onDrop">
        <input ref="fileInput" type="file" accept=".mcbundle,.zip" hidden @change="onFile" />
        <span v-if="!file">{{ t('settings.migration.pickFile') }}</span>
        <span v-else class="file-name">{{ file.name }}（{{ Math.round(file.size / 1024) }} KB）</span>
      </div>

      <div class="form-actions">
        <button class="btn-secondary" :disabled="!file || previewing" @click="doPreview">
          {{ previewing ? t('common.loading') : t('settings.migration.previewBtn') }}
        </button>
        <button class="btn-primary" :disabled="!file || applying" @click="doApply">
          {{ applying ? t('common.loading') : t('settings.migration.importBtn') }}
        </button>
      </div>

      <!-- 预览结果 -->
      <div v-if="preview" class="preview-panel">
        <div class="preview-summary">
          <span class="chip chip-create">create {{ countOf('create') }}</span>
          <span class="chip chip-skip">skip {{ countOf('skip') }}</span>
          <span class="chip chip-rename">rename {{ countOf('rename') }}</span>
        </div>
        <table class="preview-table">
          <thead>
            <tr>
              <th>{{ t('settings.migration.colModule') }}</th>
              <th>{{ t('settings.migration.colName') }}</th>
              <th>{{ t('settings.migration.colAction') }}</th>
              <th>{{ t('settings.migration.colDetail') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(d, i) in preview.items" :key="i">
              <td>{{ d.module }}</td>
              <td class="cell-name">{{ d.name }}</td>
              <td><span class="chip" :class="`chip-${d.action}`">{{ d.action }}</span></td>
              <td class="cell-detail">{{ d.detail }}</td>
            </tr>
          </tbody>
        </table>
        <ul v-if="preview.warnings && preview.warnings.length" class="warn-list">
          <li v-for="(w, i) in preview.warnings" :key="i">⚠️ {{ w }}</li>
        </ul>
      </div>

      <!-- 导入结果 -->
      <div v-if="result" class="result-panel">
        <div class="result-line">
          ✅ {{ t('settings.migration.importDone') }}
          <strong>{{ result.created }}</strong> ·
          skip {{ result.skipped }} · rename {{ result.renamed }}
        </div>
        <div class="result-line batch-line">
          {{ t('settings.migration.batchId') }}：<code>{{ result.batchId }}</code>
          <button class="btn-link" @click="doRevert(result.batchId)">{{ t('settings.migration.revertBtn') }}</button>
        </div>
        <ul v-if="result.warnings && result.warnings.length" class="warn-list">
          <li v-for="(w, i) in result.warnings" :key="i">⚠️ {{ w }}</li>
        </ul>
      </div>
    </div>

    <!-- 撤销 -->
    <div class="settings-card">
      <h3 class="card-title">{{ t('settings.migration.revertTitle') }}</h3>
      <p class="card-hint">{{ t('settings.migration.revertHint') }}</p>
      <div class="revert-row">
        <input v-model="revertBatch" class="form-input" :placeholder="t('settings.migration.batchPlaceholder')" />
        <button class="btn-secondary" :disabled="!revertBatch || reverting" @click="doRevert(revertBatch)">
          {{ reverting ? t('common.loading') : t('settings.migration.revertBtn') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { agentApi } from '@/api'
import { mcToast } from '@/composables/useMcToast'
const ctxPath = import.meta.env.BASE_URL.length > 1 ? import.meta.env.BASE_URL.replace(/\/+$/, '') : ''
const { t } = useI18n()

const agents = ref<any[]>([])
const selectedModules = ref<string[]>(['agents', 'teams', 'skills', 'kbs', 'workflows', 'triggers'])
const selectedAgent = ref('')
const includeKb = ref(true)
const includePages = ref(false)
const includeMemory = ref(true)
const exporting = ref(false)

const file = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const onConflict = ref('rename')
const preview = ref<any>(null)
const result = ref<any>(null)
const previewing = ref(false)
const applying = ref(false)
const revertBatch = ref('')
const reverting = ref(false)

const modules = computed(() => [
  { value: 'agents', label: t('settings.migration.modAgents') },
  { value: 'teams', label: t('settings.migration.modTeams') },
  { value: 'skills', label: t('settings.migration.modSkills') },
  { value: 'kbs', label: t('settings.migration.modKbs') },
  { value: 'workflows', label: t('settings.migration.modWorkflows') },
  { value: 'triggers', label: t('settings.migration.modTriggers') },
])

function countOf(action: string) {
  return (preview.value?.items || []).filter((i: any) => i.action === action).length
}

function authHeaders(): Record<string, string> {
  const h: Record<string, string> = {}
  const token = localStorage.getItem('token')
  if (token) h.Authorization = `Bearer ${token}`
  const ws = localStorage.getItem('workspaceId') || localStorage.getItem('currentWorkspaceId')
  if (ws) h['X-Workspace-Id'] = ws
  return h
}

onMounted(async () => {
  try {
    const res: any = await agentApi.list()
    agents.value = res?.data || []
  } catch {
    agents.value = []
  }
})

async function doExport() {
  exporting.value = true
  try {
    const params = new URLSearchParams()
    if (selectedModules.value.length) params.set('modules', selectedModules.value.join(','))
    if (selectedAgent.value) params.set('agents', selectedAgent.value)
    params.set('includeKnowledgeBase', String(includeKb.value))
    params.set('includePages', String(includePages.value))
    params.set('includeMemory', String(includeMemory.value))
    const res = await fetch(`${ctxPath}/api/v1/portability/export?${params.toString()}`, { headers: authHeaders() })
    if (!res.ok) {
      const body = await res.json().catch(() => ({}))
      throw new Error(body?.msg || `HTTP ${res.status}`)
    }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `assets-${new Date().toISOString().slice(0, 10)}.mcbundle`
    a.click()
    URL.revokeObjectURL(url)
    mcToast.success(t('settings.migration.exportOk'))
  } catch (e: any) {
    mcToast.error(e?.message || t('settings.migration.exportFail'))
  } finally {
    exporting.value = false
  }
}

function pickFile() {
  fileInput.value?.click()
}
function onFile(e: Event) {
  const target = e.target as HTMLInputElement
  file.value = target.files?.[0] || null
  preview.value = null
  result.value = null
}
function onDrop(e: DragEvent) {
  file.value = e.dataTransfer?.files?.[0] || null
  preview.value = null
  result.value = null
}

async function doPreview() {
  if (!file.value) return
  previewing.value = true
  try {
    const fd = new FormData()
    fd.append('file', file.value)
    const params = new URLSearchParams()
    if (selectedModules.value.length) params.set('modules', selectedModules.value.join(','))
    const res = await fetch(`${ctxPath}/api/v1/portability/import/preview?${params.toString()}`, {
      method: 'POST',
      headers: authHeaders(),
      body: fd,
    })
    const body = await res.json()
    if (!res.ok || body.code !== 200) throw new Error(body?.msg || `HTTP ${res.status}`)
    preview.value = body.data
  } catch (e: any) {
    mcToast.error(e?.message || t('settings.migration.previewFail'))
  } finally {
    previewing.value = false
  }
}

async function doApply() {
  if (!file.value) return
  applying.value = true
  try {
    const fd = new FormData()
    fd.append('file', file.value)
    const params = new URLSearchParams({ onConflict: onConflict.value })
    if (selectedModules.value.length) params.set('modules', selectedModules.value.join(','))
    const res = await fetch(`${ctxPath}/api/v1/portability/import?${params.toString()}`, {
      method: 'POST',
      headers: authHeaders(),
      body: fd,
    })
    const body = await res.json()
    if (!res.ok || body.code !== 200) throw new Error(body?.msg || `HTTP ${res.status}`)
    result.value = body.data
    preview.value = null
    mcToast.success(t('settings.migration.importOk'))
  } catch (e: any) {
    mcToast.error(e?.message || t('settings.migration.importFail'))
  } finally {
    applying.value = false
  }
}

async function doRevert(batchId: string) {
  if (!batchId) return
  if (!window.confirm(t('settings.migration.revertConfirm', { batch: batchId }))) return
  reverting.value = true
  try {
    const res = await fetch(`${ctxPath}/api/v1/portability/import/${encodeURIComponent(batchId)}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    const body = await res.json()
    if (!res.ok || body.code !== 200) throw new Error(body?.msg || `HTTP ${res.status}`)
    mcToast.success(t('settings.migration.revertOk', { n: body.data?.removed ?? 0 }))
    result.value = null
    revertBatch.value = ''
  } catch (e: any) {
    mcToast.error(e?.message || t('settings.migration.revertFail'))
  } finally {
    reverting.value = false
  }
}
</script>

<style scoped>
.migration-section { display: flex; flex-direction: column; gap: 16px; }
.section-header { margin-bottom: 4px; }
.section-title { font-size: 20px; font-weight: 700; margin: 0 0 6px; color: var(--mc-text-primary); }
.section-desc { font-size: 13px; color: var(--mc-text-secondary); margin: 0; line-height: 1.6; }
.settings-card {
  background: var(--mc-bg-elevated);
  border: 1px solid var(--mc-border-light);
  border-radius: 16px;
  padding: 18px 20px;
}
.card-title { font-size: 15px; font-weight: 700; margin: 0 0 6px; color: var(--mc-text-primary); }
.card-hint { font-size: 12px; color: var(--mc-text-tertiary); margin: 0 0 14px; line-height: 1.6; }

.safety-card { border-color: rgba(34, 197, 94, 0.28); background: color-mix(in srgb, var(--mc-success) 6%, var(--mc-bg-elevated)); }
.safety-badge { display: flex; gap: 12px; align-items: flex-start; }
.safety-icon { font-size: 22px; }
.safety-title { font-size: 14px; font-weight: 700; color: var(--mc-text-primary); margin-bottom: 6px; }
.safety-list { margin: 0; padding-left: 18px; font-size: 12px; color: var(--mc-text-secondary); line-height: 1.9; }

.module-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 8px; margin-bottom: 14px; }
.module-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--mc-text-secondary); cursor: pointer; }
.inline-check { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--mc-text-secondary); margin-bottom: 14px; }

.setting-item { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 10px 0; border-top: 1px solid var(--mc-border-light); }
.setting-label { font-size: 13px; font-weight: 600; color: var(--mc-text-primary); }
.setting-hint { font-size: 11px; color: var(--mc-text-tertiary); margin-top: 2px; }
.setting-control { flex-shrink: 0; min-width: 200px; }
.form-input {
  width: 100%; padding: 7px 10px; border-radius: 10px; font-size: 13px;
  border: 1px solid var(--mc-border); background: var(--mc-bg-muted); color: var(--mc-text-primary);
}

.form-actions { display: flex; gap: 10px; margin-top: 14px; }
.btn-primary, .btn-secondary {
  padding: 8px 16px; border-radius: 10px; font-size: 13px; font-weight: 600; cursor: pointer; border: 1px solid transparent;
}
.btn-primary { background: var(--mc-primary); color: #fff; }
.btn-secondary { background: var(--mc-bg-muted); color: var(--mc-text-primary); border-color: var(--mc-border); }
.btn-primary:disabled, .btn-secondary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-link { background: none; border: none; color: var(--mc-primary); cursor: pointer; font-size: 12px; margin-left: 8px; }

.file-drop {
  border: 1px dashed var(--mc-border); border-radius: 14px; padding: 22px; text-align: center;
  color: var(--mc-text-tertiary); font-size: 13px; cursor: pointer; margin-bottom: 14px;
  background: var(--mc-bg-muted);
}
.file-name { color: var(--mc-text-primary); font-weight: 600; }

.preview-panel, .result-panel { margin-top: 16px; border-top: 1px solid var(--mc-border-light); padding-top: 14px; }
.preview-summary { display: flex; gap: 8px; margin-bottom: 10px; }
.chip { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 999px; background: var(--mc-bg-muted); color: var(--mc-text-secondary); }
.chip-create { background: color-mix(in srgb, var(--mc-success) 16%, transparent); color: var(--mc-success); }
.chip-skip { background: var(--mc-bg-sunken); color: var(--mc-text-tertiary); }
.chip-rename { background: color-mix(in srgb, var(--mc-primary) 16%, transparent); color: var(--mc-primary); }
.preview-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.preview-table th { text-align: left; color: var(--mc-text-tertiary); font-weight: 600; padding: 6px 8px; border-bottom: 1px solid var(--mc-border-light); }
.preview-table td { padding: 6px 8px; border-bottom: 1px solid var(--mc-border-light); color: var(--mc-text-secondary); vertical-align: top; }
.cell-name { color: var(--mc-text-primary); font-weight: 600; }
.cell-detail { color: var(--mc-text-tertiary); }
.warn-list { margin: 12px 0 0; padding-left: 18px; font-size: 12px; color: var(--mc-warning, #b45309); line-height: 1.8; }
.result-line { font-size: 13px; color: var(--mc-text-primary); margin-bottom: 6px; }
.batch-line { font-size: 12px; color: var(--mc-text-secondary); }
.batch-line code { background: var(--mc-bg-muted); padding: 2px 6px; border-radius: 6px; }
.revert-row { display: flex; gap: 10px; }
</style>
