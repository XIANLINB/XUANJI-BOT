<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { NSelect, NButton, NDrawer, NDrawerContent, NAlert, NSpace, NIcon, NText } from 'naive-ui'
import { PersonOutline } from '@vicons/ionicons5'
import api from '../api'
import DataTable from '../components/DataTable.vue'
import { useFillHeight } from '../composables/useFillHeight'

const { fillHeight } = useFillHeight(16)

const friends = ref<any[]>([])
const err = ref('')
const loading = ref(false)
const botFilter = ref<string | null>(null)
const bots = ref<{ label: string; value: string }[]>([])

async function load() {
  loading.value = true
  try {
    const f: any[] = await api.getFriends()
    friends.value = f
    const set = new Set(f.map((x) => x.BOT_APPID).filter(Boolean))
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
  botFilter.value ? friends.value.filter((f) => String(f.BOT_APPID) === String(botFilter.value)) : friends.value
)

const msgs = ref<any[]>([])
const showMsgs = ref(false)
const targetName = ref('')
async function openMsgs(f: any) {
  targetName.value = f.PLATFORM_USER_ID
  try {
    const all: any[] = await api.getContactMessages('c2c', f.PLATFORM_USER_ID)
    // 同一用户可能和两个 bot 都聊过，按所属 bot 过滤
    msgs.value = all.filter((m) => String(m.BOT_APPID) === String(f.BOT_APPID))
  } catch {
    msgs.value = []
  }
  showMsgs.value = true
}
</script>

<template>
  <div>
    <div class="page-head">
      <div class="page-title">
        <NIcon size="20" color="#f0a020"><PersonOutline /></NIcon>
        <span>好友列表</span>
        <NText depth="3" style="font-size: 13px">共 {{ filtered.length }} 人</NText>
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
      empty-text="还没有单聊好友，发过消息后会自动写入"
      @row-click="(r) => openMsgs(r)"
    />

    <NDrawer v-model:show="showMsgs" :width="760" placement="right">
      <NDrawerContent :title="`单聊记录 · ${targetName}`">
        <DataTable :rows="msgs" empty-text="暂无消息" />
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
