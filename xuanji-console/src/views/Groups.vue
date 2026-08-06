<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { NSelect, NButton, NDrawer, NDrawerContent, NAlert, NSpace, NIcon, NText, NEmpty } from 'naive-ui'
import { PeopleOutline } from '@vicons/ionicons5'
import api from '../api'
import DataTable from '../components/DataTable.vue'
import { useFillHeight } from '../composables/useFillHeight'

const { fillHeight } = useFillHeight(16)

const groups = ref<any[]>([])
const err = ref('')
const loading = ref(false)
const botFilter = ref<string | null>(null)
const bots = ref<{ label: string; value: string }[]>([])

async function load() {
  loading.value = true
  try {
    const g: any[] = await api.getGroups()
    groups.value = g
    const set = new Set(g.map((x) => x.BOT_APPID).filter(Boolean))
    bots.value = [...set].map((b) => ({ label: 'Bot #' + b, value: String(b) }))
    err.value = ''
  } catch (e: any) {
    err.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

const filtered = computed(() =>
  botFilter.value ? groups.value.filter((g) => String(g.BOT_APPID) === String(botFilter.value)) : groups.value
)

const members = ref<any[]>([])
const showMembers = ref(false)
const memberGroup = ref('')
async function openMembers(g: any) {
  memberGroup.value = g.GROUP_ID
  try {
    const all: any[] = await api.getGroupMembers(g.GROUP_ID)
    // 同群号可能两个 bot 都在，按所属 bot 过滤
    members.value = all.filter((m) => String(m.BOT_APPID) === String(g.BOT_APPID))
  } catch {
    members.value = []
  }
  showMembers.value = true
}
</script>

<template>
  <div>
    <div class="page-head">
      <div class="page-title">
        <NIcon size="20" color="#2090e0"><PeopleOutline /></NIcon>
        <span>群列表</span>
        <NText depth="3" style="font-size: 13px">共 {{ filtered.length }} 个</NText>
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
    <DataTable
      :rows="filtered"
      :loading="loading"
      :max-height="fillHeight"
      :clickable="true"
      empty-text="还没有群数据，收发群消息后会自动写入"
      @row-click="(r) => openMembers(r)"
    />

    <NDrawer v-model:show="showMembers" :width="760" placement="right">
      <NDrawerContent :title="`群成员 · ${memberGroup}`">
        <DataTable :rows="members" empty-text="暂无成员" />
      </NDrawerContent>
    </NDrawer>
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
