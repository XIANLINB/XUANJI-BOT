<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NDataTable, NInput, NSwitch,
  NEmpty, NAlert, NPopconfirm, useMessage
} from 'naive-ui'
import { WarningOutline, RefreshOutline, NotificationsOutline, PlayOutline } from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import api from '../api'
import dayjs from 'dayjs'

const message = useMessage()
const loading = ref(false)
const configs = ref<any[]>([])
const records = ref<any[]>([])
const saving = ref(false)

const RULES = [
  { key: 'qps', name: 'QPS 突增', desc: '近 60 秒平均入站 QPS 超过阈值（默认 20 条/秒）', def: '20' },
  { key: 'msg-surge', name: '消息量时段突增', desc: '近 5 分钟消息数超过「近 1 小时均值 ×3」（至少 30 条）', def: '×3' },
  { key: 'event-surge', name: '事件数突增', desc: '近 5 分钟系统事件数超过「近 1 小时均值 ×3」（至少 20 个）', def: '×3' },
  { key: 'outbound-fail', name: '出站失败率上升', desc: '定时任务近 50 次执行失败率超过阈值（默认 30%）', def: '30%' },
  { key: 'resource', name: '机器资源超限', desc: 'CPU >85% / 内存 >90% / 磁盘 >90% 任一命中', def: '85/90/90' },
  { key: 'conn-down', name: '连接异常', desc: '机器人 WS 连接断开 / 非 READY 状态', def: '—' },
  { key: 'blacklist', name: '黑名单激增', desc: '两次检查间黑名单新增超过阈值（默认 10 条/分钟）', def: '10' }
]

const RULE_TAG: Record<string, { label: string; type: 'error' | 'warning' | 'info' | 'success' }> = {
  qps: { label: 'QPS 突增', type: 'warning' },
  'msg-surge': { label: '消息突增', type: 'info' },
  'event-surge': { label: '事件突增', type: 'info' },
  'outbound-fail': { label: '出站失败率', type: 'error' },
  resource: { label: '资源超限', type: 'warning' },
  'conn-down': { label: '连接异常', type: 'error' },
  blacklist: { label: '黑名单激增', type: 'error' },
  unknown: { label: '未知', type: 'default' }
}

async function load() {
  loading.value = true
  try {
    const [c, r] = await Promise.all([api.listConfigs(), api.records(100)])
    configs.value = c || []
    records.value = r?.rows ?? []
  } catch (e: any) {
    message.error('加载失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

async function save(cfg: any) {
  if (cfg.enabled && !String(cfg.alertUserId || '').trim()) {
    message.error('启用预警必须填写预警用户 ID')
    return
  }
  saving.value = true
  try {
    const r = await api.saveConfig(cfg.botKey, !!cfg.enabled, String(cfg.alertUserId || '').trim())
    if (r.status === 'ok') message.success('预警配置已保存')
    else message.error(r.msg || '保存失败')
  } catch (e: any) {
    message.error('保存失败：' + (e?.message ?? e))
  } finally {
    saving.value = false
  }
}

async function checkNow() {
  try {
    const r = await api.check()
    message.success(r.msg || '检查完成')
    await load()
  } catch (e: any) {
    message.error('检查失败：' + (e?.message ?? e))
  }
}

function fmtTime(t: number): string {
  return t > 0 ? dayjs(t * 1000).format('MM-DD HH:mm:ss') : '—'
}

const recordCols = [
  { title: '时间', key: 'createTime', width: 150, render: (r: any) => fmtTime(r.createTime) },
  { title: '机器人', key: 'botKey', width: 120, render: (r: any) => h(NTag, { size: 'small', bordered: false, type: 'default' }, { default: () => r.botKey }) },
  { title: '指标', key: 'rule', width: 110, render: (r: any) => h(NTag, { size: 'small', bordered: false, type: RULE_TAG[r.rule]?.type ?? 'default' }, { default: () => RULE_TAG[r.rule]?.label ?? r.rule }) },
  { title: '告警内容', key: 'message', minWidth: 300, ellipsis: { tooltip: true } }
]

onMounted(load)
</script>

<template>
  <div>
    <PageHero title="预警中心" subtitle="框架运行指标异常检测 · 命中后单聊通知预警用户" :icon="WarningOutline">
      <NButton type="primary" :loading="saving" @click="checkNow">
        <template #icon><NIcon><PlayOutline /></NIcon></template>
        立即检查
      </NButton>
      <NButton secondary :loading="loading" @click="load">
        <template #icon><NIcon><RefreshOutline /></NIcon></template>
        刷新
      </NButton>
    </PageHero>

    <NAlert type="info" :show-icon="true" style="margin-bottom: 14px">
      每个机器人可配置一个<b>预警用户 ID</b>（即单聊 openid，给机器人发过消息后即可在「消息监控」右侧查到）。
      框架每 60 秒检查一次，命中指标后通过机器人<b>单聊接口</b>发送预警通知；同一指标 30 分钟内只通知一次，阈值可在运行设置（xuanji_config）中覆盖。
    </NAlert>

    <NCard title="预警配置（按机器人）" :bordered="true" style="margin-bottom: 14px">
      <div v-if="configs.length" class="cfg-list">
        <div v-for="cfg in configs" :key="cfg.botKey" class="cfg-row">
          <div class="cfg-info">
            <div class="cfg-name">{{ cfg.botKey }}</div>
            <div class="cfg-desc">预警用户：{{ cfg.alertUserId || '未设置' }}</div>
          </div>
          <NInput
            v-model:value="cfg.alertUserId"
            size="small"
            placeholder="预警用户 ID（单聊 openid）"
            style="width: 260px"
          />
          <NSwitch :value="!!cfg.enabled" size="small" @update:value="(v: boolean) => { cfg.enabled = v; save(cfg) }" />
          <NButton size="small" type="primary" tertiary :loading="saving" @click="save(cfg)">保存</NButton>
        </div>
      </div>
      <NEmpty v-else description="暂无机器人" style="padding: 24px 0" />
    </NCard>

    <NCard title="监控指标说明" :bordered="true" style="margin-bottom: 14px">
      <div class="rule-grid">
        <div v-for="r in RULES" :key="r.key" class="rule-item">
          <NTag size="small" :bordered="false" :type="RULE_TAG[r.key]?.type ?? 'default'">{{ r.name }}</NTag>
          <NText depth="3" style="font-size: 12px; display: block; margin-top: 6px">{{ r.desc }}</NText>
        </div>
      </div>
    </NCard>

    <NCard title="告警记录" :bordered="true">
      <NDataTable :columns="recordCols" :data="records" :bordered="false" size="small" :loading="loading" :row-key="(r: any) => r.id" />
      <NEmpty v-if="!loading && !records.length" description="暂无告警记录（框架运行平稳）" style="padding: 30px 0" />
    </NCard>
  </div>
</template>

<style scoped>
.cfg-list { display: flex; flex-direction: column; gap: 10px; }
.cfg-row { display: flex; align-items: center; gap: 12px; padding: 10px 12px; background: var(--n-color-2); border-radius: 10px; }
.cfg-info { flex: 1; min-width: 0; }
.cfg-name { font-size: 13.5px; font-weight: 600; }
.cfg-desc { font-size: 11.5px; color: var(--n-text-color-3); margin-top: 2px; }
.rule-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; }
.rule-item { padding: 10px 12px; border: 1px solid var(--n-border-color); border-radius: 10px; }
</style>
