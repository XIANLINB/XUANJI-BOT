<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NDataTable, NModal, NAlert, NPopconfirm,
  NCheckboxGroup, NCheckbox, NSwitch, NInputNumber, NGrid, NGi, useMessage
} from 'naive-ui'
import {
  ArchiveOutline, RefreshOutline, DownloadOutline, TrashOutline, CloudUploadOutline,
  ChatbubbleEllipsesOutline
} from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import EmptyState from '../components/EmptyState.vue'
import dayjs from 'dayjs'
import { BASE, download as httpDownload } from '../api/http'
import api from '../api'

const message = useMessage()
const rows = ref<any[]>([])
const loading = ref(false)
const creating = ref(false)
const msgCreating = ref(false)

// 可选备份分类（默认全选）
const ALL_CATS = ['framework', 'platform', 'business', 'logs', 'messages']
const CATEGORY_META: Record<string, { label: string; desc: string }> = {
  framework: { label: '框架核心', desc: '框架配置 / 机器人注册 / 设置（xuanji.mv.db）' },
  platform: { label: '平台数据', desc: '机器人档案（qqbot/qqbot.mv.db）' },
  business: { label: '业务档案', desc: '各机器人的群 / 成员 / 好友档案' },
  logs: { label: '运行日志', desc: '系统事件流水 + 管理操作日志' },
  messages: { label: '聊天消息', desc: '群聊 + 单聊消息（qqbot_message）' }
}

// 手动备份：自由勾选范围
const selectedCats = ref<string[]>([...ALL_CATS])

// 自动备份设置
const autoEnabled = ref(true)
const autoCats = ref<string[]>([...ALL_CATS])
const autoRetention = ref(7)
// 聊天消息自动备份（默认每 30 天）
const msgEnabled = ref(true)
const msgIntervalDays = ref(30)
const msgRetention = ref(12)

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
  // 兼容 epoch 秒 / 毫秒两种单位
  return dayjs(n <= 9999999999 ? n * 1000 : n).utcOffset(8).format('YYYY-MM-DD HH:mm:ss')
}
function catLabels(cats: any): string {
  if (!Array.isArray(cats) || cats.length === 0) return '—'
  return cats.map((c: string) => CATEGORY_META[c]?.label ?? c).join('·')
}

const cols = [
  { title: '备份文件', key: 'name', ellipsis: { tooltip: true } },
  {
    title: '范围', key: 'categories', width: 220,
    render: (r: any) => h(NSpace, { size: 4 }, {
      default: () => (Array.isArray(r.categories) && r.categories.length
        ? r.categories.map((c: string) => h(NTag, { size: 'tiny', type: c === 'messages' ? 'warning' : 'default', bordered: false },
            { default: () => CATEGORY_META[c]?.label ?? c }))
        : [h(NText, { depth: 3 }, { default: () => '—' })])
    })
  },
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
  if (selectedCats.value.length === 0) {
    message.warning('请至少选择一个备份范围')
    return
  }
  creating.value = true
  try {
    const r = await api.createBackup(selectedCats.value, '')
    message.success('备份完成：' + r.name + '（' + catLabels(selectedCats.value) + '）')
    await load()
  } catch (e: any) {
    message.error('备份失败：' + (e?.message ?? e))
  } finally {
    creating.value = false
  }
}

function download(name: string) {
  httpDownload('/console/backup/download', { name }, name)
}

