<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NDataTable, NModal, NAlert,
  NRadioGroup, NRadioButton, NSwitch, NInputNumber, NPopconfirm, useMessage
} from 'naive-ui'
import {
  ArchiveOutline, RefreshOutline, DownloadOutline, TrashOutline, CloudUploadOutline
} from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import api from '../api'

const message = useMessage()
const rows = ref<any[]>([])
const loading = ref(false)
const creating = ref(false)
const backupScope = ref<'business' | 'log' | 'all'>('all')

// 自动备份设置
const autoEnabled = ref(true)
const autoRetention = ref(7)
const settingsLoading = ref(false)

function fmtSize(v: any): string {
  const n = Number(v)
  if (!isFinite(n) || n <= 0) return '—'
  if (n >= 1024 ** 3) return (n / 1024 ** 3).toFixed(2) + ' GB'
  if (n >= 1024 ** 2) return (n / 1024 ** 2).toFixed(1) + ' MB'
  return (n / 1024).toFixed(1) + ' KB'
}
function fmtTime(v: any): string {
  const n = Number(v)
  if (!isFinite(n) || n <= 0) return '—'
  return new Date(n).toLocaleString('zh-CN', { hour12: false })
}
const scopeLabel: Record<string, string> = {
  business: '业务库', log: '日志库', all: '全部'
}

const cols = [
  { title: '备份文件', key: 'name', ellipsis: { tooltip: true } },
  { title: '时间', key: 'mtime', width: 165, render: (r: any) => fmtTime(r.mtime) },
  { title: '大小', key: 'size', width: 90, render: (r: any) => fmtSize(r.size) },
  {
    title: '操作', key: 'op', width: 230,
    render: (r: any) => h(NSpace, { size: 6 }, {
      default: () => [
        h(NButton, { size: 'tiny', secondary: true, onClick: () => download(r.name) }, { default: () => '下载' }),
        h(NButton, { size: 'tiny', type: 'warning', tertiary: true, onClick: () => askRestore(r) }, { default: () => '恢复' }),
        h(NPopconfirm, {
          onPositiveClick: () => removeBackup(r)
        }, {
          trigger: () => h(NButton, { size: 'tiny', type: 'error', tertiary: true }, { default: () => '删除' }),
          default: () => '确定删除备份文件「' + r.name + '」？'
        })
      ]
    })
  }
]

