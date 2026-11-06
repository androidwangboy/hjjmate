<template>
  <div class="settings-section bingli-section">
    <div class="section-header">
      <h2 class="section-title">病历模板库</h2>
      <p class="section-desc">
        上传 Word / Excel / PDF 病历模板，系统抽取「样式」；可手动录入模板内容 JSON 与扩展属性 JSON。
        对话时按样式召回最相似的模板并返回其绑定内容。样式只能由上传文件抽取，不从内容 JSON 推导。
      </p>
    </div>

    <!-- 工具条 -->
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索模板名称"
        clearable
        class="kw"
        @keyup.enter="loadList"
        @clear="loadList"
      />
      <el-select
        v-model="sourceTypeFilter"
        placeholder="来源格式"
        clearable
        class="st"
        @change="loadList"
      >
        <el-option label="全部" value="" />
        <el-option label="Word (docx)" value="docx" />
        <el-option label="Excel (xlsx)" value="xlsx" />
        <el-option label="PDF" value="pdf" />
      </el-select>
      <el-button type="primary" @click="openUpload">上传模板</el-button>
      <el-button @click="loadList">刷新</el-button>
    </div>

    <!-- 列表 -->
    <el-table
      :data="records"
      v-loading="loading"
      class="tpl-table"
      border
      stripe
      empty-text="暂无模板"
    >
      <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <el-button link type="primary" class="name-link" @click="openPreview(row)">
            {{ row.name }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column prop="sourceType" label="来源" width="90">
        <template #default="{ row }">
          <el-tag size="small">{{ row.sourceType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="知识库" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ kbName(row.kbId) || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="layoutType" label="布局" width="140" show-overflow-tooltip />
      <el-table-column label="字段/表格/章节" width="150">
        <template #default="{ row }">
          {{ row.fieldCount }} / {{ row.tableCount }} / {{ row.sectionCount }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'published' ? 'success' : 'info'" size="small">
            {{ row.status === 'published' ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" :disabled="!row.filePath" @click="reExtract(row)">
            重抽样式
          </el-button>
          <el-button v-if="canAdmin" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :current-page="page"
        :page-size="size"
        @current-change="onPage"
      />
    </div>

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadVisible" title="上传病历模板" width="660px" destroy-on-close>
      <div class="file-drop" @click="pickFile" @dragover.prevent @drop.prevent="onDrop">
        <input ref="fileInput" type="file" accept=".docx,.xlsx,.pdf" hidden @change="onFile" />
        <span v-if="!uploadFile">点击或拖拽上传 Word / Excel / PDF 模板文件</span>
        <span v-else class="file-name">{{ uploadFile.name }}</span>
      </div>
      <el-form label-width="120px" class="mt-16">
        <el-form-item label="关联知识库">
          <el-select
            v-model="uploadKbId"
            placeholder="选择知识库（可选）"
            clearable
            filterable
            class="kb-select"
          >
            <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板内容 JSON">
          <el-input
            v-model="uploadContentJson"
            type="textarea"
            :rows="6"
            placeholder="可选：模板内容 JSON（与文件样式同行绑定）"
          />
        </el-form-item>
        <el-form-item label="扩展属性 JSON">
          <el-input
            v-model="uploadExtJson"
            type="textarea"
            :rows="4"
            placeholder="可选：扩展属性 JSON（如适用科室、版本、标签）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!uploadFile || uploading" @click="doUpload">
          {{ uploading ? '上传中…' : '上传' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑 / 详情对话框 -->
    <el-dialog v-model="editVisible" :title="(editForm.id ? '编辑' : '') + '模板'" width="780px" destroy-on-close>
      <el-form label-width="120px" v-if="editForm.id">
        <el-form-item label="名称">
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="来源格式">
          <el-input :model-value="editForm.sourceType" disabled />
        </el-form-item>
        <el-form-item label="关联知识库">
          <el-select
            v-model="editForm.kbId"
            placeholder="选择知识库（可选）"
            clearable
            filterable
            class="kb-select"
          >
            <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="布局类型">
          <el-input :model-value="editForm.layoutType" disabled />
        </el-form-item>
        <el-form-item label="字段/表格/章节">
          <el-input
            :model-value="`${editForm.fieldCount} / ${editForm.tableCount} / ${editForm.sectionCount}`"
            disabled
          />
        </el-form-item>
        <el-form-item label="样式指纹">
          <el-input :model-value="editForm.styleHash" disabled />
        </el-form-item>
        <el-form-item label="向量模型">
          <el-input :model-value="editForm.embeddingModel" disabled />
        </el-form-item>
        <el-form-item label="源文件" v-if="editForm.fileName">
          <el-input :model-value="editForm.fileName" disabled />
        </el-form-item>
        <el-form-item label="样式签名">
          <el-input type="textarea" :model-value="editForm.styleSignature" :rows="6" disabled />
        </el-form-item>
        <el-form-item label="模板内容 JSON">
          <el-input v-model="editForm.contentJson" type="textarea" :rows="8" />
        </el-form-item>
        <el-form-item label="扩展属性 JSON">
          <el-input v-model="editForm.extAttrsJson" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button v-if="editForm.filePath" @click="reExtract(editForm)">重抽样式</el-button>
        <el-button type="primary" :disabled="saving" @click="doSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 预览对话框（只读） -->
    <el-dialog v-model="previewVisible" title="模板预览" width="780px" destroy-on-close @close="onPreviewClose">
      <el-form label-width="120px" v-if="previewForm.id">
        <el-form-item label="名称">
          <el-input :model-value="previewForm.name" disabled />
        </el-form-item>
        <el-form-item label="来源格式">
          <el-input :model-value="previewForm.sourceType" disabled />
        </el-form-item>
        <el-form-item label="知识库">
          <el-input :model-value="kbName(previewForm.kbId)" disabled />
        </el-form-item>
        <el-form-item label="布局类型">
          <el-input :model-value="previewForm.layoutType" disabled />
        </el-form-item>
        <el-form-item label="字段/表格/章节">
          <el-input
            :model-value="`${previewForm.fieldCount} / ${previewForm.tableCount} / ${previewForm.sectionCount}`"
            disabled
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-input :model-value="previewForm.status === 'published' ? '已发布' : '草稿'" disabled />
        </el-form-item>
        <el-form-item label="样式指纹">
          <el-input :model-value="previewForm.styleHash" disabled />
        </el-form-item>
        <el-form-item label="向量模型">
          <el-input :model-value="previewForm.embeddingModel" disabled />
        </el-form-item>
        <el-form-item label="源文件" v-if="previewForm.fileName">
          <el-input :model-value="previewForm.fileName" disabled />
        </el-form-item>
        <el-form-item label="样式签名">
          <el-input type="textarea" :model-value="previewForm.styleSignature" :rows="6" disabled />
        </el-form-item>
        <el-form-item label="模板内容 JSON">
          <el-input
            type="textarea"
            :model-value="prettyJson(previewForm.contentJson)"
            :rows="10"
            disabled
          />
        </el-form-item>
        <el-form-item label="扩展属性 JSON">
          <el-input
            type="textarea"
            :model-value="prettyJson(previewForm.extAttrsJson)"
            :rows="4"
            disabled
          />
        </el-form-item>

        <el-divider content-position="left">文件内容预览</el-divider>
        <div v-if="previewForm.fileName" class="file-preview">
          <div class="file-preview-bar">
            <span class="fpt-hint">源文件：{{ previewForm.fileName }}</span>
            <el-button size="small" @click="downloadFile(previewForm)">下载源文件</el-button>
          </div>
          <iframe v-if="previewType === 'pdf' && fileBlobUrl" :src="fileBlobUrl" class="file-frame" />
          <pre v-else v-loading="previewLoading" class="file-text">{{ previewContent }}</pre>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="primary" @click="editFromPreview">编辑</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import { mcToast } from '@/composables/useMcToast'

const ctxPath =
  import.meta.env.BASE_URL.length > 1 ? import.meta.env.BASE_URL.replace(/\/+$/, '') : ''

const wsStore = useWorkspaceStore()
const canAdmin = computed(() => !!wsStore.isGlobalAdmin)

const records = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const sourceTypeFilter = ref('')
const loading = ref(false)
const kbOptions = ref<any[]>([])

const uploadVisible = ref(false)
const uploadFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const uploadContentJson = ref('')
const uploadExtJson = ref('')
const uploadKbId = ref<number | null>(null)
const uploading = ref(false)

const editVisible = ref(false)
const editForm = ref<any>({})
const saving = ref(false)

const previewVisible = ref(false)
const previewForm = ref<any>({})
const previewType = ref<'pdf' | 'text'>('text')
const previewContent = ref('')
const fileBlobUrl = ref('')
const previewLoading = ref(false)

function prettyJson(v: any): string {
  if (!v) return ''
  try {
    return JSON.stringify(JSON.parse(v), null, 2)
  } catch {
    return String(v)
  }
}

function kbName(id: any): string {
  if (id == null) return ''
  const kb = kbOptions.value.find((k) => k.id === id)
  return kb ? kb.name : ''
}

async function loadKbs() {
  try {
    const res = await fetch(`${ctxPath}/api/v1/wiki/knowledge-bases`, { headers: authHeaders() })
    const body = await res.json()
    if (!res.ok || body.code !== 200) throw new Error(body?.msg || `HTTP ${res.status}`)
    kbOptions.value = body.data || []
  } catch {
    kbOptions.value = []
  }
}

function authHeaders(): Record<string, string> {
  const h: Record<string, string> = {}
  const token = localStorage.getItem('token')
  if (token) h.Authorization = `Bearer ${token}`
  const ws = localStorage.getItem('workspaceId') || localStorage.getItem('currentWorkspaceId')
  if (ws) h['X-Workspace-Id'] = ws
  return h
}

async function loadList() {
  loading.value = true
  try {
    const params = new URLSearchParams({ page: String(page.value), size: String(size.value) })
    if (keyword.value) params.set('keyword', keyword.value)
    if (sourceTypeFilter.value) params.set('sourceType', sourceTypeFilter.value)
    const res = await fetch(`${ctxPath}/api/v1/wiki/record-templates?${params.toString()}`, {
      headers: authHeaders(),
    })
    const body = await res.json()
    if (!res.ok || body.code !== 200) throw new Error(body?.msg || `HTTP ${res.status}`)
    records.value = body.data?.records || []
    total.value = body.data?.total || 0
  } catch (e: any) {
    mcToast.error(e?.message || '加载模板列表失败')
  } finally {
    loading.value = false
  }
}

function onPage(p: number) {
  page.value = p
  loadList()
}

function openUpload() {
  uploadFile.value = null
  uploadContentJson.value = ''
  uploadExtJson.value = ''
  uploadKbId.value = null
  uploadVisible.value = true
}

function pickFile() {
  fileInput.value?.click()
}
function onFile(e: Event) {
  uploadFile.value = (e.target as HTMLInputElement).files?.[0] || null
}
function onDrop(e: DragEvent) {
  uploadFile.value = e.dataTransfer?.files?.[0] || null
}

async function doUpload() {
  if (!uploadFile.value) return
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', uploadFile.value)
    if (uploadContentJson.value.trim()) fd.append('contentJson', uploadContentJson.value)
    if (uploadExtJson.value.trim()) fd.append('extAttrsJson', uploadExtJson.value)
    if (uploadKbId.value != null) fd.append('kbId', String(uploadKbId.value))
    const res = await fetch(`${ctxPath}/api/v1/wiki/record-templates/upload`, {
      method: 'POST',
      headers: authHeaders(),
      body: fd,
    })
    const body = await res.json()
    if (!res.ok || body.code !== 200) throw new Error(body?.msg || `HTTP ${res.status}`)
    mcToast.success('上传成功')
    uploadVisible.value = false
    loadList()
  } catch (e: any) {
    mcToast.error(e?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

function openEdit(row: any) {
  editForm.value = { ...row }
  editVisible.value = true
}

function openPreview(row: any) {
  previewForm.value = { ...row }
  previewVisible.value = true
  previewContent.value = ''
  previewLoading.value = true
  if (fileBlobUrl.value) {
    URL.revokeObjectURL(fileBlobUrl.value)
    fileBlobUrl.value = ''
  }
  previewType.value = row.sourceType === 'pdf' ? 'pdf' : 'text'
  if (previewType.value === 'pdf') loadPdf(row.id)
  else loadText(row.id)
}

async function loadText(id: number) {
  try {
    const res = await fetch(`${ctxPath}/api/v1/wiki/record-templates/${id}/content`, {
      headers: authHeaders(),
    })
    const body = await res.json()
    if (!res.ok || body.code !== 200) throw new Error(body?.msg || `HTTP ${res.status}`)
    previewContent.value = body.data || '（无可抽取文本）'
  } catch (e: any) {
    previewContent.value = '预览失败：' + (e?.message || '')
  } finally {
    previewLoading.value = false
  }
}

async function loadPdf(id: number) {
  try {
    const res = await fetch(`${ctxPath}/api/v1/wiki/record-templates/${id}/file`, {
      headers: authHeaders(),
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const blob = await res.blob()
    fileBlobUrl.value = URL.createObjectURL(blob)
  } catch (e: any) {
    previewContent.value = 'PDF 预览失败：' + (e?.message || '')
    previewType.value = 'text'
  } finally {
    previewLoading.value = false
  }
}

async function downloadFile(row: any) {
  try {
    const res = await fetch(`${ctxPath}/api/v1/wiki/record-templates/${row.id}/file`, {
      headers: authHeaders(),
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.fileName || `${row.name}.${row.sourceType}`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    mcToast.error(e?.message || '下载失败')
  }
}

function onPreviewClose() {
  if (fileBlobUrl.value) {
    URL.revokeObjectURL(fileBlobUrl.value)
    fileBlobUrl.value = ''
  }
}

function editFromPreview() {
  editForm.value = { ...previewForm.value }
  editVisible.value = true
  previewVisible.value = false
}

async function getDetail(id: number): Promise<any | null> {
  try {
    const res = await fetch(`${ctxPath}/api/v1/wiki/record-templates/${id}`, { headers: authHeaders() })
    const body = await res.json()
    if (!res.ok || body.code !== 200) throw new Error(body?.msg || `HTTP ${res.status}`)
    return body.data
  } catch (e: any) {
    mcToast.error(e?.message || '获取详情失败')
    return null
  }
}

async function doSave() {
  const f = editForm.value
  if (!f.id) return
  try {
    if (f.contentJson?.trim()) JSON.parse(f.contentJson)
  } catch {
    mcToast.error('模板内容 JSON 不合法')
    return
  }
  try {
    if (f.extAttrsJson?.trim()) JSON.parse(f.extAttrsJson)
  } catch {
    mcToast.error('扩展属性 JSON 不合法')
    return
  }
  saving.value = true
  try {
    const res = await fetch(`${ctxPath}/api/v1/wiki/record-templates/${f.id}`, {
      method: 'PUT',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: f.name,
        contentJson: f.contentJson,
        extAttrsJson: f.extAttrsJson,
        kbId: f.kbId ?? null,
      }),
    })
    const body = await res.json()
    if (!res.ok || body.code !== 200) throw new Error(body?.msg || `HTTP ${res.status}`)
    mcToast.success('保存成功')
    editVisible.value = false
    loadList()
  } catch (e: any) {
    mcToast.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function reExtract(row: any) {
  if (!row.id) return
  try {
    const res = await fetch(`${ctxPath}/api/v1/wiki/record-templates/${row.id}/re-extract`, {
      method: 'POST',
      headers: authHeaders(),
    })
    const body = await res.json()
    if (!res.ok || body.code !== 200) throw new Error(body?.msg || `HTTP ${res.status}`)
    mcToast.success('样式已重新抽取')
    if (editVisible.value && editForm.value.id === row.id) {
      const detail = await getDetail(row.id)
      if (detail) editForm.value = { ...detail }
    }
    loadList()
  } catch (e: any) {
    mcToast.error(e?.message || '重抽样式失败')
  }
}

async function remove(row: any) {
  if (!row.id) return
  if (!window.confirm(`确认删除模板「${row.name}」？此操作不可恢复。`)) return
  try {
    const res = await fetch(`${ctxPath}/api/v1/wiki/record-templates/${row.id}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    const body = await res.json()
    if (!res.ok || body.code !== 200) throw new Error(body?.msg || `HTTP ${res.status}`)
    mcToast.success('已删除')
    loadList()
  } catch (e: any) {
    mcToast.error(e?.message || '删除失败')
  }
}

onMounted(() => {
  loadKbs()
  loadList()
})
</script>

<style scoped>
.bingli-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.section-header {
  margin-bottom: 4px;
}
.section-title {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 6px;
  color: var(--mc-text-primary);
}
.section-desc {
  font-size: 13px;
  color: var(--mc-text-secondary);
  margin: 0;
  line-height: 1.6;
}

.toolbar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.toolbar .kw {
  width: 240px;
}
.toolbar .st {
  width: 160px;
}

.tpl-table {
  background: var(--mc-bg-elevated);
  border-radius: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
}

.file-drop {
  border: 1px dashed var(--mc-border);
  border-radius: 14px;
  padding: 26px;
  text-align: center;
  color: var(--mc-text-tertiary);
  font-size: 13px;
  cursor: pointer;
  background: var(--mc-bg-muted);
}
.file-name {
  color: var(--mc-text-primary);
  font-weight: 600;
}
.name-link {
  padding-left: 0;
  font-weight: 600;
}
.mt-16 {
  margin-top: 16px;
}
.kb-select {
  width: 100%;
}

.file-preview-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.fpt-hint {
  font-size: 12px;
  color: var(--mc-text-secondary);
}
.file-frame {
  width: 100%;
  height: 460px;
  border: 1px solid var(--mc-border);
  border-radius: 10px;
  background: #fff;
}
.file-text {
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 460px;
  overflow: auto;
  background: var(--mc-bg-muted);
  padding: 12px 14px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.7;
  margin: 0;
  color: var(--mc-text-primary);
}
</style>
