<script setup lang="ts">
import { ref, onMounted, computed, h } from 'vue'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NForm, NFormItem, NDataTable,
  useMessage, NProgress, NAlert, NInput, NEmpty, NDivider, NTabs, NTabPane,
  NSelect, NGrid, NGi, NNumberAnimation, NGradientText, NPagination, NPopconfirm,
  NTooltip, type DataTableColumns
} from 'naive-ui'
import {
  ShieldCheckmarkOutline, KeyOutline, DocumentTextOutline, RefreshOutline,
  CheckmarkCircleOutline, SearchOutline, DownloadOutline, TrashOutline,
  LaptopOutline, PhonePortraitOutline, TabletPortraitOutline, ServerOutline, EyeOutline
} from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import StatCard from '../components/StatCard.vue'
import { securityApi } from '../api/security'
import api from '../api'
import dayjs from 'dayjs'

const message = useMessage()

// ═══════════════════ Tab1：修改访问口令 ═══════════════════
const otp = ref({ old: '', next: '', confirm: '' })
const changing = ref(false)
const hiddenRefs = ref<Record<string, any>>({ old: null, next: null, confirm: null })

function onOtpInput(field: 'old' | 'next' | 'confirm', v: string) {
  otp.value[field] = (v || '').replace(/\D/g, '').slice(0, 6)
}
function onOtpInputOld(v: string) { onOtpInput('old', v) }
function onOtpInputNext(v: string) { onOtpInput('next', v) }
function onOtpInputConfirm(v: string) { onOtpInput('confirm', v) }
function focusOtp(field: 'old' | 'next' | 'confirm') {
  const el = hiddenRefs.value[field]
  if (el) el.focus()
}
function setOldRef(el: any) { if (el) hiddenRefs.value.old = el }
function setNextRef(el: any) { if (el) hiddenRefs.value.next = el }
function setConfirmRef(el: any) { if (el) hiddenRefs.value.confirm = el }
function otpCells(field: 'old' | 'next' | 'confirm'): string[] {
  const v = otp.value[field]
  return [v[0] || '', v[1] || '', v[2] || '', v[3] || '', v[4] || '', v[5] || '']
}

const strength = computed(() => {
  const v = otp.value.next
  if (v.length < 6) return { level: 0, label: '未完成', color: '#9aa0a6' }
  if (/^(123456|654321|000000|111111|222222|333333|444444|555555|666666|777777|888888|999999|012345|123123|112233)$/.test(v)) {
    return { level: 30, label: '弱（常见口令）', color: '#fa5151' }
  }
  if (/(\d)\1{2}/.test(v)) return { level: 55, label: '中', color: '#fa8c16' }
  return { level: 80, label: '强', color: '#07c160' }
})

async function changePin() {
  if (otp.value.old.length !== 6) { message.warning('请输入当前口令（6 位数字）'); return }
  if (otp.value.next.length !== 6) { message.warning('请输入新口令（6 位数字）'); return }
  if (otp.value.next !== otp.value.confirm) { message.warning('两次输入的新口令不一致'); return }
  if (otp.value.next === otp.value.old) { message.warning('新口令不能与当前口令相同'); return }
  changing.value = true
  try {
    const r = await securityApi.changePin(otp.value.old, otp.value.next)
    if (r.error) { message.error(r.error); return }
    message.success('口令已修改，下次登录请使用新口令')
    otp.value = { old: '', next: '', confirm: '' }
    loadPinHistory()
  } catch (e: any) {
    message.error('修改失败：' + (e?.message ?? e))
  } finally {
    changing.value = false
  }
}

const pinHistory = ref<any[]>([])
async function loadPinHistory() {
  try {
    const r = await securityApi.getAudit(50, { action: 'CHANGE_PIN' })
    pinHistory.value = (r.rows || []).slice(0, 5)
  } catch { pinHistory.value = [] }
}

// ═══════════════════ Tab2：操作日志 ═══════════════════
const rows = ref<any[]>([])
const loading = ref(false)
const err = ref('')
const actions = ref<string[]>([])

