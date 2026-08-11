<script setup lang="ts">
import { ref, computed, onMounted, watch, h, nextTick } from 'vue'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NDataTable, NModal, NForm, NFormItem,
  NInput, NSelect, NSwitch, NDrawer, NDrawerContent, NEmpty, NAlert, NSpin,
  NRadioGroup, NRadioButton, NStatistic, NGrid, NGi, NDatePicker, useMessage
} from 'naive-ui'
import {
  AlarmOutline, AddOutline, RefreshOutline, PlayOutline, TrashOutline,
  TimeOutline, DocumentTextOutline, CreateOutline
} from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import CommonChart from '../components/CommonChart.vue'
import CronBuilder from '../components/CronBuilder.vue'
import api from '../api'
import dayjs from 'dayjs'
import { groupName, userName } from '../utils/names'
import { useContactsStore } from '../stores/contacts'

const message = useMessage()

const loading = ref(false)
const rows = ref<any[]>([])
const bots = ref<any[]>([])
// 群/单聊联系人收拢到 Pinia（批次11）
const contacts = useContactsStore()
const groups = computed(() => contacts.groups)
const friends = computed(() => contacts.friends)

// ═══════════ 执行分析（批次5） ═══════════
const analysis = ref<Record<string, any>>({ perJob: [] })
const trend = ref<any[]>([])
const trendDays = ref(7)

async function loadAnalysis() {
  try {
    const [s, t] = await Promise.all([
      api.schedulerStats(),
      api.schedulerTrend(trendDays.value)
    ])
    analysis.value = s || { perJob: [] }
    trend.value = t || []
  } catch (e: any) {
    message.error('执行分析加载失败：' + (e?.message ?? e))
  }
}

// ═══════════ 执行趋势图（CommonChart，批次8 统一） ═══════════
const trendOption = computed(() => {
  const days = Math.max(1, Math.min(trendDays.value, 90))
  const byDay = new Map<string, any>()
  ;(trend.value || []).forEach((r: any) => byDay.set(String(r.D || r.d), r))
  const labels: string[] = []
  const runs: number[] = []
  const fails: number[] = []
  const avg: number[] = []
  for (let i = days - 1; i >= 0; i--) {
    const d = dayjs().subtract(i, 'day').format('MM-DD')
    const key = dayjs().subtract(i, 'day').format('YYYY-MM-DD')
    const row = byDay.get(key)
    labels.push(d)
    runs.push(Number(row?.RUNS ?? row?.runs ?? 0))
    fails.push(Number(row?.FAILS ?? row?.fails ?? 0))
    avg.push(Math.round(Number(row?.AVG_MS ?? row?.avgMs ?? 0)))
  }
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['执行次数', '失败次数', '平均耗时(ms)'], bottom: 0, itemWidth: 14 },
    grid: { left: 50, right: 60, top: 30, bottom: 40 },
    xAxis: { type: 'category', data: labels },
    yAxis: [
      { type: 'value', name: '次数', minInterval: 1, nameTextStyle: { fontSize: 11 } },
      { type: 'value', name: 'ms', nameTextStyle: { fontSize: 11 } }
    ],
    series: [
      { name: '执行次数', type: 'bar', data: runs, itemStyle: { color: '#07c160', borderRadius: [3, 3, 0, 0] }, barMaxWidth: 22 },
      { name: '失败次数', type: 'bar', data: fails, itemStyle: { color: '#e5484d', borderRadius: [3, 3, 0, 0] }, barMaxWidth: 22 },
      { name: '平均耗时(ms)', type: 'line', yAxisIndex: 1, data: avg, smooth: true, itemStyle: { color: '#2090e0' } }
    ]
  }
})

watch(trendDays, loadAnalysis)

// ═══════════ 新建 / 编辑弹窗 ═══════════
const showModal = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const cronNext = ref<number | null>(null)
const form = ref<Record<string, any>>({
  name: '', jobType: 'BOT_PUSH', cron: '0 8 * * *',
  repeatMode: 'cron',         // 'cron' 按周期重复（默认） / 'one_shot' 仅触发一次
  oneShotAt: null as any,    // 毫秒时间戳，repeatMode='one_shot' 时使用（NDatePicker datetime 模式 value 是 number）
  targetPlatform: 'qqbot', targetBot: '', targetType: 'GROUP',
  targetId: '', content: '', remark: ''
})

const repeatModeOptions = [
  { label: '周期（按 cron 重复）', value: 'cron' },
  { label: '仅一次', value: 'one_shot' }
]

