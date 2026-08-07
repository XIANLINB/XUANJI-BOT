<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NInput, NForm, NFormItem, NDataTable, NPopconfirm,
  NSelect, useMessage
} from 'naive-ui'
import { ShieldCheckmarkOutline, KeyOutline, DocumentTextOutline, RefreshOutline, TrashOutline, DownloadOutline, SearchOutline } from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import api from '../api'
import dayjs from 'dayjs'

const message = useMessage()

// ══════ 修改访问口令 ══════
const oldPin = ref('')
const newPin = ref('')
const confirmPin = ref('')
const changing = ref(false)

async function changePin() {
  if (!/^\d{6}$/.test(newPin.value)) { message.warning('新口令必须是 6 位数字'); return }
  if (newPin.value !== confirmPin.value) { message.warning('两次输入的新口令不一致'); return }
  changing.value = true
  try {
    const r = await api.changePin(oldPin.value, newPin.value)
    if (r.error) { message.error(r.error); return }
    message.success('口令已修改，下次登录请使用新口令')
    oldPin.value = newPin.value = confirmPin.value = ''
  } catch (e: any) {
    message.error('修改失败：' + (e?.message ?? e))
  } finally {
    changing.value = false
  }
}

// ══════ 审计日志 ══════
const auditRows = ref<any[]>([])
const auditLoading = ref(false)
const auditActions = ref<string[]>([])
const auditFilter = ref({ action: '', ip: '', keyword: '', days: 0 })
const RANGE_OPTIONS = [
  { label: '全部时间', value: 0 },
  { label: '今天', value: 1 },
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 }
]

const actionLabel: Record<string, string> = {
  LOGIN_OK: '登录成功', LOGIN_FAIL: '登录失败', LOGOUT: '登出',
  CHANGE_PIN: '修改口令', CHANGE_PIN_FAIL: '修改口令失败',
  SQL_QUERY: 'SQL 查询', BACKUP_CREATE: '创建备份', BACKUP_RESTORE: '恢复备份',
  BACKUP_DELETE: '删除备份', PLUGIN_UNLOAD: '卸载插件', AUDIT_CLEAR: '清空审计'
}
const actionType: Record<string, 'success' | 'error' | 'warning' | 'info' | 'default'> = {
  LOGIN_OK: 'success', LOGIN_FAIL: 'error', LOGOUT: 'default',
  CHANGE_PIN: 'warning', CHANGE_PIN_FAIL: 'error',
  SQL_QUERY: 'info', BACKUP_CREATE: 'success', BACKUP_RESTORE: 'warning',
  BACKUP_DELETE: 'error', PLUGIN_UNLOAD: 'error', AUDIT_CLEAR: 'warning'
}

function fmtTime(v: any): string {
  const n = Number(v)
  if (!isFinite(n) || n <= 0) return '—'
  return new Date(n * 1000).toLocaleString('zh-CN', { hour12: false })
}

const auditCols = [
  { title: '时间', key: 'CREATE_TIME', width: 165, render: (r: any) => fmtTime(r.CREATE_TIME) },
  { title: '动作', key: 'ACTION', width: 110, render: (r: any) => h(NTag, { size: 'small', bordered: false, type: actionType[r.ACTION] ?? 'default' }, { default: () => actionLabel[r.ACTION] ?? r.ACTION }) },
  { title: '详情', key: 'DETAIL', ellipsis: { tooltip: true } },
  { title: 'IP', key: 'IP', width: 130 }
]

function auditParams(): Record<string, any> {
  const p: Record<string, any> = {}
  if (auditFilter.value.action) p.action = auditFilter.value.action
  if (auditFilter.value.ip) p.ip = auditFilter.value.ip.trim()
  if (auditFilter.value.keyword) p.keyword = auditFilter.value.keyword.trim()
  if (auditFilter.value.days > 0) {
    p.startTime = dayjs().subtract(auditFilter.value.days, 'day').startOf('day').unix()
  }
  return p
}

async function loadAudit() {
  auditLoading.value = true
  try {
    const r = await api.getAudit(200, auditParams())
    auditRows.value = r.rows || []
  } catch (e: any) {
    message.error('加载审计失败：' + (e?.message ?? e))
  } finally {
    auditLoading.value = false
  }
}

async function loadActions() {
  try {
    const list: any[] = await api.auditActions()
    auditActions.value = (list || []).map((a) => String(a.ACTION || a.action || '').trim()).filter(Boolean)
  } catch {
    auditActions.value = []
  }
}

