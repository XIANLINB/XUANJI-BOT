<script setup lang="ts">
/**
 * 文件存储页（2026-08 重写）
 * - 预览：图片 NImage 点击放大 / 语音 audio / 视频 video（不支持格式显示提示）
 * - 按类型一键删除（图片/语音/视频/文件）+ 全部删除
 * - 3 张统计卡片对齐（总数 / 总占用 / 配额+进度条）
 * - 类型占用分布 ECharts 环形图（byTypeSize 字节）
 * - 媒体为框架级共享存储（data/xuanji/media，内容哈希去重），不区分机器人
 */
import { ref, computed, onMounted, h } from 'vue'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NDataTable, NSelect, NEmpty,
  NModal, NAlert, NStatistic, NGrid, NGi, NProgress, NPopconfirm, useMessage
} from 'naive-ui'
import {
  FolderOpenOutline, RefreshOutline, TrashOutline, ImageOutline, MicOutline,
  VideocamOutline, DocumentOutline, DownloadOutline, EyeOutline,
  CheckmarkCircleOutline
} from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import CommonChart from '../components/CommonChart.vue'
import api from '../api'
import dayjs from 'dayjs'

const message = useMessage()
const loading = ref(false)
const clearing = ref(false)
const files = ref<any[]>([])
const summary = ref<Record<string, any>>({ total: 0, sizeBytes: 0, quotaBytes: 4 * 1024 * 1024 * 1024, byType: {}, byTypeSize: {} })
const typeFilter = ref<'all' | 'image' | 'voice' | 'video' | 'file'>('all')

const showDelete = ref(false)
const deleting = ref<any>(null)
const deletingName = ref('')
const showClearType = ref('')   // 确认删除的类型
const showClearAll = ref(false)

const TYPE_META: Record<string, { label: string; color: string; icon: any }> = {
  image: { label: '图片', color: '#07c160', icon: ImageOutline },
  voice: { label: '语音', color: '#f0a020', icon: MicOutline },
  video: { label: '视频', color: '#e5484d', icon: VideocamOutline },
  file:  { label: '文件', color: '#888780', icon: DocumentOutline }
}

const filtered = computed(() =>
  typeFilter.value === 'all' ? files.value : files.value.filter((f: any) => f.type === typeFilter.value))

// ============ 配额 ============
const quota = computed(() => Number(summary.value.quotaBytes || 4 * 1024 * 1024 * 1024))
const used = computed(() => Number(summary.value.sizeBytes || 0))
const usedPercent = computed(() => quota.value > 0 ? Math.min(100, Math.round(used.value * 100 / quota.value)) : 0)

// ============ 类型占用分布（ECharts 环形） ============
const typeSizeOption = computed(() => {
  const byTypeSize = summary.value.byTypeSize || {}
  const data = Object.entries(byTypeSize).map(([k, v]) => ({
    name: TYPE_META[k]?.label ?? k,
    value: Number(v),
    itemStyle: { color: TYPE_META[k]?.color ?? '#888780' }
  }))
  return {
    tooltip: { trigger: 'item', formatter: (p: any) => `${p.name}: ${fmtSize(p.value)} (${p.percent}%)` },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['45%', '70%'], center: ['50%', '44%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{d}%', fontSize: 11 },
      data
    }]
  }
})

