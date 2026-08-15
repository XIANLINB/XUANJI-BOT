<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useMessage } from 'naive-ui'
import {
  NCard, NButton, NSpace, NInput, NInputNumber, NSwitch,
  NSelect, NText, NTag, NDivider, NIcon, NTooltip,
  NPopconfirm, NTabs, NTabPane, NAlert, NGrid, NGi, NSpin
} from 'naive-ui'
import {
  PlanetOutline, PeopleOutline, SettingsOutline,
  ShieldCheckmarkOutline, RefreshOutline, InformationCircleOutline,
  ChatbubbleEllipsesOutline, ServerOutline, CloudUploadOutline, EyeOutline,
  WarningOutline, ExtensionPuzzleOutline, MegaphoneOutline, ArchiveOutline
} from '@vicons/ionicons5'
import api from '../api'
import PageHero from '../components/PageHero.vue'
import EmptyState from '../components/EmptyState.vue'
import { groupName } from '../utils/names'
import { useBotsStore } from '../stores/bots'
import { useContactsStore } from '../stores/contacts'

const message = useMessage()

interface GroupRow { GROUP_ID?: string; groupId?: string; groupOpenid?: string; GROUP_NAME?: string; groupName?: string }

// ════════════════════════════════════════════════════════════════
//  配置定义：全局（xuanji_config）+ 机器人/群（xuanji_bot_setting / xuanji_group_setting）
// ════════════════════════════════════════════════════════════════

interface ConfigDef {
  key: string
  label: string
  module: string
  type: 'switch' | 'number' | 'text' | 'longtext' | 'select'
  defaultValue: string | number | boolean
  hint: string
  options?: { label: string; value: string }[]
  /** 关联的全局键（bot/群 级覆盖全局时用于展示继承值） */
  globalKey?: string
  source: string
}

/** 模块元数据（图标 + 说明） */
const MODULES: Record<string, { label: string; icon: any; desc: string }> = {
  命令: { label: '命令', icon: ChatbubbleEllipsesOutline, desc: '命令前缀 / 命令限速' },
  监控: { label: '监控', icon: EyeOutline, desc: '控制台页面自动刷新' },
  媒体: { label: '媒体', icon: CloudUploadOutline, desc: '图片/语音按需下载与存储' },
  网络: { label: '网络', icon: ServerOutline, desc: 'QQ 开放平台 API 接入' },
  群管: { label: '群管', icon: MegaphoneOutline, desc: '入群欢迎语' },
  熔断: { label: '熔断', icon: WarningOutline, desc: '连续失败自动停呼' },
  插件: { label: '插件', icon: ExtensionPuzzleOutline, desc: '插件执行超时隔离' },
  存储: { label: '存储', icon: ArchiveOutline, desc: '聊天消息留存' }
}

/** 全局配置（xuanji_config 表，点号命名） */
const GLOBAL_SCHEMA: ConfigDef[] = [
  { key: 'command.prefix', label: '命令前缀', module: '命令', type: 'text', defaultValue: '', source: '全局表', hint: '群/私聊命令的前缀。留空 = 不裁剪前缀' },
  { key: 'framework.rate_limit.enabled', label: '启用命令限速', module: '命令', type: 'switch', defaultValue: false, source: '全局表', hint: '同一用户在这个时间窗口内只能触发一次命令（开启后生效）' },
  { key: 'framework.rate_limit.window_ms', label: '限速窗口时长（ms）', module: '命令', type: 'number', defaultValue: 2000, source: '全局表', hint: '限速窗口毫秒；默认 2000ms' },
  { key: 'console.refresh_interval_ms', label: '监控页自动刷新间隔（ms）', module: '监控', type: 'number', defaultValue: 5000, source: '全局表', hint: '健康/仪表盘页面自动刷新间隔，默认 5000' },
  { key: 'media.download.enabled', label: '启用媒体按需下载', module: '媒体', type: 'switch', defaultValue: true, source: '全局表', hint: '框架层在收到图片/语音时按需下载到本地（按内容去重，TTL+配额清理）' },
  { key: 'media.download.max_file_bytes', label: '媒体单文件上限（字节）', module: '媒体', type: 'number', defaultValue: 209715200, source: '全局表', hint: '单文件超过此大小跳过；默认 200MB' },
  { key: 'media.storage.ttl_days', label: '媒体保留天数', module: '媒体', type: 'number', defaultValue: 7, source: '全局表', hint: '下载的媒体文件保留天数；过期自动删除' },
  { key: 'media.storage.max_bytes', label: '媒体总配额（字节）', module: '媒体', type: 'number', defaultValue: 4294967296, source: '全局表', hint: '所有媒体总占用；超限自动删最旧；默认 4GB' },
  { key: 'framework.qqbot.api_base_mode', label: 'QQ 开放平台 API 基地址', module: '网络', type: 'select', defaultValue: 'new', source: '全局表', hint: 'new=新统一地址 api.bot.qq.com（不区分沙箱/正式，推荐）；legacy=老平台 api.sgroup.qq.com，按机器人环境自动选正式/沙箱。改后 30 秒内生效（WebSocket 连接需重启机器人）', options: [
    { label: '新统一地址（api.bot.qq.com）', value: 'new' },
    { label: '老平台（正式/沙箱自动区分）', value: 'legacy' }
  ] },
  { key: 'msg.retention.days', label: '聊天消息留存天数', module: '存储', type: 'number', defaultValue: 30, source: '全局表', hint: '群聊/单聊消息只保留最近 N 天，超过的由每日 04:10 定时任务自动删除（防止数据库无限增长）。默认 30 天，<1 视为 30。与「聊天消息定时备份」配合：先备份再清理。' }
]

