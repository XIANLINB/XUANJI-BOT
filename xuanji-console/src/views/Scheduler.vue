<script setup lang="ts">
import { ref, computed, onMounted, watch, h } from 'vue'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NDataTable, NModal, NForm, NFormItem,
  NInput, NSelect, NSwitch, NDrawer, NDrawerContent, NEmpty, NAlert, NSpin,
  NRadioGroup, NRadioButton, NStatistic, NGrid, NGi, useMessage
} from 'naive-ui'
import {
  AlarmOutline, AddOutline, RefreshOutline, PlayOutline, TrashOutline,
  TimeOutline, DocumentTextOutline, CreateOutline
} from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import CommonChart from '../components/CommonChart.vue'
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
  targetPlatform: 'qqbot', targetBot: '', targetType: 'GROUP',
  targetId: '', content: '', remark: ''
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
const groupOptions = computed(() =>
  (groups.value || []).map((g: any) => ({ label: `${groupName(g)} (${g.groupOpenid || g.groupId})`, value: g.groupOpenid || g.groupId })))
const friendOptions = computed(() =>
  (friends.value || []).map((f: any) => ({ label: `${userName(f)} (${f.openid || f.userId})`, value: f.openid || f.userId })))

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
    targetPlatform: 'qqbot', targetBot: '', targetType: 'GROUP',
    targetId: '', content: '', remark: ''
  }
  showModal.value = true
}

function openEdit(r: any) {
  editingId.value = r.id
  form.value = {
    name: r.name, jobType: r.jobType, cron: r.cron,
    targetPlatform: r.targetPlatform || 'qqbot', targetBot: r.targetBot || '',
    targetType: r.targetType || 'GROUP', targetId: r.targetId || '',
    content: r.content || '', remark: r.remark || ''
  }
  showModal.value = true
}

async function save() {
  if (!form.value.name?.trim()) return message.error('请填写任务名称')
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
    const body = { ...form.value }
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
    message.success('任务已删除（含执行日志）')
    showDelete.value = false
    await load()
  } catch (e: any) {
    message.error('删除失败：' + (e?.message ?? e))
  }
}

// ═══════════ 表格 ═══════════
const columns = [
  { title: '任务名称', key: 'name', minWidth: 140, ellipsis: { tooltip: true } },
  {
    title: '类型', key: 'jobType', width: 100,
    render: (r: any) => h(NTag, { size: 'small', bordered: false, type: typeTag[r.jobType]?.type ?? 'default' }, { default: () => typeTag[r.jobType]?.label ?? r.jobType })
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
    <NModal v-model:show="showModal" preset="card" :title="editingId != null ? '编辑任务' : '新建定时任务'" style="width: 560px; max-width: 94vw" :bordered="false">
      <NForm label-placement="top" size="small">
        <NFormItem label="任务名称">
          <NInput v-model:value="form.name" placeholder="如：每天早安推送" />
        </NFormItem>
        <NFormItem label="任务类型">
          <NRadioGroup v-model:value="form.jobType">
            <NRadioButton v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</NRadioButton>
          </NRadioGroup>
        </NFormItem>
        <NFormItem label="Cron 表达式">
          <NInput v-model:value="form.cron" placeholder="如 0 8 * * *" />
          <NText v-if="cronNext != null" depth="3" style="font-size: 12px; margin-top: 4px">
            <NIcon size="12" style="vertical-align: -2px"><TimeOutline /></NIcon>
            下次执行：{{ fmtTime(cronNext) }}
          </NText>
          <NText v-else-if="form.cron" type="error" depth="3" style="font-size: 12px; margin-top: 4px">cron 表达式非法</NText>
        </NFormItem>

        <template v-if="form.jobType === 'BOT_PUSH'">
          <NFormItem label="机器人">
            <NSelect v-model:value="form.targetBot" :options="botOptions" placeholder="选择要推送的机器人" filterable />
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
            />
          </NFormItem>
          <NFormItem label="推送内容">
            <NInput v-model:value="form.content" type="textarea" :rows="3" placeholder="要定时发送的消息内容" />
          </NFormItem>
        </template>

        <template v-else>
          <NFormItem label="回调 URL">
            <NInput v-model:value="form.targetId" placeholder="https://example.com/webhook" />
          </NFormItem>
          <NFormItem label="请求方法">
            <NRadioGroup v-model:value="form.targetType">
              <NRadioButton value="GET">GET</NRadioButton>
              <NRadioButton value="POST">POST</NRadioButton>
            </NRadioGroup>
          </NFormItem>
          <NFormItem label="请求体（POST 时，JSON）">
            <NInput v-model:value="form.content" type="textarea" :rows="3" placeholder='{"hello":"world"}' />
          </NFormItem>
        </template>

        <NFormItem label="备注">
          <NInput v-model:value="form.remark" placeholder="选填" />
        </NFormItem>
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
    <NModal v-model:show="showDelete" preset="card" :title="`删除定时任务 · ${deletingName}`" style="width: 440px; max-width: 92vw" :bordered="false">
      <NAlert type="warning" :show-icon="true" style="margin-bottom: 12px">此操作不可恢复</NAlert>
      <NText depth="2" style="font-size: 13px; line-height: 1.7">
        将删除任务「{{ deletingName }}」及其<b>全部执行日志</b>，任务将不再自动执行。
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
  </div>
</template>
