<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NDataTable, NInput, NSwitch,
  NAlert, NPopconfirm, NInputNumber, useMessage, NGrid, NGi
} from 'naive-ui'
import { WarningOutline, RefreshOutline, NotificationsOutline, PlayOutline, SettingsOutline } from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import EmptyState from '../components/EmptyState.vue'
import api from '../api'
import dayjs from 'dayjs'

const message = useMessage()
const loading = ref(false)
const configs = ref<any[]>([])
const records = ref<any[]>([])
const recordsLimit = ref(100)
const checking = ref(false)
const savingKey = ref('') // 正在保存的 botKey（per-row loading）
const dirtyCfg = ref(new Set<string>()) // 有未保存规则修改的 botKey
const expanded = ref<Record<string, boolean>>({})

// ══════ 全局设置：检查频率 + 冷却时间 ══════
const settings = ref({ checkIntervalMs: 60000, cooldownMinutes: 30 })
const settingsSaving = ref(false)
async function loadSettings() {
  try { settings.value = { ...settings.value, ...(await api.settings()) } } catch { /* 静默 */ }
}
async function saveSettings() {
  settingsSaving.value = true
  try {
    const r = await api.saveSettings({ checkIntervalMs: settings.value.checkIntervalMs, cooldownMinutes: settings.value.cooldownMinutes })
    if (r.status === 'ok') {
      settings.value.checkIntervalMs = r.checkIntervalMs
      settings.value.cooldownMinutes = r.cooldownMinutes
      message.success(`已保存：每 ${Math.round(r.checkIntervalMs / 1000)} 秒检查一次，同指标 ${r.cooldownMinutes} 分钟内通知一次`)
    } else message.error(r.msg || '保存失败')
  } catch (e: any) {
    message.error('保存失败：' + (e?.message ?? e))
  } finally {
    settingsSaving.value = false
  }
}

// ══════ 规则定义（与后端 RULE_DEFS 一致；level: framework=框架级 / bot=机器人级） ══════
const RULES = [
  { key: 'qps', name: 'QPS 突增', unit: '条/秒', desc: '近 60 秒平均入站 QPS 超过阈值', def: 20, level: 'framework' },
  { key: 'msg-surge', name: '消息量突增', unit: '倍', desc: '近 5 分钟消息数超过「近 1 小时均值 × 阈值」', def: 3, level: 'framework' },
  { key: 'event-surge', name: '事件数突增', unit: '倍', desc: '近 5 分钟事件数超过「近 1 小时均值 × 阈值」', def: 3, level: 'framework' },
  { key: 'outbound-fail', name: '出站失败率', unit: '%', desc: '定时任务近 50 次执行失败率超过阈值', def: 30, level: 'framework' },
  { key: 'cpu', name: 'CPU 使用率', unit: '%', desc: '系统 CPU 使用率超过阈值（框架所在机器）', def: 85, level: 'framework' },
  { key: 'mem', name: '内存使用率', unit: '%', desc: '系统内存使用率超过阈值（框架所在机器）', def: 90, level: 'framework' },
  { key: 'disk', name: '磁盘使用率', unit: '%', desc: '系统磁盘使用率超过阈值（框架所在机器）', def: 90, level: 'framework' },
  { key: 'jvm', name: 'JVM 堆使用率', unit: '%', desc: 'JVM 堆内存使用率超过阈值', def: 85, level: 'framework' },
  { key: 'conn-down', name: '连接异常', unit: '—', desc: '该机器人 WS 连接断开 / 非 READY 状态', def: 0, level: 'bot' },
  { key: 'blacklist', name: '黑名单激增', unit: '条/分', desc: '该机器人两次检查间黑名单新增超过阈值', def: 10, level: 'bot' },
  { key: 'slow-stage', name: 'Pipeline 慢阶段', unit: '次/分', desc: '单阶段超 100ms 近 1 分钟新增次数超过阈值', def: 1, level: 'framework' },
  { key: 'dedup-hit', name: '去重命中率高', unit: '次/分', desc: '近 1 分钟重复事件（去重命中）新增超过阈值', def: 30, level: 'framework' },
  { key: 'rate-limit', name: '命令限流命中', unit: '次/分', desc: '近 1 分钟命令级限流拦截次数超过阈值', def: 30, level: 'framework' },
  { key: 'ws-reconnect', name: 'WS 重连次数', unit: '次/分', desc: '该机器人 WS 近 1 分钟重连次数超过阈值（网络不稳定信号）', def: 3, level: 'bot' },
  { key: 'ws-heartbeat', name: 'WS 心跳超时', unit: '个', desc: '该机器人处于重连/连接中的会话数超过阈值（心跳丢失）', def: 2, level: 'bot' },
  { key: 'qqapi-circuit', name: 'QQ API 熔断', unit: '—', desc: 'QQ 开放平台接口连续失败触发熔断（请求快速失败保护）', def: 0, level: 'framework' },
  { key: 'pool-queue', name: '线程池排队积压', unit: '条', desc: '任一线程池排队任务数超过阈值（处理能力吃紧）', def: 100, level: 'framework' },
  { key: 'plugin-error', name: '插件加载错误', unit: '个', desc: '处于 ERROR 状态的插件数超过阈值（插件加载/初始化失败）', def: 1, level: 'framework' },
  { key: 'job-fail', name: '定时任务失败', unit: '次/分', desc: '定时任务近 1 分钟失败次数超过阈值', def: 3, level: 'framework' }
]