// 登录失败监控（来自风控数据源，防爆破）
const loginStat = ref<Record<string, any>>({ okTotal: 0, failTotal: 0, fail24h: 0 })
async function loadLoginStat() {
  try {
    const o = await api.riskOverview()
    loginStat.value = o.login || {}
  } catch { /* 统计失败不影响 */ }
}

const filter = ref<Record<string, any>>({ action: '', ip: '', keyword: '', deviceType: '', range: null })
const RANGE_OPTIONS = [
  { label: '今天', value: 1 },
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 }
]
const DEVICE_OPTIONS = [
  { label: 'PC', value: 'desktop' },
  { label: '手机', value: 'mobile' },
  { label: '平板', value: 'tablet' },
  { label: '机器人/脚本', value: 'bot' },
  { label: '未知', value: 'unknown' }
]

const ACTION_LABEL: Record<string, string> = {
  LOGIN_OK: '登录成功', LOGIN_FAIL: '登录失败', LOGOUT: '登出',
  CHANGE_PIN: '修改口令', CHANGE_PIN_FAIL: '修改口令失败',
  SQL_QUERY: 'SQL 查询',
  BOT_START: '启动机器人', BOT_STOP: '停止机器人',
  BOT_CONN_MODE_CHANGE: '切换连接方式', BOT_ADD: '新增机器人',
  BOT_UPDATE: '更新机器人', BOT_DELETE: '删除机器人', BOT_RELOAD: '热重载机器人',
  BOT_CONFIG_UPDATE: '机器人配置', BOT_OWNER_SET: '设置主人', BOT_OWNER_CLEAR: '清除主人',
  CACHE_CLEAR: '清理缓存', FILE_DELETE: '删除文件', FILE_CLEAR: '批量删文件',
  BLACKLIST_ADD: '拉黑用户', BLACKLIST_REMOVE: '解除拉黑',
  ALERT_CONFIG_UPDATE: '预警配置', ALERT_CHECK: '预警检查',
  PLUGIN_ENABLE: '启用插件', PLUGIN_DISABLE: '禁用插件', PLUGIN_RELOAD: '热重载插件',
  PLUGIN_LOAD: '加载插件', PLUGIN_UNLOAD: '卸载插件',
  PLUGIN_BIND: '插件绑定', PLUGIN_UNBIND: '插件解绑',
  PLUGIN_CONFIG_UPDATE: '插件配置', PLUGIN_KV_CLEAR: '清空插件数据',
  SETTINGS_UPDATE: '全局设置', GROUP_CONFIG_UPDATE: '群配置',
  BACKUP_CREATE: '创建备份', BACKUP_RESTORE: '恢复备份', BACKUP_DELETE: '删除备份',
  AUDIT_CLEAR: '清空审计'
}
const ACTION_TYPE: Record<string, 'success' | 'error' | 'warning' | 'info' | 'default'> = {
  LOGIN_OK: 'success', LOGIN_FAIL: 'error', LOGOUT: 'default',
  CHANGE_PIN: 'warning', CHANGE_PIN_FAIL: 'error',
  BOT_START: 'success', BOT_STOP: 'warning', BOT_CONN_MODE_CHANGE: 'warning',
  BOT_ADD: 'success', BOT_UPDATE: 'info', BOT_DELETE: 'error', BOT_RELOAD: 'info',
  BOT_CONFIG_UPDATE: 'info', BOT_OWNER_SET: 'info', BOT_OWNER_CLEAR: 'warning',
  CACHE_CLEAR: 'warning', FILE_DELETE: 'error', FILE_CLEAR: 'error',
  BLACKLIST_ADD: 'error', BLACKLIST_REMOVE: 'success',
  ALERT_CONFIG_UPDATE: 'info', ALERT_CHECK: 'info',
  PLUGIN_ENABLE: 'success', PLUGIN_DISABLE: 'warning', PLUGIN_RELOAD: 'info',
  PLUGIN_LOAD: 'success', PLUGIN_UNLOAD: 'error',
  PLUGIN_BIND: 'info', PLUGIN_UNBIND: 'warning',
  PLUGIN_CONFIG_UPDATE: 'info', PLUGIN_KV_CLEAR: 'warning',
  SETTINGS_UPDATE: 'info', GROUP_CONFIG_UPDATE: 'info',
  BACKUP_CREATE: 'success', BACKUP_RESTORE: 'warning', BACKUP_DELETE: 'error',
  AUDIT_CLEAR: 'error'
}
const DEVICE_META: Record<string, { label: string; icon: any; color: string }> = {
  desktop: { label: 'PC', icon: LaptopOutline, color: '#2090e0' },
  mobile: { label: '手机', icon: PhonePortraitOutline, color: '#07c160' },
  tablet: { label: '平板', icon: TabletPortraitOutline, color: '#722ed1' },
  bot: { label: '机器人', icon: ServerOutline, color: '#fa8c16' },
  unknown: { label: '未知', icon: EyeOutline, color: '#9aa0a6' }
}
const OS_LABEL: Record<string, string> = {
  windows: 'Windows', macos: 'macOS', linux: 'Linux', android: 'Android',
  ios: 'iOS', harmonyos: '鸿蒙', windowsphone: 'WP', unknown: '未知'
}
const BROWSER_LABEL: Record<string, string> = {
  chrome: 'Chrome', edge: 'Edge', safari: 'Safari', firefox: 'Firefox',
  opera: 'Opera', ie: 'IE', wechat: '微信', unknown: '未知'
}

