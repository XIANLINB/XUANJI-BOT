<script setup lang="ts">
import { ref, computed, onMounted, h, watch } from 'vue'
import { useMessage } from 'naive-ui'
import {
  NCard, NButton, NSpace, NSelect, NInput, NTag, NDataTable, NModal,
  NForm, NFormItem, NIcon, NPopconfirm, NSpin, NTabs, NTabPane, NSwitch, NTooltip, NText
} from 'naive-ui'
import {
  SparklesOutline, AddOutline, TrashOutline, CreateOutline, ColorWandOutline,
  BookOutline, PersonCircleOutline
} from '@vicons/ionicons5'
import api from '../api'
import type { LlmPersona, LlmMemoryRow, UserProfileRow } from '../api/llm'
import PageHero from '../components/PageHero.vue'
import EmptyState from '../components/EmptyState.vue'
import dayjs from 'dayjs'
import { useBotsStore } from '../stores/bots'

const message = useMessage()
const botsStore = useBotsStore()

const botKey = ref<string>('')
const personas = ref<LlmPersona[]>([])
const templates = ref<LlmPersona[]>([])
const memories = ref<LlmMemoryRow[]>([])
const profiles = ref<UserProfileRow[]>([])
const loading = ref(false)
const memLoading = ref(false)
const profLoading = ref(false)
const groups = ref<any[]>([])

// ──────────── 表单 ────────────
const showModal = ref(false)
const showTemplate = ref(false)
const editing = ref<LlmPersona | null>(null)
// 初始值仅作占位，openAdd/openEdit/applyTemplate 总用 emptyForm() 覆盖
const form = ref<LlmPersona>(emptyForm())

/** 时间（后端 UTC/ISO）→ UTC+8 展示。 */
function fmtSubTime(v: unknown): string {
  if (v == null || v === '') return '—'
  const d = dayjs(String(v))
  return d.isValid() ? d.utcOffset(8).format('YYYY-MM-DD HH:mm:ss') : String(v)
}

// ──────────── 记忆新增 ────────────
const memKey = ref('')
const memValue = ref('')

const botOptions = computed(() =>
  botsStore.bots.map(b => ({ label: b.name || b.botKey || b.appId || '', value: b.botKey || b.appId || '' }))
)
const groupOptions = computed(() =>
  groups.value.map((g: any) => {
    const id = g.GROUP_ID || g.groupId || g.groupOpenid || ''
    const name = g.GROUP_NAME || g.groupName || id
    return { label: name || id, value: id }
  })
)

// ──────────── 单聊用户（USER 级人格直接选"某个单聊用户"，无需群） ────────────
const friends = ref<any[]>([])
const friendOptions = computed(() =>
  friends.value
    .filter((f: any) => !form.value?.botKey || String(f.BOT_APPID ?? f.botKey ?? '') === String(form.value?.botKey ?? ''))
    .map((f: any) => {
      const id = String(f.PLATFORM_USER_ID ?? f.platform_user_id ?? f.USER_ID ?? f.user_id ?? f.openid ?? f.userId ?? '')
      const name = f.nickname || f.username || f.remark || id
      return { label: name || id, value: id }
    })
)
async function loadFriends() {
  try {
    friends.value = (await api.getFriends()) || []
  } catch {
    friends.value = []
  }
}

const SCOPE_META: Record<string, { type: 'primary' | 'success' | 'warning' | 'default'; label: string }> = {
  BOT: { type: 'primary', label: '机器人' },
  GROUP: { type: 'success', label: '群' },
  USER: { type: 'warning', label: '用户' }
}
function scopeTag(scope: string) {
  const c = SCOPE_META[scope] || { type: 'default', label: scope }
  return h(NTag, { size: 'small', type: c.type }, { default: () => c.label })
}