// 立即备份聊天消息（不受「每 N 天」定时间隔限制）
async function createMessageBackup() {
  msgCreating.value = true
  try {
    const r = await api.createMessageBackup()
    message.success('聊天消息备份完成：' + r.name + '（含群聊 + 单聊）')
    await load()
  } catch (e: any) {
    message.error('备份失败：' + (e?.message ?? e))
  } finally {
    msgCreating.value = false
  }
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

// 设置
async function loadSettings() {
  settingsLoading.value = true
  try {
    const s = await api.getBackupSettings()
    autoEnabled.value = !!s.enabled
    autoCats.value = Array.isArray(s.categories) && s.categories.length ? s.categories : [...ALL_CATS]
    autoRetention.value = Number(s.retention ?? 7)
    msgEnabled.value = !!s.msgEnabled
    msgIntervalDays.value = Number(s.msgIntervalDays ?? 30)
    msgRetention.value = Number(s.msgRetention ?? 12)
  } catch (e: any) {
    message.error('加载设置失败：' + (e?.message ?? e))
  } finally {
    settingsLoading.value = false
  }
}

const savingSettings = ref(false)
async function saveSettings() {
  savingSettings.value = true
  try {
    await api.putBackupSettings({
      enabled: autoEnabled.value,
      categories: autoCats.value,
      retention: autoRetention.value,
      msgEnabled: msgEnabled.value,
      msgIntervalDays: msgIntervalDays.value,
      msgRetention: msgRetention.value
    })
    message.success('自动备份设置已保存')
  } catch (e: any) {
    message.error('保存设置失败：' + (e?.message ?? e))
  } finally {
    savingSettings.value = false
  }
}

onMounted(() => { load(); loadSettings() })
</script>

<template>
  <div>
    <PageHero title="备份恢复" subtitle="H2 在线备份 · 范围可自由勾选 · 恢复前自动快照" :icon="ArchiveOutline" />

    <!-- 立即备份 -->
    <NCard title="立即备份" class="b-card">
      <NSpace vertical :size="14">
        <div>
          <NText style="font-size: 13px; display: block; margin-bottom: 8px">备份范围（默认全选，可自由勾选）：</NText>
          <NCheckboxGroup v-model:value="selectedCats">
            <NSpace :size="[16, 8]">
              <NCheckbox v-for="cat in ALL_CATS" :key="cat" :value="cat">
                <span style="font-weight: 500">{{ CATEGORY_META[cat].label }}</span>
                <NText depth="3" style="font-size: 12px; margin-left: 6px">{{ CATEGORY_META[cat].desc }}</NText>
              </NCheckbox>
            </NSpace>
          </NCheckboxGroup>
        </div>
        <NSpace align="center" :size="12">
          <NButton type="primary" :loading="creating" :disabled="selectedCats.length === 0" @click="create">
            <template #icon><NIcon><CloudUploadOutline /></NIcon></template>
            立即备份
          </NButton>
          <NText depth="3" style="font-size: 12px">备份产物为 zip（含 manifest.json）存于运行目录 backups/ 下</NText>
        </NSpace>
      </NSpace>
    </NCard>

    <!-- 自动备份设置 -->
    <NCard title="自动备份设置" class="b-card">
      <NSpace vertical :size="14">
        <NSpace align="center" :size="10">
          <NSwitch v-model:value="autoEnabled" :loading="settingsLoading" />
          <NText style="font-size: 13px">每日 03:00 自动备份</NText>
        </NSpace>
        <div>
          <NText style="font-size: 13px; display: block; margin-bottom: 8px">自动备份范围：</NText>
          <NCheckboxGroup v-model:value="autoCats">
            <NSpace :size="[16, 8]">
              <NCheckbox v-for="cat in ALL_CATS" :key="cat" :value="cat">
                {{ CATEGORY_META[cat].label }}
              </NCheckbox>
            </NSpace>
          </NCheckboxGroup>
        </div>
        <NSpace align="center" :size="8">
          <NText depth="3" style="font-size: 13px">保留</NText>
          <NInputNumber v-model:value="autoRetention" :min="1" :max="30" size="small" style="width: 90px" />
          <NText depth="3" style="font-size: 13px">份（超出自动删最旧）</NText>
        </NSpace>
      </NSpace>
    </NCard>

    <!-- 聊天消息定时备份 -->
    <NCard title="聊天消息定时备份" class="b-card">
      <template #header-extra>
        <NTag size="small" type="warning" :bordered="false">默认每 30 天</NTag>
      </template>
      <NSpace vertical :size="14">
        <NSpace align="center" :size="10">
          <NSwitch v-model:value="msgEnabled" :loading="settingsLoading" />
          <NText style="font-size: 13px">
            <NIcon size="14" style="vertical-align: -2px; margin-right: 4px"><ChatbubbleEllipsesOutline /></NIcon>
            独立备份聊天消息（群聊 + 单聊）
          </NText>
        </NSpace>
        <NGrid :cols="2" :x-gap="16" :y-gap="10">
          <NGi>
            <NSpace align="center" :size="8">
              <NText depth="3" style="font-size: 13px">每</NText>
              <NInputNumber v-model:value="msgIntervalDays" :min="1" :max="365" size="small" style="width: 100px" />
              <NText depth="3" style="font-size: 13px">天备份一次</NText>
            </NSpace>
          </NGi>
          <NGi>
            <NSpace align="center" :size="8">
              <NText depth="3" style="font-size: 13px">保留</NText>
              <NInputNumber v-model:value="msgRetention" :min="1" :max="100" size="small" style="width: 90px" />
              <NText depth="3" style="font-size: 13px">份</NText>
            </NSpace>
          </NGi>
        </NGrid>
        <NText depth="3" style="font-size: 12px">
          消息备份仅含 qqbot_message 表（与「运行日志」「业务档案」互不重叠，可单独恢复聊天记录而不影响其它数据）。
        </NText>
        <NSpace align="center" :size="10" style="margin-top: 2px">
          <NButton size="small" type="warning" tertiary :loading="msgCreating" @click="createMessageBackup">
            <template #icon><NIcon size="14"><DownloadOutline /></NIcon></template>
            立即备份一次聊天消息
          </NButton>
          <NText depth="3" style="font-size: 12px">立即生成一份 -msg- 备份，不受「每 {{ msgIntervalDays }} 天」定时间隔限制</NText>
        </NSpace>
      </NSpace>
    </NCard>

    <NCard title="保存设置" class="b-card">
      <NButton type="primary" size="small" :loading="savingSettings" @click="saveSettings">保存自动备份设置</NButton>
    </NCard>

    <!-- 备份列表 -->
    <NCard title="备份列表" class="b-card">
      <template #header-extra>
        <NButton secondary size="small" :loading="loading" @click="load">
          <template #icon><NIcon size="14"><RefreshOutline /></NIcon></template>
          刷新
        </NButton>
      </template>
      <EmptyState v-if="!loading && !rows.length" description="暂无备份文件" />
      <NDataTable v-else :columns="cols" :data="rows" :loading="loading" :bordered="false" size="small" :pagination="{ pageSize: 20 }" />
    </NCard>

    <!-- 恢复确认弹窗（双重确认） -->
    <NModal v-model:show="showRestoreModal" preset="card" title="恢复备份" style="width: 520px; max-width: 92vw" :bordered="false">
      <NAlert type="error" :show-icon="true" title="高风险操作" style="margin-bottom: 12px">
        即将用「{{ restoreTarget?.name }}」覆盖当前对应数据库！
      </NAlert>
      <NText tag="div" depth="3" style="font-size: 13px; line-height: 1.8; display: block; margin-bottom: 12px">
        备份范围：<b>{{ catLabels(restoreTarget?.categories) }}</b><br>
        执行流程：<br>
        1. 先自动快照当前库到 <b>backups/pre_restore_时间戳/</b>（可回滚）；<br>
        2. 按清单逐个库 RUNSCRIPT 导入；<br>
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