function fmtTime(v: any): string {
  const n = Number(v)
  if (!isFinite(n) || n <= 0) return '—'
  return dayjs(n <= 9999999999 ? n * 1000 : n).format('YYYY-MM-DD HH:mm:ss')
}

async function loadActions() {
  try {
    const list: any[] = await securityApi.auditActions()
    actions.value = (list || []).map((a) => String(a.ACTION || a.action || '').trim()).filter(Boolean)
  } catch { actions.value = [] }
}

function params(): Record<string, any> {
  const p: Record<string, any> = {}
  if (filter.value.action) p.action = filter.value.action
  if (filter.value.ip) p.ip = filter.value.ip.trim()
  if (filter.value.keyword) p.keyword = filter.value.keyword.trim()
  if (filter.value.deviceType) p.deviceType = filter.value.deviceType
  if (filter.value.range) {
    p.startTime = dayjs().subtract(filter.value.range, 'day').startOf('day').unix()
  }
  return p
}

async function loadAudit() {
  loading.value = true
  err.value = ''
  try {
    const r = await securityApi.getAudit(1000, params())
    rows.value = r.rows || []
  } catch (e: any) { err.value = e?.message || String(e) }
  finally { loading.value = false }
}

async function exportAudit(format: 'csv' | 'json') {
  try {
    await securityApi.exportAudit(format, params())
  } catch (e: any) {
    err.value = e?.message || String(e)
  }
}

async function clearAudit() {
  try {
    await securityApi.clearAudit()
    await loadAudit()
  } catch (e: any) { err.value = e?.message || String(e) }
}

const statCards = computed(() => {
  const today0 = dayjs().startOf('day').unix()
  const today = rows.value.filter((r) => Number(r.CREATE_TIME) >= today0).length
  const fail = rows.value.filter((r) => String(r.ACTION).includes('FAIL') || r.ACTION === 'AUDIT_CLEAR').length
  const ips = new Set(rows.value.map((r) => String(r.IP || '')).filter(Boolean)).size
  const fail24h = Number(loginStat.value.fail24h ?? 0)
  return [
    { key: 'total', label: '记录总数', val: rows.value.length, color: '#5b5bd6', icon: DocumentTextOutline },
    { key: 'today', label: '今日操作', val: today, color: '#07c160', icon: RefreshOutline },
    { key: 'fail', label: '失败/危险操作', val: fail, color: '#fa5151', icon: ShieldCheckmarkOutline },
    { key: 'ip', label: '独立 IP', val: ips, color: '#2090e0', icon: LaptopOutline },
    { key: 'loginFail24h', label: '登录失败(24h)', val: fail24h, color: fail24h > 0 ? '#fa5151' : '#07c160', icon: KeyOutline }
  ]
})

