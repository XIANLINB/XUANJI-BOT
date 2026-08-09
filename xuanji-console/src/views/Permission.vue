<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NButton, NCard, NDataTable, NInput, NForm, NFormItem, NSelect, NSpace, NTag, NText, useMessage,
  NGrid, NGi, NIcon, NTooltip, NNumberAnimation
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { ShieldCheckmarkOutline, BanOutline, RefreshOutline, TimeOutline } from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import api from '../api'
import dayjs from 'dayjs'
import { groupName, userName } from '../utils/names'

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

// 黑名单统计（全局，来自风控数据源）
const risk = ref<Record<string, any>>({ blacklist: {}, block: {} })
const timeline = ref<any[]>([])
const showTimeline = ref(false)
const riskLoading = ref(false)

async function loadRisk() {
  riskLoading.value = true
  try {
    const [o, t] = await Promise.all([api.riskOverview(), api.riskTimeline()])
    risk.value = o || {}
    timeline.value = t?.rows ?? []
  } catch { /* 统计失败不影响主流程 */ }
  finally { riskLoading.value = false }
}

function fmtTime(t: number): string {
  return t > 0 ? dayjs(t * 1000).format('YYYY-MM-DD HH:mm:ss') : '—'
}

const timelineCols = [
  { title: '时间', key: 'createTime', width: 160, render: (r: any) => fmtTime(r.createTime) },
  { title: '动作', key: 'action', width: 80, render: (r: any) => h(NTag, { size: 'small', bordered: false, type: r.action === 'ADD' ? 'error' : 'success' }, { default: () => r.action === 'ADD' ? '拉黑' : '解除' }) },
  { title: '群', key: 'groupId', minWidth: 180, render: (r: any) => h('span', groupName(r)) },
  { title: '用户', key: 'userId', minWidth: 180, render: (r: any) => h('span', userName(r)) },
  { title: '原因', key: 'reason', minWidth: 160, ellipsis: { tooltip: true }, render: (r: any) => h('span', r.reason || '—') }
]

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

onMounted(() => { loadBots(); loadRisk() })
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
      <!-- 黑名单统计行 -->
      <NGrid :cols="24" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 14px">
        <NGi span="24 s:12 m:6">
          <div class="risk-stat">
            <div class="risk-icon" style="background: #854F0B1a; color: #854F0B"><NIcon size="16"><ShieldCheckmarkOutline /></NIcon></div>
            <div class="risk-info">
              <NNumberAnimation :from="0" :to="Number(risk.blacklist?.total ?? 0)" :duration="600" class="risk-val" />
              <div class="risk-label">黑名单总数（全局）</div>
            </div>
          </div>
        </NGi>
        <NGi span="24 s:12 m:6">
          <div class="risk-stat">
            <div class="risk-icon" style="background: #e5484d1a; color: #e5484d"><NIcon size="16"><BanOutline /></NIcon></div>
            <div class="risk-info">
              <NNumberAnimation :from="0" :to="Number(risk.block?.blocks ?? 0)" :duration="600" class="risk-val" style="color: #e5484d" />
              <div class="risk-label">累计拦截次数（黑名单命中）</div>
            </div>
          </div>
        </NGi>
        <NGi span="24 s:12 m:6">
          <div class="risk-stat">
            <div class="risk-icon" style="background: #18a0581a; color: #18a058"><NIcon size="16"><RefreshOutline /></NIcon></div>
            <div class="risk-info">
              <NNumberAnimation :from="0" :to="Number(risk.blacklist?.add24h ?? 0)" :duration="600" class="risk-val" />
              <div class="risk-label">近 24h 拉黑</div>
            </div>
          </div>
        </NGi>
        <NGi span="24 s:12 m:6">
          <div class="risk-stat">
            <div class="risk-icon" style="background: #2090e01a; color: #2090e0"><NIcon size="16"><RefreshOutline /></NIcon></div>
            <div class="risk-info">
              <NNumberAnimation :from="0" :to="Number(risk.blacklist?.remove24h ?? 0)" :duration="600" class="risk-val" />
              <div class="risk-label">近 24h 解除</div>
            </div>
          </div>
        </NGi>
      </NGrid>

      <NDataTable
        :columns="blackColumns"
        :data="blacklist"
        :loading="loading"
        :row-key="(r: any) => r.id ?? (r.groupId + r.userId)"
        :bordered="false"
      />
      <NText depth="3" style="font-size: 12px; display: block; margin-top: 10px">
        黑名单为全局闸门：命中即拒绝所有命令（优先级高于主人放行）。「累计拦截次数」= 黑名单用户在 Pipeline 权限检查阶段被拒绝的事件总数。
      </NText>

      <!-- 操作时间线折叠区 -->
      <NButton size="small" secondary style="margin-top: 14px" :loading="riskLoading" @click="showTimeline = !showTimeline">
        <template #icon><NIcon size="14"><TimeOutline /></NIcon></template>
        {{ showTimeline ? '收起操作记录' : `查看黑名单操作记录（${timeline.length} 条）` }}
      </NButton>
      <NDataTable
        v-if="showTimeline"
        :columns="timelineCols"
        :data="timeline"
        :bordered="false"
        size="small"
        style="margin-top: 10px"
        :row-key="(r: any) => r.id"
      />
    </NCard>
  </div>
</template>

<style scoped>
.card {
  border-radius: 14px;
}
.risk-stat {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: rgba(128, 128, 128, 0.05);
  border-radius: 10px;
  height: 100%;
}
.risk-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.risk-info { min-width: 0; }
.risk-val { font-size: 18px; font-weight: 700; font-variant-numeric: tabular-nums; }
.risk-label { font-size: 11.5px; color: var(--n-text-color-3); }
</style>
