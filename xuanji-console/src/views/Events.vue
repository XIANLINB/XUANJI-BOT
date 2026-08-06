<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { NSelect, NButton, NAlert, NSpace, NIcon, NText } from 'naive-ui'
import { FlashOutline } from '@vicons/ionicons5'
import api from '../api'
import DataTable from '../components/DataTable.vue'
import { useFillHeight } from '../composables/useFillHeight'
import { useBots } from '../composables/useBots'

const { fillHeight } = useFillHeight(16)
const { bots, loadBots } = useBots()

const botFilter = ref<string>('')
const rows = ref<any[]>([])
const err = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const r = await api.getEvents(botFilter.value, 200)
    rows.value = r.rows || []
    err.value = ''
  } catch (e: any) {
    err.value = e.message
    rows.value = []
  } finally {
    loading.value = false
  }
}

const sources = computed(() => {
  const m: Record<string, number> = {}
  rows.value.forEach((r) => {
    const k = r.BOT_APPID ?? '—'
    m[k] = (m[k] || 0) + 1
  })
  return m
})

onMounted(async () => {
  await loadBots()
  await load()
})
</script>

<template>
  <div>
    <div class="page-head">
      <div class="page-title">
        <NIcon size="20" color="#18a058"><FlashOutline /></NIcon>
        <span>系统事件</span>
        <NText depth="3" style="font-size: 13px">共 {{ rows.length }} 条</NText>
      </div>
      <NSpace align="center">
        <NSelect
          v-model:value="botFilter"
          :options="bots"
          placeholder="按 Bot 过滤"
          clearable
          style="width: 200px"
        />
        <NButton type="primary" :loading="loading" @click="load">刷新</NButton>
      </NSpace>
    </div>

    <NAlert v-if="err" type="error" :title="'加载失败'" style="margin-bottom: 16px">{{ err }}</NAlert>
    <DataTable :rows="rows" :loading="loading" :max-height="fillHeight" empty-text="暂无系统事件（加群/退群/加好友等会写入）" />
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}
.page-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}
</style>
