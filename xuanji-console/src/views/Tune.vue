<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NEmpty, NDivider, NInput,
  NRadioGroup, NRadioButton, NSpin, NAlert, NModal, NGrid, NGi, NTooltip
} from 'naive-ui'
import {
  SettingsOutline, InformationCircleOutline, StatsChartOutline,
  DownloadOutline, RefreshOutline
} from '@vicons/ionicons5'
import { systemApi } from '../api/system'
import api from '../api'
import PageHero from '../components/PageHero.vue'

const message = useMessage()

// ═══════════════════ 三档模板选择 ═══════════════════
const tuneMode = ref<'eco' | 'sport' | 'perf'>('eco')
const tune = ref<Record<string, any>>({ eco: null, sport: null, perf: null })
const tuneLoading = ref(false)
const activeTune = computed(() => tune.value[tuneMode.value])
const capacity = computed(() => activeTune.value?.capacity ?? {})
const params = computed(() => activeTune.value?.params ?? {})
const latencyBreakdown = computed(() => activeTune.value?.latencyBreakdown ?? [])

const sysSummary = computed(() => activeTune.value?.sysSummary ?? {})
const networkInfo = ref<any>({ totalRxMBps: 0, totalTxMBps: 0, interfaces: [], note: '采集需 Linux + 二次采样' })
const gcInfo = ref<any>({ totalCount: 0, totalTimeMs: 0, collectors: [] })
const processInfo = ref<any>({ threadCount: 0, openFiles: 'N/A' })

const tuneCurrent = ref<{ mode: string; modeLabel: string; appliedAt: number; params?: any; paceMsNow?: string; needsRestart?: boolean }>({ mode: 'none', modeLabel: '未应用模板', appliedAt: 0 })
const tuneApplying = ref(false)

const showRestartModal = ref(false)
const restartConfirmInput = ref('')
const restarting = ref(false)

// 一键恢复默认（二次确认）
const showResetModal = ref(false)
const resetConfirm = ref('')
const resetting = ref(false)

/** 恢复默认配置：删除所有 tune.* + 重置 outbound.pace_ms 为 0。 */
async function doResetTune() {
  if (resetConfirm.value !== 'RESET') {
    message.warning('请输入 RESET 确认')
    return
  }
  resetting.value = true
  try {
    const r = await api.resetTune()
    if (r?.status === 'ok') {
      message.success(`已恢复默认（重置 ${r.resetCount} 项）；出站节奏立即还原 0，线程池参数需重启框架`)
    } else {
      message.warning(r?.msg || '恢复完成但后端返回非 ok')
    }
    showResetModal.value = false
    resetConfirm.value = ''
    await loadTune()
  } catch (e: any) {
    message.error('恢复失败：' + (e?.message ?? e))
  } finally {
    resetting.value = false
  }
}

async function loadTune() {
  tuneLoading.value = true
  try {
    const [eco, sport, perf, cur, overview] = await Promise.all([
      api.getTuneRecommend('eco'),
      api.getTuneRecommend('sport'),
      api.getTuneRecommend('perf'),
      api.getTuneCurrent(),
      api.getMetricsOverview()
    ])
    tune.value.eco = eco
    tune.value.sport = sport
    tune.value.perf = perf
    tuneCurrent.value = cur ?? { mode: 'none', modeLabel: '未应用模板', appliedAt: 0 }
    if (['eco', 'sport', 'perf'].includes(tuneCurrent.value.mode)) {
      tuneMode.value = tuneCurrent.value.mode as any
    }
    const sys = overview?.system ?? {}
    if (sys.network) networkInfo.value = sys.network
    if (sys.gc) gcInfo.value = sys.gc
    if (sys.process) processInfo.value = sys.process
  } catch (e: any) {
    message.error('性能模板加载失败：' + (e?.message ?? e))
  } finally {
    tuneLoading.value = false
  }
}