async function load() {
  loading.value = true
  try {
    rows.value = await api.listBackups()
  } catch (e: any) {
    message.error('加载备份列表失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

async function create() {
  creating.value = true
  try {
    const r = await api.createBackup(backupScope.value)
    message.success('备份完成：' + r.name + '（' + scopeLabel[backupScope.value] + '）')
    await load()
  } catch (e: any) {
    message.error('备份失败：' + (e?.message ?? e))
  } finally {
    creating.value = false
  }
}

function download(name: string) {
  window.open('/xuanji/api/v1/console/backup/download?name=' + encodeURIComponent(name), '_blank')
}

// 恢复确认
const showRestoreModal = ref(false)
const restoreTarget = ref<any>(null)
const restoreConfirmText = ref('')
const restoring = ref(false)

function askRestore(r: any) {
  restoreTarget.value = r
  restoreConfirmText.value = ''
  showRestoreModal.value = true
}

async function doRestore() {
  if (restoreConfirmText.value !== '恢复') { message.warning('请准确输入「恢复」以确认'); return }
  restoring.value = true
  try {
    const r = await api.restoreBackup(restoreTarget.value.name)
    message.success(r.message || '恢复完成')
    showRestoreModal.value = false
  } catch (e: any) {
    message.error('恢复失败：' + (e?.message ?? e))
  } finally {
    restoring.value = false
  }
}

async function removeBackup(r: any) {
  try {
    await api.deleteBackup(r.name)
    message.success('已删除备份')
    await load()
  } catch (e: any) {
    message.error('删除失败：' + (e?.message ?? e))
  }
}

// 自动备份设置
async function loadSettings() {
  settingsLoading.value = true
  try {
    const s = await api.getBackupSettings()
    autoEnabled.value = !!s.enabled
    autoRetention.value = Number(s.retention ?? 7)
  } catch (e: any) {
    message.error('加载设置失败：' + (e?.message ?? e))
  } finally {
    settingsLoading.value = false
  }
}

async function saveSettings() {
  try {
    await api.putBackupSettings({ enabled: autoEnabled.value, retention: autoRetention.value })
    message.success('自动备份设置已保存')
  } catch (e: any) {
    message.error('保存设置失败：' + (e?.message ?? e))
  }
}

onMounted(() => { load(); loadSettings() })
</script>

<template>
  <div>
    <PageHero title="备份恢复" subtitle="H2 在线备份 · 业务库/日志库/全部 · 恢复前自动快照" :icon="ArchiveOutline" />

    <!-- 立即备份 + 自动备份 -->
    <NCard title="备份操作" class="b-card">
      <NSpace vertical :size="14">
        <NSpace align="center" :size="12">
          <NText style="font-size: 13px">备份范围：</NText>
          <NRadioGroup v-model:value="backupScope">
            <NRadioButton value="business">业务库</NRadioButton>
            <NRadioButton value="log">日志库</NRadioButton>
            <NRadioButton value="all">全部</NRadioButton>
          </NRadioGroup>
          <NText depth="3" style="font-size: 12px">
            {{ backupScope === 'business' ? '框架库 + 平台库 + 各机器人实例库（不含日志）' : backupScope === 'log' ? '仅框架日志库（xuanji.log）' : '业务库 + 日志库（完整备份）' }}
          </NText>
          <NButton type="primary" :loading="creating" @click="create">
            <template #icon><NIcon><CloudUploadOutline /></NIcon></template>
            立即备份
          </NButton>
        </NSpace>
        <NSpace align="center" :size="12">
          <NSwitch v-model:value="autoEnabled" :loading="settingsLoading" />
          <NText style="font-size: 13px">每日 03:00 自动备份（范围跟随上方手动选择，独立保存）</NText>
          <NText depth="3" style="font-size: 13px">保留</NText>
          <NInputNumber v-model:value="autoRetention" :min="1" :max="30" size="small" style="width: 90px" />
          <NText depth="3" style="font-size: 13px">份（超出自动删最旧）</NText>
          <NButton size="small" secondary @click="saveSettings">保存设置</NButton>
        </NSpace>
      </NSpace>
    </NCard>

    <!-- 备份列表 -->
    <NCard title="备份列表" class="b-card">
      <template #header-extra>
        <NButton secondary size="small" :loading="loading" @click="load">
          <template #icon><NIcon size="14"><RefreshOutline /></NIcon></template>
          刷新
        </NButton>
      </template>
      <NDataTable :columns="cols" :data="rows" :loading="loading" :bordered="false" size="small" />
      <NText depth="3" style="font-size: 12px; display: block; margin-top: 12px">
        备份文件存放于框架运行目录 backups/ 下（zip，含 manifest.json 清单）。
      </NText>
    </NCard>

    <!-- 恢复确认弹窗（双重确认） -->
    <NModal v-model:show="showRestoreModal" preset="card" title="恢复备份" style="width: 520px; max-width: 92vw" :bordered="false">
      <NAlert type="error" :show-icon="true" title="高风险操作" style="margin-bottom: 12px">
        即将用「{{ restoreTarget?.name }}」覆盖当前全部数据库！
      </NAlert>
      <NText tag="div" depth="3" style="font-size: 13px; line-height: 1.8; display: block; margin-bottom: 12px">
        执行流程：<br>
        1. 先自动快照当前库到 <b>backups/pre_restore_时间戳/</b>（可回滚）；<br>
        2. 逐个库 RUNSCRIPT 导入备份内容；<br>
        3. 完成后<b>建议重启应用</b>（连接池旧连接可能读到旧数据）。
      </NText>
      <NText style="font-size: 13px; display: block; margin-bottom: 4px">请输入 <b>恢复</b> 以确认操作：</NText>
      <NInput v-model:value="restoreConfirmText" placeholder="输入「恢复」" style="width: 200px" />
      <template #footer>
        <NSpace justify="end" :size="8">
          <NButton size="small" @click="showRestoreModal = false">取消</NButton>
          <NButton size="small" type="error" :loading="restoring" :disabled="restoreConfirmText !== '恢复'" @click="doRestore">
            确认恢复
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.b-card { margin-top: 16px; }
</style>
