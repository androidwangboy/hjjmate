<template>
  <div class="settings-section">
    <div class="section-header">
      <div>
        <h2 class="section-title">{{ t('security.members.title') }}</h2>
        <p class="section-desc">{{ t('security.members.desc') }}</p>
      </div>
      <button class="btn-primary" @click="openAddDialog">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        {{ t('security.members.addMember') }}
      </button>
    </div>

    <!-- Members Table -->
    <div class="rules-table-wrapper">
      <div v-if="loading" class="empty-state">{{ t('security.members.loading') }}</div>
      <div v-else-if="members.length === 0" class="empty-state">{{ t('security.members.noMembers') }}</div>
      <table v-else class="rules-table">
        <thead>
          <tr>
            <th>{{ t('security.members.columns.user') }}</th>
            <th>{{ t('security.members.columns.role') }}</th>
            <th>{{ t('security.members.columns.agents') }}</th>
            <th>{{ t('security.members.columns.joined') }}</th>
            <th style="width: 80px;">{{ t('security.members.columns.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="member in members" :key="member.id">
            <td>
              <div class="member-info">
                <div class="member-avatar">{{ (member.username || member.userId + '').charAt(0).toUpperCase() }}</div>
                <div class="member-detail">
                  <span class="member-name">{{ member.nickname || member.username || ('User #' + member.userId) }}</span>
                  <span v-if="member.username && member.nickname" class="member-username">@{{ member.username }}</span>
                </div>
              </div>
            </td>
            <td>
              <select
                :value="member.role"
                @change="updateRole(member, ($event.target as HTMLSelectElement).value)"
                :disabled="member.role === 'owner'"
                class="config-select"
              >
                <option value="owner" disabled>{{ t('security.members.roles.owner') }}</option>
                <option value="admin">{{ t('security.members.roles.admin') }}</option>
                <option value="member">{{ t('security.members.roles.member') }}</option>
                <option value="viewer">{{ t('security.members.roles.viewer') }}</option>
              </select>
            </td>
            <td class="agents-cell">
              <div class="multi-select" :class="{ 'is-disabled': member.role === 'owner' }">
                <div
                  class="multi-select-trigger form-input"
                  :class="{ 'is-open': openAgentRowId === member.id }"
                  @click="member.role !== 'owner' && toggleRowSelect(member)"
                >
                  <span v-if="member.role === 'owner'" class="owner-all">
                    {{ t('security.members.addDialog.agentsAll') }}
                  </span>
                  <span v-else-if="!(memberAgents[member.id] || []).length" class="placeholder">
                    {{ t('security.members.addDialog.agentsPlaceholder') }}
                  </span>
                  <span v-else class="multi-select-tags">
                    <span v-for="id in memberAgents[member.id]" :key="id" class="tag">
                      {{ agentName(id) }}
                      <button
                        type="button"
                        class="tag-remove"
                        :title="t('security.members.actions.remove')"
                        @click.stop="toggleRowAgent(member.id, id)"
                      >&times;</button>
                    </span>
                  </span>
                  <svg v-if="member.role !== 'owner'" class="caret" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="6 9 12 15 18 9" />
                  </svg>
                </div>
                <div v-if="openAgentRowId === member.id && member.role !== 'owner'" class="multi-select-panel">
                  <div v-if="agents.length === 0" class="multi-select-empty">
                    {{ t('security.members.addDialog.noAgents') }}
                  </div>
                  <label v-for="agent in agents" :key="agent.id" class="multi-select-option">
                    <input
                      type="checkbox"
                      :checked="(memberAgents[member.id] || []).includes(agent.id)"
                      @change="toggleRowAgent(member.id, agent.id)"
                    />
                    <span class="option-name">{{ agent.name }}</span>
                  </label>
                </div>
              </div>
            </td>
            <td class="date-cell">{{ formatDate(member.createTime) }}</td>
            <td>
              <div class="action-btns">
                <button
                  v-if="member.role !== 'owner'"
                  class="action-btn danger"
                  @click="removeMember(member)"
                  :title="t('security.members.actions.remove')"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                    <path d="M10 11v6"/><path d="M14 11v6"/>
                  </svg>
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Add Member Dialog -->
    <Teleport to="body">
      <div v-if="showAddDialog" class="modal-overlay">
        <div class="modal">
          <div class="modal-header">
            <h3>{{ t('security.members.addDialog.title') }}</h3>
            <button class="modal-close" @click="showAddDialog = false">&times;</button>
          </div>
          <div class="modal-body">
            <div class="form-grid" style="grid-template-columns: 1fr;">
              <div class="form-group">
                <label>{{ t('security.members.addDialog.username') }} <span class="required">*</span></label>
                <input v-model.trim="newMemberForm.username" type="text" class="form-input" :placeholder="t('security.members.addDialog.usernamePlaceholder')" />
                <span class="form-hint">{{ t('security.members.addDialog.usernameHint') }}</span>
              </div>
              <div class="form-group">
                <label>{{ t('security.members.addDialog.password') }}</label>
                <input v-model="newMemberForm.password" type="password" class="form-input" :placeholder="t('security.members.addDialog.passwordPlaceholder')" />
                <span class="form-hint">{{ t('security.members.addDialog.passwordHint') }}</span>
              </div>
              <div class="form-group">
                <label>{{ t('security.members.addDialog.nickname') }}</label>
                <input v-model.trim="newMemberForm.nickname" type="text" class="form-input" :placeholder="t('security.members.addDialog.nicknamePlaceholder')" />
              </div>
              <div class="form-group">
                <label>{{ t('security.members.addDialog.agents') }}</label>
                <div ref="agentSelectRef" class="multi-select">
                  <div
                    class="multi-select-trigger form-input"
                    :class="{ 'is-open': agentSelectOpen }"
                    @click="toggleAddAgentSelect"
                  >
                    <span v-if="newMemberForm.agents.length === 0" class="placeholder">
                      {{ t('security.members.addDialog.agentsPlaceholder') }}
                    </span>
                    <span v-else class="multi-select-tags">
                      <span v-for="id in newMemberForm.agents" :key="id" class="tag">
                        {{ agentName(id) }}
                        <button
                          type="button"
                          class="tag-remove"
                          :title="t('security.members.actions.remove')"
                          @click.stop="toggleAgent(id)"
                        >&times;</button>
                      </span>
                    </span>
                    <svg class="caret" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="6 9 12 15 18 9" />
                    </svg>
                  </div>
                  <div v-if="agentSelectOpen" class="multi-select-panel">
                    <div v-if="agents.length === 0" class="multi-select-empty">
                      {{ t('security.members.addDialog.noAgents') }}
                    </div>
                    <label v-for="agent in agents" :key="agent.id" class="multi-select-option">
                      <input
                        type="checkbox"
                        :checked="newMemberForm.agents.includes(agent.id)"
                        @change="toggleAgent(agent.id)"
                      />
                      <span class="option-name">{{ agent.name }}</span>
                    </label>
                  </div>
                </div>
                <span class="form-hint">{{ t('security.members.addDialog.agentsHint') }}</span>
              </div>
              <div class="form-group">
                <label>{{ t('security.members.addDialog.role') }}</label>
                <select v-model="newMemberForm.role" class="form-input">
                  <option value="admin">{{ t('security.members.roles.admin') }}</option>
                  <option value="member">{{ t('security.members.roles.member') }}</option>
                  <option value="viewer">{{ t('security.members.roles.viewer') }}</option>
                </select>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary" @click="showAddDialog = false; agentSelectOpen = false">{{ t('security.members.actions.cancel') }}</button>
            <button class="btn-primary" @click="addMember" :disabled="!newMemberForm.username">{{ t('security.members.actions.confirm') }}</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { mcToast } from '@/composables/useMcToast'
import { workspaceTeamApi, agentApi } from '@/api/index'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type { Agent } from "@/types";

const { t } = useI18n()

interface Member {
  id: number
  workspaceId: number
  userId: number
  username?: string
  nickname?: string
  role: string
  agents?: (string | number)[]
  createTime: string
}

const store = useWorkspaceStore()
const members = ref<Member[]>([])
const loading = ref(false)
const showAddDialog = ref(false)
const agents = ref<Agent[]>([])
const agentSelectOpen = ref(false)
const agentSelectRef = ref<HTMLElement | null>(null)
const openAgentRowId = ref<number | null>(null)
const memberAgents = reactive<Record<number, (string | number)[]>>({})
const agentNameFallback = reactive<Record<string, string>>({})

function openAddDialog() {
  Object.assign(newMemberForm, defaultForm())
  agentSelectOpen.value = false
  showAddDialog.value = true
}

const defaultForm = () => ({ username: '', password: '', nickname: '', role: 'member', agents: [] as (string | number)[] })
const newMemberForm = reactive(defaultForm())

onMounted(() => {
  fetchMembers()
  fetchAgents()
  document.addEventListener('mousedown', onDocMouseDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocMouseDown)
})

function onDocMouseDown(e: MouseEvent) {
  const target = e.target as Element
  const insideMulti = !!target.closest('.multi-select')
  const insidePanel = !!target.closest('.multi-select-panel')
  if (!insideMulti && !insidePanel) {
    if (agentSelectOpen.value) agentSelectOpen.value = false
    if (openAgentRowId.value !== null) openAgentRowId.value = null
  }
}

function toggleRowSelect(member: Member) {
  openAgentRowId.value = openAgentRowId.value === member.id ? null : member.id
}

function toggleAddAgentSelect() {
  agentSelectOpen.value = !agentSelectOpen.value
}

function toggleAgent(id: string | number) {
  const idx = newMemberForm.agents.indexOf(id)
  if (idx === -1) newMemberForm.agents.push(id)
  else newMemberForm.agents.splice(idx, 1)
}

function toggleRowAgent(memberId: number, id: string | number) {
  if (!Array.isArray(memberAgents[memberId])) memberAgents[memberId] = []
  const idx = memberAgents[memberId].indexOf(id)
  if (idx === -1) memberAgents[memberId].push(id)
  else memberAgents[memberId].splice(idx, 1)
  const member = members.value.find((m) => m.id === memberId)
  if (member) updateMemberAgents(member)
}

async function updateMemberAgents(member: Member) {
  const wsId = store.currentWorkspaceId
  if (!wsId) return
  try {
    await workspaceTeamApi.updateMemberRole(
      wsId,
      member.userId,
      member.role,
      [...(memberAgents[member.id] || [])],
    )
    mcToast.success(t('security.members.messages.agentSaveSuccess'))
  } catch {
    mcToast.error(t('security.members.messages.updateFailed'))
  }
}

function normalizeAgentIds(raw: unknown, fallback: Record<string, string>): (string | number)[] {
  if (!Array.isArray(raw)) return []
  return raw.map((x) => {
    if (x && typeof x === 'object' && 'id' in (x as Record<string, unknown>)) {
      const obj = x as Record<string, unknown>
      const id = obj.id as string | number
      if (obj.name) fallback[String(id)] = String(obj.name)
      return id
    }
    return x as string | number
  })
}

function agentName(id: string | number) {
  if (id && typeof id === 'object') {
    return (id as Record<string, unknown>).name as string || String(id)
  }
  const s = String(id)
  return agents.value.find((a) => String(a.id) === s)?.name || agentNameFallback[s] || s
}

async function fetchMembers() {
  const wsId = store.currentWorkspaceId
  if (!wsId) return
  loading.value = true
  try {
    const res: any = await workspaceTeamApi.listMembers(wsId)
    members.value = res.data || []
    for (const m of members.value) {
      memberAgents[m.id] = normalizeAgentIds(m.agents, agentNameFallback)
    }
  } catch (e: any) {
    mcToast.error(e.message)
  } finally {
    loading.value = false
  }
}

async function fetchAgents(){
  try{
    const res: any = await agentApi.list({enabled:true})
    agents.value = res.data || []
  }catch (e: any){
    mcToast.error(e.message)
  }
}
async function addMember() {
  const wsId = store.currentWorkspaceId
  if (!wsId || !newMemberForm.username) return
  try {
    await workspaceTeamApi.addMember(wsId, {
      username: newMemberForm.username,
      password: newMemberForm.password || undefined,
      nickname: newMemberForm.nickname || undefined,
      role: newMemberForm.role,
      agents: newMemberForm.agents,
    })
    mcToast.success(t('security.members.messages.addSuccess'))
    showAddDialog.value = false
    Object.assign(newMemberForm, defaultForm())
    fetchMembers()
  } catch (e: any) {
    mcToast.error(e?.msg || e?.message || t('security.members.messages.addFailed'))
  }
}

async function updateRole(member: Member, role: string) {
  const wsId = store.currentWorkspaceId
  if (!wsId) return
  try {
    await workspaceTeamApi.updateMemberRole(wsId, member.userId, role)
    member.role = role
    mcToast.success(t('security.members.messages.updateSuccess'))
  } catch {
    mcToast.error(t('security.members.messages.updateFailed'))
  }
}

async function removeMember(member: Member) {
  const wsId = store.currentWorkspaceId
  if (!wsId) return
  if (!confirm(t('security.members.messages.removeConfirm'))) return
  try {
    await workspaceTeamApi.removeMember(wsId, member.userId)
    mcToast.success(t('security.members.messages.removeSuccess'))
    fetchMembers()
  } catch {
    mcToast.error(t('security.members.messages.removeFailed'))
  }
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString()
}
</script>

<style>
@import '../shared.css';
</style>

<style scoped>
.member-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.member-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(217, 119, 87, 0.12);
  color: var(--mc-primary, #D97757);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
  flex-shrink: 0;
}

.member-detail {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.member-name {
  font-weight: 500;
  color: var(--mc-text-primary);
  font-size: 14px;
}

.member-username {
  font-size: 12px;
  color: var(--mc-text-tertiary);
}

.date-cell {
  color: var(--mc-text-tertiary);
  font-size: 13px;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.required {
  color: var(--mc-danger, #e74c3c);
}

.form-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--mc-text-tertiary);
}

.multi-select {
  position: relative;
}

.multi-select-trigger {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 38px;
  cursor: pointer;
  user-select: none;
}

.multi-select-trigger.is-open {
  border-color: var(--mc-primary, #D97757);
}

.multi-select-trigger .placeholder {
  color: var(--mc-text-tertiary);
}

.multi-select-trigger .caret {
  margin-left: auto;
  color: var(--mc-text-tertiary);
  flex-shrink: 0;
}

.multi-select-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.multi-select-tags .tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(217, 119, 87, 0.12);
  color: var(--mc-primary, #D97757);
  font-size: 12px;
}

.multi-select-tags .tag-remove {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0;
}

.multi-select-panel {
  position: absolute;
  z-index: 20;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  max-height: 220px;
  overflow-y: auto;
  background: var(--mc-bg-elevated, #fff);
  border: 1px solid var(--mc-border, #e2e2e2);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  padding: 4px;
}

.multi-select-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}

.multi-select-option:hover {
  background: var(--mc-bg-hover, #f5f5f5);
}

.multi-select-option input {
  accent-color: var(--mc-primary, #D97757);
}

.multi-select-empty {
  padding: 10px;
  font-size: 13px;
  color: var(--mc-text-tertiary);
  text-align: center;
}

.agents-cell {
  vertical-align: top;
}

.agents-cell .multi-select {
  min-width: 180px;
}

/* owner 角色不可选择专家，显示"全部" */
.multi-select.is-disabled {
  opacity: 0.75;
  pointer-events: none;
}

.owner-all {
  color: var(--mc-text-secondary, #555);
  font-size: 14px;
}

/* 专家下拉为绝对定位，需放开容器的 overflow 否则被裁剪；同时圆角补偿 */
.rules-table-wrapper {
  overflow: visible;
}

.modal {
  overflow: visible;
}

.rules-table thead th:first-child {
  border-top-left-radius: 10px;
}

.rules-table thead th:last-child {
  border-top-right-radius: 10px;
}

.rules-table tbody tr:last-child td:first-child {
  border-bottom-left-radius: 10px;
}

.rules-table tbody tr:last-child td:last-child {
  border-bottom-right-radius: 10px;
}
</style>