const columns = [
  { title: '级别', key: 'scope', width: 80, render: (r: LlmPersona) => scopeTag(r.scope) },
  { title: '名称', key: 'name', width: 110, render: (r: LlmPersona) => r.name || '（未命名）' },
  { title: '覆盖对象', key: 'target', render: (r: LlmPersona) =>
      r.scope === 'GROUP' ? (r.groupId || '—') : r.scope === 'USER' ? (r.userId || '—') : '（全局）' },
  { title: '角色扮演', key: 'roleplayMode', width: 90, render: (r: LlmPersona) =>
      r.roleplayMode ? h(NTag, { size: 'small', type: 'warning' }, { default: () => '🎭 开启' }) : '—' },
  { title: '更新时间', key: 'updatedAt', width: 150, render: (r: LlmPersona) => fmtSubTime(r.updatedAt) },
  { title: '操作', key: 'actions', width: 130, render: (r: LlmPersona) =>
      h(NSpace, { size: 6 }, {
        default: () => [
          h(NButton, { size: 'tiny', secondary: true, onClick: () => openEdit(r) }, { default: () => '编辑' }),
          h(NPopconfirm, { onPositiveClick: () => remove(r) }, {
            trigger: () => h(NButton, { size: 'tiny', type: 'error', secondary: true }, { default: () => '删除' }),
            default: () => '确认删除？'
          })
        ]
      }) },
]

const memColumns = [
  { title: 'Key', key: 'key', width: 140 },
  { title: '内容', key: 'value', ellipsis: { tooltip: true } },
  { title: '级别', key: 'scope', width: 80, render: (r: LlmMemoryRow) =>
      r.groupId ? h(NTag, { size: 'small', type: 'success' }, { default: () => '群' })
        : r.userId ? h(NTag, { size: 'small', type: 'warning' }, { default: () => '用户' })
        : h(NTag, { size: 'small', type: 'primary' }, { default: () => '机器人' }) },
  { title: '更新时间', key: 'updatedAt', width: 150, render: (r: LlmMemoryRow) => fmtSubTime(r.updatedAt) },
  { title: '操作', key: 'actions', width: 80, render: (r: LlmMemoryRow) =>
      h(NPopconfirm, { onPositiveClick: () => removeMemory(r) }, {
        trigger: () => h(NButton, { size: 'tiny', type: 'error', secondary: true }, { default: () => '删除' }),
        default: () => '确认删除？'
      }) },
]

