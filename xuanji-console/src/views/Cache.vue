<script setup lang="ts">
/**
 * 缓存清理页（2026-08 重写）
 * 支持多选：每项显示 名称 + 数据源 + 行数 + 安全等级 + 说明
 * 一键清理所选项，每项单独 try-catch（失败不影响其他项）
 */
import { ref, computed, onMounted, watch, h } from 'vue'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NAlert, NModal, NCheckbox,
  NEmpty, NSpin, NSelect, useMessage
} from 'naive-ui'
import {
  ServerOutline, PeopleOutline, DocumentTextOutline, ImageOutline,
  TrashOutline, RefreshOutline, AlertCircleOutline
} from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import api from '../api'
import { useBotsStore } from '../stores/bots'

const message = useMessage()
const botsStore = useBotsStore()
const loading = ref(false)
const clearing = ref(false)
const cache = ref<Record<string, any>>({})
const selected = ref<string[]>([])
const showConfirm = ref(false)
const botKey = ref('')  // 空 = 全部机器人

/** 机器人筛选选项（机器人级缓存项受 botKey 影响） */
const botOptions = computed(() => {
  const opts: { label: string; value: string }[] = [{ label: '全部机器人', value: '' }]
  for (const b of botsStore.bots) {
    opts.push({
      label: b.name ? `${b.name} (${b.appId})` : `Bot #${b.appId}`,
      value: String(b.appId)
    })
  }
  return opts
})

