<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { NButton, NCard, NDescriptions, NDescriptionsItem, NTag, NText, useMessage } from 'naive-ui'
import { GitNetworkOutline } from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import api from '../api'

const message = useMessage()
const loading = ref(false)
const data = ref<any>(null)

const accessUrl = computed(() => {
  if (!data.value?.reverse?.enabled) return ''
  return `ws://${location.host}${data.value.reverse.path || '/onebot/ws'}`
})

async function load() {
  loading.value = true
  try {
    data.value = await api.getStatus()
  } catch (e: any) {
    message.error('获取 OneBot 状态失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制')
  } catch {
    message.warning('复制失败，请手动复制')
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero title="OneBot" subtitle="OneBot v11 适配器状态与接入地址（Napcat / Lagrange / go-cqhttp）" :icon="GitNetworkOutline">
      <NButton :loading="loading" @click="load">刷新</NButton>
    </PageHero>

    <NCard :bordered="false" class="card" :loading="loading">
      <template v-if="data">
        <NDescriptions label-placement="left" :column="1" bordered>
          <NDescriptionsItem label="适配器">
            <NTag :type="data.enabled ? 'success' : 'default'" :bordered="false" size="small">
              {{ data.enabled ? '已启用' : '未启用' }}
            </NTag>
          </NDescriptionsItem>
          <NDescriptionsItem label="反向 WS">
            <NTag :type="data.reverse?.enabled ? 'success' : 'default'" :bordered="false" size="small">
              {{ data.reverse?.enabled ? '开启' : '关闭' }}
            </NTag>
            <span v-if="data.reverse?.enabled" style="margin-left: 8px; font-size: 12px; color: #888">
              路径 {{ data.reverse?.path }} · Token {{ data.reverse?.accessTokenSet ? '已设置' : '未设置' }}
            </span>
          </NDescriptionsItem>
          <NDescriptionsItem v-if="accessUrl" label="接入地址">
            <NText code style="font-size: 12px">{{ accessUrl }}</NText>
            <NButton text size="tiny" style="margin-left: 8px" @click="copyText(accessUrl)">复制</NButton>
          </NDescriptionsItem>
          <NDescriptionsItem label="正向 WS">
            <NTag :type="data.forward?.enabled ? 'success' : 'default'" :bordered="false" size="small">
              {{ data.forward?.enabled ? '开启' : '关闭' }}
            </NTag>
            <span v-if="data.forward?.enabled" style="margin-left: 8px; font-size: 12px; color: #888">
              {{ data.forward?.url }}
            </span>
          </NDescriptionsItem>
          <NDescriptionsItem label="在线会话">
            <NTag type="info" :bordered="false" size="small">{{ data.onlineCount ?? 0 }} 个</NTag>
          </NDescriptionsItem>
        </NDescriptions>

        <template v-if="data.sessions?.length">
          <NText strong style="display: block; margin: 16px 0 8px">会话列表</NText>
          <div class="session-row" v-for="(s, i) in data.sessions" :key="i">
            <NTag size="small" :type="s.open ? 'success' : 'default'" :bordered="false">
              {{ s.open ? '在线' : '离线' }}
            </NTag>
            <span style="font-family: monospace; font-size: 12px">{{ s.selfId }}</span>
            <span style="font-size: 12px; color: #888">{{ s.direction }}</span>
          </div>
        </template>
      </template>
      <NText v-else depth="3">未获取到状态（OneBot 适配器可能未启用，或应用未启动）</NText>
    </NCard>
  </div>
</template>

<style scoped>
.card {
  border-radius: 14px;
}
.session-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.06);
}
</style>