/** 机器人/群配置（xuanji_bot_setting / xuanji_group_setting，下划线命名，可覆盖全局） */
const BOT_SCHEMA: ConfigDef[] = [
  { key: 'command_prefix', label: '命令前缀', module: '命令', type: 'text', defaultValue: '', globalKey: 'command.prefix', source: 'bot/群表', hint: '该机器人/群专属命令前缀（覆盖全局）' },
  { key: 'rate_limit_enabled', label: '启用命令限速', module: '命令', type: 'switch', defaultValue: false, globalKey: 'framework.rate_limit.enabled', source: 'bot/群表', hint: '该机器人/群是否启用命令限速（覆盖全局）' },
  { key: 'rate_limit_window_ms', label: '限速窗口（ms）', module: '命令', type: 'number', defaultValue: 2000, globalKey: 'framework.rate_limit.window_ms', source: 'bot/群表', hint: '该机器人/群专属限速窗口' },
  { key: 'welcome_enabled', label: '入群欢迎语', module: '群管', type: 'switch', defaultValue: false, source: 'bot/群表', hint: '机器人被加入该群时自动发送欢迎语' },
  { key: 'welcome_message', label: '欢迎语内容', module: '群管', type: 'longtext', defaultValue: '', source: 'bot/群表', hint: '支持多行文本，可包含 @变量' },
  { key: 'cb_threshold', label: '熔断阈值', module: '熔断', type: 'number', defaultValue: 5, source: 'bot/群表', hint: '连续失败多少次后熔断停呼' },
  { key: 'cb_cooldown_ms', label: '熔断冷却（ms）', module: '熔断', type: 'number', defaultValue: 30000, source: 'bot/群表', hint: '熔断后多久恢复' },
  { key: 'plugin_timeout_ms', label: '插件超时（ms）', module: '插件', type: 'number', defaultValue: 5000, source: 'bot/群表', hint: '插件执行超过此值则隔离跳过' },
  { key: 'media_download_enabled', label: '启用媒体按需下载', module: '媒体', type: 'switch', defaultValue: true, globalKey: 'media.download.enabled', source: 'bot/群表', hint: '该机器人/群是否启用媒体下载（覆盖全局）' }
]

/** 模块顺序 */
const MODULE_ORDER = ['命令', '群管', '熔断', '插件', '媒体', '监控', '网络', '存储']

// ════════════════════════════════════════════════════════════════
//  状态
// ════════════════════════════════════════════════════════════════

const loading = ref(false)
const saving = ref(false)
const activeTab = ref('global')  // global / bot / group / ignore

const selectedBot = ref<string>('')
const selectedGroup = ref<string>('')  // 空 = bot 级

// 机器人 / 群列表统一收拢到 Pinia
const botsStore = useBotsStore()
const contacts = useContactsStore()
const bots = computed(() => botsStore.bots)
const botConfigs = ref<Record<string, Record<string, string>>>({})
const groupConfigs = ref<Record<string, Record<string, Record<string, string>>>>({})
const globalRows = ref<Record<string, string>>({})

// 真实群列表（/console/contacts/groups 返回全量，按 BOT_APPID 过滤）
const allGroups = ref<GroupRow[]>([])

// 改动暂存
const dirty = ref<Record<string, string>>({})

// 配置项关键字过滤（按名称/键过滤，三 tab 共享）
const filterText = ref('')
function matchesFilter(item: ConfigDef): boolean {
  if (!filterText.value.trim()) return true
  const kw = filterText.value.trim().toLowerCase()
  return item.label.toLowerCase().includes(kw) || item.key.toLowerCase().includes(kw)
}

// 当前 Tab 展示的配置
const currentSchema = computed<ConfigDef[]>(() => {
  if (activeTab.value === 'global') return GLOBAL_SCHEMA
  return BOT_SCHEMA
})