const auditColumns = computed<DataTableColumns>(() => [
  { title: '时间', key: 'CREATE_TIME', width: 160, fixed: 'left', render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums; color: #86909c' }, fmtTime(row.CREATE_TIME)) },
  {
    title: '动作', key: 'ACTION', width: 110,
    render: (row) => h(NTag, { size: 'small', bordered: false, round: true, type: ACTION_TYPE[row.ACTION] ?? 'default' },
      { default: () => ACTION_LABEL[row.ACTION] ?? row.ACTION })
  },
  { title: '详情', key: 'DETAIL', minWidth: 200, ellipsis: { tooltip: true }, render: (row) => h('span', String(row.DETAIL || '—')) },
  { title: 'IP', key: 'IP', width: 130, render: (row) => h('span', { style: 'font-variant-numeric: tabular-nums' }, String(row.IP || '—')) },
  {
    title: '设备', key: 'DEVICE_TYPE', width: 170,
    render: (row) => {
      const dt = String(row.DEVICE_TYPE || 'unknown')
      const meta = DEVICE_META[dt] || DEVICE_META.unknown
      return h(NTooltip, null, {
        trigger: () => h('span', { style: 'display:inline-flex;align-items:center;gap:4px;font-size:12px;color:#4b5563' }, [
          h(NIcon, { size: 13, style: { color: meta.color } }, { default: () => h(meta.icon) }),
          meta.label,
          h('span', { style: 'color:#9aa0a6;font-size:11px' },
            OS_LABEL[String(row.DEVICE_OS || 'unknown')] + ' · ' + BROWSER_LABEL[String(row.DEVICE_BROWSER || 'unknown')])
        ]),
        default: () => '设备类型: ' + meta.label + '\n系统: ' + OS_LABEL[String(row.DEVICE_OS || 'unknown')]
          + '\n浏览器: ' + BROWSER_LABEL[String(row.DEVICE_BROWSER || 'unknown')]
      })
    }
  }
])

const page = ref(1)
const pageSize = ref(20)
const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return rows.value.slice(start, start + pageSize.value)
})

onMounted(async () => {
  loadPinHistory()
  await loadActions()
  await loadAudit()
  loadLoginStat()
})
</script>

