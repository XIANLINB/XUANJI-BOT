<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useRoute } from 'vue-router'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NDataTable, NInput,
  NInputNumber, NSwitch, NSelect, NForm, NFormItem, NEmpty, NModal, NSpin,
  NAlert, useMessage
} from 'naive-ui'
import {
  ExtensionPuzzleOutline, RefreshOutline, SettingsOutline, TerminalOutline,
  ServerOutline, PeopleOutline, CubeOutline, TrashOutline, DownloadOutline
} from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import api from '../api'
import dayjs from 'dayjs'
import { exportCsv, exportJson } from '../utils/export'

const route = useRoute()
const message = useMessage()
const pluginId = computed(() => String(route.params.pluginId || ''))

const plugin = ref<any>(null)
const commands = ref<any[]>([])
const configSchema = ref<any[]>([])
const configValues = ref<Record<string, any>>({})
const kv = ref<Record<string, string>>({})
const loading = ref(false)
const saving = ref(false)
const showClearKv = ref(false)
const clearingKv = ref(false)

const roleLabel: Record<string, string> = {
  MEMBER: '成员', ADMIN: '管理员', GROUP_OWNER: '群主', BOT_MASTER: '机器人主人',
  BLACKLIST: '黑名单', NONE: '无限制', member: '成员', admin: '管理员', owner: '群主'
}