// 群列表（当前 bot 下）：真实群 + 配置表里出现过但可能已退出的群（合并去重）
const groupsByBot = computed<GroupRow[]>(() => {
  if (!selectedBot.value) return []
  return groupsForBot(selectedBot.value)
})

// 按 bot appId 过滤真实群列表（忽略消息 Tab 遍历所有 bot 时用）
function groupsForBot(appId: string): GroupRow[] {
  const byConfig = Object.keys(groupConfigs.value?.[appId] || {}).map(gid => ({ GROUP_ID: gid, groupId: gid } as GroupRow))
  const byReal = allGroups.value
    .filter(g => String(g.BOT_APPID ?? '') === String(appId))
    .map(g => ({ ...g, GROUP_ID: g.GROUP_ID || g.groupId || g.groupOpenid, groupId: g.GROUP_ID || g.groupId || g.groupOpenid } as GroupRow))
  const seen = new Set<string>()
  const merged: GroupRow[] = []
  for (const g of [...byReal, ...byConfig]) {
    const id = g.GROUP_ID || g.groupId || g.groupOpenid
    if (!id || seen.has(id)) continue
    seen.add(id)
    merged.push(g)
  }
  return merged
}

const groupOptions = computed(() => [
  { label: '（默认 = 整个机器人）', value: '' },
  ...groupsByBot.value.map(g => {
    const id = g.GROUP_ID || g.groupId || g.groupOpenid
    return { label: id, value: id }
  })
])

// ════════════════════════════════════════════════════════════════
//  数据加载 / 取值
// ════════════════════════════════════════════════════════════════