<template>
  <div>
    <PageHero title="安全管理" subtitle="修改访问口令 · 操作审计留痕" :icon="ShieldCheckmarkOutline" />

    <NTabs type="line" animated default-value="pin" style="margin-top: 4px">
      <!-- ═══════ Tab1：修改访问口令 ═══════ -->
      <NTabPane name="pin" tab="修改访问口令">
        <div class="pin-grid">
          <NCard class="b-card" :content-style="{ padding: '18px 20px' }">
            <template #header>
              <NSpace align="center" :size="8">
                <NIcon size="18" color="#185FA5"><KeyOutline /></NIcon>
                <span style="font-weight: 600">修改访问口令</span>
                <NTag size="small" :bordered="false" type="info" round>6 位数字</NTag>
              </NSpace>
            </template>

            <NAlert type="info" :show-icon="true" style="margin-bottom: 16px">
              修改后当前会话不受影响，下次登录请使用新口令。
            </NAlert>

            <NForm label-placement="left" :show-feedback="false" class="pin-form">
              <NFormItem label="当前口令">
                <div class="otp-wrap" @click="focusOtp('old')">
                  <NInput class="otp-hidden" :maxlength="6" :ref="setOldRef" @update:value="onOtpInputOld" />
                  <div class="otp-cells">
                    <span v-for="(c, i) in otpCells('old')" :key="i" class="otp-cell" :class="{ filled: !!c }">{{ c }}</span>
                  </div>
                </div>
              </NFormItem>

              <NFormItem label="新口令">
                <div class="otp-wrap" @click="focusOtp('next')">
                  <NInput class="otp-hidden" :maxlength="6" :ref="setNextRef" @update:value="onOtpInputNext" />
                  <div class="otp-cells">
                    <span v-for="(c, i) in otpCells('next')" :key="i" class="otp-cell" :class="{ filled: !!c }">{{ c }}</span>
                  </div>
                </div>
                <div class="strength-bar" v-if="otp.next.length === 6">
                  <NProgress type="line" :percentage="strength.level" :color="strength.color" :show-indicator="false" :height="6" style="width: 180px" />
                  <span class="strength-label" :style="{ color: strength.color }">{{ strength.label }}</span>
                </div>
              </NFormItem>

              <NFormItem label="确认新口令">
                <div class="otp-wrap" @click="focusOtp('confirm')">
                  <NInput class="otp-hidden" :maxlength="6" :ref="setConfirmRef" @update:value="onOtpInputConfirm" />
                  <div class="otp-cells">
                    <span v-for="(c, i) in otpCells('confirm')" :key="i" class="otp-cell" :class="{ filled: !!c }">{{ c }}</span>
                  </div>
                </div>
              </NFormItem>

              <NFormItem label=" ">
                <NButton type="primary" :loading="changing" @click="changePin">
                  <template #icon><NIcon size="14"><CheckmarkCircleOutline /></NIcon></template>
                  修改口令
                </NButton>
              </NFormItem>
            </NForm>

            <NDivider v-if="pinHistory.length" style="margin: 14px 0 10px">最近修改历史</NDivider>
            <div v-if="pinHistory.length" class="history-list">
              <div v-for="(p, idx) in pinHistory" :key="idx" class="pin-history-row">
                <NIcon size="13" color="#185FA5"><RefreshOutline /></NIcon>
                <span class="history-time">{{ fmtTime(p.CREATE_TIME) }}</span>
                <span class="history-ip">{{ p.IP || '—' }}</span>
                <NText depth="3" class="history-detail" ellipsis>{{ p.DETAIL }}</NText>
              </div>
            </div>
          </NCard>

          <NCard class="b-card" :content-style="{ padding: '18px 20px' }">
            <template #header>
              <NSpace align="center" :size="8">
                <NIcon size="18" color="#185FA5"><ShieldCheckmarkOutline /></NIcon>
                <span style="font-weight: 600">安全须知</span>
              </NSpace>
            </template>
            <ul class="tips">
              <li>访问口令用于控制台登录鉴权，请妥善保管，勿泄露给他人。</li>
              <li>口令仅存储 PBKDF2 加盐哈希，明文不会落库，也无法从数据库还原。</li>
              <li>所有敏感操作（登录、改口令、机器人启停、缓存清理、文件删除等）都会记录到「操作日志」页签。</li>
              <li>审计日志会记录设备类型、操作系统与浏览器，异常登录可据此排查。</li>
            </ul>
          </NCard>
        </div>
      </NTabPane>

      <!-- ═══════ Tab2：操作日志 ═══════ -->
      <NTabPane name="audit" tab="操作日志">
        <div class="page-head">
          <div class="page-title">
            <NGradientText :gradient="{ deg: 90, from: '#5b5bd6', to: '#fa8c16' }" :size="18" style="font-weight: 700">
              操作日志
            </NGradientText>
            <NText depth="3" style="font-size: 13px">共 {{ rows.length }} 条</NText>
          </div>
          <NSpace align="center" :wrap="false">
            <NButton type="primary" :loading="loading" @click="loadAudit" size="small">
              <template #icon><NIcon size="14"><RefreshOutline /></NIcon></template>
              刷新
            </NButton>
            <NButton size="small" secondary @click="exportAudit('csv')">
              <template #icon><NIcon size="14"><DownloadOutline /></NIcon></template>
              导出 CSV
            </NButton>
            <NButton size="small" secondary @click="exportAudit('json')">
              <template #icon><NIcon size="14"><DownloadOutline /></NIcon></template>
              导出 JSON
            </NButton>
            <NPopconfirm @positive-click="clearAudit">
              <template #trigger>
                <NButton size="small" type="error" tertiary>
                  <template #icon><NIcon size="14"><TrashOutline /></NIcon></template>
                  清空
                </NButton>
              </template>
              确定清空全部操作日志？此操作也会记录在案。
            </NPopconfirm>
          </NSpace>
        </div>

        <!-- 统计卡 -->
        <NGrid :cols="24" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 16px">
          <NGi v-for="c in statCards" :key="c.key" span="24 s:12 m:8">
            <StatCard :icon="c.icon" :color="c.color" :value="c.val" :label="c.label" :duration="800" />
          </NGi>
        </NGrid>

        <!-- 筛选栏 -->
        <NSpace :size="8" align="center" wrap style="margin-bottom: 12px">
          <NSelect
            v-model:value="filter.action"
            :options="actions.map((a) => ({ label: ACTION_LABEL[a] ?? a, value: a }))"
            placeholder="动作" clearable size="small" style="width: 130px"
          />
          <NInput v-model:value="filter.ip" size="small" placeholder="IP 模糊" clearable style="width: 130px" />
          <NInput v-model:value="filter.keyword" size="small" placeholder="详情关键词" clearable style="width: 140px" />
          <NSelect v-model:value="filter.deviceType" :options="DEVICE_OPTIONS" placeholder="设备" clearable size="small" style="width: 110px" />
          <NSelect v-model:value="filter.range" :options="RANGE_OPTIONS" placeholder="时间范围" clearable size="small" style="width: 110px" />
          <NButton size="small" type="primary" tertiary @click="loadAudit">
            <template #icon><NIcon><SearchOutline /></NIcon></template>
            查询
          </NButton>
        </NSpace>

        <NAlert v-if="err" type="error" :title="'加载失败'" style="margin-bottom: 16px">{{ err }}</NAlert>

        <NDataTable
          :columns="auditColumns"
          :data="pagedRows"
          :pagination="false"
          :loading="loading"
          :bordered="false"
          size="small"
          striped
          style="margin-bottom: 12px"
        />
        <NSpace justify="end" align="center" style="margin-bottom: 16px">
          <NPagination
            v-model:page="page"
            v-model:page-size="pageSize"
            :item-count="rows.length"
            :page-sizes="[20, 50, 100, 200]"
            show-size-picker
            size="small"
          />
        </NSpace>
      </NTabPane>
    </NTabs>
  </div>