async function exportAudit(format: 'csv' | 'json') {
  try {
    await api.exportAudit(format, auditParams())
    message.success(`审计日志已导出（${format.toUpperCase()}，按当前筛选）`)
  } catch (e: any) {
    message.error('导出失败：' + (e?.message ?? e))
  }
}

async function clearAudit() {
  try {
    await api.clearAudit()
    message.success('审计日志已清空')
    await loadAudit()
  } catch (e: any) {
    message.error('清空失败：' + (e?.message ?? e))
  }
}

onMounted(() => {
  loadAudit()
  loadActions()
})
</script>

<template>
  <div>
    <PageHero title="安全管理" subtitle="修改访问口令 · 操作审计留痕" :icon="ShieldCheckmarkOutline">
      <NButton secondary :loading="auditLoading" @click="loadAudit">
        <template #icon><NIcon><RefreshOutline /></NIcon></template>
        刷新审计
      </NButton>
    </PageHero>

    <!-- 修改口令 -->
    <NCard title="修改访问口令" class="b-card">
      <template #header-extra><NIcon size="18" color="#185FA5"><KeyOutline /></NIcon></template>
      <NForm label-placement="left" :show-feedback="false" class="pin-form">
        <NFormItem label="当前口令">
          <NInput v-model:value="oldPin" type="password" maxlength="6" placeholder="6 位数字" style="width: 260px" />
        </NFormItem>
        <NFormItem label="新口令">
          <NInput v-model:value="newPin" type="password" maxlength="6" placeholder="6 位数字" style="width: 260px" />
        </NFormItem>
        <NFormItem label="确认新口令">
          <NInput v-model:value="confirmPin" type="password" maxlength="6" placeholder="再次输入新口令" style="width: 260px" />
        </NFormItem>
        <NFormItem label=" ">
          <NButton type="primary" :loading="changing" @click="changePin">修改口令</NButton>
          <NText depth="3" style="font-size: 12px; margin-left: 12px">改后不影响当前会话，下次登录使用新口令</NText>
        </NFormItem>
      </NForm>
    </NCard>

    <!-- 审计日志 -->
    <NCard title="操作审计日志" class="b-card">
      <template #header-extra>
        <NSpace :size="8" align="center">
          <NIcon size="18" color="#854F0B"><DocumentTextOutline /></NIcon>
          <NText depth="3" style="font-size: 12px">共 {{ auditRows.length }} 条（当前筛选）</NText>
        </NSpace>
      </template>

      <!-- 筛选栏 -->
      <NSpace :size="8" align="center" style="margin-bottom: 12px" wrap>
        <NSelect
          v-model:value="auditFilter.action"
          :options="auditActions.map((a) => ({ label: actionLabel[a] ?? a, value: a }))"
          placeholder="动作"
          clearable
          size="small"
          style="width: 150px"
        />
        <NInput v-model:value="auditFilter.ip" size="small" placeholder="IP 模糊" clearable style="width: 150px" />
        <NInput v-model:value="auditFilter.keyword" size="small" placeholder="详情关键词" clearable style="width: 160px" />
        <NSelect v-model:value="auditFilter.days" :options="RANGE_OPTIONS" size="small" style="width: 120px" />
        <NButton size="small" type="primary" tertiary :loading="auditLoading" @click="loadAudit">
          <template #icon><NIcon><SearchOutline /></NIcon></template>
          查询
        </NButton>
        <NButton size="small" secondary @click="loadAudit">
          <template #icon><NIcon><RefreshOutline /></NIcon></template>
          刷新
        </NButton>
        <NSpace :size="6" style="margin-left: auto">
          <NButton size="small" secondary @click="exportAudit('csv')">
            <template #icon><NIcon><DownloadOutline /></NIcon></template>
            导出 CSV
          </NButton>
          <NButton size="small" secondary @click="exportAudit('json')">
            <template #icon><NIcon><DownloadOutline /></NIcon></template>
            导出 JSON
          </NButton>
        </NSpace>
      </NSpace>

      <NDataTable :columns="auditCols" :data="auditRows" :loading="auditLoading" :bordered="false" size="small" />
      <div style="display: flex; justify-content: flex-end; margin-top: 12px">
        <NPopconfirm @positive-click="clearAudit">
          <template #trigger>
            <NButton size="small" type="error" tertiary>
              <template #icon><NIcon size="14"><TrashOutline /></NIcon></template>
              清空审计
            </NButton>
          </template>
          确定清空全部审计日志？此操作也会记录在案。
        </NPopconfirm>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.b-card { margin-top: 16px; }
.pin-form { max-width: 480px; }
</style>