async function applyTuneMode() {
  tuneApplying.value = true
  try {
    const r = await api.applyTune(tuneMode.value)
    const p = r?.params ?? {}
    message.success(
      `已应用「${r?.modeLabel ?? tuneMode.value}」模板：` +
      `出站节奏 ${p.outPaceMs ?? '?'}ms/条 立即生效；线程池参数已写入，需重启框架生效`,
      { duration: 5000 }
    )
    tuneCurrent.value.needsRestart = true
    await loadTune()
  } catch (e: any) {
    message.error('保存模板失败：' + (e?.message ?? e))
  } finally {
    tuneApplying.value = false
  }
}

async function doRestart() {
  if (restartConfirmInput.value !== 'RESTART') {
    message.error('必须精确输入 RESTART 才能重启（防误触）')
    return
  }
  restarting.value = true
  try {
    await systemApi.restart('RESTART')
    message.warning('重启指令已接受，2 秒后框架关闭；start.sh 会自动拉起新进程。', { duration: 8000 })
    setTimeout(() => {
      showRestartModal.value = false
      restartConfirmInput.value = ''
      setTimeout(() => { window.location.reload() }, 25000)
    }, 2000)
  } catch (e: any) {
    message.error('重启失败：' + (e?.message ?? e))
  } finally {
    restarting.value = false
  }
}

async function showStartScript() {
  try {
    const content = await systemApi.getStartScript()
    message.info('脚本已加载（按 F12 查看 response）', { duration: 3000 })
    console.log('[Start.sh]\n' + content)
    await navigator.clipboard.writeText(content)
    message.success('start.sh 已复制到剪贴板，粘贴到 JAR 同目录即可使用')
  } catch (e: any) {
    message.error('获取脚本失败：' + (e?.message ?? e))
  }
}

function fmtDetected(ms: any): string {
  const n = Number(ms)
  if (!isFinite(n) || n <= 0) return '—'
  return new Date(n).toLocaleTimeString('zh-CN', { hour12: false })
}
function fmtTuneInt(v: any): string {
  const n = Number(v)
  return isFinite(n) && n >= 0 ? String(n) : '—'
}
function fmtAppliedAt(ms: any): string {
  const n = Number(ms)
  if (!isFinite(n) || n <= 0) return '—'
  return new Date(n).toLocaleString('zh-CN', { hour12: false })
}

onMounted(loadTune)
</script>