async function load() {
  loading.value = true
  try {
    cache.value = await api.getCache(botKey.value)
  } catch (e: any) {
    message.error('加载失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

// 缓存项定义（与后端 DataCenterController cache() 返回的 keys 一致）
const items = computed(() => {
  const list = [
    { key: 'dedup', icon: ServerOutline, iconColor: '#5b5bd6' },
    { key: 'sessions', icon: PeopleOutline, iconColor: '#18a058' },
    { key: 'frameworkLog', icon: DocumentTextOutline, iconColor: '#f0a020' },
    { key: 'auditLog', icon: DocumentTextOutline, iconColor: '#722ed1' },
    { key: 'eventLog', icon: AlertCircleOutline, iconColor: '#13c2c2' },
    { key: 'schedulerLog', icon: DocumentTextOutline, iconColor: '#5b8def' },
    { key: 'blacklistLog', icon: AlertCircleOutline, iconColor: '#9aa0a6' },
    { key: 'alertRecord', icon: AlertCircleOutline, iconColor: '#e5484d' },
    { key: 'messages', icon: ServerOutline, iconColor: '#5b5bd6' },
    { key: 'mediaFiles', icon: ImageOutline, iconColor: '#fa8c16' }
  ]
  return list.map(it => ({
    ...it,
    ...(cache.value[it.key] || { name: it.key, source: '', level: 'safe', scope: 'framework', desc: '', rows: 0 })
  }))
})

const totalRows = computed(() => items.value.reduce((s, it) => s + Number(it.rows || 0), 0))
const selectedItems = computed(() => items.value.filter(it => selected.value.includes(it.key)))

const levelMeta: Record<string, { label: string; type: 'success' | 'warning' | 'error' }> = {
  safe:    { label: '可一键清', type: 'success' },
  lossy:   { label: '可重建', type: 'warning' },
  caution: { label: '业务数据', type: 'error' }
}

/** scope 标记：framework=框架级 / bot=机器人级 */
const scopeMeta: Record<string, { label: string; type: 'info' | 'warning' }> = {
  framework: { label: '框架级', type: 'info' },
  bot:       { label: '机器人级', type: 'warning' }
}

// 全选/反选
const allKeys = computed(() => items.value.map(it => it.key))
const allSelected = computed(() => allKeys.value.length > 0 && selected.value.length === allKeys.value.length)
const someSelected = computed(() => selected.value.length > 0 && selected.value.length < allKeys.value.length)
function toggleAll() {
  selected.value = allSelected.value ? [] : [...allKeys.value]
}
function clearSelection() {
  selected.value = []
}

async function confirmClear() {
  clearing.value = true
  try {
    const r: any = await api.clearCache(selected.value, botKey.value)
    showConfirm.value = false
    if (r.status === 'ok') {
      message.success(r.msg || '已清理')
      selected.value = []
    } else {
      message.error(r.msg || '清理失败')
    }
    if (r.errors && Object.keys(r.errors).length > 0) {
      message.warning('部分失败：' + JSON.stringify(r.errors))
    }
    await load()
  } catch (e: any) {
    message.error('清理失败：' + (e?.message ?? e))
  } finally {
    clearing.value = false
  }
}

watch(cache, () => {
  // 数据刷新时清理已不存在的选中项
  selected.value = selected.value.filter(k => items.value.find(it => it.key === k))
})

watch(botKey, load)
onMounted(() => {
  botsStore.loadBots()
  load()
})
</script>

<template>
  <div>
    <PageHero title="缓存清理" subtitle="勾选要清理的项 · 一键清理所选 · 各类别独立隔离" :icon="TrashOutline">
      <NTag :bordered="false" type="info">共 {{ items.length }} 项可清理 · {{ totalRows }} 条记录</NTag>
      <NSelect
        v-model:value="botKey"
        :options="botOptions"
        size="small"
        placeholder="选择机器人（机器人级项生效）"
        style="width: 220px"
      />
      <NButton secondary :loading="loading" @click="load">
        <template #icon><NIcon><RefreshOutline /></NIcon></template>
        刷新
      </NButton>
    </PageHero>

    <NAlert type="warning" :show-icon="true" style="margin-bottom: 14px">
      <b>清理说明</b>：安全（绿色）项可放心清理；可重建（橙色）项清理后系统会自动重建；业务数据（红色）项清理后不可恢复，请谨慎选择。
      <b>框架级</b>（蓝色标签）为跨所有机器人的共享数据；<b>机器人级</b>（橙色标签）为每个机器人独立的数据——选定机器人后，
      机器人级项只统计/清理该机器人的数据。媒体文件为框架级共享存储（内容哈希去重），不区分机器人。
      清理时每项独立执行，单项失败不影响其他项。
    </NAlert>

    <NCard :bordered="true">
      <template #header>
        <NSpace align="center" :size="10">
          <NCheckbox
            :checked="allSelected"
            :indeterminate="someSelected"
            @update:checked="toggleAll"
          >
            <b>全选 / 反选</b>
          </NCheckbox>
          <NText v-if="selected.length" depth="3" style="font-size: 12px">
            已选 {{ selected.length }} 项 · 共 {{ selectedItems.reduce((s, it) => s + Number(it.rows || 0), 0) }} 条记录
          </NText>
        </NSpace>
      </template>
      <template #header-extra>
        <NSpace :size="8">
          <NButton v-if="selected.length" size="small" @click="clearSelection">清空选择</NButton>
          <NButton
            type="error"
            :disabled="!selected.length"
            :loading="clearing"
            @click="showConfirm = true"
          >
            <template #icon><NIcon><TrashOutline /></NIcon></template>
            一键清理所选 ({{ selected.length }})
          </NButton>
        </NSpace>
      </template>

      <NSpin :show="loading">
        <div class="item-list">
          <div
            v-for="it in items"
            :key="it.key"
            class="item-card"
            :class="{ selected: selected.includes(it.key), caution: it.level === 'caution', lossy: it.level === 'lossy' }"
          >
            <NCheckbox
              :checked="selected.includes(it.key)"
              @update:checked="(v: boolean) => selected = v ? [...selected, it.key] : selected.filter(k => k !== it.key)"
            />
            <div class="item-icon" :style="{ background: it.iconColor + '15', color: it.iconColor }">
              <NIcon size="22"><component :is="it.icon" /></NIcon>
            </div>
            <div class="item-main">
              <div class="item-line1">
                <span class="item-name">{{ it.name }}</span>
                <NTag :bordered="false" :type="scopeMeta[it.scope]?.type ?? 'default'" size="small">
                  {{ scopeMeta[it.scope]?.label ?? it.scope }}
                </NTag>
                <NTag :bordered="false" :type="levelMeta[it.level]?.type ?? 'default'" size="small">
                  {{ levelMeta[it.level]?.label ?? it.level }}
                </NTag>
                <span class="item-rows">{{ Number(it.rows || 0).toLocaleString() }} 条</span>
              </div>
              <div class="item-source">{{ it.source }}</div>
              <div class="item-desc">{{ it.desc }}</div>
            </div>
          </div>
        </div>
        <NEmpty v-if="!items.length" description="暂无缓存项" style="padding: 40px 0" />
      </NSpin>
    </NCard>

    <NModal v-model:show="showConfirm" preset="card" title="确认清理所选缓存" style="width: 520px; max-width: 92vw" :bordered="false">
      <NAlert type="warning" :show-icon="true" style="margin-bottom: 12px">
        此操作将清空以下 {{ selectedItems.length }} 项缓存
      </NAlert>
      <div class="confirm-list">
        <div v-for="it in selectedItems" :key="it.key" class="confirm-item">
          <NIcon size="14" :color="it.iconColor"><component :is="it.icon" /></NIcon>
          <span><b>{{ it.name }}</b></span>
          <span class="confirm-rows">{{ Number(it.rows || 0).toLocaleString() }} 条</span>
          <NTag :bordered="false" :type="levelMeta[it.level]?.type ?? 'default'" size="tiny">
            {{ levelMeta[it.level]?.label ?? it.level }}
          </NTag>
        </div>
      </div>
      <template #footer>
        <NSpace justify="end">
          <NButton size="small" @click="showConfirm = false">取消</NButton>
          <NButton size="small" type="error" :loading="clearing" @click="confirmClear">确认清理</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.item-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.item-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #EEF0F3;
  border-radius: 8px;
  background: #fff;
  transition: all 0.15s;
  cursor: pointer;
}
.item-card:hover {
  border-color: #1E88E5;
  background: #f7faff;
}
.item-card.selected {
  border-color: #1E88E5;
  background: #e8f3fd;
}
.item-card.caution:not(.selected) {
  border-color: #ffd6d6;
  background: #fff8f8;
}
.item-card.lossy:not(.selected) {
  border-color: #ffe7c2;
  background: #fffaf0;
}
.item-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.item-main {
  flex: 1;
  min-width: 0;
}
.item-line1 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}
.item-name {
  font-size: 13.5px;
  font-weight: 600;
  color: #1f2329;
}
.item-rows {
  font-size: 12px;
  color: #6b7280;
  font-weight: 500;
}
.item-source {
  font-size: 11.5px;
  color: #9aa0a6;
  margin-bottom: 4px;
}
.item-desc {
  font-size: 12px;
  color: #4b5563;
  line-height: 1.5;
}

.confirm-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.confirm-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: #f9fafb;
  border-radius: 6px;
  font-size: 13px;
}
.confirm-rows {
  color: #6b7280;
  font-size: 12px;
  margin-left: auto;
}
</style>