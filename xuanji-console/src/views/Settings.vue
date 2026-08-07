<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useMessage } from 'naive-ui'
import {
  NCard, NButton, NSpace, NInput, NInputNumber, NSwitch,
  NSelect, NText, NTag, NEmpty, NDivider, NIcon, NTooltip,
  NPopconfirm, NTabs, NTabPane
} from 'naive-ui'
import {
  PlanetOutline, PeopleOutline, SettingsOutline,
  ShieldCheckmarkOutline, RefreshOutline
} from '@vicons/ionicons5'
import api from '../api'
import PageHero from '../components/PageHero.vue'
import { groupName } from '../utils/names'

const message = useMessage()

interface BotInfo { botKey: string; appId: string; platform: string }
interface GroupRow { GROUP_ID?: string; groupId?: string; groupOpenid?: string; GROUP_NAME?: string; groupName?: string }
interface SchemaItem {
  key: string
  label: string
  type: 'switch' | 'number' | 'text' | 'longtext' | 'select'
  defaultValue: string | number | boolean
  hint: string
  /** select 类型的选项列表 */
  options?: { label: string; value: string }[]
  /** 作用域：global=仅全局；bot=仅 bot；group=仅群；all=全部；不写默认 global+bot+group */
  scope?: ('global' | 'bot' | 'group')[]
}
type ScopeKind = 'global' | 'bot' | 'group'

// =============== 统一配置 Schema（所有作用域合一） ===============
const SCHEMA: SchemaItem[] = [
  // ---- 全局配置（xuanji_config）----
  { key: 'ignore_bot_messages', label: '忽略其他机器人消息', type: 'switch', defaultValue: false, scope: ['global', 'bot', 'group'], hint: '全局开关；bot/群 级可单独覆盖；true=收到机器人消息只记录不处理' },
  { key: 'command.prefix', label: '命令前缀', type: 'text', defaultValue: '', scope: ['global'], hint: '群/私聊命令的前缀。留空 = 不裁剪前缀' },
  { key: 'framework.rate_limit.enabled', label: '启用命令限速', type: 'switch', defaultValue: false, scope: ['global', 'bot', 'group'], hint: '同一用户在这个时间窗口内只能触发一次命令（开启后生效）' },
  { key: 'framework.rate_limit.window_ms', label: '限速窗口时长（ms）', type: 'number', defaultValue: 2000, scope: ['global', 'bot', 'group'], hint: '限速窗口毫秒；默认 2000ms' },
  { key: 'console.refresh_interval_ms', label: '监控页自动刷新间隔（ms）', type: 'number', defaultValue: 5000, scope: ['global'], hint: '健康/仪表盘页面自动刷新间隔，默认 5000' },
  { key: 'media.download.enabled', label: '启用媒体按需下载', type: 'switch', defaultValue: false, scope: ['global'], hint: '框架层在收到图片/语音时按需下载到本地（按内容去重，TTL+配额清理）' },
  { key: 'media.download.max_file_bytes', label: '媒体单文件上限（字节）', type: 'number', defaultValue: 209715200, scope: ['global'], hint: '单文件超过此大小跳过；默认 200MB' },
  { key: 'media.storage.ttl_days', label: '媒体保留天数', type: 'number', defaultValue: 7, scope: ['global'], hint: '下载的媒体文件保留天数；过期自动删除' },
  { key: 'media.storage.max_bytes', label: '媒体总配额（字节）', type: 'number', defaultValue: 4294967296, scope: ['global'], hint: '所有媒体总占用；超限自动删最旧；默认 4GB' },
  { key: 'framework.qqbot.api_base_mode', label: 'QQ 开放平台 API 基地址', type: 'select', defaultValue: 'new', scope: ['global'], hint: 'new=新统一地址 api.bot.qq.com（不区分沙箱/正式，推荐）；legacy=老平台 api.sgroup.qq.com，按机器人环境自动选正式/沙箱。改后 30 秒内生效（WebSocket 连接需重启机器人）', options: [
    { label: '新统一地址（api.bot.qq.com）', value: 'new' },
    { label: '老平台（正式/沙箱自动区分）', value: 'legacy' }
  ] },
  // ---- 机器人配置（xuanji_bot_setting）----
  { key: 'command_prefix', label: '机器人命令前缀', type: 'text', defaultValue: '', scope: ['bot', 'group'], hint: '该机器人/群专属命令前缀（覆盖全局）' },
  { key: 'rate_limit_enabled', label: '启用命令限速（本机器人）', type: 'switch', defaultValue: false, scope: ['bot', 'group'], hint: '该机器人/群是否启用命令限速（覆盖全局）' },
  { key: 'rate_limit_window_ms', label: '限速窗口（ms）', type: 'number', defaultValue: 2000, scope: ['bot', 'group'], hint: '该机器人/群专属限速窗口' },
  { key: 'welcome_enabled', label: '入群欢迎语', type: 'switch', defaultValue: false, scope: ['bot', 'group'], hint: '机器人被加入该群时自动发送欢迎语' },
  { key: 'welcome_message', label: '欢迎语内容', type: 'longtext', defaultValue: '', scope: ['bot', 'group'], hint: '支持多行文本，可包含 @变量' },
  { key: 'cb_threshold', label: '熔断阈值', type: 'number', defaultValue: 5, scope: ['bot', 'group'], hint: '连续失败多少次后熔断停呼' },
  { key: 'cb_cooldown_ms', label: '熔断冷却（ms）', type: 'number', defaultValue: 30000, scope: ['bot', 'group'], hint: '熔断后多久恢复' },
  { key: 'plugin_timeout_ms', label: '插件超时（ms）', type: 'number', defaultValue: 5000, scope: ['bot', 'group'], hint: '插件执行超过此值则隔离跳过' },
  { key: 'media_download_enabled', label: '启用媒体按需下载（本机器人）', type: 'switch', defaultValue: false, scope: ['bot', 'group'], hint: '该机器人/群是否启用媒体下载（覆盖全局）' },
]