async function load() {
  loading.value = true
  try {
    const [cfg] = await Promise.all([
      api.getConfig(),
      botsStore.loadBots(),
      contacts.loadContacts()
    ])
    globalRows.value = cfg.global ?? {}
    botConfigs.value = cfg.bots ?? {}
    groupConfigs.value = cfg.groups ?? {}
    allGroups.value = (contacts.groups as GroupRow[]) || []
    dirty.value = {}
    // 保存/重置后忽略消息开关状态需同步刷新（Q13）
    loadIgnoreFromCfg()
  } catch (e: any) {
    message.error('加载失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

function defaultValueString(item: ConfigDef): string {
  const v = item.defaultValue
  return v === undefined || v === null ? '' : String(v)
}

/** 当前作用域的原始值（未设 = ''） */
function currentRaw(item: ConfigDef): string {
  if (activeTab.value === 'global') {
    return globalRows.value[item.key] ?? ''
  }
  if (selectedBot.value && selectedGroup.value) {
    return groupConfigs.value?.[selectedBot.value]?.[selectedGroup.value]?.[item.key] ?? ''
  }
  if (selectedBot.value) {
    return botConfigs.value?.[selectedBot.value]?.[item.key] ?? ''
  }
  return ''
}

/** 是否已设置（当前作用域存了值） */
function isSet(item: ConfigDef): boolean {
  return currentRaw(item) !== ''
}

/** 继承值（未设置时取全局对应键 / 默认值） */
function inheritedValue(item: ConfigDef): string {
  if (item.globalKey) {
    const g = globalRows.value[item.globalKey]
    if (g !== undefined && g !== '') return g
  }
  return defaultValueString(item)
}

/** 展示值（dirty 优先） */
function fieldValue(item: ConfigDef): string {
  if (item.key in dirty.value) return dirty.value[item.key]
  return currentRaw(item)
}

/** 开关展示值：已设置/dirty 用实际值，未设置回退 defaultValue（默认开/关显示）。 */
function switchValue(item: ConfigDef): boolean {
  const raw = fieldValue(item)
  if (raw !== '') return raw === 'true'
  return item.defaultValue === true
}

function setField(item: ConfigDef, v: string | boolean | number) {
  dirty.value[item.key] = String(v)
}

function isDirty(key: string): boolean {
  return key in dirty.value
}

// ════════════════════════════════════════════════════════════════
//  保存 / 重置
// ════════════════════════════════════════════════════════════════

async function save() {
  if (Object.keys(dirty.value).length === 0) {
    message.info('没有改动')
    return
  }
  saving.value = true
  try {
    const body = { ...dirty.value }
    // switch 归一化
    currentSchema.value.forEach(item => {
      if (item.type === 'switch' && item.key in body) {
        body[item.key] = body[item.key] === 'true' || body[item.key] === true ? 'true' : 'false'
      }
    })
    if (activeTab.value === 'global') {
      await api.putGlobalConfig(body)
    } else if (selectedGroup.value) {
      await api.putGroupConfig(selectedBot.value, selectedGroup.value, body)
    } else {
      await api.putBotConfig(selectedBot.value, body)
    }
    message.success('已保存（即时生效）')
    await load()
  } catch (e: any) {
    message.error('保存失败：' + (e?.message ?? e))
  } finally {
    saving.value = false
  }
}

async function resetKey(item: ConfigDef) {
  try {
    const scope = activeTab.value === 'global' ? 'global' : selectedGroup.value ? 'group' : 'bot'
    const botKey = activeTab.value === 'global' ? 'global' : selectedBot.value
    await api.deleteConfigKey(scope as any, botKey, item.key, selectedGroup.value || undefined)
    message.success(`已重置：${item.label}`)
    delete dirty.value[item.key]
    await load()
  } catch (e: any) {
    message.error('重置失败：' + (e?.message ?? e))
  }
}

function discardDirty() {
  dirty.value = {}
}

// ════════════════════════════════════════════════════════════════
//  忽略机器人消息（保留三级）
// ════════════════════════════════════════════════════════════════

const globalIgnore = ref(false)
const botIgnore = ref<Record<string, boolean>>({} as Record<string, boolean>)
const groupIgnore = ref<Record<string, Record<string, boolean>>>({})

function loadIgnoreFromCfg() {
  const g = globalRows.value['ignore_bot_messages']
  // 未配置时默认忽略（与后端 ConfigService.isIgnoreBotMessages 默认 true 对齐）
  globalIgnore.value = g === undefined ? true : (g === 'true' || g === true)
  bots.value.forEach(b => {
    const v = botConfigs.value?.[b.appId]?.['ignore_bot_messages']
    if (v !== undefined) botIgnore.value[b.appId] = v === 'true'
  })
  Object.entries(groupConfigs.value || {}).forEach(([bid, gmap]) => {
    Object.entries(gmap).forEach(([gid, kv]) => {
      if ('ignore_bot_messages' in kv) {
        if (!groupIgnore.value[bid]) groupIgnore.value[bid] = {}
        groupIgnore.value[bid][gid] = kv['ignore_bot_messages'] === 'true'
      }
    })
  })
}

async function saveIgnoreGlobal(v: boolean) {
  try {
    await api.putGlobalConfig({ ignore_bot_messages: v ? 'true' : 'false' })
    message.success(v ? '已全局忽略其他机器人消息' : '已取消全局忽略')
    await load()
  } catch (e: any) { message.error('保存失败：' + e?.message) }
}

async function saveIgnoreBot(appId: string, v: boolean) {
  try {
    if (v) await api.putBotConfig(appId, { ignore_bot_messages: 'true' })
    else await api.deleteConfigKey('bot', appId, 'ignore_bot_messages')
    message.success(`${appId} ${v ? '已忽略' : '已取消忽略'}机器人消息`)
    await load()
  } catch (e: any) { message.error('保存失败：' + e?.message) }
}

async function saveIgnoreGroup(appId: string, gid: string, v: boolean) {
  try {
    if (v) await api.putGroupConfig(appId, gid, { ignore_bot_messages: 'true' })
    else await api.deleteConfigKey('group', appId, 'ignore_bot_messages', gid)
    message.success(`${gid} ${v ? '已忽略' : '已取消忽略'}机器人消息`)
    await load()
  } catch (e: any) { message.error('保存失败：' + e?.message) }
}

watch(selectedBot, () => { selectedGroup.value = ''; dirty.value = {} })
watch(activeTab, () => { dirty.value = {} })

onMounted(async () => {
  await load()
})
</script>

<template>
  <div>
    <PageHero
      title="框架配置"
      subtitle="全局配置 · 机器人配置 · 群配置 · 忽略机器人消息 —— 修改即时生效，无需重启"
      :icon="SettingsOutline"
    >
      <NButton :loading="loading" @click="load">重新加载</NButton>
    </PageHero>

    <div v-if="loading" style="padding: 80px 0; text-align: center">
      <NSpin size="large" />
    </div>

    <template v-if="!loading">
      <NCard :bordered="false" class="settings-tabs-card">
        <NTabs v-model:value="activeTab" type="line" animated size="large" class="settings-tabs">

          <!-- ════════ Tab1：全局配置 ════════ -->
          <NTabPane name="global">
            <template #tab>
              <div class="tab-label">
                <NIcon size="18" color="#5b5bd6"><PlanetOutline /></NIcon>
                <span>全局配置</span>
              </div>
            </template>
            <div class="tab-pane-inner">
              <NAlert type="info" :show-icon="true" style="margin-bottom: 14px" class="tab-desc">
                全局配置存于 <b>xuanji_config</b> 表，对所有机器人生效。
                <b>机器人/群级未单独设置时，会继承这里的值。</b>
              </NAlert>

              <NInput v-model:value="filterText" clearable placeholder="搜索配置项（名称 / 键）" style="max-width: 320px; margin-bottom: 12px">
                <template #prefix><NIcon><InformationCircleOutline /></NIcon></template>
              </NInput>

              <div v-for="mod in MODULE_ORDER" :key="mod">
                <template v-if="GLOBAL_SCHEMA.some(s => s.module === mod && matchesFilter(s))">
                  <NCard size="small" :bordered="false" class="module-card">
                    <template #header>
                      <NSpace align="center" :size="6">
                        <NIcon size="15" color="#5b5bd6"><component :is="MODULES[mod].icon" /></NIcon>
                        <span style="font-weight: 600">{{ MODULES[mod].label }}模块</span>
                        <NText depth="3" style="font-size: 11px">{{ MODULES[mod].desc }}</NText>
                      </NSpace>
                    </template>
                    <div class="config-list">
                      <div v-for="item in GLOBAL_SCHEMA.filter(s => s.module === mod && matchesFilter(s))" :key="item.key"
                        class="config-row" :class="{ 'is-dirty': isDirty(item.key) }">
                        <div class="config-label">
                          <NText class="config-label-text">{{ item.label }}</NText>
                          <div class="config-label-sub">
                            <NTag size="tiny" :bordered="false" type="info">{{ item.source }}</NTag>
                            <NText depth="3" class="config-key">{{ item.key }}</NText>
                            <NTag size="tiny" :bordered="false" type="default" style="color: var(--n-text-color-3)">
                              默认：{{ item.type === 'switch' ? (item.defaultValue ? '开' : '关') : defaultValueString(item) }}
                            </NTag>
                          </div>
                        </div>
                        <div class="config-field">
                          <NSwitch v-if="item.type === 'switch'"
                            :value="switchValue(item)"
                            @update:value="(v: boolean) => setField(item, v)" />
                          <NInputNumber v-else-if="item.type === 'number'"
                            :value="Number(fieldValue(item)) || 0"
                            :placeholder="defaultValueString(item)"
                            style="width: 100%" :min="0"
                            @update:value="(v: number | null) => setField(item, v ?? 0)" />
                          <NSelect v-else-if="item.type === 'select'"
                            :value="fieldValue(item) || String(item.defaultValue)"
                            :options="item.options ?? []" style="width: 100%"
                            @update:value="(v: string | null) => setField(item, v ?? String(item.defaultValue))" />
                          <NInput v-else-if="item.type === 'longtext'" type="textarea"
                            :value="fieldValue(item)" :placeholder="defaultValueString(item) || '（多行文本）'"
                            :autosize="{ minRows: 2, maxRows: 5 }"
                            @update:value="(v: string) => setField(item, v)" />
                          <NInput v-else :value="fieldValue(item)"
                            :placeholder="defaultValueString(item) || '（空）'"
                            @update:value="(v: string) => setField(item, v)" />
                        </div>
                        <div class="config-actions">
                          <NTooltip trigger="hover">
                            <template #trigger>
                              <NButton size="small" quaternary @click="resetKey(item)">
                                <NIcon><RefreshOutline /></NIcon>
                              </NButton>
                            </template>
                            一键重置（删除该键 → 回到默认）
                          </NTooltip>
                        </div>
                      </div>
                    </div>
                  </NCard>
                </template>
              </div>

              <NDivider />
              <NSpace>
                <NButton type="primary" :loading="saving" :disabled="Object.keys(dirty).length === 0" @click="save">
                  保存配置{{ Object.keys(dirty).length ? `（${Object.keys(dirty).length} 项）` : '' }}
                </NButton>
                <NButton v-if="Object.keys(dirty).length" quaternary @click="discardDirty">放弃改动</NButton>
              </NSpace>
              <NText depth="3" class="tip">
                提示：未设置项显示默认值 placeholder；右侧 ⟳ 一键重置；「已设置」项才有实际写入值。
              </NText>
            </div>
          </NTabPane>

          <!-- ════════ Tab2：机器人配置 ════════ -->
          <NTabPane name="bot">
            <template #tab>
              <div class="tab-label">
                <NIcon size="18" color="#2090e0"><PeopleOutline /></NIcon>
                <span>机器人配置</span>
              </div>
            </template>
            <div class="tab-pane-inner">
              <NAlert type="info" :show-icon="true" style="margin-bottom: 14px" class="tab-desc">
                机器人配置存于 <b>xuanji_bot_setting</b> 表，只对该机器人生效。
                <b>未设置时继承全局</b>（显示灰底「继承全局」）；设置后覆盖全局（显示橙底「覆盖全局」）。
              </NAlert>

              <NCard size="small" :bordered="false" class="scope-bar">
                <NSpace align="center" :wrap="false">
                  <NText strong>机器人：</NText>
                  <NSelect v-model:value="selectedBot" :options="bots.map(b => ({ label: `${b.appId}（${b.platform}）`, value: b.appId }))"
                    placeholder="选机器人" style="width: 260px" />
                </NSpace>
              </NCard>

              <EmptyState v-if="!selectedBot" description="请先选机器人" />

              <div v-else>
                <NInput v-model:value="filterText" clearable placeholder="搜索配置项（名称 / 键）" style="max-width: 320px; margin-bottom: 12px">
                  <template #prefix><NIcon><InformationCircleOutline /></NIcon></template>
                </NInput>
                <div v-for="mod in MODULE_ORDER" :key="mod">
                  <template v-if="BOT_SCHEMA.some(s => s.module === mod && matchesFilter(s))">
                    <NCard size="small" :bordered="false" class="module-card">
                      <template #header>
                        <NSpace align="center" :size="6">
                          <NIcon size="15" color="#2090e0"><component :is="MODULES[mod].icon" /></NIcon>
                          <span style="font-weight: 600">{{ MODULES[mod].label }}模块</span>
                          <NText depth="3" style="font-size: 11px">{{ MODULES[mod].desc }}</NText>
                        </NSpace>
                      </template>
                      <div class="config-list">
                        <div v-for="item in BOT_SCHEMA.filter(s => s.module === mod && matchesFilter(s))" :key="item.key"
                          class="config-row" :class="{ 'is-dirty': isDirty(item.key) }">
                          <div class="config-label">
                            <NText class="config-label-text">{{ item.label }}</NText>
                            <div class="config-label-sub">
                              <NTag v-if="isSet(item)" size="tiny" type="warning" :bordered="false">覆盖全局</NTag>
                              <NTag v-else size="tiny" :bordered="false">继承全局</NTag>
                              <NText depth="3" class="config-key">{{ item.key }}</NText>
                            </div>
                          </div>
                          <div class="config-field">
                            <NSwitch v-if="item.type === 'switch'"
                              :value="isDirty(item.key) ? fieldValue(item) === 'true' : isSet(item) ? fieldValue(item) === 'true' : inheritedValue(item) === 'true'"
                              @update:value="(v: boolean) => setField(item, v)" />
                            <NInputNumber v-else-if="item.type === 'number'"
                              :value="Number(fieldValue(item)) || (isSet(item) ? 0 : Number(inheritedValue(item)) || 0)"
                              :placeholder="isSet(item) ? '' : '继承全局: ' + inheritedValue(item)"
                              style="width: 100%" :min="0"
                              @update:value="(v: number | null) => setField(item, v ?? 0)" />
                            <NInput v-else-if="item.type === 'longtext'" type="textarea"
                              :value="fieldValue(item)"
                              :placeholder="isSet(item) ? '' : '继承全局: ' + inheritedValue(item)"
                              :autosize="{ minRows: 2, maxRows: 5 }"
                              @update:value="(v: string) => setField(item, v)" />
                            <NInput v-else :value="fieldValue(item)"
                              :placeholder="isSet(item) ? '' : '继承全局: ' + inheritedValue(item)"
                              @update:value="(v: string) => setField(item, v)" />
                          </div>
                          <div class="config-actions">
                            <NTooltip trigger="hover">
                              <template #trigger>
                                <NButton size="small" quaternary @click="resetKey(item)">
                                  <NIcon><RefreshOutline /></NIcon>
                                </NButton>
                              </template>
                              一键重置（删除该键 → 回到继承全局）
                            </NTooltip>
                          </div>
                        </div>
                      </div>
                    </NCard>
                  </template>
                </div>

                <NDivider />
                <NSpace>
                  <NButton type="primary" :loading="saving" :disabled="Object.keys(dirty).length === 0" @click="save">
                    保存配置{{ Object.keys(dirty).length ? `（${Object.keys(dirty).length} 项）` : '' }}
                  </NButton>
                  <NButton v-if="Object.keys(dirty).length" quaternary @click="discardDirty">放弃改动</NButton>
                </NSpace>
                <NText depth="3" class="tip">
                  未设置的项显示灰底「继承全局」+ 全局当前值；设置后变橙底「覆盖全局」；⟳ 可一键删除恢复继承。
                </NText>
              </div>
            </div>
          </NTabPane>

          <!-- ════════ Tab3：群配置 ════════ -->
          <NTabPane name="group">
            <template #tab>
              <div class="tab-label">
                <NIcon size="18" color="#722ed1"><ShieldCheckmarkOutline /></NIcon>
                <span>群配置</span>
              </div>
            </template>
            <div class="tab-pane-inner">
              <NAlert type="info" :show-icon="true" style="margin-bottom: 14px" class="tab-desc">
                群配置存于 <b>xuanji_group_setting</b> 表，只对该群生效。优先级：<b>群 &gt; 机器人 &gt; 全局</b>。
                未设置时继承机器人级（再继承全局）。
              </NAlert>

              <NCard size="small" :bordered="false" class="scope-bar">
                <NSpace align="center" :wrap="false">
                  <NText strong>机器人：</NText>
                  <NSelect v-model:value="selectedBot" :options="bots.map(b => ({ label: `${b.appId}（${b.platform}）`, value: b.appId }))"
                    placeholder="选机器人" style="width: 220px" />
                  <NText v-if="selectedBot" strong>群：</NText>
                  <NSelect v-if="selectedBot" v-model:value="selectedGroup" :options="groupOptions"
                    placeholder="选群（默认 = 整个机器人）" style="width: 260px" />
                </NSpace>
              </NCard>

              <EmptyState v-if="!selectedBot" description="请先选机器人" />

              <div v-else-if="selectedGroup">
                <NInput v-model:value="filterText" clearable placeholder="搜索配置项（名称 / 键）" style="max-width: 320px; margin-bottom: 12px">
                  <template #prefix><NIcon><InformationCircleOutline /></NIcon></template>
                </NInput>
                <div v-for="mod in MODULE_ORDER" :key="mod">
                  <template v-if="BOT_SCHEMA.some(s => s.module === mod && matchesFilter(s))">
                    <NCard size="small" :bordered="false" class="module-card">
                      <template #header>
                        <NSpace align="center" :size="6">
                          <NIcon size="15" color="#722ed1"><component :is="MODULES[mod].icon" /></NIcon>
                          <span style="font-weight: 600">{{ MODULES[mod].label }}模块</span>
                        </NSpace>
                      </template>
                      <div class="config-list">
                        <div v-for="item in BOT_SCHEMA.filter(s => s.module === mod && matchesFilter(s))" :key="item.key"
                          class="config-row" :class="{ 'is-dirty': isDirty(item.key) }">
                          <div class="config-label">
                            <NText class="config-label-text">{{ item.label }}</NText>
                            <div class="config-label-sub">
                              <NTag v-if="isSet(item)" size="tiny" type="warning" :bordered="false">覆盖</NTag>
                              <NTag v-else size="tiny" :bordered="false">继承</NTag>
                              <NText depth="3" class="config-key">{{ item.key }}</NText>
                            </div>
                          </div>
                          <div class="config-field">
                            <NSwitch v-if="item.type === 'switch'"
                              :value="fieldValue(item) === 'true' || (!isSet(item) && inheritedValue(item) === 'true')"
                              @update:value="(v: boolean) => setField(item, v)" />
                            <NInputNumber v-else-if="item.type === 'number'"
                              :value="Number(fieldValue(item)) || (isSet(item) ? 0 : Number(inheritedValue(item)) || 0)"
                              :placeholder="isSet(item) ? '' : '继承: ' + inheritedValue(item)"
                              style="width: 100%" :min="0"
                              @update:value="(v: number | null) => setField(item, v ?? 0)" />
                            <NInput v-else-if="item.type === 'longtext'" type="textarea"
                              :value="fieldValue(item)"
                              :placeholder="isSet(item) ? '' : '继承: ' + inheritedValue(item)"
                              :autosize="{ minRows: 2, maxRows: 5 }"
                              @update:value="(v: string) => setField(item, v)" />
                            <NInput v-else :value="fieldValue(item)"
                              :placeholder="isSet(item) ? '' : '继承: ' + inheritedValue(item)"
                              @update:value="(v: string) => setField(item, v)" />
                          </div>
                          <div class="config-actions">
                            <NTooltip trigger="hover">
                              <template #trigger>
                                <NButton size="small" quaternary @click="resetKey(item)">
                                  <NIcon><RefreshOutline /></NIcon>
                                </NButton>
                              </template>
                              一键重置（删除该键 → 回到继承）
                            </NTooltip>
                          </div>
                        </div>
                      </div>
                    </NCard>
                  </template>
                </div>

                <NDivider />
                <NSpace>
                  <NButton type="primary" :loading="saving" :disabled="Object.keys(dirty).length === 0" @click="save">
                    保存配置{{ Object.keys(dirty).length ? `（${Object.keys(dirty).length} 项）` : '' }}
                  </NButton>
                  <NButton v-if="Object.keys(dirty).length" quaternary @click="discardDirty">放弃改动</NButton>
                </NSpace>
              </div>
            </div>
          </NTabPane>

          <!-- ════════ Tab4：忽略机器人消息 ════════ -->
          <NTabPane name="ignore">
            <template #tab>
              <div class="tab-label">
                <NIcon size="18" color="#d03050"><ShieldCheckmarkOutline /></NIcon>
                <span>忽略机器人消息</span>
              </div>
            </template>
            <div class="tab-pane-inner">
              <NText depth="3" style="display: block; margin-bottom: 14px; font-size: 12px; line-height: 1.6">
                消息作者 <NTag size="tiny" :bordered="false">author.bot=true</NTag> 即其他机器人/本机器人发出的消息。<br />
                开启「忽略」后只记录不处理（不触发命令、不回复）。
                <NTag size="tiny" type="warning" :bordered="false" style="margin-left: 4px">优先级：群级 &gt; bot 级 &gt; 全局</NTag>
              </NText>

              <div class="ignore-global">
                <div>
                  <NText strong>全局</NText>
                  <NText depth="3" style="font-size: 11px; margin-left: 8px">忽略所有机器人的消息</NText>
                </div>
                <NSwitch :value="globalIgnore" @update:value="(v: boolean) => { globalIgnore = v; saveIgnoreGlobal(v) }" />
              </div>

              <NDivider style="margin: 14px 0" />
              <EmptyState v-if="!bots.length" description="暂无机器人" />

              <div v-for="bot in bots" :key="bot.appId" class="ignore-bot-block">
                <div class="ignore-bot-header">
                  <div>
                    <NText strong>{{ bot.appId }}</NText>
                    <NTag size="tiny" :bordered="false" type="info" style="margin-left: 6px">
                      {{ groupsForBot(bot.appId).length }} 个群
                    </NTag>
                  </div>
                  <NSwitch
                    :value="!!botIgnore[bot.appId]"
                    @update:value="(v: boolean) => saveIgnoreBot(bot.appId, v)"
                  />
                </div>

                <div class="ignore-groups">
                  <EmptyState
                    v-if="!groupsForBot(bot.appId).length"
                    description="该机器人暂无群数据（正常接收消息后会建群）"
                  />
                  <div
                    v-for="g in groupsForBot(bot.appId)"
                    :key="(g.GROUP_ID || g.groupId || g.groupOpenid)"
                    class="ignore-group-row"
                  >
                    <div class="ignore-group-info">
                      <NText class="ignore-group-name">{{ groupName(g) }}</NText>
                      <NText depth="3" style="font-size: 11px; font-family: ui-monospace, SFMono-Regular, monospace">
                        {{ g.GROUP_ID || g.groupId || g.groupOpenid }}
                      </NText>
                    </div>
                    <NSwitch
                      size="small"
                      :value="!!groupIgnore[bot.appId]?.[(g.GROUP_ID || g.groupId || g.groupOpenid)]"
                      @update:value="(v: boolean) => saveIgnoreGroup(bot.appId, (g.GROUP_ID || g.groupId || g.groupOpenid)!, v)"
                    />
                  </div>
                </div>
              </div>
            </div>
          </NTabPane>

        </NTabs>
      </NCard>
    </template>
  </div>
</template>

<style scoped>
.settings-tabs-card {
  border-radius: 14px;
  margin-top: 16px;
}
.settings-tabs {
  padding: 4px 4px 0 4px;
}
.settings-tabs :deep(.n-tabs-tab) {
  padding: 14px 18px;
}
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.tab-pane-inner {
  padding: 18px 4px 8px 4px;
  max-width: 100%;
  width: 100%;
}
.tab-desc {
  font-size: 13px;
  line-height: 1.8;
}

.scope-bar {
  background: rgba(91, 91, 214, 0.04);
  border: 1px dashed rgba(91, 91, 214, 0.2);
  border-radius: 10px;
  margin-bottom: 16px;
}

.module-card {
  background: rgba(128, 128, 128, 0.03);
  border: 1px solid var(--n-border-color);
  border-radius: 10px;
  margin-bottom: 14px;
}

.config-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.config-row {
  display: grid;
  grid-template-columns: 300px 1fr auto;
  gap: 16px;
  align-items: center;
  padding: 10px 14px;
  background: var(--n-card-color);
  border-radius: 8px;
  border: 1px solid var(--n-border-color);
}
.config-row.is-dirty {
  border-color: #f0a020;
  background: rgba(240, 160, 32, 0.05);
}
.config-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.config-label-text {
  font-weight: 500;
  font-size: 14px;
}
.config-label-sub {
  display: flex;
  align-items: center;
  gap: 6px;
}
.config-key {
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, monospace;
}
.config-field {
  width: 100%;
}
.config-actions {
  display: flex;
  align-items: center;
}

.ignore-global {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: rgba(208, 48, 80, 0.04);
  border-left: 3px solid #d03050;
  border-radius: 8px;
}
.ignore-bot-block {
  border: 1px solid var(--n-border-color);
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 12px;
}
.ignore-bot-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.ignore-groups {
  padding-left: 12px;
  border-top: 1px dashed var(--n-border-color);
  padding-top: 10px;
}
.ignore-group-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.02);
  margin-bottom: 4px;
}
.ignore-group-row:hover {
  background: rgba(0, 0, 0, 0.05);
}
.ignore-group-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ignore-group-name {
  font-size: 13px;
}

.tip {
  display: block;
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.7;
}
</style>