// ============ 工具 ============
function fmtSize(n: number): string {
  if (!n || n <= 0) return '0 B'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(2) + ' MB'
  return (n / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}
function fmtTime(t: number): string {
  return t > 0 ? dayjs(t * 1000).format('YYYY-MM-DD HH:mm') : '—'
}

const typeOptions = [
  { label: '全部类型', value: 'all' },
  { label: '图片', value: 'image' },
  { label: '语音', value: 'voice' },
  { label: '视频', value: 'video' },
  { label: '文件', value: 'file' }
]

async function load() {
  loading.value = true
  try {
    const r = await api.getFiles()
    files.value = r?.files ?? []
    summary.value = {
      total: r?.total ?? 0,
      sizeBytes: r?.sizeBytes ?? 0,
      quotaBytes: r?.quotaBytes ?? 4 * 1024 * 1024 * 1024,
      byType: r?.byType ?? {},
      byTypeSize: r?.byTypeSize ?? {}
    }
  } catch (e: any) {
    message.error('加载失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

// ============ 预览 ============
const preview = ref<any>(null)  // { type, url, name }
const showPreview = ref(false)

function openPreview(row: any) {
  if (!row.url) return
  preview.value = row
  showPreview.value = true
}

// ============ 删除 ============
function askDelete(row: any) {
  deleting.value = row
  deletingName.value = row.path
  showDelete.value = true
}

async function confirmDelete() {
  if (!deleting.value) return
  try {
    const r = await api.deleteFile(deleting.value.path)
    showDelete.value = false
    if (r.status === 'ok') message.success('已删除')
    else message.error(r.msg || '删除失败')
    await load()
  } catch (e: any) {
    message.error('删除失败：' + (e?.message ?? e))
  }
}

async function confirmClearType() {
  clearing.value = true
  try {
    const r: any = await api.clearFiles(showClearType.value || undefined)
    showClearType.value = ''
    message.success(r.msg || '已清理')
    await load()
  } catch (e: any) {
    message.error('清理失败：' + (e?.message ?? e))
  } finally {
    clearing.value = false
  }
}

async function confirmClearAll() {
  clearing.value = true
  try {
    const r: any = await api.clearFiles()
    showClearAll.value = false
    message.success(r.msg || '已清理')
    await load()
  } catch (e: any) {
    message.error('清理失败：' + (e?.message ?? e))
  } finally {
    clearing.value = false
  }
}

// ============ 表格列 ============
const columns = [
  {
    title: '类型', key: 'type', width: 80,
    render: (r: any) => h(NTag, { size: 'small', bordered: false, type: 'default' }, {
      default: () => TYPE_META[r.type]?.label ?? r.type
    })
  },
  {
    title: '文件', key: 'path', minWidth: 260, ellipsis: { tooltip: true },
    render: (r: any) => h('span', { style: 'font-family:Consolas,monospace;font-size:12px' }, { default: () => r.path })
  },
  { title: '大小', key: 'size', width: 100, render: (r: any) => fmtSize(r.size) },
  { title: '修改时间', key: 'mtime', width: 140, render: (r: any) => fmtTime(r.mtime) },
  {
    title: '预览', key: 'preview', width: 90,
    render: (r: any) => h(NButton, {
      size: 'tiny', secondary: true, disabled: !r.url, onClick: () => openPreview(r)
    }, { default: () => h(NSpace, { size: 4, align: 'center' }, { default: () => [h(NIcon, { size: 14 }, { default: () => h(EyeOutline) }), '查看'] }) })
  },
  {
    title: '操作', key: 'op', width: 90,
    render: (r: any) => h(NButton, { size: 'tiny', type: 'error', ghost: true, onClick: () => askDelete(r) }, { default: () => '删除' })
  }
]

onMounted(load)
</script>

<template>
  <div>
    <PageHero title="文件存储" subtitle="媒体本地存储 · 在线预览 · 按类型清理（磁盘管理闭环）" :icon="FolderOpenOutline">
      <NSelect v-model:value="typeFilter" :options="typeOptions" size="small" style="width: 130px" />
      <NButton secondary :loading="loading" @click="load">
        <template #icon><NIcon><RefreshOutline /></NIcon></template>
        刷新
      </NButton>
    </PageHero>

    <NAlert type="info" :show-icon="true" style="margin-bottom: 14px">
      媒体为<b>框架级共享存储</b>（<span style="font-family: monospace">data/xuanji/media</span>，内容哈希去重），不区分机器人；
      图片/视频可直接预览，语音若浏览器不支持该格式（如 silk/amr）会显示提示，可下载查看。
    </NAlert>

    <!-- ═══ 3 张统计卡片（统一高度） ═══ -->
    <NGrid :cols="3" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 14px">
      <NGi span="3 m:1">
        <NCard class="stat-card" hoverable>
          <div class="stat-row">
            <NIcon size="22" color="#5b5bd6"><FolderOpenOutline /></NIcon>
            <div class="stat-meta">
              <div class="stat-label">文件总数</div>
              <NStatistic :value="summary.total" style="--n-value-font-size: 22px" />
            </div>
          </div>
        </NCard>
      </NGi>
      <NGi span="3 m:1">
        <NCard class="stat-card" hoverable>
          <div class="stat-row">
            <NIcon size="22" color="#fa8c16"><DocumentOutline /></NIcon>
            <div class="stat-meta">
              <div class="stat-label">总占用</div>
              <NStatistic :value="fmtSize(used)" style="--n-value-font-size: 22px" />
            </div>
          </div>
        </NCard>
      </NGi>
      <NGi span="3 m:1">
        <NCard class="stat-card" hoverable>
          <div class="stat-row">
            <NIcon size="22" color="#1E88E5"><TrashOutline /></NIcon>
            <div class="stat-meta">
              <div class="stat-label">配额</div>
              <div style="display:flex;align-items:baseline;gap:8px">
                <NText style="font-size:22px;font-weight:600">{{ fmtSize(quota) }}</NText>
                <NText depth="3" style="font-size:12px">已用 {{ usedPercent }}%</NText>
              </div>
            </div>
          </div>
          <NProgress
            :percentage="usedPercent"
            :color="usedPercent > 90 ? '#e5484d' : '#1E88E5'"
            :height="6"
            :border-radius="3"
            style="margin-top: 8px"
          />
          <div class="stat-sub">超限自动删最旧（media.storage.max_bytes 可配）</div>
        </NCard>
      </NGi>
    </NGrid>

    <!-- ═══ 类型占用分布 + 一键清理 ═══ -->
    <NGrid :cols="24" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 14px">
      <NGi span="24 m:9">
        <NCard :bordered="true">
          <template #header><span style="font-weight:600">类型占用分布</span></template>
          <template #header-extra>
            <NText depth="3" style="font-size:11.5px">各类型文件占用磁盘字节</NText>
          </template>
          <CommonChart v-if="Object.keys(summary.byTypeSize || {}).length" :option="typeSizeOption" height="240px" />
          <NEmpty v-else description="暂无文件" style="padding: 40px 0" />
        </NCard>
      </NGi>
      <NGi span="24 m:15">
        <NCard :bordered="true">
          <template #header><span style="font-weight:600">按类型清理</span></template>
          <template #header-extra>
            <NPopconfirm @positive-click="confirmClearAll">
              <template #trigger>
                <NButton size="small" type="error" tertiary :disabled="!summary.total" :loading="clearing">
                  <template #icon><NIcon><TrashOutline /></NIcon></template>
                  一键清理全部
                </NButton>
              </template>
              将删除全部 {{ summary.total }} 个媒体文件，不可恢复。确认？
            </NPopconfirm>
          </template>
          <div class="type-clear-grid">
            <div v-for="(meta, k) in TYPE_META" :key="k" class="type-clear-item">
              <div class="type-clear-icon" :style="{ background: meta.color + '15', color: meta.color }">
                <NIcon size="26"><component :is="meta.icon" /></NIcon>
              </div>
              <div class="type-clear-info">
                <div class="type-clear-name">{{ meta.label }}</div>
                <div class="type-clear-cnt">
                  {{ Number((summary.byType || {})[k] || 0) }} 个 · {{ fmtSize(Number((summary.byTypeSize || {})[k] || 0)) }}
                </div>
              </div>
              <NPopconfirm @positive-click="confirmClearType">
                <template #trigger>
                  <NButton size="tiny" type="error" ghost :disabled="!Number((summary.byType || {})[k] || 0)">
                    清理{{ meta.label }}
                  </NButton>
                </template>
                将删除全部 {{ meta.label }} 文件（{{ Number((summary.byType || {})[k] || 0) }} 个），不可恢复。确认？
              </NPopconfirm>
            </div>
          </div>
        </NCard>
      </NGi>
    </NGrid>

    <!-- ═══ 文件列表 ═══ -->
    <NCard :bordered="true">
      <template #header>
        <NSpace align="center" :size="8">
          <span style="font-weight:600">文件列表</span>
          <NTag v-if="typeFilter !== 'all'" :bordered="false" size="small" type="info">
            {{ TYPE_META[typeFilter]?.label ?? typeFilter }} · {{ filtered.length }} 个
          </NTag>
        </NSpace>
      </template>
      <NDataTable :columns="columns" :data="filtered" :bordered="false" size="small" :loading="loading" :row-key="(r: any) => r.path" />
      <NEmpty v-if="!loading && !filtered.length" description="暂无媒体文件" style="padding: 30px 0" />
    </NCard>

    <!-- ═══ 预览弹窗 ═══ -->
    <NModal
      v-model:show="showPreview"
      preset="card"
      :title="preview ? (TYPE_META[preview.type]?.label + ' · ' + preview.path) : ''"
      style="width: 640px; max-width: 94vw"
      :bordered="false"
    >
      <div v-if="preview" class="preview-body">
        <template v-if="preview.type === 'image'">
          <img :src="preview.url" class="preview-img" :alt="preview.path" />
        </template>
        <template v-else-if="preview.type === 'voice'">
          <div class="voice-preview">
            <NIcon size="48" color="#f0a020"><MicOutline /></NIcon>
            <NText style="font-size: 14px; margin-bottom: 8px">{{ preview.path }}</NText>
            <audio :src="preview.url" controls style="width: 100%; max-width: 420px">
              你的浏览器不支持 audio 播放
            </audio>
            <NText depth="3" style="font-size: 11.5px; margin-top: 8px">
              若无法播放，可能是 silk/amr 等 QQ 语音格式，浏览器不支持，可下载后用本地播放器打开
            </NText>
          </div>
        </template>
        <template v-else-if="preview.type === 'video'">
          <video :src="preview.url" controls class="preview-video">
            你的浏览器不支持 video 播放
          </video>
        </template>
        <template v-else>
          <div class="voice-preview">
            <NIcon size="48" color="#888780"><DocumentOutline /></NIcon>
            <NText style="font-size: 14px">{{ preview.path }}</NText>
            <NText depth="3" style="font-size: 12px">{{ fmtSize(preview.size) }} · 该类型不支持内嵌预览</NText>
          </div>
        </template>
        <div style="display:flex;justify-content:center;margin-top:14px">
          <NButton size="small" tag="a" :href="preview.url" target="_blank" download>
            <template #icon><NIcon><DownloadOutline /></NIcon></template>
            下载文件
          </NButton>
        </div>
      </div>
    </NModal>

    <!-- ═══ 单文件删除确认 ═══ -->
    <NModal v-model:show="showDelete" preset="card" :title="`删除文件 · ${deletingName}`" style="width: 460px; max-width: 92vw" :bordered="false">
      <NAlert type="warning" :show-icon="true" style="margin-bottom: 12px">此操作不可恢复</NAlert>
      <NText depth="2" style="font-size: 13px; line-height: 1.7">
        将永久删除媒体目录下的文件：<br />
        <span style="font-family: Consolas, monospace; font-size: 12px">{{ deletingName }}</span>
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

<style scoped>
.stat-card {
  height: 100%;
  min-height: 104px;
}
.stat-card :deep(.n-statistic .n-statistic__label) {
  font-size: 12.5px !important;
  color: #6b7280 !important;
  margin-bottom: 2px !important;
}
.stat-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.stat-meta {
  flex: 1;
  min-width: 0;
}
.stat-label {
  font-size: 12.5px;
  color: #6b7280;
  margin-bottom: 4px;
}
.stat-sub {
  font-size: 11px;
  color: #9aa0a6;
  margin-top: 6px;
}

.type-clear-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.type-clear-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #EEF0F3;
  border-radius: 8px;
  background: #fff;
}
.type-clear-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.type-clear-info {
  flex: 1;
  min-width: 0;
}
.type-clear-name {
  font-size: 13.5px;
  font-weight: 600;
  color: #1f2329;
}
.type-clear-cnt {
  font-size: 11.5px;
  color: #6b7280;
  margin-top: 2px;
}

.preview-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.preview-img {
  display: block;
  max-width: 100%;
  max-height: 480px;
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 6px;
  background: #fafbfc;
}
.preview-video {
  display: block;
  width: 100%;
  max-height: 460px;
  border-radius: 8px;
  background: #000;
}
.voice-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 24px 0;
  width: 100%;
}
</style>