// =============== 状态 ===============
const loading = ref(false)
const saving = ref(false)
const activeTab = ref('settings')  // settings / ignore

// 选择器：scope = 'global' 时 selectedBot 为空；scope = 'bot'/'group' 时 selectedBot 是 appId
const scopeKind = ref<ScopeKind>('global')
const selectedBot = ref<string>('')
const selectedGroup = ref<string>('')  // 空字符串 = bot 级（不细化到群）

const bots = ref<BotInfo[]>([])
const botConfigs = ref<Record<string, Record<string, string>>>({})
const groupConfigs = ref<Record<string, Record<string, Record<string, string>>>>({})
const globalRows = ref<Record<string, string>>({})

// 当前作用域下的配置项（按 scope 过滤）
const visibleSchema = computed(() => {
  const allow: ScopeKind[] = scopeKind.value === 'global'
    ? ['global']
    : scopeKind.value === 'bot'
      ? ['bot', 'group']  // bot 级页面显示 bot 专属 + group 专属
      : ['group']
  return SCHEMA.filter(s => !s.scope || s.scope.some(x => allow.includes(x)))
})

// 群列表（当前 bot 下）
const groupsByBot = computed<GroupRow[]>(() => {
  if (!selectedBot.value) return []
  // groupsByBot 的 key 是 botKey（实际就是 appId）
  const groups = groupConfigs.value?.[selectedBot.value]
  if (!groups) return []
  return Object.entries(groups).map(([gid, _kv]) => ({ GROUP_ID: gid, groupId: gid } as GroupRow))
})

// 选项
const scopeOptions = computed(() => [
  { label: '🌐 全局（所有机器人）', value: 'global' },
  ...bots.value.map(b => ({ label: `👥 ${b.appId}（${b.platform}）`, value: b.appId }))
])

const groupOptions = computed(() => [
  { label: '（默认 = 整个机器人）', value: '' },
  ...groupsByBot.value.map(g => {
    const id = g.GROUP_ID || g.groupId || g.groupOpenid
    return { label: id, value: id }
  })
])

// 当前选定的实际值（来自 cfg）
function currentValue(item: SchemaItem): string {
  if (scopeKind.value === 'global') return globalRows.value[item.key] ?? ''
  if (selectedBot.value && selectedGroup.value) {
    return groupConfigs.value?.[selectedBot.value]?.[selectedGroup.value]?.[item.key] ?? ''
  }
  if (selectedBot.value) {
    return botConfigs.value?.[selectedBot.value]?.[item.key] ?? ''
  }
  return ''
}