async function loadPersonas() {
  if (!botKey.value) return
  loading.value = true
  try {
    personas.value = await api.llmApi.personas(botKey.value)
  } catch (e: any) {
    message.error('加载人格失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

async function loadMemories() {
  if (!botKey.value) return
  memLoading.value = true
  try {
    memories.value = await api.llmApi.memory(botKey.value)
  } catch (e: any) {
    message.error('加载记忆失败: ' + (e.message || e))
  } finally {
    memLoading.value = false
  }
}

// ──────────── 用户认知（画像） ────────────
async function loadProfiles() {
  if (!botKey.value) return
  profLoading.value = true
  try {
    profiles.value = await api.llmApi.userProfiles(botKey.value)
  } catch (e: any) {
    message.error('加载用户认知失败: ' + (e.message || e))
  } finally {
    profLoading.value = false
  }
}

async function removeProfile(r: UserProfileRow) {
  try {
    await api.llmApi.deleteUserProfile(r.botKey, r.groupId, r.userId)
    message.success('已删除该用户画像')
    loadProfiles()
  } catch (e: any) {
    message.error('删除失败: ' + (e.message || e))
  }
}

const profileColumns = [
  { title: '昵称', key: 'nickname', width: 110, render: (r: UserProfileRow) => r.nickname || '（未知）' },
  { title: '角色', key: 'role', width: 80, render: (r: UserProfileRow) =>
      r.role ? h(NTag, { size: 'small', type: 'info' }, { default: () => r.role }) : '—' },
  { title: '消息数', key: 'msgCount', width: 80 },
  { title: '画像摘要', key: 'summary', ellipsis: { tooltip: true }, render: (r: UserProfileRow) =>
      r.summary || h(NText, { depth: 3 }, { default: () => '（尚未提炼，需开用户画像并等待间隔）' }) },
  { title: '说话风格', key: 'style', width: 180, ellipsis: { tooltip: true }, render: (r: UserProfileRow) =>
      r.style || '—' },
  { title: '最后活跃', key: 'lastSeen', width: 150, render: (r: UserProfileRow) => fmtSubTime(r.lastSeen) },
  { title: '操作', key: 'actions', width: 80, render: (r: UserProfileRow) =>
      h(NPopconfirm, { onPositiveClick: () => removeProfile(r) }, {
        trigger: () => h(NButton, { size: 'tiny', type: 'error', secondary: true }, { default: () => '删除' }),
        default: () => '确认删除该用户认知？'
      }) },
]

// ──────────── 编辑表单 ────────────

// 级别变化时联动：scope=BOT 清空一切；scope=GROUP 清 userId；scope=USER 清 groupId（直接选单聊用户，不依赖群）
watch(() => form.value?.scope, (v) => {
  if (v === 'BOT') {
    form.value.groupId = null
    form.value.userId = null
  } else if (v === 'GROUP') {
    form.value.userId = null
  } else if (v === 'USER') {
    form.value.groupId = null // 单聊用户不再需要指定群
  }
})

async function loadBots() {
  try {
    await botsStore.loadBots()
    if (botsStore.bots.length > 0) botKey.value = botsStore.bots[0].botKey || botsStore.bots[0].appId || ''
  } catch (e: any) {
    message.error('加载机器人失败: ' + (e.message || e))
  }
}

async function loadGroups() {
  try {
    groups.value = (await api.getGroups()) || []
  } catch {
    groups.value = []
  }
}

async function loadTemplates() {
  try {
    templates.value = (await api.llmApi.personaTemplates()) || []
  } catch {
    templates.value = []
  }
}

function emptyForm(): LlmPersona {
  return {
    id: 0, scope: 'BOT', botKey: botKey.value, groupId: null, userId: null,
    name: '', age: '', gender: '', personality: '', background: '', scenario: '',
    speechStyle: '', firstMes: '', mesExample: '', systemExtra: '', legacyPersona: '',
    anchors: '', roleplayMode: false
  }
}

function openAdd() {
  editing.value = null
  form.value = emptyForm()
  loadFriends() // 预加载单聊用户列表（USER 级用）
  showModal.value = true
}

function openEdit(p: LlmPersona) {
  editing.value = p
  form.value = { ...emptyForm(), ...p }
  loadFriends() // 预加载单聊用户列表（USER 级用）
  showModal.value = true
}

function applyTemplate(id: number) {
  const t = templates.value.find(x => x.id === id)
  if (!t) return
  form.value = { ...emptyForm(), ...t, id: 0, botKey: botKey.value, scope: 'BOT', groupId: null, userId: null }
  showTemplate.value = false
  showModal.value = true
  message.success(`已套用模版「${t.name}」，可继续编辑`)
}

async function save() {
  if (!form.value.botKey) {
    message.warning('请选择机器人')
    return
  }
  if (!form.value.name && !form.value.personality && !form.value.legacyPersona) {
    message.warning('至少填写名称或性格之一')
    return
  }
  if (form.value.scope === 'GROUP' && !form.value.groupId) {
    message.warning('群级人格必须指定群')
    return
  }
  if (form.value.scope === 'USER' && !form.value.userId) {
    message.warning('用户级人格必须指定单聊用户')
    return
  }
  if (form.value.roleplayMode && !form.value.personality && !form.value.anchors) {
    message.warning('角色扮演模式需填写性格或人设锚点')
    return
  }
  try {
    await api.llmApi.savePersona(form.value)
    message.success('已保存')
    showModal.value = false
    await loadPersonas()
  } catch (e: any) {
    message.error('保存失败: ' + (e.message || e))
  }
}

async function remove(p: LlmPersona) {
  try {
    await api.llmApi.deletePersona(p.id)
    message.success('已删除')
    await loadPersonas()
  } catch (e: any) {
    message.error('删除失败: ' + (e.message || e))
  }
}

async function addMemory() {
  if (!memKey.value.trim() || !memValue.value.trim()) {
    message.warning('Key 和内容不能为空')
    return
  }
  try {
    await api.llmApi.saveMemory({ botKey: botKey.value, key: memKey.value.trim(), value: memValue.value.trim() })
    message.success('已保存')
    memKey.value = ''
    memValue.value = ''
    await loadMemories()
  } catch (e: any) {
    message.error('保存失败: ' + (e.message || e))
  }
}

async function removeMemory(m: LlmMemoryRow) {
  try {
    await api.llmApi.deleteMemory(m.id)
    message.success('已删除')
    await loadMemories()
  } catch (e: any) {
    message.error('删除失败: ' + (e.message || e))
  }
}

onMounted(async () => {
  await loadBots()
  await loadGroups()
  await loadTemplates()
  if (botKey.value) {
    await loadPersonas()
    await loadMemories()
    await loadProfiles()
  }
})
</script>

<template>
  <div class="page">
    <PageHero title="人格管理" subtitle="三级人格：用户级 > 群级 > 机器人级。结构化角色卡 + 角色扮演模式（不自称AI）" :icon="SparklesOutline">
      <NSpace>
        <NSelect v-model:value="botKey" :options="botOptions" placeholder="选择机器人" clearable style="width: 200px"
          @update:value="() => { loadPersonas(); loadMemories(); loadProfiles() }" />
      </NSpace>
    </PageHero>

    <NCard :bordered="true">
      <NTabs type="line" animated>
        <NTabPane name="persona" tab="人格列表">
          <template v-if="!botKey">
            <EmptyState description="请先选择机器人" />
          </template>
          <template v-else>
            <div class="toolbar">
              <NButton type="primary" @click="openAdd">
                <template #icon><NIcon><AddOutline /></NIcon></template>
                新建人格
              </NButton>
              <NButton secondary @click="showTemplate = true">
                <template #icon><NIcon><ColorWandOutline /></NIcon></template>
                从模版新建
              </NButton>
            </div>
            <NSpin :show="loading">
              <NDataTable :columns="columns" :data="personas" :row-key="(r: LlmPersona) => r.id" :bordered="false" :pagination="{ pageSize: 20 }" />
            </NSpin>
          </template>
        </NTabPane>

        <NTabPane name="memory" tab="记忆管理">
          <template v-if="!botKey">
            <EmptyState description="请先选择机器人" />
          </template>
          <template v-else>
            <NForm label-placement="left" label-width="70" label-align="right" class="mem-form">
              <NFormItem label="Key">
                <NInput v-model:value="memKey" placeholder="如：我的名字 / 我的口味" style="width: 280px" />
              </NFormItem>
              <NFormItem label="内容">
                <NInput v-model:value="memValue" placeholder="如：小明 / 不吃辣" style="width: 360px" />
              </NFormItem>
              <NFormItem label=" ">
                <NButton type="primary" @click="addMemory">新增记忆</NButton>
              </NFormItem>
            </NForm>
            <div class="mem-tip">
              <NTag size="small" type="info" :bordered="false">
                对话中说「记住xxx」也会自动落库；本页可查看/删除 bot/群/用户记忆
              </NTag>
            </div>
            <NSpin :show="memLoading">
              <NDataTable :columns="memColumns" :data="memories" :row-key="(r: LlmMemoryRow) => r.id" :bordered="false" :pagination="{ pageSize: 20 }" />
            </NSpin>
          </template>
        </NTabPane>

        <NTabPane name="profile" tab="用户认知">
          <template v-if="!botKey">
            <EmptyState description="请先选择机器人" />
          </template>
          <template v-else>
            <div class="mem-tip">
              <NTag size="small" type="info" :bordered="false">
                全量消息模式下日积月累的群成员画像（需在 AI 设置开启「用户画像」）。AI 对话时会注入「当前对话者」认知
              </NTag>
            </div>
            <NSpin :show="profLoading">
              <NDataTable :columns="profileColumns" :data="profiles" :row-key="(r: UserProfileRow) => r.botKey + ':' + r.groupId + ':' + r.userId" :bordered="false" :pagination="{ pageSize: 20 }" />
            </NSpin>
          </template>
        </NTabPane>
      </NTabs>
    </NCard>

    <!-- 从模版新建 -->
    <NModal v-model:show="showTemplate" preset="card" title="从模版新建" style="width: 480px">
      <NSpace vertical>
        <EmptyState v-if="templates.length === 0" description="暂无内置模版" />
        <NButton v-for="t in templates" :key="t.id" size="large" secondary block @click="applyTemplate(t.id)">
          <template #icon><NIcon><ColorWandOutline /></NIcon></template>
          {{ t.name }}<span v-if="t.age">（{{ t.age }}岁）</span>
          <NTag v-if="t.roleplayMode" size="small" type="warning" style="margin-left: 8px">角色扮演</NTag>
        </NButton>
      </NSpace>
    </NModal>

    <!-- 编辑人格 -->
    <NModal v-model:show="showModal" preset="card" :title="editing ? '编辑人格' : '新建人格'" style="width: 720px">
      <NForm label-placement="left" label-width="90" label-align="right">
        <div class="form-sec">基本信息</div>
        <NSpace vertical>
          <NSpace>
            <NFormItem label="级别" style="margin-bottom: 0">
              <NSelect v-model:value="form.scope" style="width: 150px"
                :options="[
                  { label: '机器人级（全局）', value: 'BOT' },
                  { label: '群级（指定群）', value: 'GROUP' },
                  { label: '用户级（指定用户）', value: 'USER' }
                ]" />
            </NFormItem>
            <NFormItem label="角色扮演" style="margin-bottom: 0">
              <NTooltip trigger="hover">
                <template #trigger><NSwitch v-model:value="form.roleplayMode" /></template>
                开启后 AI 不自称模型，按角色身份发言
              </NTooltip>
            </NFormItem>
          </NSpace>
          <NSpace v-if="form.scope === 'GROUP'" align="center">
            <NFormItem label="指定群" style="margin-bottom: 0">
              <NSelect v-model:value="form.groupId" :options="groupOptions" placeholder="选择群（仅作用于该群）" clearable style="width: 280px" />
            </NFormItem>
          </NSpace>
          <NSpace v-if="form.scope === 'USER'" align="center">
            <NFormItem label="单聊用户" style="margin-bottom: 0">
              <NSelect
                v-model:value="form.userId"
                :options="friendOptions"
                placeholder="选择单聊用户（该用户私聊时生效，记忆/人格独立于群聊）"
                clearable
                filterable
                style="width: 280px"
              />
            </NFormItem>
            <NText depth="3" style="font-size: 12px">单聊级记忆只存在该用户，不与 bot/群聊共享</NText>
          </NSpace>
          <NSpace>
            <NFormItem label="姓名" style="margin-bottom: 0">
              <NInput v-model:value="form.name" placeholder="如：苏落落" style="width: 160px" />
            </NFormItem>
            <NFormItem label="年龄" style="margin-bottom: 0">
              <NInput v-model:value="form.age" placeholder="如：16" style="width: 100px" />
            </NFormItem>
            <NFormItem label="称呼" style="margin-bottom: 0">
              <NInput v-model:value="form.gender" placeholder="她/他/它" style="width: 100px" />
            </NFormItem>
          </NSpace>
        </NSpace>

        <div class="form-sec">性格与背景</div>
        <NFormItem label="性格">
          <NInput v-model:value="form.personality" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }"
            placeholder="第二人称：你是.../你总是...。越具体越好" />
        </NFormItem>
        <NFormItem label="背景故事">
          <NInput v-model:value="form.background" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }"
            placeholder="身份、外貌、身世等" />
        </NFormItem>
        <NFormItem label="场景设定">
          <NInput v-model:value="form.scenario" placeholder="如：你在经营一家深夜咖啡馆" />
        </NFormItem>

        <div class="form-sec">风格与示例</div>
        <NFormItem label="说话风格">
          <NInput v-model:value="form.speechStyle" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="口头禅、语气、句长，如：简短、爱用 emoji、口头禅是「就是说」" />
        </NFormItem>
        <NFormItem label="示例对话">
          <NInput v-model:value="form.mesExample" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }"
            placeholder="教语气的对话范例（可含 emoji）：&#10;你：今天好累&#10;落落：哇！快坐下～" />
        </NFormItem>

        <div class="form-sec">高级</div>
        <NFormItem label="开场白">
          <NInput v-model:value="form.firstMes" placeholder="新对话第一条消息（可选）" />
        </NFormItem>
        <NFormItem label="人设锚点">
          <NTooltip trigger="hover">
            <template #trigger>
              <NInput v-model:value="form.anchors" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }"
                placeholder="语气/世界观/禁忌，用 | 或换行分隔，如：高冷话少 | 绝不主动提起过去 | 讨厌被人叫小可爱" />
            </template>
            角色扮演模式下 AI 每次回复后自动自评是否偏离锚点，偏离自动记录并强化（建议 3~5 条）
          </NTooltip>
        </NFormItem>
        <NFormItem label="额外指令">
          <NInput v-model:value="form.systemExtra" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="追加的系统指令（可选）" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showModal = false">取消</NButton>
          <NButton type="primary" @click="save">保存</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.form-sec {
  font-size: 13px;
  font-weight: 600;
  color: #5b8def;
  margin: 12px 0 6px;
  padding-left: 8px;
  border-left: 3px solid #5b8def;
}
.mem-form {
  margin-bottom: 4px;
}
.mem-tip {
  margin-bottom: 10px;
}
</style>