<template>
  <div>
    <PageHero
      title="性能模板"
      subtitle="三档一键配置（经济 1-2 / 运动 5-10 / 性能 20+ bot）· 基于本机资源实时估算"
      :icon="StatsChartOutline"
    />

    <template v-if="!tuneLoading || activeTune">
      <NSpace vertical :size="14" class="tune-page">

        <!-- ═══ 当前模板卡 ═══ -->
        <NCard size="small" :bordered="false" class="tune-current-card">
          <div class="tune-current-row">
            <div>
              <NText strong style="font-size: 14px">当前使用模板</NText>
              <NText depth="3" style="font-size: 12px; display: block; margin-top: 2px">
                {{ tuneCurrent.mode === 'none' ? '从未应用过模板（框架使用 application.yml / 代码硬编码默认配置）' : '应用时间：' + fmtAppliedAt(tuneCurrent.appliedAt) + (tuneCurrent.needsRestart ? ' · 已写入但需重启生效' : '') }}
              </NText>
            </div>
            <NTag
              :type="tuneCurrent.mode === 'none' ? 'default' : tuneCurrent.mode === 'perf' ? 'warning' : tuneCurrent.mode === 'sport' ? 'info' : 'success'"
              :bordered="false"
              size="large"
              round
              class="tune-current-tag"
            >
              {{ tuneCurrent.modeLabel }}
            </NTag>
          </div>
        </NCard>

        <!-- ═══ 三档选择 ═══ -->
        <NRadioGroup v-model:value="tuneMode" size="medium">
          <NRadioButton value="eco">
            <NSpace align="center" :size="6">
              <NTag size="tiny" :bordered="false" type="success">经济</NTag>
              <span>1-2 bot · 资源优先</span>
            </NSpace>
          </NRadioButton>
          <NRadioButton value="sport">
            <NSpace align="center" :size="6">
              <NTag size="tiny" :bordered="false" type="info">运动</NTag>
              <span>5-10 bot · 平衡</span>
            </NSpace>
          </NRadioButton>
          <NRadioButton value="perf">
            <NSpace align="center" :size="6">
              <NTag size="tiny" :bordered="false" type="warning">性能</NTag>
              <span>20+ bot · 极限吞吐</span>
            </NSpace>
          </NRadioButton>
        </NRadioGroup>

        <NSpin :show="tuneLoading">
          <template v-if="activeTune">
            <!-- ═══ 系统资源检测 ═══ -->
            <NCard size="small" :bordered="false" class="tune-sys-card">
              <template #header>
                <NSpace align="center" :size="6">
                  <NIcon size="14" color="#5b5bd6"><InformationCircleOutline /></NIcon>
                  <span style="font-weight: 600; font-size: 13px">本机系统资源</span>
                  <NTag size="tiny" :bordered="false" type="info">实时检测</NTag>
                </NSpace>
              </template>
              <NGrid :cols="24" :x-gap="12" :y-gap="8" responsive="screen" item-responsive>
                <NGi span="12 m:4"><div class="kv"><div class="kv-label">CPU 核数 / 型号</div><NText strong>{{ sysSummary.cpuCores ?? '—' }} 核 · {{ sysSummary.cpuModel ?? '—' }}</NText></div></NGi>
                <NGi span="12 m:4"><div class="kv"><div class="kv-label">物理内存</div><NText strong>{{ sysSummary.memGb ?? '—' }} GB</NText></div></NGi>
                <NGi span="12 m:4"><div class="kv"><div class="kv-label">网络上下行（Linux）</div><NText strong :style="{ color: Number(networkInfo.totalRxMBps) + Number(networkInfo.totalTxMBps) > 0 ? '#18a058' : undefined }">↓ {{ networkInfo.totalRxMBps ?? 0 }} MB/s · ↑ {{ networkInfo.totalTxMBps ?? 0 }} MB/s</NText></div></NGi>
                <NGi span="12 m:4"><div class="kv"><div class="kv-label">JVM GC（累计）</div><NText strong>{{ gcInfo.totalCount ?? 0 }} 次 / {{ Math.round((gcInfo.totalTimeMs ?? 0) / 1000) }} s</NText></div></NGi>
                <NGi span="12 m:4"><div class="kv"><div class="kv-label">进程线程数</div><NText strong>{{ processInfo.threadCount ?? '—' }} 个</NText></div></NGi>
                <NGi span="12 m:4"><div class="kv"><div class="kv-label">系统 / JDK</div><NText strong>{{ sysSummary.osName ?? '—' }} · {{ sysSummary.javaVersion ?? '—' }}</NText></div></NGi>
              </NGrid>
              <NText depth="3" style="font-size: 11px; display: block; margin-top: 6px">
                网络带宽采样需 Linux（读取 /proc/net/dev，差值计算 MB/s）；首次采样需再等 5s。文件句柄/进程数仅 Unix-like 平台支持。
              </NText>
            </NCard>

            <!-- ═══ 模板说明 + 容量估算 ═══ -->
            <NAlert type="info" :show-icon="true">
              <template #title>{{ activeTune.modeLabel }}模板 · 推荐挂载 {{ activeTune.recommendedBotCount }} 个机器人</template>
              {{ activeTune.note }}
            </NAlert>

            <NGrid :cols="24" :x-gap="12" :y-gap="10" responsive="screen" item-responsive>
              <NGi span="12 m:6"><div class="kv"><div class="kv-label">可支持机器人（内存约束）</div><NText strong style="color:#5b5bd6">{{ capacity.maxBotsByMem ?? '—' }} 个</NText></div></NGi>
              <NGi span="12 m:6"><div class="kv"><div class="kv-label">可支持机器人（CPU 约束）</div><NText strong>{{ capacity.maxBotsByCpu ?? '—' }} 个</NText></div></NGi>
              <NGi span="12 m:6"><div class="kv"><div class="kv-label">综合建议上限</div><NText strong style="color:#18a058">{{ capacity.maxBots ?? '—' }} 个</NText></div></NGi>
              <NGi span="12 m:6"><div class="kv"><div class="kv-label">单 bot 内存</div><NText strong>{{ capacity.memPerBotMb ?? '—' }} MB</NText></div></NGi>
              <NGi span="12 m:6"><div class="kv"><div class="kv-label">入站吞吐</div><NText strong>{{ capacity.msgInPerSec ?? '—' }} 条/秒</NText></div></NGi>
              <NGi span="12 m:6"><div class="kv"><div class="kv-label">出站吞吐</div><NText strong>{{ capacity.msgOutPerSec ?? '—' }} 条/秒</NText></div></NGi>
            </NGrid>

            <!-- ═══ 推荐参数 vs 实际值 详细对比 ═══ -->
            <NCard size="small" :bordered="false" class="tune-compare-card">
              <template #header>
                <NSpace align="center" :size="6">
                  <NIcon size="14" color="#f0a020"><SettingsOutline /></NIcon>
                  <span style="font-weight: 600; font-size: 13px">模板推荐值 vs 框架当前实际值</span>
                  <NTag size="tiny" :bordered="false" type="info">保存后：出站节奏立即生效，线程池/连接池需重启</NTag>
                </NSpace>
              </template>
              <NGrid :cols="24" :x-gap="10" :y-gap="10" responsive="screen" item-responsive>
                <NGi span="12 m:8">
                  <div class="cmp-row">
                    <div class="cmp-label">Spring @Scheduled 调度池</div>
                    <div class="cmp-pair">
                      <div class="cmp-recommended"><NText depth="3" style="font-size: 11px">推荐</NText><NText strong>{{ fmtTuneInt(params.schedPool) }} 线程</NText></div>
                      <div class="cmp-actual"><NText depth="3" style="font-size: 11px">实际</NText><NText strong>4 线程</NText></div>
                    </div>
                    <NText depth="3" style="font-size: 10px">application.yml 硬编码</NText>
                  </div>
                </NGi>
                <NGi span="12 m:8">
                  <div class="cmp-row">
                    <div class="cmp-label">QQ-WS 连接池（核心/最大）</div>
                    <div class="cmp-pair">
                      <div class="cmp-recommended"><NText depth="3" style="font-size: 11px">推荐</NText><NText strong>{{ fmtTuneInt(params.wsCore) }} / {{ fmtTuneInt(params.wsMax) }}</NText></div>
                      <div class="cmp-actual"><NText depth="3" style="font-size: 11px">实际</NText><NText strong>4 / 16</NText></div>
                    </div>
                    <NText depth="3" style="font-size: 10px">代码硬编码（QqBotWsManager）</NText>
                  </div>
                </NGi>
                <NGi span="12 m:8">
                  <div class="cmp-row">
                    <div class="cmp-label">QQ-WS 心跳池</div>
                    <div class="cmp-pair">
                      <div class="cmp-recommended"><NText depth="3" style="font-size: 11px">推荐</NText><NText strong>{{ fmtTuneInt(params.heartbeatPool) }} 线程</NText></div>
                      <div class="cmp-actual"><NText depth="3" style="font-size: 11px">实际</NText><NText strong>max(2,CPU核数)</NText></div>
                    </div>
                    <NText depth="3" style="font-size: 10px">代码自动按核数</NText>
                  </div>
                </NGi>
                <NGi span="12 m:8">
                  <div class="cmp-row">
                    <div class="cmp-label">HikariCP 主库</div>
                    <div class="cmp-pair">
                      <div class="cmp-recommended"><NText depth="3" style="font-size: 11px">推荐</NText><NText strong>{{ fmtTuneInt(params.hikariMainMax) }} 连接</NText></div>
                      <div class="cmp-actual"><NText depth="3" style="font-size: 11px">实际</NText><NText strong>3 连接</NText></div>
                    </div>
                    <NText depth="3" style="font-size: 10px">application.yml 硬编码</NText>
                  </div>
                </NGi>
                <NGi span="12 m:8">
                  <div class="cmp-row">
                    <div class="cmp-label">HikariCP 日志库</div>
                    <div class="cmp-pair">
                      <div class="cmp-recommended"><NText depth="3" style="font-size: 11px">推荐</NText><NText strong>{{ fmtTuneInt(params.hikariLogMax) }} 连接</NText></div>
                      <div class="cmp-actual"><NText depth="3" style="font-size: 11px">实际</NText><NText strong>3 连接</NText></div>
                    </div>
                    <NText depth="3" style="font-size: 10px">application.yml 硬编码</NText>
                  </div>
                </NGi>
                <NGi span="12 m:8">
                  <div class="cmp-row">
                    <div class="cmp-label">HikariCP 每实例库（per bot）</div>
                    <div class="cmp-pair">
                      <div class="cmp-recommended"><NText depth="3" style="font-size: 11px">推荐</NText><NText strong>{{ fmtTuneInt(params.hikariInstanceMax) }} 连接</NText></div>
                      <div class="cmp-actual"><NText depth="3" style="font-size: 11px">实际</NText><NText strong>3 连接</NText></div>
                    </div>
                    <NText depth="3" style="font-size: 10px">代码硬编码（BotDataSourceRegistry）</NText>
                  </div>
                </NGi>
                <NGi span="12 m:8">
                  <div class="cmp-row">
                    <div class="cmp-label">BotPipeline 每 bot 并发</div>
                    <div class="cmp-pair">
                      <div class="cmp-recommended"><NText depth="3" style="font-size: 11px">推荐</NText><NText strong>{{ fmtTuneInt(params.botConcurrency) }} 线程</NText></div>
                      <div class="cmp-actual"><NText depth="3" style="font-size: 11px">实际</NText><NText strong>1（串行）</NText></div>
                    </div>
                    <NText depth="3" style="font-size: 10px">保存后重启生效</NText>
                  </div>
                </NGi>
                <NGi span="12 m:8">
                  <div class="cmp-row">
                    <div class="cmp-label">出站池每 bot 线程</div>
                    <div class="cmp-pair">
                      <div class="cmp-recommended"><NText depth="3" style="font-size: 11px">推荐</NText><NText strong>{{ fmtTuneInt(params.outThreadsPerBot) }} 线程</NText></div>
                      <div class="cmp-actual"><NText depth="3" style="font-size: 11px">实际</NText><NText strong>1（串行）</NText></div>
                    </div>
                    <NText depth="3" style="font-size: 10px">保存后重启生效</NText>
                  </div>
                </NGi>
                <NGi span="12 m:8">
                  <div class="cmp-row">
                    <div class="cmp-label">出站节奏</div>
                    <div class="cmp-pair">
                      <div class="cmp-recommended"><NText depth="3" style="font-size: 11px">推荐</NText><NText strong>{{ fmtTuneInt(params.outPaceMs) }} ms/条</NText></div>
                      <div class="cmp-actual"><NText depth="3" style="font-size: 11px">实际</NText><NText strong :style="{ color: Number(tuneCurrent.paceMsNow) > 0 ? '#18a058' : '#f0a020' }">{{ Number(tuneCurrent.paceMsNow) > 0 ? (fmtTuneInt(tuneCurrent.paceMsNow) + ' ms/条') : '不节流（0）' }}</NText></div>
                    </div>
                    <NText depth="3" style="font-size: 10px">✅ 运行时立即生效</NText>
                  </div>
                </NGi>
              </NGrid>
            </NCard>

            <!-- ═══ 预计延迟分析 ═══ -->
            <NCard size="small" :bordered="false" class="tune-latency-card">
              <template #header>
                <NSpace align="center" :size="6">
                  <NIcon size="14" color="#2080f0"><StatsChartOutline /></NIcon>
                  <span style="font-weight: 600; font-size: 13px">单事件预计延迟（按链路分阶段）</span>
                  <NTag size="tiny" :bordered="false" type="info">目标 ≤ 3 秒</NTag>
                </NSpace>
              </template>
              <NGrid :cols="24" :x-gap="10" :y-gap="6" responsive="screen" item-responsive>
                <NGi v-for="(s, i) in latencyBreakdown" :key="i" :span="24">
                  <div class="latency-row" :class="{ 'is-total': s.isTotal, 'over-budget': s.warning }">
                    <div class="latency-name">{{ s.name }}</div>
                    <div class="latency-desc">{{ s.desc }}</div>
                    <div class="latency-val">
                      <NText strong>{{ s.normal }} ms</NText>
                      <NText depth="3" style="font-size: 11px"> / 最坏 {{ s.worst }} ms</NText>
                    </div>
                    <NTag v-if="s.warning" size="tiny" type="error" :bordered="false">{{ s.warning }}</NTag>
                  </div>
                </NGi>
              </NGrid>
            </NCard>

            <!-- ═══ 风险提示 ═══ -->
            <NAlert type="warning" :show-icon="true" title="风险提示">
              <ul class="tune-risk-list">
                <li v-for="(r, i) in activeTune.risks" :key="i">{{ r }}</li>
              </ul>
            </NAlert>

            <!-- ═══ 启动优化建议 ═══ -->
            <NAlert type="default" :show-icon="true" title="启动优化建议">
              <ul class="tune-risk-list">
                <li><b>JVM 内存</b>：建议 <code>-Xms512m -Xmx{{ capacity.maxBots >= 20 ? '4g' : (capacity.maxBots >= 5 ? '2g' : '1g') }} -XX:+UseG1GC -XX:MaxGCPauseMillis=200</code></li>
                <li><b>磁盘</b>：建议 SSD（消息/事件表写入延迟关键），数据目录放本地（不挂 NFS）</li>
                <li><b>文件句柄</b>：Linux 上调高 <code>ulimit -n 8192</code>（性能模板 20+ bot 必备）</li>
                <li><b>网络</b>：性能模板需上行 ≥ 5 MB/s（QQ 回调密集）；若为云服务器注意内网 vs 公网</li>
                <li><b>OS</b>：Windows / Linux / macOS 均可；性能模板推荐 Linux（句柄 / ulimit 控制更细）</li>
              </ul>
            </NAlert>

            <!-- ═══ 保存按钮 + 重启按钮 ═══ -->
            <div class="tune-actions">
              <NSpace align="center" :size="10" :wrap="true">
                <NButton
                  type="primary"
                  :loading="tuneApplying"
                  :disabled="tuneCurrent.mode === tuneMode.value"
                  @click="applyTuneMode"
                >
                  <template #icon><NIcon><SettingsOutline /></NIcon></template>
                  {{ tuneCurrent.mode === tuneMode.value ? '✓ 当前已应用该模板' : '保存配置（应用此模板）' }}
                </NButton>
                <NButton secondary @click="showStartScript">
                  <template #icon><NIcon><DownloadOutline /></NIcon></template>
                  获取 start.sh
                </NButton>
                <NButton
                  secondary
                  :disabled="tuneCurrent.mode === 'none'"
                  @click="showResetModal = true"
                >
                  <template #icon><NIcon><RefreshOutline /></NIcon></template>
                  恢复默认配置
                </NButton>
                <NButton type="warning" @click="showRestartModal = true">
                  <template #icon><NIcon><RefreshOutline /></NIcon></template>
                  一键重启框架
                </NButton>
                <NText v-if="tuneCurrent.needsRestart" depth="3" style="font-size: 12px; color: #f0a020">
                  ⚠ 模板已写入但线程池参数需重启框架生效
                </NText>
              </NSpace>
            </div>
          </template>
          <NEmpty v-else description="模板加载中…" style="padding: 24px 0" />
        </NSpin>
      </NSpace>
    </template>

    <!-- ═══════ 重启框架确认弹窗 ═══════ -->
    <NModal v-model:show="showRestartModal" preset="card" title="一键重启框架" style="width: 500px" :mask-closable="false">
      <NAlert type="warning" :show-icon="true" style="margin-bottom: 14px">
        重启会导致<b>所有机器人的 WS 连接断开</b>，QQ 平台回调会在 5~10 秒内自动恢复。
        请确认当前没有重要消息正在处理。
      </NAlert>
      <NText style="font-size: 13px; display: block; margin-bottom: 6px">
        请输入 <code style="background: rgba(240, 160, 32, 0.15); padding: 2px 6px; border-radius: 4px">RESTART</code> 确认（防误触）：
      </NText>
      <NInput v-model:value="restartConfirmInput" placeholder="RESTART" style="margin-bottom: 12px" />
      <NText depth="3" style="font-size: 11px; display: block; margin-bottom: 14px">
        重启前请确保已部署 start.sh 守护脚本（点上方「获取 start.sh」复制）。
        关闭 Spring 后由 start.sh 自动拉起新进程。
      </NText>
      <NSpace justify="end" :size="10">
        <NButton @click="showRestartModal = false; restartConfirmInput = ''">取消</NButton>
        <NButton type="error" :loading="restarting" :disabled="restartConfirmInput !== 'RESTART'" @click="doRestart">
          确认重启
        </NButton>
      </NSpace>
    </NModal>

    <!-- ═══════ 恢复默认配置确认弹窗 ═══════ -->
    <NModal v-model:show="showResetModal" preset="card" title="恢复默认配置" style="width: 480px" :mask-closable="false">
      <NAlert type="warning" :show-icon="true" style="margin-bottom: 12px">
        将删除所有 tune.* 配置（含当前模板记录 + 出站节奏），回到<b>框架代码硬编码默认值</b>。
      </NAlert>
      <NText depth="2" style="font-size: 13px; line-height: 1.7; display: block; margin-bottom: 14px">
        立即生效：出站节奏还原为 0（不节流）。<br />
        需重启框架：调度池 / WS 连接池 / Hikari 等线程池参数会回到代码硬编码默认值（4/4-16/3）。
      </NText>
      <NFormItem label="二次确认" :show-feedback="false" style="margin-bottom: 4px">
        <NInput v-model:value="resetConfirm" placeholder="请输入 RESET 确认" />
      </NFormItem>
      <template #footer>
        <NSpace justify="end" :size="10">
          <NButton size="small" @click="showResetModal = false; resetConfirm = ''">取消</NButton>
          <NButton
            size="small"
            type="warning"
            :disabled="resetConfirm !== 'RESET'"
            :loading="resetting"
            @click="doResetTune"
          >
            <template #icon><NIcon><RefreshOutline /></NIcon></template>
            确认恢复
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.tune-page {
  width: 100%;
  max-width: 100%;
}
.tune-current-card {
  background: rgba(24, 160, 88, 0.05);
  border: 1px solid rgba(24, 160, 88, 0.18);
  border-radius: 10px;
}
.tune-current-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.tune-current-tag {
  font-weight: 600;
}
.tune-sys-card {
  background: rgba(32, 128, 224, 0.04);
  border: 1px solid rgba(32, 128, 224, 0.18);
  border-radius: 10px;
}
.tune-latency-card {
  background: rgba(91, 91, 214, 0.04);
  border: 1px solid rgba(91, 91, 214, 0.18);
  border-radius: 10px;
}
.tune-compare-card {
  background: rgba(240, 160, 32, 0.04);
  border: 1px dashed rgba(240, 160, 32, 0.35);
  border-radius: 10px;
}
.tune-actions {
  padding: 14px 16px;
  background: rgba(91, 91, 214, 0.05);
  border: 1px solid rgba(91, 91, 214, 0.18);
  border-radius: 10px;
}
.kv {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.kv-label {
  font-size: 13px;
  color: var(--n-text-color-2);
}
.cmp-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 6px;
  border: 1px solid var(--n-border-color);
}
.cmp-label {
  font-size: 12px;
  color: var(--n-text-color-2);
  font-weight: 500;
}
.cmp-pair {
  display: flex;
  gap: 12px;
  align-items: baseline;
}
.cmp-recommended,
.cmp-actual {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.cmp-recommended strong {
  color: #5b5bd6;
}
.cmp-actual strong {
  color: #f0a020;
}
.latency-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 6px;
  border: 1px solid var(--n-border-color);
}
.latency-row.is-total {
  background: rgba(91, 91, 214, 0.08);
  border: 1px solid rgba(91, 91, 214, 0.25);
  font-weight: 600;
}
.latency-row.over-budget {
  border-color: #e5484d;
}
.latency-name {
  font-size: 13px;
  font-weight: 500;
}
.latency-desc {
  font-size: 11px;
  color: var(--n-text-color-3);
}
.latency-val {
  font-size: 12px;
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.tune-risk-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.8;
  font-size: 13px;
}
</style>