const LEVEL_LABEL: Record<string, string> = {
  framework: '框架级', bot: 'Bot 级'
}
const LEVEL_TAG: Record<string, 'info' | 'warning'> = {
  framework: 'info', bot: 'warning'
}

const RULE_TAG: Record<string, { label: string; type: 'error' | 'warning' | 'info' | 'success' | 'default' }> = {
  qps: { label: 'QPS 突增', type: 'warning' },
  'msg-surge': { label: '消息突增', type: 'info' },
  'event-surge': { label: '事件突增', type: 'info' },
  'outbound-fail': { label: '出站失败率', type: 'error' },
  cpu: { label: 'CPU', type: 'warning' },
  mem: { label: '内存', type: 'warning' },
  disk: { label: '磁盘', type: 'warning' },
  jvm: { label: 'JVM', type: 'warning' },
  'conn-down': { label: '连接异常', type: 'error' },
  blacklist: { label: '黑名单激增', type: 'error' },
  'slow-stage': { label: '慢阶段', type: 'warning' },
  'dedup-hit': { label: '去重命中', type: 'info' },
  'rate-limit': { label: '命令限流', type: 'info' },
  'ws-reconnect': { label: 'WS 重连', type: 'warning' },
  'ws-heartbeat': { label: '心跳超时', type: 'error' },
  'qqapi-circuit': { label: 'API 熔断', type: 'error' },
  'pool-queue': { label: '池积压', type: 'warning' },
  'plugin-error': { label: '插件错误', type: 'error' },
  'job-fail': { label: '任务失败', type: 'error' },
  unknown: { label: '未知', type: 'default' }
}

/** 从后端返回的 rules 里取某个规则的配置（后端已补默认值）。 */
function ruleOf(cfg: any, key: string): { enabled: boolean; threshold: number } {
  const r = cfg.rules?.[key]
  return { enabled: r?.enabled !== false, threshold: r?.threshold ?? 0 }
}
/** 前端修改规则后回写 cfg.rules 并标记未保存。 */
function setRule(cfg: any, key: string, patch: Partial<{ enabled: boolean; threshold: number }>) {
  if (!cfg.rules) cfg.rules = {}
  if (!cfg.rules[key]) cfg.rules[key] = {}
  Object.assign(cfg.rules[key], patch)
  const s = new Set(dirtyCfg.value); s.add(cfg.botKey); dirtyCfg.value = s
}