function defaultValueString(item: SchemaItem): string {
  const v = item.defaultValue
  return v === undefined || v === null ? '' : String(v)
}

function toBool(v: string): boolean {
  return v === 'true' || v === '1'
}

// 改动暂存
const dirty = ref<Record<string, string>>({})

function isDirty(key: string): boolean {
  return key in dirty.value
}

function isOverridden(item: SchemaItem): boolean {
  const v = currentValue(item)
  return v !== '' && v !== defaultValueString(item)
}

function fieldValue(item: SchemaItem): string {
  if (isDirty(item.key)) return dirty.value[item.key]
  return currentValue(item)
}

function setField(item: SchemaItem, v: string | boolean | number) {
  dirty.value[item.key] = String(v)
}

// =============== 数据加载 ===============
async function load() {
  loading.value = true
  try {
    const [cfg, botList] = await Promise.all([
      api.getConfig(),
      api.getBots().catch(() => [])
    ])
    globalRows.value = cfg.global ?? {}
    botConfigs.value = cfg.bots ?? {}
    groupConfigs.value = cfg.groups ?? {}
    bots.value = (botList as BotInfo[]).map((b: any) => ({
      botKey: b.botKey,
      appId: b.appId,
      platform: b.platform
    }))
    dirty.value = {}
  } catch (e: any) {
    message.error('加载失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

// =============== 保存 / 重置 ===============
function addUnknownRow() {
  // 在全局页面支持自由新增键（保持旧能力）
  const key = prompt('新键名（英文，例如 my_plugin.enabled）')
  if (!key || key.trim() === '') return
  if (SCHEMA.some(s => s.key === key)) {
    message.warning('该键已是已知项')
    return
  }
  dirty.value[key] = ''
}

async function save() {
  if (Object.keys(dirty.value).length === 0) {
    message.info('没有改动')
    return
  }
  saving.value = true
  try {
    const body = { ...dirty.value }
    // boolean 类型归一化（确保 'true'/'false'）
    visibleSchema.value.forEach(item => {
      if (item.type === 'switch' && item.key in body) {
        body[item.key] = body[item.key] === 'true' || body[item.key] === true ? 'true' : 'false'
      }
    })
    if (scopeKind.value === 'global') {
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

async function resetKey(item: SchemaItem) {
  try {
    await api.deleteConfigKey(scopeKind.value, selectedBot.value || 'global', item.key, selectedGroup.value || undefined)
    message.success(`已重置：${item.label}`)
    delete dirty.value[item.key]
    await load()
  } catch (e: any) {
    message.error('重置失败：' + (e?.message ?? e))
  }
}

async function resetAllVisible() {
  if (!confirm('一键重置当前作用域下所有配置项到默认值？此操作不可撤销。')) return
  for (const item of visibleSchema.value) {
    try {
      await api.deleteConfigKey(scopeKind.value, selectedBot.value || 'global', item.key, selectedGroup.value || undefined)
    } catch (e: any) { /* 单项失败继续 */ }
  }
  message.success('已一键重置当前作用域所有配置项')
  await load()
}

function discardDirty() {
  dirty.value = {}
}

// =============== 忽略机器人消息（保留原三级样式） ===============
const globalIgnore = ref(false)
const botIgnore = ref<Record<string, boolean>>({} as Record<string, boolean>)
const groupIgnore = ref<Record<string, Record<string, boolean>>>({})

function loadIgnoreFromCfg() {
  const g = globalRows.value['ignore_bot_messages']
  globalIgnore.value = g === 'true' || g === true
  // xuanji_bot_setting 里的 ignore_bot_messages 是 bot 级（已在 globalRows 不再重复读 botConfigs）
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

function onScopeChange() {
  if (scopeKind.value === 'global') {
    selectedBot.value = ''
    selectedGroup.value = ''
  } else if (scopeKind.value === 'bot') {
    selectedGroup.value = ''
  } else {
    // group：自动选中第一个群
    if (!selectedGroup.value && groupsByBot.value.length) {
      const first = groupsByBot.value[0]
      selectedGroup.value = first.GROUP_ID || first.groupId || first.groupOpenid || ''
    }
  }
  dirty.value = {}
}

watch(selectedBot, () => { selectedGroup.value = ''; dirty.value = {} })

onMounted(async () => {
  await load()
  loadIgnoreFromCfg()
})
</script>

<template>
  <div>
    <PageHero
      title="运行设置"
      subtitle="作用域选择（全局/机器人/群）· 修改即时生效，无需重启"
      :icon="SettingsOutline"
    >
      <NButton :loading="loading" @click="load">重新加载</NButton>
    </PageHero>

    <NEmpty v-if="loading" description="加载中…" style="padding: 60px 0" />

    <template v-if="!loading">
      <NCard :bordered="false" class="settings-tabs-card">
        <NTabs v-model:value="activeTab" type="line" animated size="large" class="settings-tabs">
          <!-- ===================== Tab 1：设置（合并全局+机器人+群） ===================== -->
          <NTabPane name="settings">
            <template #tab>
              <div class="tab-label">
                <NIcon size="18" color="#5b5bd6"><PlanetOutline /></NIcon>
                <span>运行设置</span>
              </div>
            </template>
            <div class="tab-pane-inner">

              <!-- 作用域选择器 -->
              <NCard size="small" :bordered="false" class="scope-bar">
                <NSpace align="center" :wrap="false">
                  <NText strong>作用域：</NText>
                  <NSelect
                    v-model:value="scopeKind"
                    :options="[
                      { label: '🌐 全局（所有机器人）', value: 'global' },
                      { label: '👥 机器人级', value: 'bot' },
                      { label: '💬 群级', value: 'group' }
                    ]"
                    style="width: 180px"
                    @update:value="onScopeChange"
                  />
                  <NText v-if="scopeKind !== 'global'" depth="3">机器人：</NText>
                  <NSelect
                    v-if="scopeKind !== 'global'"
                    v-model:value="selectedBot"
                    :options="bots.map(b => ({ label: `${b.appId}（${b.platform}）`, value: b.appId }))"
                    placeholder="选机器人"
                    style="width: 220px"
                  />
                  <NText v-if="scopeKind === 'group'" depth="3">群：</NText>
                  <NSelect
                    v-if="scopeKind === 'group'"
                    v-model:value="selectedGroup"
                    :options="groupOptions"
                    placeholder="选群（默认 = 整个机器人）"
                    style="width: 260px"
                  />
                  <NDivider v-if="dirty && Object.keys(dirty).length" vertical />
                  <NTag v-if="Object.keys(dirty).length" type="warning" :bordered="false">
                    {{ Object.keys(dirty).length }} 项未保存
                  </NTag>
                </NSpace>
              </NCard>

              <NDivider style="margin: 12px 0" />

              <!-- 配置面板 -->
              <NEmpty v-if="scopeKind !== 'global' && !selectedBot" description="请先选机器人" style="padding: 24px 0" />
              <div v-else class="config-list">
                <div v-for="item in visibleSchema" :key="item.key" class="config-row" :class="{ 'is-dirty': isDirty(item.key), 'is-overridden': isOverridden(item) }">
                  <div class="config-label">
                    <NText class="config-label-text">{{ item.label }}</NText>
                    <NTooltip trigger="hover">
                      <template #trigger><NText depth="3" class="field-q">?</NText></template>
                      {{ item.hint }}
                    </NTooltip>
                    <NTag v-if="isOverridden(item)" size="tiny" type="success" :bordered="false">已设置</NTag>
                    <NText depth="3" class="config-key">{{ item.key }}</NText>
                  </div>
                  <div class="config-field">
                      <NSwitch
                        v-if="item.type === 'switch'"
                        :value="fieldValue(item) === 'true'"
                        @update:value="(v: boolean) => setField(item, v)"
                      />
                      <NInputNumber
                        v-else-if="item.type === 'number'"
                        :value="Number(fieldValue(item)) || 0"
                        :placeholder="String(item.defaultValue)"
                        style="width: 100%"
                        :min="0"
                        @update:value="(v: number | null) => setField(item, v ?? 0)"
                      />
                      <NSelect
                        v-else-if="item.type === 'select'"
                        :value="fieldValue(item) || String(item.defaultValue)"
                        :options="item.options ?? []"
                        style="width: 100%"
                        @update:value="(v: string | null) => setField(item, v ?? String(item.defaultValue))"
                      />
                      <NInput
                        v-else-if="item.type === 'longtext'"
                        type="textarea"
                        :value="fieldValue(item)"
                        :placeholder="String(item.defaultValue) || '（多行文本）'"
                        :autosize="{ minRows: 2, maxRows: 5 }"
                        @update:value="(v: string) => setField(item, v)"
                      />
                      <NInput
                        v-else
                        :value="fieldValue(item)"
                        :placeholder="String(item.defaultValue) || '（空）'"
                        @update:value="(v: string) => setField(item, v)"
                      />
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

              <NDivider />
              <NSpace>
                <NButton type="primary" :loading="saving" :disabled="Object.keys(dirty).length === 0" @click="save">
                  保存配置{{ Object.keys(dirty).length ? `（${Object.keys(dirty).length} 项）` : '' }}
                </NButton>
                <NButton v-if="Object.keys(dirty).length" quaternary @click="discardDirty">放弃改动</NButton>
                <NPopconfirm @positive-click="resetAllVisible">
                  <template #trigger>
                    <NButton quaternary type="warning">
                      <template #icon><NIcon><RefreshOutline /></NIcon></template>
                      一键重置全部
                    </NButton>
                  </template>
                  当前作用域下所有配置项都会回到默认值，无法撤销。确定？
                </NPopconfirm>
                <NButton v-if="scopeKind === 'global'" dashed @click="addUnknownRow">+ 新增自由键</NButton>
              </NSpace>
              <NText depth="3" class="tip">
                提示：未设置项显示 <NTag size="tiny" :bordered="false">灰色 placeholder</NTag>（默认值）；「已设置」绿标表示已覆盖默认；右侧 ⟳ 一键重置；底部「一键重置全部」清空当前作用域所有键。
              </NText>
            </div>
          </NTabPane>

          <!-- ===================== Tab 2：忽略机器人消息（保留三级样式） ===================== -->
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
              <NEmpty v-if="!bots.length" description="暂无机器人" style="padding: 16px 0" />

              <div v-for="bot in bots" :key="bot.appId" class="ignore-bot-block">
                <div class="ignore-bot-header">
                  <div>
                    <NText strong>{{ bot.appId }}</NText>
                    <NTag size="tiny" :bordered="false" type="info" style="margin-left: 6px">
                      {{ groupsByBot.filter(g => (g.GROUP_ID || g.groupId) && (groupConfigs[bot.appId]?.[(g.GROUP_ID || g.groupId)] || Object.keys(groupConfigs[bot.appId] || {}).length > 0)).length || Object.keys(groupConfigs[bot.appId] || {}).length }} 个群
                    </NTag>
                  </div>
                  <NSwitch
                    :value="!!botIgnore[bot.appId]"
                    @update:value="(v: boolean) => saveIgnoreBot(bot.appId, v)"
                  />
                </div>

                <div class="ignore-groups">
                  <NEmpty
                    v-if="!groupsByBot.length"
                    size="small"
                    description="该机器人暂无群数据（正常接收消息后会建群）"
                    style="padding: 8px 0"
                  />
                  <div
                    v-for="g in groupsByBot"
                    :key="(g.GROUP_ID || g.groupId || g.groupOpenid)"
                    class="ignore-group-row"
                  >
                    <div class="ignore-group-info">
                      <NText class="ignore-group-name">
                        {{ groupName(g) }}
                      </NText>
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
  max-width: 1200px;
}

.scope-bar {
  background: rgba(91, 91, 214, 0.04);
  border: 1px dashed rgba(91, 91, 214, 0.2);
  border-radius: 10px;
  margin-bottom: 8px;
}

.config-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.config-row {
  display: grid;
  grid-template-columns: 280px 1fr auto;
  gap: 16px;
  align-items: center;
  padding: 12px 16px;
  background: var(--n-card-color);
  border-radius: 8px;
  border: 1px solid var(--n-border-color);
}
.config-row.is-dirty {
  border-color: #f0a020;
  background: rgba(240, 160, 32, 0.05);
}
.config-row.is-overridden {
  border-left: 3px solid #18a058;
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

.g-key-hint {
  font-size: 11px;
  color: var(--n-text-color-3);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>