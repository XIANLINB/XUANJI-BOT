<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { NTabs, NTabPane, NSelect, NButton, NAlert, NSpace, NIcon, NText, NTag } from 'naive-ui'
import { ChatbubbleOutline } from '@vicons/ionicons5'
import api from '../api'
import DataTable from '../components/DataTable.vue'
import { useFillHeight } from '../composables/useFillHeight'
import { useBots } from '../composables/useBots'

const { fillHeight } = useFillHeight(16)
const { bots, loadBots } = useBots()

const tab = ref<'group' | 'c2c'>('group')
const botFilter = ref<string>('')
const rows = ref<any[]>([])
const err = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const r = await api.getMessages(tab.value, botFilter.value, 0, 200)
    rows.value = r.rows || []
    err.value = ''
  } catch (e: any) {
    err.value = e.message
    rows.value = []
  } finally {
    loading.value = false
  }
}

const counts = computed(() => ({
  in: rows.value.filter((r) => r.DIRECTION === 'IN').length,
  out: rows.value.filter((r) => r.DIRECTION === 'OUT').length
}))

onMounted(async () => {
  await loadBots()
  await load()
})
watch(tab, load)
watch(botFilter, load)
</script>

<template>
  <div>
    <div class="page-head">
      <div class="page-title">
        <NIcon size="20" color="#5b5bd6"><ChatbubbleOutline /></NIcon>
        <span>消息监控</span>
        <NText depth="3" style="font-size: 13px">共 {{ rows.length }} 条</NText>
        <NTag v-if="rows.length" :bordered="false" size="small" type="warning">收 {{ counts.in }}</NTag>
        <NTag v-if="rows.length" :bordered="false" size="small" type="success">发 {{ counts.out }}</NTag>
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
    <NTabs v-model:value="tab" type="segment">
      <NTabPane name="group" tab="群消息">
        <DataTable v-if="tab === 'group'" :rows="rows" :loading="loading" :max-height="fillHeight" empty-text="暂无群消息" />
      </NTabPane>
      <NTabPane name="c2c" tab="单聊消息">
        <DataTable v-if="tab === 'c2c'" :rows="rows" :loading="loading" :max-height="fillHeight" empty-text="暂无单聊消息" />
      </NTabPane>
    </NTabs>
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