async function load() {
  loading.value = true
  try {
    const [c, r] = await Promise.all([api.listConfigs(), api.records(recordsLimit.value)])
    configs.value = c || []
    records.value = r?.rows ?? []
  } catch (e: any) {
    message.error('加载失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

function loadMoreRecords() {
  recordsLimit.value += 100
  load()
}

async function save(cfg: any) {
  if (cfg.enabled && !String(cfg.alertUserId || '').trim()) {
    message.error('启用预警必须填写预警用户 ID')
    return
  }
  savingKey.value = cfg.botKey
  try {
    const r = await api.saveConfig(cfg.botKey, !!cfg.enabled, String(cfg.alertUserId || '').trim(), cfg.rules)
    if (r.status === 'ok') {
      message.success('预警配置已保存')
      const s = new Set(dirtyCfg.value); s.delete(cfg.botKey); dirtyCfg.value = s
    } else message.error(r.msg || '保存失败')
  } catch (e: any) {
    message.error('保存失败：' + (e?.message ?? e))
  } finally {
    savingKey.value = ''
  }
}

async function checkNow() {
  checking.value = true
  try {
    const r = await api.check()
    message.success(r.msg || '检查完成')
    await load()
  } catch (e: any) {
    message.error('检查失败：' + (e?.message ?? e))
  } finally {
    checking.value = false
  }
}

function fmtTime(t: number): string {
  return t > 0 ? dayjs(t * 1000).utcOffset(8).format('MM-DD HH:mm:ss') : '—'
}

const recordCols = [
  { title: '时间', key: 'createTime', width: 150, render: (r: any) => fmtTime(r.createTime) },
  { title: '机器人', key: 'botKey', width: 130, render: (r: any) => h(NTag, { size: 'small', bordered: false, type: 'default' }, { default: () => r.botKey }) },
  { title: '指标', key: 'rule', width: 110, render: (r: any) => h(NTag, { size: 'small', bordered: false, type: RULE_TAG[r.rule]?.type ?? 'default' }, { default: () => RULE_TAG[r.rule]?.label ?? r.rule }) },
  { title: '告警内容', key: 'message', minWidth: 300, ellipsis: { tooltip: true } }
]

onMounted(() => { load(); loadSettings() })
</script>

<template>
  <div>
    <PageHero title="预警中心" subtitle="框架运行指标异常检测 · 命中后单聊通知预警用户" :icon="WarningOutline">
      <NButton type="primary" :loading="checking" @click="checkNow">
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
      框架定期检查指标，命中后通过机器人<b>单聊接口</b>发送预警通知。
      <b>每个指标可独立开关、可调阈值，默认全部开启。</b>
    </NAlert>

    <!-- ══════ 全局设置：检查频率 + 冷却 ══════ -->
    <NCard title="全局设置（检查频率 / 通知冷却）" :bordered="true" style="margin-bottom: 14px">
      <div class="global-settings">
        <div class="gs-item">
          <div class="gs-info">
            <div class="gs-name">检查频率</div>
            <div class="gs-desc">框架每隔多久检测一次全部指标（10 秒 ~ 10 分钟）</div>
          </div>
          <NInputNumber v-model:value="settings.checkIntervalMs" :min="10000" :max="600000" :step="5000" size="small" style="width: 150px" />
          <NText depth="3" style="font-size: 12px; width: 60px">{{ Math.round(settings.checkIntervalMs / 1000) }} 秒</NText>
        </div>
        <div class="gs-item">
          <div class="gs-info">
            <div class="gs-name">通知冷却</div>
            <div class="gs-desc">同一指标命中后，多少分钟内不再重复通知（1 ~ 1440 分钟）</div>
          </div>
          <NInputNumber v-model:value="settings.cooldownMinutes" :min="1" :max="1440" :step="5" size="small" style="width: 150px" />
          <NText depth="3" style="font-size: 12px; width: 60px">{{ settings.cooldownMinutes }} 分钟</NText>
        </div>
        <div class="gs-item">
          <div></div>
          <NButton type="primary" :loading="settingsSaving" @click="saveSettings">
            <template #icon><NIcon><SettingsOutline /></NIcon></template>
            保存全局设置
          </NButton>
        </div>
      </div>
    </NCard>

    <!-- ══════ 预警配置（按机器人 + 规则开关/阈值） ══════ -->
    <NCard title="预警配置（按机器人）" :bordered="true" style="margin-bottom: 14px">
      <div v-if="configs.length" class="cfg-list">
        <div v-for="cfg in configs" :key="cfg.botKey" class="cfg-row">
          <div class="cfg-info">
            <div class="cfg-name">
              {{ cfg.botName || cfg.botKey }}
              <NTag size="tiny" :bordered="false" type="default" style="margin-left: 6px">{{ cfg.botKey }}</NTag>
            </div>
            <div class="cfg-desc">预警用户：{{ cfg.alertUserId || '未设置' }}</div>
          </div>
          <NInput
            v-model:value="cfg.alertUserId"
            size="small"
            placeholder="预警用户 ID（单聊 openid）"
            style="width: 240px"
          />
          <NSwitch :value="!!cfg.enabled" size="small" @update:value="(v: boolean) => { cfg.enabled = v; save(cfg) }" />
          <NTag v-if="dirtyCfg.has(cfg.botKey)" size="tiny" :bordered="false" type="warning">未保存</NTag>
          <NButton size="small" type="primary" tertiary :loading="savingKey === cfg.botKey" @click="save(cfg)">保存</NButton>
          <NButton size="small" secondary @click="expanded[cfg.botKey] = !expanded[cfg.botKey]">
            <template #icon><NIcon size="14"><SettingsOutline /></NIcon></template>
            {{ expanded[cfg.botKey] ? '收起规则' : '规则配置' }}
          </NButton>
        </div>

        <!-- 规则配置（展开显示） -->
        <div v-if="configs.some((c) => expanded[c.botKey])" class="rule-panels">
          <div v-for="cfg in configs" :key="'rules-' + cfg.botKey" v-show="expanded[cfg.botKey]" class="rule-panel">
            <div class="rule-panel-head">
              <NText strong>{{ cfg.botName || cfg.botKey }} · 监控规则</NText>
              <NText depth="3" style="font-size: 12px">
                <NTag size="tiny" :bordered="false" type="info">框架级</NTag> 全框架共享指标（任一 bot 配置命中即告警）
                <NTag size="tiny" :bordered="false" type="warning" style="margin-left: 8px">Bot 级</NTag> 仅检测该机器人自身状态
              </NText>
            </div>
            <NGrid :cols="24" :x-gap="10" :y-gap="10" responsive="screen" item-responsive>
              <NGi v-for="r in RULES" :key="r.key" span="24 s:12 m:8 l:6">
                <div class="rule-card" :class="{ disabled: !ruleOf(cfg, r.key).enabled }">
                  <div class="rule-head">
                    <NTag size="small" :bordered="false" :type="RULE_TAG[r.key]?.type ?? 'default'">{{ r.name }}</NTag>
                    <NSpace :size="4" align="center">
                      <NTag size="tiny" :bordered="false" :type="LEVEL_TAG[r.level] ?? 'default'">{{ LEVEL_LABEL[r.level] }}</NTag>
                      <NSwitch
                        :value="ruleOf(cfg, r.key).enabled"
                        size="small"
                        @update:value="(v: boolean) => setRule(cfg, r.key, { enabled: v })"
                      />
                    </NSpace>
                  </div>
                  <div class="rule-desc">{{ r.desc }}</div>
                  <div v-if="r.unit !== '—'" class="rule-threshold">
                    <NInputNumber
                      :value="ruleOf(cfg, r.key).threshold"
                      size="tiny"
                      :min="0"
                      :step="r.unit === '倍' ? 0.5 : 1"
                      @update:value="(v: number | null) => setRule(cfg, r.key, { threshold: v ?? 0 })"
                      style="width: 90px"
                    />
                    <span class="unit">{{ r.unit }}</span>
                  </div>
                  <NText v-else depth="3" style="font-size: 11px">无阈值（状态判定）</NText>
                </div>
              </NGi>
            </NGrid>
            <div style="text-align: right; margin-top: 10px">
              <NButton size="small" type="primary" :loading="savingKey === cfg.botKey" @click="save(cfg)">
                保存 {{ cfg.botName || cfg.botKey }} 规则
              </NButton>
            </div>
          </div>
        </div>
      </div>
      <EmptyState v-else description="暂无机器人" />
    </NCard>

    <NCard title="告警记录" :bordered="true">
      <EmptyState v-if="!loading && !records.length" description="暂无告警记录（框架运行平稳）" />
      <NDataTable v-else :columns="recordCols" :data="records" :bordered="false" size="small" :loading="loading" :row-key="(r: any) => r.id" :pagination="{ pageSize: 20 }" />
      <NButton v-if="records.length > 0 && records.length >= recordsLimit" size="small" secondary style="margin-top: 8px" @click="loadMoreRecords">
        加载更多
      </NButton>
    </NCard>
  </div>
</template>

<style scoped>
.global-settings { display: flex; flex-direction: column; gap: 12px; }
.gs-item { display: flex; align-items: center; gap: 12px; }
.gs-info { flex: 1; min-width: 0; }
.gs-name { font-size: 13px; font-weight: 600; }
.gs-desc { font-size: 11.5px; color: var(--n-text-color-3); margin-top: 2px; }
.cfg-list { display: flex; flex-direction: column; gap: 10px; }
.cfg-row { display: flex; align-items: center; gap: 12px; padding: 10px 12px; background: var(--n-color-2); border-radius: 10px; flex-wrap: wrap; }
.cfg-info { flex: 1; min-width: 140px; }
.cfg-name { font-size: 13.5px; font-weight: 600; }
.cfg-desc { font-size: 11.5px; color: var(--n-text-color-3); margin-top: 2px; }
.rule-panels { display: flex; flex-direction: column; gap: 14px; margin-top: 4px; }
.rule-panel { border: 1px solid var(--n-border-color); border-radius: 10px; padding: 12px 14px; }
.rule-panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; flex-wrap: wrap; gap: 6px; }
.rule-card {
  border: 1px solid var(--n-border-color);
  border-radius: 10px;
  padding: 10px 12px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: opacity 0.2s;
}
.rule-card.disabled { opacity: 0.55; }
.rule-head { display: flex; align-items: center; justify-content: space-between; }
.rule-desc { font-size: 11px; color: var(--n-text-color-3); line-height: 1.5; min-height: 30px; }
.rule-threshold { display: flex; align-items: center; gap: 6px; }
.unit { font-size: 11px; color: var(--n-text-color-3); }
</style>
