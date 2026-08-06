<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NButton, NCard, NDataTable, NInput, NForm, NFormItem, NSelect, NSpace, NTag, NText, useMessage
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { ShieldCheckmarkOutline } from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import api from '../api'

const message = useMessage()
const bots = ref<any[]>([])
const botKey = ref('')
const ownerOpenid = ref('')
const currentOwner = ref('—')

// 黑名单
const groupId = ref('')
const userId = ref('')
const reason = ref('')
const blacklist = ref<any[]>([])
const loading = ref(false)

async function loadBots() {
  try {
    const list: any[] = await api.getBots()
    bots.value = list
    if (!botKey.value && list.length > 0) {
      botKey.value = list[0].botKey || list[0].appId
      await switchBot()
    }
  } catch (e: any) {
    message.error('加载机器人失败：' + (e?.message ?? e))
  }
}

async function switchBot() {
  if (!botKey.value) return
  loading.value = true
  try {
    const [owner, bl] = await Promise.all([
      api.getOwner(botKey.value),
      api.listBlacklist(botKey.value)
    ])
    currentOwner.value = owner?.ownerOpenid || owner?.owner || '—'
    blacklist.value = bl || []
  } catch (e: any) {
    message.error('加载权限失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

async function saveOwner() {
  if (!botKey.value || !ownerOpenid.value.trim()) {
    message.warning('请填写主人 memberOpenid')
    return
  }
  try {
    const r = await api.setOwner(botKey.value, ownerOpenid.value.trim())
    if (r.error) message.error(r.error)
    else message.success('主人已设置')
  } catch (e: any) {
    message.error('设置失败：' + (e?.message ?? e))
  } finally {
    ownerOpenid.value = ''
    switchBot()
  }
}

async function clearOwner() {
  try {
    await api.clearOwner(botKey.value)
    message.success('主人已清除')
  } catch (e: any) {
    message.error('清除失败：' + (e?.message ?? e))
  } finally {
    switchBot()
  }
}

async function addBlack() {
  if (!botKey.value || !userId.value.trim()) {
    message.warning('请填写成员 ID（memberOpenid）')
    return
  }
  try {
    const r = await api.addBlacklist(botKey.value, groupId.value.trim(), userId.value.trim(), reason.value)
    if (r.error) message.error(r.error)
    else message.success('已拉黑')
  } catch (e: any) {
    message.error('拉黑失败：' + (e?.message ?? e))
  } finally {
    groupId.value = ''
    userId.value = ''
    reason.value = ''
    switchBot()
  }
}

async function removeBlack(row: any) {
  try {
    if (row.id != null) {
      // 按 id 删最稳：空群黑名单（group_id=''）用 URL 参数会匹配不到
      await api.removeBlacklistById(row.id)
    } else {
      await api.removeBlacklist(botKey.value, row.groupId || '', row.userId || row.userOpenid || '')
    }
    message.success('已移出黑名单')
  } catch (e: any) {
    message.error('移除失败：' + (e?.message ?? e))
  } finally {
    switchBot()
  }
}

const blackColumns: DataTableColumns<any> = [
  { title: '群 ID', key: 'groupId', width: 180 },
  { title: '成员 ID', key: 'userId', width: 220 },
  { title: '原因', key: 'reason', ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'op',
    width: 110,
    render: (r: any) =>
      h(
        NButton,
        { size: 'small', type: 'error', onClick: () => removeBlack(r) },
        { default: () => '移出' }
      )
  }
]

onMounted(loadBots)
</script>

<template>
  <div>
    <PageHero title="权限管理" subtitle="机器人主人与黑名单（等级制：NONE &lt; BLACKLIST &lt; MEMBER &lt; ADMIN &lt; GROUP_OWNER &lt; BOT_MASTER）" :icon="ShieldCheckmarkOutline">
      <NSelect
        v-model:value="botKey"
        :options="bots.map((b) => ({ label: (b.botKey || b.appId) + (b.name ? ` (${b.name})` : ''), value: b.botKey || b.appId }))"
        placeholder="选择机器人"
        style="width: 260px"
        @update:value="switchBot"
      />
    </PageHero>

    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 14px">
      <NCard title="机器人主人" :bordered="false" class="card">
        <NText depth="3" style="display: block; margin-bottom: 10px">
          当前主人：<NTag size="small" :bordered="false" type="info">{{ currentOwner }}</NTag>
          （拥有 BOT_MASTER 最高权限，每机器人唯一）
        </NText>
        <NForm>
          <NFormItem label="主人 memberOpenid">
            <NInput v-model:value="ownerOpenid" placeholder="机器人主人的 memberOpenid（群主/管理员 openid）" clearable />
          </NFormItem>
        </NForm>
        <NSpace>
          <NButton type="primary" @click="saveOwner">设置主人</NButton>
          <NButton v-if="currentOwner !== '—'" @click="clearOwner">清除主人</NButton>
        </NSpace>
      </NCard>

      <NCard title="黑名单" :bordered="false" class="card">
        <NForm>
          <NFormItem label="群 ID">
            <NInput v-model:value="groupId" placeholder="留空 = 全部群" clearable />
          </NFormItem>
          <NFormItem label="成员 memberOpenid">
            <NInput v-model:value="userId" placeholder="要拉黑的成员 memberOpenid" clearable />
          </NFormItem>
          <NFormItem label="原因">
            <NInput v-model:value="reason" placeholder="可选" clearable />
          </NFormItem>
        </NForm>
        <NButton type="error" :disabled="!userId.trim()" @click="addBlack">加入黑名单</NButton>
      </NCard>
    </div>

    <NCard :bordered="false" class="card" style="margin-top: 14px">
      <NDataTable
        :columns="blackColumns"
        :data="blacklist"
        :loading="loading"
        :row-key="(r: any) => r.id ?? (r.groupId + r.userId)"
        :bordered="false"
      />
      <NText depth="3" style="font-size: 12px; display: block; margin-top: 10px">
        黑名单为全局闸门：命中即拒绝所有命令（优先级高于主人放行）。
      </NText>
    </NCard>
  </div>
</template>

<style scoped>
.card {
  border-radius: 14px;
}
</style>