async function load() {
  loading.value = true
  try {
    const [plist, cmds] = await Promise.all([api.getPlugins(), api.getCommands()])
    plugin.value = (plist || []).find((p: any) => p.id === pluginId.value) ?? null
    commands.value = (cmds || []).filter((c: any) => c.pluginId === pluginId.value)
    const [schema, cfg, kvData] = await Promise.all([
      api.getPluginConfigSchema(pluginId.value),
      api.getPluginConfig(pluginId.value),
      api.getPluginKv(pluginId.value)
    ])
    configSchema.value = schema || []
    const vals: Record<string, any> = { ...(cfg?.values ?? {}) }
    for (const f of configSchema.value) {
      if (f.type === 'BOOLEAN') vals[f.key] = vals[f.key] === 'true' || vals[f.key] === '1' || vals[f.key] === true
      else if (f.type === 'NUMBER') vals[f.key] = vals[f.key] === undefined || vals[f.key] === '' ? Number(f.defaultValue ?? 0) : Number(vals[f.key])
    }
    configValues.value = vals
    kv.value = kvData?.values ?? {}
  } catch (e: any) {
    message.error('加载插件失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

async function saveConfig() {
  const values: Record<string, string> = {}
  for (const f of configSchema.value) {
    const v = configValues.value[f.key]
    if (f.type === 'BOOLEAN') values[f.key] = v ? 'true' : 'false'
    else if (v === undefined || v === null || v === '') values[f.key] = String(f.defaultValue ?? '')
    else values[f.key] = String(v)
  }
  saving.value = true
  try {
    await api.putPluginConfig(pluginId.value, values)
    message.success('配置已保存（即时生效）')
    kv.value = (await api.getPluginKv(pluginId.value))?.values ?? {}
  } catch (e: any) {
    message.error('保存失败：' + (e?.message ?? e))
  } finally {
    saving.value = false
  }
}

async function confirmClearKv() {
  clearingKv.value = true
  try {
    await api.clearPluginKv(pluginId.value)
    kv.value = {}
    showClearKv.value = false
    message.success('持久化数据已清空')
  } catch (e: any) {
    message.error('清空失败：' + (e?.message ?? e))
  } finally {
    clearingKv.value = false
  }
}

async function toggle() {
  if (!plugin.value) return
  try {
    if (plugin.value.running) {
      await api.stopPlugin(pluginId.value)
      message.success('插件已停用')
    } else {
      await api.startPlugin(pluginId.value)
      message.success('插件已启用')
    }
    await load()
  } catch (e: any) {
    message.error((e?.message ?? e))
  }
}

async function reload() {
  try {
    const r = await api.reloadPlugin(pluginId.value)
    if (r.status === 'error') message.error('热加载失败（看后端日志）')
    else message.success('插件已热加载')
    await load()
  } catch (e: any) {
    message.error('热加载失败：' + (e?.message ?? e))
  }
}

// ===== 机器人绑定（弹窗）=====
const showBindings = ref(false)
const bindRows = ref<any[]>([])
const bindSelectBotKey = ref('')
const botsList = ref<any[]>([])

async function openBindings() {
  bindSelectBotKey.value = ''
  try {
    const [binds, bots] = await Promise.all([api.listBindings(pluginId.value), api.getBots()])
    bindRows.value = binds || []
    botsList.value = bots || []
    showBindings.value = true
  } catch (e: any) {
    message.error('加载绑定失败：' + (e?.message ?? e))
  }
}

async function addBinding() {
  if (!bindSelectBotKey.value) { message.warning('请选择机器人'); return }
  const bot = botsList.value.find((b: any) => (b.botKey || b.appId) === bindSelectBotKey.value)
  const platform = bot?.platform || 'qqbot'
  try {
    await api.bindPlugin(pluginId.value, platform, bindSelectBotKey.value)
    message.success('已绑定')
  } catch (e: any) {
    message.error('绑定失败：' + (e?.message ?? e))
  } finally {
    bindRows.value = await api.listBindings(pluginId.value)
  }
}

async function removeBinding(row: any) {
  try {
    await api.unbindPlugin(pluginId.value, row.platform, row.botKey)
    message.success('已解绑')
  } catch (e: any) {
    message.error('解绑失败：' + (e?.message ?? e))
  } finally {
    bindRows.value = await api.listBindings(pluginId.value)
  }
}

// ===== 表格列 =====
const commandCols = [
  { title: '命令', key: 'cmd', width: 130, render: (r: any) => h(NTag, { size: 'small', bordered: false, type: 'info' }, { default: () => r.cmd }) },
  { title: '方法', key: 'method' },
  {
    title: '作用域', key: 'scope', width: 150,
    render: (r: any) => h(NSpace, { size: 6 }, {
      default: () => [
        r.group ? h(NTag, { size: 'small', bordered: false, type: 'success' }, { default: () => '群聊' }) : null,
        r.private ? h(NTag, { size: 'small', bordered: false, type: 'warning' }, { default: () => '私聊' }) : null
      ]
    })
  },
  { title: '权限', key: 'roles', render: (r: any) => h(NTag, { size: 'small', bordered: false, type: 'error' }, { default: () => (r.roles || []).map((x: string) => roleLabel[x] ?? x).join(' / ') }) },
  { title: '限流(ms)', key: 'rateLimitMs', width: 90, render: (r: any) => (r.rateLimitMs > 0 ? String(r.rateLimitMs) : '—') }
]
const kvRows = computed(() => Object.entries(kv.value).map(([k, v]) => ({ key: k, value: v })))
const kvCols = [
  { title: '键', key: 'key' },
  { title: '值', key: 'value', ellipsis: { tooltip: true } }
]

// ══════ 插件 KV 导出（批次6） ══════
function exportKv(format: 'csv' | 'json') {
  const data = kvRows.value
  const stamp = dayjs().format('YYYYMMDD-HHmmss')
  const base = pluginId.value || 'plugin'
  if (format === 'json') {
    exportJson(kv.value, `${base}-kv-${stamp}.json`)
  } else {
    exportCsv(data, [
      { key: 'key', label: '键' },
      { key: 'value', label: '值' }
    ], `${base}-kv-${stamp}.csv`)
  }
  message.success(`已导出 ${data.length} 条 KV（${format.toUpperCase()}）`)
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero
      :title="plugin?.name || pluginId"
      :subtitle="(plugin?.description || '插件详情页') + (plugin ? ` · v${plugin.version}` : '')"
      :icon="ExtensionPuzzleOutline"
    >
      <NSpace align="center">
        <template v-if="plugin">
          <NTag :type="plugin.running ? 'success' : 'default'" :bordered="false" round>{{ plugin.running ? '运行中' : '已停用' }}</NTag>
          <NButton :type="plugin.running ? 'warning' : 'primary'" size="small" @click="toggle">
            {{ plugin.running ? '停用' : '启用' }}
          </NButton>
          <NButton size="small" secondary @click="reload">热加载</NButton>
          <NButton size="small" tertiary @click="openBindings">机器人绑定</NButton>
        </template>
        <NButton size="small" tertiary :loading="loading" @click="load">
          <template #icon><NIcon><RefreshOutline /></NIcon></template>
          刷新
        </NButton>
      </NSpace>
    </PageHero>

    <NSpin :show="loading">
      <NEmpty v-if="!loading && !plugin" description="插件不存在或未加载（试试菜单里的「扫描新插件」）" style="padding: 60px 0" />

      <template v-else>
        <!-- 概览信息 -->
        <NCard title="插件信息" class="b-card">
          <template #header-extra><NIcon size="18" color="#5b5bd6"><CubeOutline /></NIcon></template>
          <NSpace :size="24" wrap>
            <div class="kv"><span class="kv-label">插件 ID</span><NText strong>{{ plugin?.id }}</NText></div>
            <div class="kv"><span class="kv-label">版本</span><NText strong>{{ plugin?.version }}</NText></div>
            <div class="kv"><span class="kv-label">作者</span><NText strong>{{ plugin?.provider || '—' }}</NText></div>
            <div class="kv"><span class="kv-label">描述</span><NText strong>{{ plugin?.description || '—' }}</NText></div>
          </NSpace>
        </NCard>

        <!-- 命令 -->
        <NCard title="命令（该插件注册）" class="b-card">
          <template #header-extra><NIcon size="18" color="#185FA5"><TerminalOutline /></NIcon></template>
          <NDataTable v-if="commands.length" :columns="commandCols" :data="commands" :bordered="false" size="small" />
          <NEmpty v-else description="该插件未注册命令（可用 @Command 注解声明）" style="padding: 24px 0" />
        </NCard>

        <!-- 配置（schema 动态表单） -->
        <NCard title="配置" class="b-card">
          <template #header-extra><NIcon size="18" color="#0F6E56"><SettingsOutline /></NIcon></template>
          <template v-if="configSchema.length">
            <NForm label-placement="top" :show-feedback="false">
              <div class="cfg-grid">
                <NFormItem v-for="f in configSchema" :key="f.key" :label="f.label" style="margin-bottom: 14px">
                  <template #label-extra v-if="f.description">
                    <NText depth="3" style="font-size: 11px; margin-left: 6px">{{ f.description }}</NText>
                  </template>
                  <NInputNumber v-if="f.type === 'NUMBER'" v-model:value="configValues[f.key]" :min="0" style="width: 100%" />
                  <NSwitch v-else-if="f.type === 'BOOLEAN'" v-model:value="configValues[f.key]" />
                  <NSelect v-else-if="f.type === 'SELECT'" v-model:value="configValues[f.key]"
                    :options="(f.options || []).map((o: string) => ({ label: o, value: o }))" />
                  <NInput v-else v-model:value="configValues[f.key]" :placeholder="'默认: ' + (f.defaultValue ?? '')" />
                </NFormItem>
              </div>
            </NForm>
            <div style="display: flex; justify-content: flex-end">
              <NButton type="primary" :loading="saving" @click="saveConfig">保存配置</NButton>
            </div>
          </template>
          <NEmpty v-else description="该插件未声明配置项（实现 PluginConfigProvider 后自动出现）" style="padding: 24px 0" />
        </NCard>

        <!-- 数据存储 -->
        <NCard title="数据存储（插件持久化 KV）" class="b-card">
          <template #header-extra>
            <NSpace :size="8" align="center">
              <NButton size="tiny" secondary :disabled="!kvRows.length" @click="exportKv('csv')">
                <template #icon><NIcon size="14"><DownloadOutline /></NIcon></template>
                导出 CSV
              </NButton>
              <NButton size="tiny" secondary :disabled="!kvRows.length" @click="exportKv('json')">
                <template #icon><NIcon size="14"><DownloadOutline /></NIcon></template>
                导出 JSON
              </NButton>
              <NButton size="tiny" tertiary :disabled="!kvRows.length" @click="showClearKv = true">
                <template #icon><NIcon size="14"><TrashOutline /></NIcon></template>
                一键清空
              </NButton>
              <NIcon size="18" color="#854F0B"><ServerOutline /></NIcon>
            </NSpace>
          </template>
          <NDataTable v-if="kvRows.length" :columns="kvCols" :data="kvRows" :bordered="false" size="small" />
          <NEmpty v-else description="暂无存储数据（插件用 PluginStorage 写入后出现）" style="padding: 24px 0" />
        </NCard>
      </template>
    </NSpin>

    <!-- 清空持久化数据确认弹窗 -->
    <NModal v-model:show="showClearKv" preset="card" :title="`清空持久化数据 · ${plugin?.name || pluginId}`" style="width: 460px; max-width: 92vw" :bordered="false">
      <NAlert type="warning" :show-icon="true" style="margin-bottom: 12px">
        此操作不可恢复
      </NAlert>
      <NText depth="2" style="font-size: 13px; line-height: 1.7">
        将删除插件「{{ plugin?.name || pluginId }}」写入的<b>全部持久化数据</b>（xuanji_plugin_kv，签到金币、累计记录等），
        仅清除该插件的 KV 命名空间，<b>不影响配置面板</b>与其它插件数据。
      </NText>
      <template #footer>
        <NSpace justify="end">
          <NButton size="small" @click="showClearKv = false">取消</NButton>
          <NButton size="small" type="error" :loading="clearingKv" @click="confirmClearKv">确认清空</NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- 绑定弹窗 -->
    <NModal v-model:show="showBindings" preset="card" :title="`插件绑定 · ${plugin?.name || pluginId}`" style="width: 520px; max-width: 92vw" :bordered="false">
      <NSpace vertical :size="12">
        <NSpace :size="8" align="center">
          <NSelect v-model:value="bindSelectBotKey"
            :options="botsList.map((b: any) => ({ label: (b.botKey || b.appId) + (b.name ? ` (${b.name})` : ''), value: b.botKey || b.appId }))"
            placeholder="选择机器人" style="width: 300px" />
          <NButton type="primary" size="small" :disabled="!bindSelectBotKey" @click="addBinding">绑定该机器人</NButton>
        </NSpace>
        <NText depth="3" style="font-size: 12px">当前绑定列表（空 = 全局生效）：</NText>
        <div v-if="bindRows.length">
          <div v-for="(row, i) in bindRows" :key="i"
            style="display: flex; align-items: center; justify-content: space-between; padding: 6px 0; border-bottom: 0.5px solid rgba(0,0,0,0.06)">
            <span style="font-size: 13px">{{ row.botKey }} <NTag size="small" :bordered="false" type="info">{{ row.platform }}</NTag></span>
            <NButton size="tiny" type="error" @click="removeBinding(row)">解绑</NButton>
          </div>
        </div>
        <NText v-else depth="3" style="font-size: 12px">暂无绑定（全局生效）</NText>
      </NSpace>
    </NModal>
  </div>
</template>

<style scoped>
.b-card { margin-top: 16px; }
.kv { display: flex; flex-direction: column; gap: 4px; }
.kv-label { font-size: 13px; color: var(--n-text-color-2); }
.cfg-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 0 20px; }
</style>