/** 仅一次：根据时间戳生成 cron（秒 分 时 日 月 *），并同步到 form.cron 与后台预览。 */
function buildOneShotCron(epochMs: number): string {
  const d = new Date(epochMs)
  const s = String(d.getSeconds()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const mo = String(d.getMonth() + 1).padStart(2, '0')
  return `0 ${s} ${m} ${h} ${dd} ${mo} *`
}

/** 重复模式变化时同步 cron：cron 模式恢复默认（若 form.cron 是"空/上次缓存的一次性"）；one_shot 模式按 oneShotAt 算。 */
watch(() => form.value.repeatMode, (v) => {
  if (v === 'one_shot') {
    if (!form.value.oneShotAt) {
      form.value.oneShotAt = Date.now() + 60 * 60 * 1000
    }
    form.value.cron = buildOneShotCron(form.value.oneShotAt)
  } else {
    // 切回 cron 默认（仅在当前 cron 看起来是 one_shot 形式时重置，避免覆盖编辑中的自定义）
    const parts = (form.value.cron || '').trim().split(/\s+/)
    const looksOneShot = parts.length === 7 && parts[0] === '0' && parts[4] === '*'
    if (!form.value.cron || looksOneShot) {
      form.value.cron = '0 8 * * *'
    }
  }
})

/** one_shot 时间调整时同步 cron。 */
watch(() => form.value.oneShotAt, (v) => {
  if (form.value.repeatMode === 'one_shot' && v != null) {
    form.value.cron = buildOneShotCron(v)
  }
})

const typeOptions = [
  { label: '定时推送消息（BOT_PUSH）', value: 'BOT_PUSH' },
  { label: 'HTTP 回调（HTTP）', value: 'HTTP' }
]
const targetTypeOptions = [
  { label: '群聊', value: 'GROUP' },
  { label: '单聊', value: 'C2C' }
]

const botOptions = computed(() =>
  (bots.value || []).map((b: any) => ({ label: `${b.name || b.appId} (${b.appId})`, value: b.appId })))
/** 群 ID 兼容大小写读取（H2 返回大写 GROUP_ID，值为 QQ group_openid） */
const groupIdOf = (g: any): string =>
  String(g.GROUP_ID ?? g.group_id ?? g.groupOpenid ?? g.groupId ?? '')
/** 用户 ID 兼容大小写读取（H2 返回大写 PLATFORM_USER_ID，值为 QQ user_openid） */
const friendIdOf = (f: any): string =>
  String(f.PLATFORM_USER_ID ?? f.platform_user_id ?? f.USER_ID ?? f.user_id ?? f.openid ?? f.userId ?? '')
// 目标群/用户必须按「已选机器人」过滤（后端 /contacts/groups 是跨所有 bot 聚合，每行带 BOT_APPID 章）
const groupOptions = computed(() =>
  (groups.value || [])
    .filter((g: any) => !form.value.targetBot || String(g.BOT_APPID ?? g.botKey ?? '') === String(form.value.targetBot))
    .map((g: any) => ({ label: `${groupName(g)} (${groupIdOf(g)})`, value: groupIdOf(g) })))
const friendOptions = computed(() =>
  (friends.value || [])
    .filter((f: any) => !form.value.targetBot || String(f.BOT_APPID ?? f.botKey ?? '') === String(form.value.targetBot))
    .map((f: any) => ({ label: `${userName(f)} (${friendIdOf(f)})`, value: friendIdOf(f) })))

// 切换机器人时清掉旧目标，避免残留上一个 bot 的群/用户
//（编辑回显时 suppress 一次，避免把已保存的 targetId 清掉）
let suppressBotReset = false
watch(() => form.value.targetBot, (nv, ov) => {
  if (suppressBotReset) return
  if (ov && ov !== nv) form.value.targetId = ''
})

// ═══════════ 日志抽屉 ═══════════
const showLogs = ref(false)
const logJobName = ref('')
const logRows = ref<any[]>([])

// ═══════════ 删除确认 ═══════════
const showDelete = ref(false)
const deletingId = ref<number | null>(null)
const deletingName = ref('')

async function load() {
  loading.value = true
  try {
    rows.value = await api.listJobs()
    bots.value = await api.getBots()
    await contacts.loadContacts()
    await loadAnalysis()
  } catch (e: any) {
    message.error('加载失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

function fmtTime(v: any): string {
  const n = Number(v)
  if (!isFinite(n) || n <= 0) return '—'
  return dayjs(n * 1000).format('MM-DD HH:mm')
}

const typeTag: Record<string, { label: string; type: 'info' | 'warning' | 'default' }> = {
  BOT_PUSH: { label: '消息推送', type: 'info' },
  HTTP: { label: 'HTTP 回调', type: 'warning' }
}

function targetText(r: any): string {
  if (r.jobType === 'HTTP') return r.targetId
  const plat = r.targetPlatform || '—'
  const kind = r.targetType === 'C2C' ? '单聊' : '群聊'
  return `${plat} · ${kind} ${r.targetId}`
}

// ═══════════ cron 预览 ═══════════
watch(
  () => form.value.cron,
  async (cron) => {
    if (!cron || !/^[0-9*?/#,\- ]+$/.test(cron)) { cronNext.value = null; return }
    try {
      const r = await api.cronPreview(cron)
      cronNext.value = r.valid ? r.nextRun : null
    } catch { cronNext.value = null }
  },
  { immediate: true }
)

// ═══════════ 新建 / 编辑 ═══════════
function openCreate() {
  editingId.value = null
  form.value = {
    name: '', jobType: 'BOT_PUSH', cron: '0 8 * * *',
    repeatMode: 'cron', oneShotAt: null as any,
    targetPlatform: 'qqbot', targetBot: '', targetType: 'GROUP',
    targetId: '', content: '', remark: ''
  }
  showModal.value = true
}

function openEdit(r: any) {
  editingId.value = r.id
  suppressBotReset = true   // 回显 targetId 时抑制机器人切换清理
  // 兼容老任务（DB 列不存在时 repeatMode 是 undefined）默认 cron
  const rm = (r.repeatMode === 'one_shot' || r.repeatMode === 'cron') ? r.repeatMode : 'cron'
  form.value = {
    name: r.name, jobType: r.jobType, cron: r.cron,
    repeatMode: rm, oneShotAt: null,
    targetPlatform: r.targetPlatform || 'qqbot', targetBot: r.targetBot || '',
    targetType: r.targetType || 'GROUP', targetId: r.targetId || '',
    content: r.content || '', remark: r.remark || ''
  }
  nextTick(() => { suppressBotReset = false })
  showModal.value = true
}

async function save() {
  if (!form.value.name?.trim()) return message.error('请填写任务名称')
  if (form.value.repeatMode === 'one_shot' && !form.value.oneShotAt) {
    return message.error('请选择单次执行时间')
  }
  if (form.value.jobType === 'BOT_PUSH') {
    if (!form.value.targetBot) return message.error('请选择机器人')
    if (!form.value.targetId) return message.error('请选择推送目标')
    if (!form.value.content?.trim()) return message.error('请填写推送内容')
  }
  if (form.value.jobType === 'HTTP' && !/^https?:\/\//.test(form.value.targetId || '')) {
    return message.error('URL 必须以 http:// 或 https:// 开头')
  }
  saving.value = true
  try {
    // oneShotAt 仅前端用，后端根据 cron + repeatMode 处理，不需要持久化
    const { oneShotAt, ...body } = form.value
    if (editingId.value != null) {
      await api.updateJob(editingId.value, body)
      message.success('任务已更新')
    } else {
      await api.createJob(body)
      message.success('任务已创建')
    }
    showModal.value = false
    await load()
  } catch (e: any) {
    message.error((e?.message ?? e))
  } finally {
    saving.value = false
  }
}

// ═══════════ 启停 / 触发 ═══════════
async function toggle(r: any, enabled: boolean) {
  try {
    await api.toggleJob(r.id, enabled)
    r.enabled = enabled
    message.success(enabled ? '任务已启用' : '任务已停用')
    await load()
  } catch (e: any) {
    message.error((e?.message ?? e))
  }
}

async function run(r: any) {
  try {
    const res = await api.runJob(r.id)
    if (res.status === 'ok') message.success('已触发执行')
    else message.error(res.msg || '触发失败')
  } catch (e: any) {
    message.error((e?.message ?? e))
  }
}

// ═══════════ 日志 ═══════════
async function openLogs(r: any) {
  logJobName.value = r.name
  showLogs.value = true
  try {
    logRows.value = (await api.jobLogs(r.id, 50))?.rows ?? []
  } catch (e: any) {
    logRows.value = []
    message.error('日志加载失败：' + (e?.message ?? e))
  }
}

const logStatusTag: Record<string, { label: string; type: 'success' | 'error' | 'warning' | 'default' }> = {
  SUCCESS: { label: '成功', type: 'success' },
  FAIL: { label: '失败', type: 'error' },
  SKIP: { label: '跳过', type: 'warning' }
}

// ═══════════ 删除 ═══════════
function askDelete(r: any) {
  deletingId.value = r.id
  deletingName.value = r.name
  showDelete.value = true
}

async function confirmDelete() {
  if (deletingId.value == null) return
  try {
    await api.deleteJob(deletingId.value)
    message.success('任务已停用并移入历史（记录保留）')
    showDelete.value = false
    await load()
  } catch (e: any) {
    message.error('删除失败：' + (e?.message ?? e))
  }
}

// ═══════════ 历史任务抽屉 ═══════════
const showHistory = ref(false)
const historyRows = ref<any[]>([])
const historyLoading = ref(false)

function jobStateTag(r: any) {
  if (r.deleted) return h(NTag, { size: 'tiny', bordered: false, type: 'default' }, { default: () => '已删除' })
  if (r.enabled) return h(NTag, { size: 'tiny', bordered: false, type: 'success' }, { default: () => '进行中' })
  return h(NTag, { size: 'tiny', bordered: false, type: 'warning' }, { default: () => '已停用' })
}

async function openHistory() {
  showHistory.value = true
  historyLoading.value = true
  try {
    historyRows.value = await api.listAllJobs()
  } catch (e: any) {
    message.error('历史加载失败：' + (e?.message ?? e))
  } finally {
    historyLoading.value = false
  }
}

async function restoreJob(r: any) {
  try {
    await api.restoreJob(r.id)
    message.success(`任务「${r.name}」已恢复`)
    await openHistory()
    await load()
  } catch (e: any) {
    message.error('恢复失败：' + (e?.message ?? e))
  }
}

function copyCron(r: any) {
  navigator.clipboard?.writeText(r.cron || '').then(() => {
    message.success('cron 已复制')
  }).catch(() => {
    message.warning('复制失败，请手动复制')
  })
}

const historyColumns = [
  { title: '任务名称', key: 'name', minWidth: 130, ellipsis: { tooltip: true },
    render: (r: any) => h('span', { style: r.deleted ? 'color: var(--n-text-color-3); text-decoration: line-through' : undefined }, { default: () => r.name }) },
  {
    title: '类型', key: 'jobType', width: 90,
    render: (r: any) => h(NTag, { size: 'small', bordered: false, type: typeTag[r.jobType]?.type ?? 'default' }, { default: () => typeTag[r.jobType]?.label ?? r.jobType })
  },
  {
    title: '模式', key: 'repeatMode', width: 70,
    render: (r: any) => r.repeatMode === 'one_shot'
      ? h(NTag, { size: 'tiny', bordered: false, type: 'warning' }, { default: () => '仅一次' })
      : h(NTag, { size: 'tiny', bordered: false, type: 'default' }, { default: () => '周期' })
  },
  { title: 'Cron', key: 'cron', width: 120, render: (r: any) => h('code', { style: 'font-size:12px;background:var(--n-color-2);padding:2px 6px;border-radius:4px' }, { default: () => r.cron }) },
  {
    title: '状态', key: 'state', width: 80,
    render: (r: any) => jobStateTag(r)
  },
  { title: '上次执行', key: 'lastRun', width: 100, render: (r: any) => fmtTime(r.lastRun) },
  {
    title: '操作', key: 'op', width: 170,
    render: (r: any) => h(NSpace, { size: 4 }, { default: () => [
      h(NButton, { size: 'tiny', secondary: true, onClick: () => openLogs(r) }, { default: () => '日志' }),
      h(NButton, { size: 'tiny', tertiary: true, onClick: () => copyCron(r) }, { default: () => '复制cron' }),
      r.deleted ? h(NButton, { size: 'tiny', type: 'primary', ghost: true, onClick: () => restoreJob(r) }, { default: () => '恢复' }) : null
    ] })
  }
]

// ═══════════ 表格 ═══════════
const columns = [
  { title: '任务名称', key: 'name', minWidth: 140, ellipsis: { tooltip: true } },
  {
    title: '类型', key: 'jobType', width: 100,
    render: (r: any) => h(NTag, { size: 'small', bordered: false, type: typeTag[r.jobType]?.type ?? 'default' }, { default: () => typeTag[r.jobType]?.label ?? r.jobType })
  },
  {
    title: '模式', key: 'repeatMode', width: 80,
    render: (r: any) => r.repeatMode === 'one_shot'
      ? h(NTag, { size: 'tiny', bordered: false, type: 'warning' }, { default: () => '仅一次' })
      : h(NTag, { size: 'tiny', bordered: false, type: 'default' }, { default: () => '周期' })
  },
  {
    title: 'Cron', key: 'cron', width: 130,
    render: (r: any) => h('code', { style: 'font-size:12px;background:var(--n-color-2);padding:2px 6px;border-radius:4px' }, { default: () => r.cron })
  },
  { title: '目标', key: 'target', minWidth: 180, ellipsis: { tooltip: true }, render: (r: any) => targetText(r) },
  {
    title: '状态', key: 'enabled', width: 90,
    render: (r: any) => h(NSwitch, {
      value: r.enabled, size: 'small',
      'onUpdate:value': (v: boolean) => toggle(r, v)
    })
  },
  { title: '上次执行', key: 'lastRun', width: 110, render: (r: any) => fmtTime(r.lastRun) },
  { title: '下次执行', key: 'nextRun', width: 110, render: (r: any) => fmtTime(r.nextRun) },
  {
    title: '执行/失败', key: 'count', width: 90,
    render: (r: any) => h(NSpace, { size: 4 }, { default: () => [
      h(NTag, { size: 'tiny', bordered: false, type: 'success' }, { default: () => String(r.runCount ?? 0) }),
      h(NTag, { size: 'tiny', bordered: false, type: 'error' }, { default: () => String(r.failCount ?? 0) })
    ] })
  },
  {
    title: '操作', key: 'op', width: 150,
    render: (r: any) => h(NSpace, { size: 4 }, { default: () => [
      h(NButton, { size: 'tiny', secondary: true, onClick: () => openEdit(r) }, { default: () => '编辑' }),
      h(NButton, { size: 'tiny', tertiary: true, onClick: () => run(r) }, { default: () => '执行' }),
      h(NButton, { size: 'tiny', tertiary: true, onClick: () => openLogs(r) }, { default: () => '日志' }),
      h(NButton, { size: 'tiny', type: 'error', ghost: true, onClick: () => askDelete(r) }, { default: () => '删除' })
    ] })
  }
]

// ═══════════ 任务执行情况表（批次5） ═══════════
const analysisCols = [
  { title: '任务', key: 'jobName', minWidth: 140, ellipsis: { tooltip: true } },
  { title: '执行次数', key: 'runs', width: 90, render: (r: any) => h('span', { style: 'font-variant-numeric: tabular-nums' }, String(r.runs ?? 0)) },
  {
    title: '成功率', key: 'successRate', width: 100,
    render: (r: any) => h(NTag, { size: 'small', bordered: false, type: (r.successRate ?? 100) >= 90 ? 'success' : (r.successRate ?? 100) >= 60 ? 'warning' : 'error' }, { default: () => (r.successRate ?? 100) + '%' })
  },
  { title: '平均耗时', key: 'avgMs', width: 90, render: (r: any) => h('span', { style: 'font-variant-numeric: tabular-nums' }, (r.avgMs ?? 0) + 'ms') },
  { title: '最近耗时', key: 'lastElapsedMs', width: 90, render: (r: any) => h('span', { style: 'font-variant-numeric: tabular-nums' }, (r.lastElapsedMs ?? 0) + 'ms') },
  {
    title: '最近状态', key: 'lastStatus', width: 90,
    render: (r: any) => h(NTag, { size: 'small', bordered: false, type: r.lastStatus === 'SUCCESS' ? 'success' : r.lastStatus === 'FAIL' ? 'error' : 'default' }, { default: () => logStatusTag[r.lastStatus]?.label ?? r.lastStatus ?? '—' })
  },
  { title: '最近错误', key: 'lastError', minWidth: 160, ellipsis: { tooltip: true }, render: (r: any) => h('span', r.lastError || '—') }
]

onMounted(load)
</script>

<template>
  <div>
    <PageHero title="定时任务" subtitle="定时推送 / HTTP 回调 · cron 驱动 · 自动执行" :icon="AlarmOutline">
      <NButton secondary @click="openHistory">
        <template #icon><NIcon><TimeOutline /></NIcon></template>
        历史任务
      </NButton>
      <NButton type="primary" @click="openCreate">
        <template #icon><NIcon><AddOutline /></NIcon></template>
        新建任务
      </NButton>
      <NButton secondary :loading="loading" @click="load">
        <template #icon><NIcon><RefreshOutline /></NIcon></template>
        刷新
      </NButton>
    </PageHero>

    <NAlert type="info" :show-icon="true" style="margin-bottom: 14px">
      cron 示例：<b>0 8 * * *</b> 每天 08:00 · <b>*/5 * * * *</b> 每 5 分钟 · <b>0 3 * * 1</b> 每周一 03:00。
      任务到点自动执行，也可「执行」按钮手动触发（不影响原调度计划）。
    </NAlert>

    <!-- ═══════ 执行分析（批次5） ═══════ -->
    <NGrid :cols="24" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 14px">
      <NGi :span="4"><NCard :bordered="true"><NStatistic label="任务总数" :value="analysis.jobTotal ?? 0" /></NCard></NGi>
      <NGi :span="4"><NCard :bordered="true"><NStatistic label="启用中" :value="analysis.jobEnabled ?? 0"><template #suffix><NText depth="3" style="font-size: 12px">个</NText></template></NStatistic></NCard></NGi>
      <NGi :span="4"><NCard :bordered="true"><NStatistic label="累计成功率" :value="analysis.successRate ?? 100"><template #suffix><NText depth="3" style="font-size: 12px">%</NText></template></NStatistic></NCard></NGi>
      <NGi :span="4"><NCard :bordered="true"><NStatistic label="今日执行" :value="analysis.todayRuns ?? 0"><template #suffix><NText depth="3" style="font-size: 12px">次</NText></template></NStatistic></NCard></NGi>
      <NGi :span="4"><NCard :bordered="true"><NStatistic label="今日失败" :value="analysis.todayFails ?? 0"><template #suffix><NText depth="3" style="font-size: 12px">次</NText></template></NStatistic></NCard></NGi>
      <NGi :span="4"><NCard :bordered="true"><NStatistic label="今日平均耗时" :value="analysis.todayAvgMs ?? 0"><template #suffix><NText depth="3" style="font-size: 12px">ms</NText></template></NStatistic></NCard></NGi>
    </NGrid>

    <NCard title="执行趋势" :bordered="true" style="margin-bottom: 14px">
      <template #header-extra>
        <NSpace :size="6" align="center">
          <NRadioGroup v-model:value="trendDays" size="small">
            <NRadioButton :value="7">近 7 天</NRadioButton>
            <NRadioButton :value="14">近 14 天</NRadioButton>
            <NRadioButton :value="30">近 30 天</NRadioButton>
          </NRadioGroup>
        </NSpace>
      </template>
      <CommonChart :option="trendOption" height="240px" />
    </NCard>

    <NCard :bordered="true" style="margin-bottom: 14px">
      <template #header>
        <NSpace align="center" :size="8">
          <span>任务执行情况</span>
          <NText depth="3" style="font-size: 12px">按任务聚合：成功率 / 平均耗时 / 最近一次执行</NText>
        </NSpace>
      </template>
      <NDataTable
        :columns="analysisCols"
        :data="analysis.perJob || []"
        :bordered="false"
        size="small"
        :row-key="(r: any) => r.jobId"
      />
      <NEmpty v-if="!(analysis.perJob || []).length" description="暂无执行记录（任务触发后出现）" style="padding: 24px 0" />
    </NCard>

    <NCard :bordered="true">
      <NSpin :show="loading">
        <NDataTable :columns="columns" :data="rows" :bordered="false" size="small" :row-key="(r: any) => r.id" />
        <NEmpty v-if="!loading && !rows.length" description="暂无定时任务，点右上角「新建任务」创建" style="padding: 32px 0" />
      </NSpin>
    </NCard>

    <!-- 新建 / 编辑弹窗 -->
    <NModal
      v-model:show="showModal"
      preset="card"
      :title="editingId != null ? '编辑任务' : '新建定时任务'"
      style="width: 760px; max-width: 94vw"
      :bordered="false"
      class="task-modal"
    >
      <NForm
        label-placement="left"
        label-width="92"
        size="medium"
        :show-feedback="false"
        style="row-gap: 16px"
      >
        <!-- ═══════ 基础信息 ═══════ -->
        <div class="tm-section-title tm-section-title-tight">
          <NIcon size="14" color="#5b5bd6"><DocumentTextOutline /></NIcon>
          <span>基础信息</span>
        </div>
        <NFormItem label="任务名称">
          <NInput v-model:value="form.name" placeholder="如：每天早安推送" clearable />
        </NFormItem>
        <NFormItem label="任务类型">
          <NRadioGroup v-model:value="form.jobType">
            <NRadioButton v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</NRadioButton>
          </NRadioGroup>
        </NFormItem>
        <NFormItem label="备注">
          <NInput v-model:value="form.remark" placeholder="选填 · 任务用途说明" clearable />
        </NFormItem>

        <!-- ═══════ 执行模式：仅一次 / 周期 ═══════ -->
        <NFormItem label="执行模式">
          <NRadioGroup v-model:value="form.repeatMode" name="repeatMode">
            <NRadioButton
              v-for="o in repeatModeOptions"
              :key="o.value"
              :value="o.value"
              :label="o.label"
            />
          </NRadioGroup>
          <NText v-if="form.repeatMode === 'one_shot'" depth="3" style="font-size: 12px; margin-left: 14px">
            仅触发一次后自动停用（适合"3 分钟后提醒一下"类需求）
          </NText>
        </NFormItem>

        <NFormItem v-if="form.repeatMode === 'one_shot'" label="执行时间">
          <NSpace :size="10" align="center">
            <NDatePicker
              v-model:value="form.oneShotAt"
              type="datetime"
              format="yyyy-MM-dd HH:mm"
              date-format="yyyy-MM-dd"
              time-format="HH:mm"
              placeholder="选择日期与时间"
              clearable
              style="width: 240px"
            />
            <NButton
              size="small"
              tertiary
              @click="form.oneShotAt = Date.now() + 10 * 60 * 1000"
            >
              +10 分钟
            </NButton>
            <NText v-if="form.oneShotAt" depth="3" style="font-size: 12px">
              即 {{ fmtTime(form.oneShotAt / 1000) }} 触发
            </NText>
          </NSpace>
        </NFormItem>

        <!-- cron 文本 + 预览（cron 模式显示完整 builder；one_shot 也展示只读预览） -->
        <template v-if="form.repeatMode === 'cron'">
          <div class="tm-section-title">
            <NIcon size="14" color="#07c160"><TimeOutline /></NIcon>
            <span>Cron 表达式</span>
            <NTag v-if="cronNext != null" :bordered="false" size="small" type="success" style="margin-left: auto">
              <NIcon size="11" style="vertical-align: -1px"><TimeOutline /></NIcon>
              下次执行：{{ fmtTime(cronNext) }}
            </NTag>
            <NTag v-else-if="form.cron" :bordered="false" size="small" type="error" style="margin-left: auto">cron 表达式非法</NTag>
          </div>
          <NFormItem label="cron 文本">
            <NInput
              v-model:value="form.cron"
              placeholder="分 时 日 月 周（专家模式直接输入）"
              clearable
              style="font-family: ui-monospace, SFMono-Regular, Menlo, monospace"
            >
              <template #prefix><NIcon size="15"><TimeOutline /></NIcon></template>
            </NInput>
          </NFormItem>
          <div class="cron-builder-wrap">
            <div class="cb-hint">
              <NIcon size="13" color="#8a93a6"><TimeOutline /></NIcon>
              快捷生成（与上方输入双向同步）
            </div>
            <CronBuilder :cron="form.cron" @update:cron="form.cron = $event" />
          </div>
        </template>
        <template v-else>
          <div class="tm-section-title">
            <NIcon size="14" color="#5b5bd6"><TimeOutline /></NIcon>
            <span>单次触发预览</span>
            <NTag :bordered="false" size="small" type="success" style="margin-left: auto">
              <NIcon size="11" style="vertical-align: -1px"><TimeOutline /></NIcon>
              触发：{{ form.oneShotAt ? fmtTime(form.oneShotAt / 1000) : '—' }}
            </NTag>
          </div>
          <NFormItem label="cron 文本">
            <NInput
              :value="form.cron"
              readonly
              disabled
              style="font-family: ui-monospace, SFMono-Regular, Menlo, monospace; color: var(--n-text-color-3)"
            >
              <template #prefix><NIcon size="15"><TimeOutline /></NIcon></template>
            </NInput>
          </NFormItem>
        </template>

        <!-- ═══════ 推送目标 / HTTP 回调 ═══════ -->
        <div class="tm-section-title">
          <NIcon size="14" :color="form.jobType === 'HTTP' ? '#f0a020' : '#2090e0'">
            <component :is="form.jobType === 'HTTP' ? CreateOutline : CreateOutline" />
          </NIcon>
          <span>{{ form.jobType === 'HTTP' ? 'HTTP 回调配置' : '推送目标' }}</span>
        </div>

        <template v-if="form.jobType === 'BOT_PUSH'">
          <NFormItem label="机器人">
            <NSelect
              v-model:value="form.targetBot"
              :options="botOptions"
              placeholder="选择要推送的机器人"
              filterable
              clearable
            />
          </NFormItem>
          <NFormItem label="目标类型">
            <NRadioGroup v-model:value="form.targetType">
              <NRadioButton v-for="t in targetTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</NRadioButton>
            </NRadioGroup>
          </NFormItem>
          <NFormItem :label="form.targetType === 'GROUP' ? '目标群聊' : '目标用户'">
            <NSelect
              v-model:value="form.targetId"
              :options="form.targetType === 'GROUP' ? groupOptions : friendOptions"
              placeholder="选择目标"
              filterable
              clearable
            />
          </NFormItem>
          <NFormItem label="推送内容">
            <NInput
              v-model:value="form.content"
              type="textarea"
              :rows="4"
              placeholder="要定时发送的消息内容"
              show-count
            />
          </NFormItem>
        </template>

        <template v-else>
          <NFormItem label="回调 URL">
            <NInput v-model:value="form.targetId" placeholder="https://example.com/webhook" clearable>
              <template #prefix><NIcon size="15"><CreateOutline /></NIcon></template>
            </NInput>
          </NFormItem>
          <NFormItem label="请求方法">
            <NRadioGroup v-model:value="form.targetType">
              <NRadioButton value="GET">GET</NRadioButton>
              <NRadioButton value="POST">POST</NRadioButton>
            </NRadioGroup>
          </NFormItem>
          <NFormItem label="请求体">
            <NInput
              v-model:value="form.content"
              type="textarea"
              :rows="4"
              placeholder='POST 时填 JSON，例如 {"hello":"world"}'
              style="font-family: ui-monospace, SFMono-Regular, Menlo, monospace"
            />
          </NFormItem>
        </template>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton size="small" @click="showModal = false">取消</NButton>
          <NButton size="small" type="primary" :loading="saving" @click="save">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- 执行日志抽屉 -->
    <NDrawer v-model:show="showLogs" :width="560" placement="right">
      <NDrawerContent :title="`执行日志 · ${logJobName}`">
        <NDataTable
          v-if="logRows.length"
          :columns="[
            { title: '时间', key: 'startTime', width: 120, render: (r: any) => fmtTime(r.startTime) },
            { title: '状态', key: 'status', width: 70, render: (r: any) => h(NTag, { size: 'small', bordered: false, type: logStatusTag[r.status]?.type ?? 'default' }, { default: () => logStatusTag[r.status]?.label ?? r.status }) },
            { title: '耗时', key: 'elapsedMs', width: 80, render: (r: any) => (r.elapsedMs ?? 0) + 'ms' },
            { title: '结果', key: 'result', ellipsis: { tooltip: true } }
          ]"
          :data="logRows"
          :bordered="false"
          size="small"
        />
        <NEmpty v-else description="暂无执行记录（任务触发后出现）" style="padding: 32px 0" />
      </NDrawerContent>
    </NDrawer>

    <!-- 删除确认 -->
    <NModal v-model:show="showDelete" preset="card" :title="`停用并归档任务 · ${deletingName}`" style="width: 440px; max-width: 92vw" :bordered="false">
      <NAlert type="warning" :show-icon="true" style="margin-bottom: 12px">删除后任务不再执行</NAlert>
      <NText depth="2" style="font-size: 13px; line-height: 1.7">
        任务「{{ deletingName }}」将被停用并移入<b>历史任务</b>（记录与执行日志保留，可在历史抽屉中查看或一键恢复）。
      </NText>
      <template #footer>
        <NSpace justify="end">
          <NButton size="small" @click="showDelete = false">取消</NButton>
          <NButton size="small" type="error" @click="confirmDelete">
            <template #icon><NIcon><TrashOutline /></NIcon></template>
            确认删除
          </NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- 历史任务抽屉：全部任务（含已删除），只读 + 日志/恢复 -->
    <NDrawer v-model:show="showHistory" :width="900" placement="right">
      <NDrawerContent title="历史任务 · 全部记录（含已删除）" closable>
        <NSpin :show="historyLoading">
          <NDataTable
            :columns="historyColumns"
            :data="historyRows"
            :bordered="false"
            size="small"
            :row-key="(r: any) => r.id"
          />
          <NEmpty v-if="!historyLoading && !historyRows.length" description="暂无历史任务" style="padding: 40px 0" />
        </NSpin>
      </NDrawerContent>
    </NDrawer>
  </div>
</template>

<style scoped>
.tm-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--n-text-color-1);
  padding: 6px 0 8px;
  margin-top: 14px;
  border-bottom: 1px dashed var(--n-border-color);
}
.tm-section-title:first-of-type { margin-top: 0; }
.tm-section-title-tight { margin-top: 4px; padding: 0 0 6px; border-bottom: none; }

.cron-builder-wrap {
  background: var(--n-color-2);
  border-radius: 10px;
  padding: 14px 16px 16px;
  margin-top: 10px;
  margin-bottom: 6px;
}
.cb-hint {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--n-text-color-3);
  margin-bottom: 10px;
}

/* 让 NForm 在 label-placement=left 下更舒展（每项之间留白充足） */
.task-modal :deep(.n-form-item) {
  margin-bottom: 22px;
}
.task-modal :deep(.n-form-item:last-child) {
  margin-bottom: 0;
}
/* 让每个段标题与上一项之间留更大空隙 */
.task-modal :deep(.tm-section-title + .n-form-item) {
  margin-top: 0;
}
/* CronBuilder 区域内的紧凑 row 再给一点行距 */
.task-modal :deep(.cron-builder-wrap .cb-row) {
  margin-bottom: 4px;
}
</style>