</template>

<style scoped>
.b-card { margin-top: 0; }
.pin-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}
@media (max-width: 1280px) {
  .pin-grid { grid-template-columns: 1fr; }
}
.pin-form { max-width: 460px; }
.otp-wrap {
  position: relative;
  display: inline-block;
  cursor: text;
}
.otp-hidden {
  position: absolute;
  opacity: 0;
  width: 1px;
  height: 1px;
  left: 0;
  top: 0;
  pointer-events: none;
}
.otp-cells {
  display: flex;
  gap: 8px;
}
.otp-cell {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  background: #fff;
  color: transparent;
  transition: all 0.15s;
}
.otp-cell.filled {
  color: #1f2329;
  border-color: #5b5bd6;
  background: #f0f1ff;
}
.otp-wrap:hover .otp-cell { border-color: #b3b8c0; }
.otp-wrap:focus-within .otp-cell {
  border-color: #5b5bd6;
  box-shadow: 0 0 0 2px rgba(91, 91, 214, 0.12);
}
.otp-wrap:focus-within .otp-cell.filled { color: #5b5bd6; }
.strength-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}
.strength-label {
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}
.history-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.pin-history-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 5px 8px;
  border-radius: 6px;
  background: rgba(128, 128, 128, 0.05);
}
.history-time {
  font-variant-numeric: tabular-nums;
  color: #4b5563;
}
.history-ip {
  font-variant-numeric: tabular-nums;
  color: #6b7280;
  min-width: 100px;
}
.history-detail {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tips {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 2;
  color: var(--n-text-color-2);
}
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}
.page-title { display: flex; align-items: center; gap: 10px; }
</style>
