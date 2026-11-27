<template>
  <div class="page-container">
    <div class="page-shell">
      <div class="page-header">
        <div class="page-lead">
          <div class="page-kicker">{{ t('datasources.kicker') }}</div>
          <h1 class="page-title">{{ t('datasources.title') }}</h1>
          <p class="page-desc">{{ t('datasources.desc') }}</p>
        </div>
        <button class="btn-primary" @click="openCreateModal">
          <el-icon><Plus /></el-icon>
          {{ t('datasources.addButton') }}
        </button>
      </div>

      <!-- 数据源列表 -->
        <div class="table-wrap">
          <table class="data-table">
        <thead>
          <tr>
            <th>{{ t('datasources.columns.name') }}</th>
            <th>{{ t('datasources.columns.connection') }}</th>
            <th>{{ t('datasources.columns.status') }}</th>
            <th>{{ t('datasources.columns.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="ds in datasources" :key="ds.id" class="data-row">
            <td>
              <div class="tool-info">
                <div class="tool-icon-wrap" :class="{ 'icon-ok': ds.lastTestOk === true, 'icon-fail': ds.lastTestOk === false }">
                  <el-icon><Coin /></el-icon>
                </div>
                <div>
                  <div class="tool-name" :title="ds.name">{{ ds.name }}</div>
                  <div class="tool-type-inline">
                    <span class="type-badge" :class="'type-' + ds.dbType">{{ dbTypeLabel(ds.dbType) }}</span>
                  </div>
                  <div class="tool-desc" v-if="ds.description" :title="ds.description">{{ ds.description }}</div>
                </div>
              </div>
            </td>
            <td>
              <div class="conn-info">
                <code class="conn-host" :title="`${ds.host}:${ds.port}`">{{ ds.host }}:{{ ds.port }}</code>
                <span class="conn-db" :title="`${ds.databaseName}${ds.schemaName ? ` / ${ds.schemaName}` : ''}`">{{ ds.databaseName }}<template v-if="ds.schemaName"> / {{ ds.schemaName }}</template></span>
              </div>
            </td>
            <td>
              <div class="status-cell">
                <span class="status-dot" :class="statusClass(ds)"></span>
                <span class="status-label">{{ ds.lastTestOk === true ? t('datasources.messages.testSuccess') : ds.lastTestOk === false ? t('datasources.messages.testFailed') : 'Pending' }}</span>
                <label class="toggle-switch">
                  <input type="checkbox" :checked="ds.enabled" @change="toggleDs(ds)" />
                  <span class="toggle-slider"></span>
                </label>
              </div>
            </td>
            <td>
              <div class="row-actions">
                <button class="row-btn" @click="openDetailModal(ds)" :title="t('common.view')">
                  <el-icon><View /></el-icon>
                </button>
                <button class="row-btn test-btn" @click="testConnection(ds)" :disabled="testing === ds.id" :title="t('datasources.testButton')">
                  <el-icon v-if="testing !== ds.id"><Select /></el-icon>
                  <span v-else class="spinner"></span>
                </button>
                <button class="row-btn" @click="openEditModal(ds)" :title="t('common.edit')">
                  <el-icon><EditPen /></el-icon>
                </button>
                <!-- 修改人:祝奔；时间：2026-11-19T08:24:55.722Z；修改原因/内容：数据源未启用或连接未成功时均禁用表字段维护按钮；代码来源：AI生成 -->
                <button class="row-btn schema-btn" @click="openSchemaModal(ds)" :disabled="isSchemaDisabled(ds)" :title="schemaButtonTitle(ds)">
                  <el-icon><Grid /></el-icon>
                </button>
                <button class="row-btn danger" @click="deleteDs(ds.id)" :title="t('common.delete')">
                  <el-icon><Delete /></el-icon>
                </button>
              </div>
            </td>
          </tr>
          <tr v-if="datasources.length === 0">
            <td colspan="4" class="empty-row">
              <div class="empty-state">
                <el-icon class="empty-icon"><Coin /></el-icon>
                <p>{{ t('datasources.empty') }}</p>
                <button class="btn-primary" style="margin-top: 8px" @click="openCreateModal">
                  {{ t('datasources.addButton') }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
          </table>
        </div>
    </div>

    <div v-if="detailDs" class="modal-overlay">
      <div class="modal">
        <div class="modal-header">
          <h2>{{ detailDs.name }}</h2>
          <button class="modal-close" @click="closeDetailModal">
            <el-icon><CloseBold /></el-icon>
          </button>
        </div>
        <div class="modal-body detail-grid">
          <div class="detail-item">
            <div class="detail-label">{{ t('datasources.columns.type') }}</div>
            <div class="detail-value">{{ dbTypeLabel(detailDs.dbType) }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">{{ t('datasources.columns.status') }}</div>
            <div class="detail-value">{{ detailDs.enabled ? 'Enabled' : 'Disabled' }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">{{ t('datasources.fields.host') }}</div>
            <div class="detail-value mono">{{ detailDs.host }}:{{ detailDs.port }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">{{ t('datasources.fields.databaseName') }}</div>
            <div class="detail-value">{{ detailDs.databaseName }}</div>
            <div v-if="detailDs.schemaName" class="detail-subvalue">{{ detailDs.schemaName }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">{{ t('datasources.fields.username') }}</div>
            <div class="detail-value">{{ detailDs.username || '-' }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">{{ t('datasources.fields.extraParams') }}</div>
            <div class="detail-value detail-block">{{ detailDs.extraParams || '-' }}</div>
          </div>
          <div class="detail-item detail-item-full" v-if="detailDs.description">
            <div class="detail-label">{{ t('datasources.fields.description') }}</div>
            <div class="detail-value detail-block">{{ detailDs.description }}</div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showModal" class="modal-overlay">
      <div class="modal">
        <div class="modal-header">
          <h2>{{ editingDs ? t('datasources.modal.editTitle') : t('datasources.modal.newTitle') }}</h2>
          <button class="modal-close" @click="closeModal">
            <el-icon><CloseBold /></el-icon>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-section-title">{{ t('datasources.sections.basic') }}</div>
          <div class="form-grid">
            <div class="form-group">
              <label class="form-label">{{ t('datasources.fields.name') }} *</label>
              <input v-model="form.name" class="form-input" :placeholder="t('datasources.placeholders.name')" />
            </div>
            <div class="form-group">
              <label class="form-label">{{ t('datasources.fields.dbType') }} *</label>
              <select v-model="form.dbType" class="form-input" @change="onDbTypeChange">
                <option value="mysql">MySQL</option>
                <option value="postgresql">PostgreSQL</option>
                <option value="oracle">Oracle</option>
                <option value="clickhouse">ClickHouse</option>
                <option value="mariadb">MariaDB</option>
              </select>
            </div>
            <div class="form-group full-width">
              <label class="form-label">{{ t('datasources.fields.description') }}</label>
              <input v-model="form.description" class="form-input" :placeholder="t('datasources.placeholders.description')" />
            </div>
          </div>

          <div class="form-section-title">{{ t('datasources.sections.connection') }}</div>
          <div class="form-grid">
            <div class="form-group" style="flex: 2">
              <label class="form-label">{{ t('datasources.fields.host') }} *</label>
              <input v-model="form.host" class="form-input" :placeholder="t('datasources.placeholders.host')" />
            </div>
            <div class="form-group" style="flex: 1">
              <label class="form-label">{{ t('datasources.fields.port') }} *</label>
              <input v-model.number="form.port" type="number" class="form-input" :placeholder="String(defaultPort)" />
            </div>
            <div class="form-group">
              <label class="form-label">{{ t('datasources.fields.databaseName') }} *</label>
              <input v-model="form.databaseName" class="form-input" :placeholder="databaseNamePlaceholder" />
            </div>
            <div class="form-group" v-if="showSchemaField">
              <label class="form-label">{{ schemaFieldLabel }}</label>
              <input v-model="form.schemaName" class="form-input" :placeholder="t('datasources.placeholders.schemaName')" />
            </div>
          </div>

          <div class="form-section-title">{{ t('datasources.sections.auth') }}</div>
          <div class="form-grid">
            <div class="form-group">
              <label class="form-label">{{ t('datasources.fields.username') }}</label>
              <input v-model="form.username" class="form-input" autocomplete="off" :placeholder="t('datasources.placeholders.username')" />
            </div>
            <div class="form-group">
              <label class="form-label">{{ t('datasources.fields.password') }}</label>
              <div class="password-wrap">
                <input v-model="form.password" :type="showPassword ? 'text' : 'password'" class="form-input" autocomplete="new-password" :placeholder="t('datasources.placeholders.password')" />
                <button class="password-toggle" @click="showPassword = !showPassword" type="button">
                  <el-icon v-if="!showPassword"><View /></el-icon>
                  <el-icon v-else><Hide /></el-icon>
                </button>
              </div>
            </div>
          </div>

          <div class="form-section-title advanced-toggle" @click="showAdvanced = !showAdvanced">
            {{ t('datasources.sections.advanced') }}
            <el-icon :class="{ rotated: showAdvanced }"><ArrowDown /></el-icon>
          </div>
          <div class="form-grid" v-if="showAdvanced">
            <div class="form-group full-width">
              <label class="form-label">{{ t('datasources.fields.extraParams') }}</label>
              <input v-model="form.extraParams" class="form-input" :placeholder="t('datasources.placeholders.extraParams')" />
              <span class="form-hint">{{ t('datasources.hints.extraParams') }}</span>
            </div>
          </div>

          <div v-if="modalTestResult !== null" class="test-result" :class="modalTestResult ? 'test-ok' : 'test-fail'">
            <el-icon v-if="modalTestResult"><CircleCheckFilled /></el-icon>
            <el-icon v-else><CircleCloseFilled /></el-icon>
            {{ modalTestResult ? t('datasources.messages.testSuccess') : t('datasources.messages.testFailed') }}
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-test" @click="testInModal" :disabled="modalTesting || !canSave">
            <span v-if="modalTesting" class="spinner"></span>
            <template v-else>{{ t('datasources.testButton') }}</template>
          </button>
          <div style="flex: 1"></div>
          <button class="btn-secondary" @click="closeModal">{{ t('common.cancel') }}</button>
          <button class="btn-primary" @click="saveDs" :disabled="!canSave">{{ t('common.save') }}</button>
        </div>
      </div>
    </div>

    <!-- 修改人:祝奔；时间：2026-11-19T08:10:28.021Z；修改原因/内容：新增表字段维护弹框，左侧为表搜索列表与是否使用，右侧维护表描述及字段信息；代码来源：AI生成 -->
    <div v-if="showSchemaModal" class="modal-overlay">
      <div class="modal schema-modal">
        <div class="modal-header">
          <h2>{{ t('datasources.schema.title') }}<span v-if="schemaDs" class="schema-ds-name"> · {{ schemaDs.name }}</span></h2>
          <button class="modal-close" @click="closeSchemaModal">
            <el-icon><CloseBold /></el-icon>
          </button>
        </div>
        <div class="schema-body">
          <div class="schema-left">
            <div class="schema-search">
              <el-icon><Search /></el-icon>
              <input v-model="schemaKeyword" class="form-input" :placeholder="t('datasources.schema.searchPlaceholder')" />
            </div>
            <div class="schema-list-label">{{ t('datasources.schema.tableList') }}</div>
            <div class="schema-table-list">
              <!-- 修改人:祝奔；时间：2026-11-19T08:34:53.175Z；修改原因/内容：表集合改为接口数据后增加加载中提示；代码来源：AI生成 -->
              <div v-if="schemaLoading" class="schema-loading">
                <span class="spinner"></span> {{ t('datasources.schema.loading') }}
              </div>
              <div
                v-for="table in filteredSchemaTables"
                :key="table.name"
                class="schema-table-item"
                :class="{ active: table.name === selectedTableName }"
                :title="table.enabled ? t('datasources.schema.tableUsed') : t('datasources.schema.tableUnused')"
                @click="selectSchemaTable(table)"
              >
                <div class="schema-table-main">
                  <span class="schema-table-name">{{ table.name }}</span>
                  <span class="schema-table-comment">{{ table.comment }}</span>
                </div>
                <!-- 修改人:祝奔；时间：2026-11-19T08:15:31.042Z；修改原因/内容：列表项仅保留使用状态展示，避免开关与选中操作相互误触；代码来源：AI生成 -->
                <span class="schema-used-dot" :class="{ used: table.enabled }"></span>
              </div>
              <div v-if="!schemaLoading && filteredSchemaTables.length === 0" class="schema-empty">
                {{ t('datasources.schema.noTables') }}
              </div>
            </div>
          </div>

          <div class="schema-right">
            <template v-if="selectedTable">
              <div class="schema-right-top">
                <div class="schema-table-head">
                  <div>
                    <div class="schema-section-label">{{ t('datasources.schema.tableName') }}</div>
                    <div class="schema-table-title">{{ selectedTable.name }}</div>
                    <!-- 修改人:祝奔；时间：2026-11-19T08:49:01.621Z；修改原因/内容：展示接口返回的来源库与表类型；代码来源：AI生成 -->
                    <div class="schema-table-meta" v-if="selectedTable.schema">{{ t('datasources.schema.sourceSchema') }}: {{ selectedTable.schema }} · {{ selectedTable.type }}</div>
                  </div>
                  <!-- 修改人:祝奔；时间：2026-11-19T08:15:31.042Z；修改原因/内容：将表使用开关独立到右侧详情区，与列表选中操作分离；代码来源：AI生成 -->
                  <label class="schema-use-toggle">
                    <span class="schema-use-text">{{ selectedTable.enabled ? t('datasources.schema.tableUsed') : t('datasources.schema.tableUnused') }}</span>
                    <span class="toggle-switch">
                      <input type="checkbox" v-model="selectedTable.enabled" />
                      <span class="toggle-slider"></span>
                    </span>
                  </label>
                </div>
                <div class="schema-section-label">{{ t('datasources.schema.tableComment') }}</div>
                <input v-model="selectedTable.comment" class="form-input" :placeholder="t('datasources.schema.tableCommentPlaceholder')" />
              </div>
              <div class="schema-right-bottom">
                <div class="schema-section-label">{{ t('datasources.schema.columns') }}</div>
                <div class="schema-columns-wrap">
                  <!-- 修改人:祝奔；时间：2026-11-19T09:02:06.584Z；修改原因/内容：字段加载中提示；代码来源：AI生成 -->
                  <div v-if="columnsLoading" class="schema-columns-loading">
                    <span class="spinner"></span> {{ t('datasources.schema.columnsLoading') }}
                  </div>
                  <table class="schema-columns-table" v-show="!columnsLoading">
                    <thead>
                      <tr>
                        <th>{{ t('datasources.schema.colName') }}</th>
                        <th>{{ t('datasources.schema.colType') }}</th>
                        <th>{{ t('datasources.schema.colComment') }}</th>
                        <th>{{ t('datasources.schema.colCustomComment') }}</th>
                        <th>{{ t('datasources.schema.colUse') }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="col in selectedTable.columns" :key="col.name">
                        <td class="col-name">{{ col.name }}</td>
                        <td><span class="col-type">{{ col.type }}</span></td>
                        <!-- 修改人:祝奔；时间：2026-11-19T09:18:14.021Z；修改原因/内容：字段描述来自数据库只读展示，仅自定义描述可修改；代码来源：AI生成 -->
                        <td class="col-comment">{{ col.comment }}</td>
                        <td>
                          <input v-model="col.customComment" class="form-input col-input" :placeholder="t('datasources.schema.customCommentPlaceholder')" />
                        </td>
                        <td class="col-use">
                          <input type="checkbox" v-model="col.enabled" />
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </template>
            <div v-else class="schema-placeholder">{{ schemaLoading ? t('datasources.schema.loading') : t('datasources.schema.selectTableHint') }}</div>
          </div>
        </div>
        <div class="modal-footer">
          <div style="flex: 1"></div>
          <button class="btn-secondary" @click="closeSchemaModal">{{ t('datasources.schema.cancel') }}</button>
          <button class="btn-primary" @click="saveSchema">{{ t('datasources.schema.save') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { mcToast } from '@/composables/useMcToast'
import { mcConfirm } from '@/components/common/useConfirm'
import {
  ArrowDown,
  CircleCheckFilled,
  CircleCloseFilled,
  CloseBold,
  Coin,
  Delete,
  EditPen,
  // 修改人:祝奔；时间：2026-11-19T08:10:28.021Z；修改原因/内容：新增表字段维护入口与表搜索所需图标；代码来源：AI生成
  Grid,
  Hide,
  Plus,
  Search,
  Select,
  View,
} from '@element-plus/icons-vue'
import { datasourceApi } from '@/api/index'

const { t } = useI18n()

interface Datasource {
  id: number | string
  name: string
  description: string
  dbType: string
  host: string
  port: number
  databaseName: string
  username: string
  password: string
  extraParams: string
  schemaName: string
  enabled: boolean
  lastTestOk: boolean | null
  lastTestTime: string | null
}

const datasources = ref<Datasource[]>([])
const showModal = ref(false)
const editingDs = ref<Datasource | null>(null)
const detailDs = ref<Datasource | null>(null)
const testing = ref<number | string | null>(null)
const showPassword = ref(false)
const showAdvanced = ref(false)
const modalTesting = ref(false)
const modalTestResult = ref<boolean | null>(null)

// 修改人:祝奔；时间：2026-11-19T08:10:28.021Z；修改原因/内容：新增表字段维护弹框的接口类型、状态、模拟表数据及搜索/选中计算属性；代码来源：AI生成
interface DbColumn {
  name: string
  type: string
  comment: string
  customComment: string
  enabled: boolean
}

interface DbTable {
  name: string
  comment: string
  // 修改人:祝奔；时间：2026-11-19T08:49:01.621Z；修改原因/内容：表集合增加来源库与表类型展示字段；代码来源：AI生成
  schema: string
  type: string
  enabled: boolean
  columns: DbColumn[]
}

const showSchemaModal = ref(false)
const schemaDs = ref<Datasource | null>(null)
const schemaTables = ref<DbTable[]>([])
const schemaKeyword = ref('')
const selectedTableName = ref('')

// 修改人:祝奔；时间：2026-11-19T08:34:53.175Z；修改原因/内容：新增表集合接口返回类型与加载状态；代码来源：AI生成
interface DatasourceTableVO {
  tableName: string
  tableComment: string | null
  tableSchema: string | null
  tableType: string
  databaseName: string | null
  dbType: string | null
  // 修改人:祝奔；时间：2026-11-19T08:49:01.621Z；修改原因/内容：接口返回落库的表启用状态；代码来源：AI生成
  enabled: boolean
}

const schemaLoading = ref(false)

// 修改人:祝奔；时间：2026-11-19T09:02:06.584Z；修改原因/内容：新增字段集合接口返回类型与加载状态；代码来源：AI生成
interface DatasourceColumnVO {
  columnName: string
  columnType: string | null
  columnComment: string | null
  customComment: string | null
  enabled: boolean
}

const columnsLoading = ref(false)

const filteredSchemaTables = computed(() => {
  const kw = schemaKeyword.value.trim().toLowerCase()
  if (!kw) return schemaTables.value
  return schemaTables.value.filter(t =>
    t.name.toLowerCase().includes(kw) || (t.comment || '').toLowerCase().includes(kw))
})

const selectedTable = computed(() =>
  schemaTables.value.find(t => t.name === selectedTableName.value) || null)

const PORT_MAP: Record<string, number> = {
  mysql: 3306, mariadb: 3306, postgresql: 5432, clickhouse: 8123, oracle: 1521,
}

const defaultPort = computed(() => PORT_MAP[form.value.dbType] || 3306)

// Oracle 的 databaseName 字段语义是 Service Name；schema 字段对 PG/Oracle 都有意义（Oracle 下为 owner 过滤）
const showSchemaField = computed(() => ['postgresql', 'oracle'].includes(form.value.dbType))
const databaseNamePlaceholder = computed(() => form.value.dbType === 'oracle'
  ? t('datasources.placeholders.serviceName')
  : t('datasources.placeholders.databaseName'))
const schemaFieldLabel = computed(() => form.value.dbType === 'oracle'
  ? t('datasources.fields.ownerSchema')
  : t('datasources.fields.schemaName'))

const defaultForm = () => ({
  name: '', description: '', dbType: 'mysql', host: '', port: 3306,
  databaseName: '', username: '', password: '', extraParams: '', schemaName: '', enabled: true,
})
const form = ref<any>(defaultForm())

const canSave = computed(() => form.value.name && form.value.host && form.value.port && form.value.databaseName)

onMounted(loadDatasources)

async function loadDatasources() {
  try {
    const res: any = await datasourceApi.list()
    datasources.value = res.data || []
  } catch { datasources.value = [] }
}

function dbTypeLabel(dbType: string) {
  const labels: Record<string, string> = { mysql: 'MySQL', postgresql: 'PostgreSQL', clickhouse: 'ClickHouse', mariadb: 'MariaDB', oracle: 'Oracle' }
  return labels[dbType] || dbType
}

function statusClass(ds: Datasource) {
  if (!ds.enabled) return 'dot-disabled'
  if (ds.lastTestOk === true) return 'dot-ok'
  if (ds.lastTestOk === false) return 'dot-fail'
  return 'dot-unknown'
}

function onDbTypeChange() {
  form.value.port = PORT_MAP[form.value.dbType] || 3306
  // Oracle 与 PostgreSQL 共用 schema 字段（Oracle 下作为 owner 过滤），切换时不清空
  if (!['postgresql', 'oracle'].includes(form.value.dbType)) form.value.schemaName = ''
}

function openCreateModal() {
  editingDs.value = null
  form.value = defaultForm()
  showPassword.value = false
  showAdvanced.value = false
  modalTestResult.value = null
  showModal.value = true
}

function openEditModal(ds: Datasource) {
  editingDs.value = ds
  form.value = { ...ds }
  showPassword.value = false
  showAdvanced.value = !!(ds.extraParams)
  modalTestResult.value = null
  showModal.value = true
}

function closeModal() {
  showModal.value = false
  editingDs.value = null
  modalTestResult.value = null
}

function openDetailModal(ds: Datasource) {
  detailDs.value = ds
}

function closeDetailModal() {
  detailDs.value = null
}

async function saveDs() {
  try {
    let saved: any
    if (editingDs.value) {
      saved = await datasourceApi.update(editingDs.value.id, form.value)
    } else {
      saved = await datasourceApi.create(form.value)
    }
    closeModal()
    await loadDatasources()
    const id = saved?.data?.id
    if (id) autoTestAfterSave(id)
  } catch (e: any) { mcToast.error(e?.message || t('datasources.messages.saveFailed')) }
}

async function autoTestAfterSave(id: number | string) {
  testing.value = id
  try {
    const res: any = await datasourceApi.test(id)
    const ok = res.data?.success
    if (ok) mcToast.success(t('datasources.messages.testSuccess'))
    else mcToast.warning(t('datasources.messages.testFailed'))
    await loadDatasources()
  } catch {
  } finally { testing.value = null }
}

async function testInModal() {
  if (!editingDs.value) {
    try {
      const saved: any = await datasourceApi.create(form.value)
      editingDs.value = saved.data
      form.value = { ...saved.data }
      await loadDatasources()
    } catch (e: any) {
      mcToast.error(e?.message || t('datasources.messages.saveFailed'))
      return
    }
  } else {
    try {
      await datasourceApi.update(editingDs.value.id, form.value)
      await loadDatasources()
    } catch (e: any) {
      mcToast.error(e?.message || t('datasources.messages.saveFailed'))
      return
    }
  }

  modalTesting.value = true
  modalTestResult.value = null
  try {
    const res: any = await datasourceApi.test(editingDs.value!.id)
    modalTestResult.value = !!res.data?.success
    await loadDatasources()
  } catch { modalTestResult.value = false }
  finally { modalTesting.value = false }
}

async function deleteDs(id: string | number) {
  const ok = await mcConfirm({
    title: t('datasources.messages.deleteTitle'),
    message: t('datasources.messages.deleteConfirm'),
    tone: 'danger',
  })
  if (!ok) return
  try {
    await datasourceApi.delete(id)
    await loadDatasources()
  } catch (e: any) { mcToast.error(e?.message || t('datasources.messages.deleteFailed')) }
}

async function toggleDs(ds: Datasource) {
  try {
    await datasourceApi.toggle(ds.id, !ds.enabled)
    await loadDatasources()
  } catch (e: any) { mcToast.error(e?.message || t('datasources.messages.toggleFailed')) }
}

async function testConnection(ds: Datasource) {
  testing.value = ds.id
  try {
    const res: any = await datasourceApi.test(ds.id)
    const ok = res.data?.success
    if (ok) mcToast.success(t('datasources.messages.testSuccess'))
    else mcToast.error(t('datasources.messages.testFailed'))
    await loadDatasources()
  } catch (e: any) {
    mcToast.error(e?.message || t('datasources.messages.testFailed'))
  } finally { testing.value = null }
}

// 修改人:祝奔；时间：2026-11-19T08:24:55.722Z；修改原因/内容：表字段维护按钮需同时满足数据源已启用且连接成功；代码来源：AI生成
function isSchemaDisabled(ds: Datasource) {
  return !ds.enabled || ds.lastTestOk !== true
}

function schemaButtonTitle(ds: Datasource) {
  if (!ds.enabled) return t('datasources.schema.actionDisabledByEnabledTip')
  if (ds.lastTestOk !== true) return t('datasources.schema.actionDisabledTip')
  return t('datasources.schema.action')
}

// 修改人:祝奔；时间：2026-11-19T08:34:53.175Z；修改原因/内容：打开弹框时调用接口加载真实表集合；代码来源：AI生成
async function openSchemaModal(ds: Datasource) {
  schemaDs.value = ds
  schemaKeyword.value = ''
  selectedTableName.value = ''
  schemaTables.value = []
  showSchemaModal.value = true
  schemaLoading.value = true
  try {
    const res: any = await datasourceApi.tables(ds.id)
    const list: DatasourceTableVO[] = res.data || []
    // 修改人:祝奔；时间：2026-11-19T08:49:01.621Z；修改原因/内容：表启用状态以接口落库配置为准，未匹配则不启用；代码来源：AI生成
    schemaTables.value = list.map(vo => {
      return {
        name: vo.tableName,
        comment: vo.tableComment || '',
        schema: vo.tableSchema || '',
        type: vo.tableType || 'TABLE',
        enabled: vo.enabled === true,
        // 修改人:祝奔；时间：2026-11-19T09:02:06.584Z；修改原因/内容：字段改为点击表时按接口加载；代码来源：AI生成
        columns: [],
      }
    })
    // 修改人:祝奔；时间：2026-11-19T09:02:06.584Z；修改原因/内容：打开弹框后自动加载首张表的字段；代码来源：AI生成
    if (schemaTables.value.length > 0) {
      await selectSchemaTable(schemaTables.value[0])
    } else {
      selectedTableName.value = ''
    }
  } catch (e: any) {
    mcToast.error(e?.message || t('datasources.schema.loadFailed'))
  } finally {
    schemaLoading.value = false
  }
}

function closeSchemaModal() {
  showSchemaModal.value = false
  schemaDs.value = null
}

// 修改人:祝奔；时间：2026-11-19T09:02:06.584Z；修改原因/内容：点击表时调用接口加载字段并渲染；代码来源：AI生成
async function selectSchemaTable(table: DbTable) {
  selectedTableName.value = table.name
  if (!schemaDs.value) return
  table.columns = []
  columnsLoading.value = true
  try {
    const res: any = await datasourceApi.tableColumns(schemaDs.value.id, table.name)
    const list: DatasourceColumnVO[] = res.data || []
    table.columns = list.map(col => ({
      name: col.columnName,
      type: col.columnType || '',
      comment: col.columnComment || '',
      customComment: col.customComment || '',
      enabled: col.enabled === true,
    }))
  } catch (e: any) {
    mcToast.error(e?.message || t('datasources.schema.columnsLoadFailed'))
    table.columns = []
  } finally {
    columnsLoading.value = false
  }
}

// 修改人:祝奔；时间：2026-11-19T09:18:14.021Z；修改原因/内容：保存时仅提交启用的表与字段全量配置；代码来源：AI生成
async function saveSchema() {
  if (!schemaDs.value) return
  const request = {
    datasourceId: String(schemaDs.value.id),
    tables: schemaTables.value
      .filter(table => table.enabled)
      .map(table => ({
        tableId: '',
        tableName: table.name,
        tableComment: table.comment,
        tableSchema: table.schema,
        tableType: table.type,
        enabled: true,
        columns: table.columns.map(col => ({
          columnName: col.name,
          columnType: col.type,
          columnComment: col.comment,
          customComment: col.customComment,
          enabled: col.enabled,
        })),
      })),
  }
  try {
    await datasourceApi.saveSchema(request)
    mcToast.success(t('datasources.schema.saved'))
    closeSchemaModal()
  } catch (e: any) {
    mcToast.error(e?.message || t('datasources.schema.saveFailed'))
  }
}
</script>

<style scoped>
/* ===== Shell ===== */
.page-container { height: 100%; overflow-y: auto; }
.page-shell { padding: 24px; }

/* ===== Header ===== */
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.page-lead { display: flex; flex-direction: column; gap: 6px; }
.page-kicker {
  display: inline-flex; width: fit-content;
  padding: 4px 10px; border-radius: 999px;
  font-size: 11px; font-weight: 700; letter-spacing: 0.1em; text-transform: uppercase;
  color: var(--mc-primary); background: var(--mc-primary-bg);
}
.page-title { font-size: clamp(24px, 3.5vw, 36px); font-weight: 800; color: var(--mc-text-primary); margin: 0; }
.page-desc { font-size: 14px; color: var(--mc-text-secondary); margin: 0; }

/* ===== Buttons ===== */
.btn-primary { display: flex; align-items: center; gap: 6px; padding: 9px 16px; background: var(--mc-primary); color: #fff; border: none; border-radius: 10px; font-size: 14px; font-weight: 600; cursor: pointer; white-space: nowrap; }
.btn-primary:hover { background: var(--mc-primary-hover); }
.btn-primary:disabled { opacity: .4; cursor: not-allowed; }
.btn-secondary { padding: 8px 16px; background: var(--mc-bg-elevated); color: var(--mc-text-primary); border: 1px solid var(--mc-border); border-radius: 8px; font-size: 14px; cursor: pointer; }
.btn-secondary:hover { background: var(--mc-bg-sunken); }
.btn-test { display: flex; align-items: center; gap: 6px; padding: 8px 16px; background: transparent; color: var(--mc-primary); border: 1px solid var(--mc-primary); border-radius: 8px; font-size: 14px; font-weight: 500; cursor: pointer; min-width: 90px; justify-content: center; }
.btn-test:hover { background: var(--mc-primary-bg); }
.btn-test:disabled { opacity: .4; cursor: not-allowed; }

/* ===== Table — one surface, no nesting ===== */
.table-wrap {
  background: var(--mc-bg-elevated);
  border: 1px solid var(--mc-border);
  border-radius: 16px;
  overflow: hidden;
}
.data-table { width: 100%; border-collapse: collapse; }
.data-table th {
  padding: 10px 16px; text-align: left;
  font-size: 11px; font-weight: 600; letter-spacing: .06em; text-transform: uppercase;
  color: var(--mc-text-tertiary);
  background: var(--mc-bg-sunken);
  border-bottom: 1px solid var(--mc-border);
  white-space: nowrap;
}
.data-row { border-bottom: 1px solid var(--mc-border-light); transition: background .12s; }
.data-row:last-child { border-bottom: none; }
.data-row:hover { background: var(--mc-bg-sunken); }
.data-table td { padding: 12px 16px; font-size: 14px; color: var(--mc-text-primary); vertical-align: middle; }

/* ===== Name cell ===== */
.tool-info { display: flex; align-items: center; gap: 10px; }
.tool-icon-wrap { width: 32px; height: 32px; background: var(--mc-bg-sunken); border-radius: 8px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; color: var(--mc-text-tertiary); }
.tool-icon-wrap.icon-ok { color: #2e7d32; background: color-mix(in srgb, #2e7d32 10%, transparent); }
.tool-icon-wrap.icon-fail { color: var(--mc-danger, #ef4444); background: color-mix(in srgb, var(--mc-danger, #ef4444) 10%, transparent); }
.tool-name { font-weight: 600; color: var(--mc-text-primary); }
.tool-type-inline { margin-top: 4px; }
.tool-desc { font-size: 12px; color: var(--mc-text-tertiary); margin-top: 1px; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ===== Connection cell ===== */
.conn-info { display: flex; flex-direction: column; gap: 2px; }
.conn-host { display: inline-block; max-width: 180px; background: var(--mc-bg-sunken); padding: 2px 8px; border-radius: 6px; font-size: 12px; color: var(--mc-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conn-db { font-size: 12px; color: var(--mc-text-tertiary); max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ===== Type badge ===== */
.type-badge { padding: 2px 8px; border-radius: 6px; font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: .04em; color: var(--mc-text-tertiary); background: var(--mc-bg-sunken); }
.type-mysql { color: #1a73e8; background: color-mix(in srgb, #1a73e8 10%, transparent); }
.type-postgresql { color: #336791; background: color-mix(in srgb, #336791 10%, transparent); }
.type-clickhouse { color: #e6a817; background: color-mix(in srgb, #e6a817 10%, transparent); }
.type-mariadb { color: #c0392b; background: color-mix(in srgb, #c0392b 10%, transparent); }
.type-oracle { color: #f80000; background: color-mix(in srgb, #f80000 10%, transparent); }

/* ===== Status cell ===== */
.status-cell { display: flex; align-items: center; gap: 8px; }
.status-label { font-size: 13px; font-weight: 500; color: var(--mc-text-secondary); white-space: nowrap; }
.status-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.dot-ok { background: #34d399; box-shadow: 0 0 4px rgba(52,211,153,.45); }
.dot-fail { background: var(--mc-danger, #ef4444); box-shadow: 0 0 4px rgba(239,68,68,.4); }
.dot-unknown { background: var(--mc-text-tertiary); opacity: .4; }
.dot-disabled { background: var(--mc-text-tertiary); opacity: .3; }

/* ===== Toggle ===== */
.toggle-switch { position: relative; display: inline-block; width: 36px; height: 20px; cursor: pointer; }
.toggle-switch input { opacity: 0; width: 0; height: 0; }
.toggle-slider { position: absolute; inset: 0; background: var(--mc-border); border-radius: 20px; transition: .2s; }
.toggle-slider::before { content: ''; position: absolute; width: 14px; height: 14px; left: 3px; top: 3px; background: var(--mc-bg-elevated); border-radius: 50%; transition: .2s; }
.toggle-switch input:checked + .toggle-slider { background: var(--mc-primary); }
.toggle-switch input:checked + .toggle-slider::before { transform: translateX(16px); }

/* ===== Row actions ===== */
.row-actions { display: flex; gap: 5px; }
.row-btn { width: 30px; height: 30px; border: 1px solid var(--mc-border); border-radius: 8px; background: transparent; cursor: pointer; display: flex; align-items: center; justify-content: center; color: var(--mc-text-tertiary); transition: all .12s; }
.row-btn:hover { background: var(--mc-bg-sunken); color: var(--mc-text-primary); }
.row-btn:disabled { opacity: .3; cursor: not-allowed; }
.row-btn.danger:hover { background: color-mix(in srgb, var(--mc-danger, #ef4444) 10%, transparent); border-color: var(--mc-danger); color: var(--mc-danger); }
.row-btn.test-btn:hover { border-color: var(--mc-primary); color: var(--mc-primary); }

/* ===== Spinner ===== */
.spinner { width: 12px; height: 12px; border: 2px solid var(--mc-border); border-top-color: var(--mc-primary); border-radius: 50%; animation: spin .6s linear infinite; display: inline-block; }
@keyframes spin { to { transform: rotate(360deg); } }

/* ===== Empty state ===== */
.empty-row { padding: 48px 16px !important; }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 6px; color: var(--mc-text-tertiary); }
.empty-icon { font-size: 32px; }
.empty-state p { font-size: 14px; margin: 0; }

/* ===== Modal ===== */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.45); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 20px; }
.modal { background: var(--mc-bg-elevated); border: 1px solid var(--mc-border); border-radius: 14px; width: 100%; max-width: 580px; max-height: 90vh; display: flex; flex-direction: column; box-shadow: 0 16px 48px rgba(0,0,0,.18); }
.modal-header { display: flex; align-items: center; justify-content: space-between; padding: 18px 22px; border-bottom: 1px solid var(--mc-border-light); }
.modal-header h2 { font-size: 17px; font-weight: 600; color: var(--mc-text-primary); margin: 0; }
.modal-close { width: 28px; height: 28px; border: none; background: none; cursor: pointer; color: var(--mc-text-tertiary); display: flex; align-items: center; justify-content: center; border-radius: 6px; }
.modal-close:hover { background: var(--mc-bg-sunken); }
.modal-body { flex: 1; overflow-y: auto; padding: 18px 22px; }
.modal-footer { display: flex; align-items: center; gap: 8px; padding: 14px 22px; border-top: 1px solid var(--mc-border-light); }

/* Detail grid */
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.detail-item { display: flex; flex-direction: column; gap: 4px; }
.detail-item-full { grid-column: 1 / -1; }
.detail-label { font-size: 11px; font-weight: 600; letter-spacing: .06em; text-transform: uppercase; color: var(--mc-text-tertiary); }
.detail-value { font-size: 14px; color: var(--mc-text-primary); word-break: break-word; }
.detail-subvalue { font-size: 12px; color: var(--mc-text-tertiary); }
.detail-block { padding: 8px 12px; border-radius: 8px; background: var(--mc-bg-sunken); font-family: 'SF Mono', 'Fira Code', monospace; font-size: 13px; white-space: pre-wrap; }

/* Form */
.form-section-title { font-size: 13px; font-weight: 600; color: var(--mc-text-secondary); text-transform: uppercase; letter-spacing: .05em; margin: 20px 0 10px; padding-bottom: 6px; border-bottom: 1px solid var(--mc-border-light); }
.form-section-title:first-child { margin-top: 0; }
.advanced-toggle { cursor: pointer; display: flex; align-items: center; gap: 4px; user-select: none; }
.advanced-toggle svg { transition: transform .2s; }
.advanced-toggle svg.rotated { transform: rotate(180deg); }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.form-group { display: flex; flex-direction: column; gap: 4px; }
.form-group.full-width { grid-column: 1 / -1; }
.form-label { font-size: 13px; font-weight: 500; color: var(--mc-text-secondary); }
.form-input { padding: 8px 12px; border: 1px solid var(--mc-border); border-radius: 8px; font-size: 14px; color: var(--mc-text-primary); outline: none; background: var(--mc-bg-sunken); width: 100%; }
.form-input:focus { border-color: var(--mc-primary); box-shadow: 0 0 0 2px rgba(217,119,87,.12); }
.form-hint { font-size: 11px; color: var(--mc-text-tertiary); margin-top: 2px; }
.password-wrap { position: relative; }
.password-wrap .form-input { padding-right: 36px; }
.password-toggle { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); border: none; background: none; cursor: pointer; color: var(--mc-text-tertiary); padding: 4px; display: flex; align-items: center; justify-content: center; }
.password-toggle:hover { color: var(--mc-text-secondary); }
.test-result { display: flex; align-items: center; gap: 8px; padding: 10px 14px; border-radius: 8px; font-size: 13px; font-weight: 500; margin-top: 16px; }
.test-ok { background: color-mix(in srgb, #2e7d32 10%, transparent); color: #2e7d32; }
.test-fail { background: color-mix(in srgb, var(--mc-danger, #ef4444) 10%, transparent); color: var(--mc-danger, #ef4444); }

/* 修改人:祝奔；时间：2026-11-19T08:10:28.021Z；修改原因/内容：新增表字段维护弹框的左右分栏布局、表列表与字段表格样式；代码来源：AI生成 */
/* ===== Table schema modal ===== */
.schema-modal { max-width: 1040px; height: min(80vh, 720px); }
.schema-ds-name { margin-left: 4px; font-weight: 400; color: var(--mc-text-tertiary); }
/* 修改人:祝奔；时间：2026-11-19T08:22:08.586Z；修改原因/内容：禁用状态下的表字段维护按钮不显示悬停高亮；代码来源：AI生成 */
.schema-btn:not(:disabled):hover { border-color: var(--mc-primary); color: var(--mc-primary); }
.schema-body { flex: 1; display: flex; min-height: 0; }
.schema-left { width: 300px; flex-shrink: 0; display: flex; flex-direction: column; gap: 10px; padding: 16px; border-right: 1px solid var(--mc-border-light); }
.schema-search { position: relative; }
.schema-search .el-icon { position: absolute; left: 10px; top: 50%; transform: translateY(-50%); color: var(--mc-text-tertiary); }
.schema-search .form-input { padding-left: 32px; }
.schema-list-label { font-size: 11px; font-weight: 600; letter-spacing: .06em; text-transform: uppercase; color: var(--mc-text-tertiary); }
.schema-table-list { flex: 1; min-height: 0; overflow-y: auto; display: flex; flex-direction: column; gap: 6px; }
.schema-table-item { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 10px; border: 1px solid var(--mc-border-light); border-radius: 8px; cursor: pointer; transition: all .12s; }
.schema-table-item:hover { background: var(--mc-bg-sunken); }
.schema-table-item.active { border-color: var(--mc-primary); background: var(--mc-primary-bg); }
.schema-table-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.schema-table-name { font-size: 13px; font-weight: 600; color: var(--mc-text-primary); }
.schema-table-comment { font-size: 11px; color: var(--mc-text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 修改人:祝奔；时间：2026-11-19T08:15:31.042Z；修改原因/内容：新增表使用状态圆点样式并移除原列表复选框样式；代码来源：AI生成 */
.schema-used-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; background: var(--mc-border); }
.schema-used-dot.used { background: #34d399; box-shadow: 0 0 4px rgba(52,211,153,.45); }
.col-use input { accent-color: var(--mc-primary); cursor: pointer; }
.schema-empty { padding: 24px 0; text-align: center; font-size: 13px; color: var(--mc-text-tertiary); }
/* 修改人:祝奔；时间：2026-11-19T08:34:53.175Z；修改原因/内容：新增表集合加载中样式；代码来源：AI生成 */
.schema-loading { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 24px 0; font-size: 13px; color: var(--mc-text-tertiary); }
.schema-right { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.schema-right-top { display: flex; flex-direction: column; gap: 6px; padding: 16px 20px; border-bottom: 1px solid var(--mc-border-light); }
/* 修改人:祝奔；时间：2026-11-19T08:15:31.042Z；修改原因/内容：新增右侧表详情区的使用开关布局样式；代码来源：AI生成 */
.schema-table-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 8px; }
.schema-use-toggle { display: flex; align-items: center; gap: 8px; cursor: pointer; user-select: none; }
.schema-use-text { font-size: 12px; color: var(--mc-text-secondary); }
.schema-section-label { font-size: 11px; font-weight: 600; letter-spacing: .06em; text-transform: uppercase; color: var(--mc-text-tertiary); }
.schema-table-title { font-size: 16px; font-weight: 700; color: var(--mc-text-primary); margin-bottom: 4px; }
/* 修改人:祝奔；时间：2026-11-19T08:49:01.621Z；修改原因/内容：新增来源库信息样式；代码来源：AI生成 */
.schema-table-meta { margin-bottom: 4px; font-size: 11px; color: var(--mc-text-tertiary); }
.schema-right-top .form-input { max-width: 420px; }
.schema-right-bottom { flex: 1; min-height: 0; display: flex; flex-direction: column; gap: 8px; padding: 14px 20px 16px; }
.schema-columns-wrap { flex: 1; min-height: 0; overflow: auto; border: 1px solid var(--mc-border-light); border-radius: 8px; }
/* 修改人:祝奔；时间：2026-11-19T09:02:06.584Z；修改原因/内容：新增字段加载中样式；代码来源：AI生成 */
.schema-columns-loading { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 24px 0; font-size: 13px; color: var(--mc-text-tertiary); }
.schema-columns-table { width: 100%; border-collapse: collapse; }
.schema-columns-table th { position: sticky; top: 0; padding: 8px 10px; text-align: left; font-size: 11px; font-weight: 600; color: var(--mc-text-tertiary); background: var(--mc-bg-sunken); border-bottom: 1px solid var(--mc-border-light); white-space: nowrap; }
.schema-columns-table td { padding: 6px 10px; font-size: 13px; color: var(--mc-text-primary); border-bottom: 1px solid var(--mc-border-light); }
.schema-columns-table tr:last-child td { border-bottom: none; }
.col-name { font-weight: 600; font-family: 'SF Mono', 'Fira Code', monospace; }
.col-type { padding: 2px 6px; border-radius: 4px; font-size: 11px; color: var(--mc-text-secondary); background: var(--mc-bg-sunken); white-space: nowrap; }
.col-comment { color: var(--mc-text-secondary); }
.col-input { min-width: 160px; padding: 5px 8px; font-size: 12px; }
.col-use { text-align: center; }
.schema-placeholder { flex: 1; display: flex; align-items: center; justify-content: center; font-size: 14px; color: var(--mc-text-tertiary); }

/* Responsive */
@media (max-width: 900px) {
  .page-header { flex-direction: column; }
  .btn-primary { width: 100%; justify-content: center; }
  .detail-grid { grid-template-columns: 1fr; }
  .schema-modal { height: 88vh; }
  .schema-body { flex-direction: column; }
  .schema-left { width: 100%; max-height: 220px; border-right: none; border-bottom: 1px solid var(--mc-border-light); }
}
</style>
