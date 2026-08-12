<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NInput, NGrid, NGi, NEmpty,
  NModal, NAlert, useMessage, NSelect, NTabs, NTabPane, NForm, NFormItem,
  NRadio, NRadioGroup, NRadioButton, NSwitch, NSpin, NDivider
} from 'naive-ui'
import {
  StorefrontOutline, RefreshOutline, SearchOutline, SettingsOutline, TrashOutline, CubeOutline,
  PlayOutline, StopOutline, DownloadOutline, AddOutline, CheckmarkOutline, CloseOutline
} from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import StatCard from '../components/StatCard.vue'
import api from '../api'

const router = useRouter()
const message = useMessage()

// ═══════════════ 本地插件（Tab1） ═══════════════
const rows = ref<any[]>([])
const keyword = ref('')
const category = ref('all')
const loading = ref(false)
const scanning = ref(false)

const CATEGORIES = [
  { value: 'all', label: '全部' },
  { value: 'entertainment', label: '娱乐' },
  { value: 'tool', label: '工具' },
  { value: 'group-admin', label: '群管' },
  { value: 'service', label: '服务' },
  { value: 'other', label: '其他' }
]
const CATEGORY_LABEL: Record<string, string> = {
  entertainment: '娱乐', tool: '工具', 'group-admin': '群管', service: '服务', other: '其他'
}

const cmdStat = ref<Record<string, any>>({ execCount: 0, failCount: 0, successRate: 100, rateLimitHits: 0 })
async function loadCmdStat() {
  try {
    const o = await api.riskOverview()
    cmdStat.value = { ...o.command, rateLimitHits: o.rateLimit?.commandHits ?? 0 }
  } catch { /* 统计失败不影响列表 */ }
}

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return rows.value.filter((p) => {
    if (category.value !== 'all' && p.category !== category.value) return false
    if (!kw) return true
    return (p.name || '').toLowerCase().includes(kw) ||
      (p.id || '').toLowerCase().includes(kw) ||
      (p.description || '').toLowerCase().includes(kw)
  })
})

async function load() {
  loading.value = true
  try {
    rows.value = await api.getPlugins()
  } catch (e: any) {
    message.error('加载插件失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

async function scan() {
  scanning.value = true
  try {
    const r = await api.scanPlugins()
    const n = (r?.loaded || []).length
    message.success(n > 0 ? `扫描完成，新加载 ${n} 个插件` : '扫描完成，无新插件')
    await load()
  } catch (e: any) {
    message.error('扫描失败：' + (e?.message ?? e))
  } finally {
    scanning.value = false
  }
}

async function toggle(p: any) {
  try {
    if (p.running) {
      await api.stopPlugin(p.id)
      message.success('已停用：' + (p.name || p.id))
    } else {
      await api.startPlugin(p.id)
      message.success('已启用：' + (p.name || p.id))
    }
    await load()
  } catch (e: any) {
    message.error('操作失败：' + (e?.message ?? e))
  }
}

async function reload(p: any) {
  try {
    const r = await api.reloadPlugin(p.id)
    if (r.status === 'error') message.error('热重载失败（看后端日志）')
    else message.success('已热重载：' + (p.name || p.id))
    await load()
  } catch (e: any) {
    message.error('热重载失败：' + (e?.message ?? e))
  }
}

async function unload(p: any) {
  try {
    const r = await api.unloadPlugin(p.id)
    if (r.status === 'ok') message.success('已卸载：' + (p.name || p.id))
    else message.error('卸载失败（看后端日志）')
    await load()
  } catch (e: any) {
    message.error('卸载失败：' + (e?.message ?? e))
  }
}

const showUnloadModal = ref(false)
const confirmPlugin = ref<any>(null)
function askUnload(p: any) {
  confirmPlugin.value = p
  showUnloadModal.value = true
}
function doUnload() {
  if (confirmPlugin.value) unload(confirmPlugin.value)
  showUnloadModal.value = false
}
function openPage(p: any) {
  router.push('/plugins/p/' + p.id)
}

// ═══════════════ 插件市场（Tab2） ═══════════════
const marketPlugins = ref<any[]>([])
const marketLoading = ref(false)
const installingId = ref('')

async function loadMarket() {
  marketLoading.value = true
  try {
    marketPlugins.value = await api.marketList()
  } catch (e: any) {
    message.error('拉取插件市场失败：' + (e?.message ?? e))
  } finally {
    marketLoading.value = false
  }
}

async function install(p: any) {
  installingId.value = p.pluginId + '@' + p.version
  try {
    const r = await api.marketInstall(p.pluginId, p.version)
    if (r.status === 'ok') {
      message.success(`已下载安装 ${p.pluginId}@${p.version}，正在加载...`)
      await api.scanPlugins()
      message.success('插件已加载，可到「本地插件」查看')
      await load()
    } else {
      message.error('安装失败：' + (r.message || '未知错误'))
    }
  } catch (e: any) {
    message.error('安装失败：' + (e?.message ?? e))
  } finally {
    installingId.value = ''
  }
}

// ═══════════════ 上传 / 我的提交（Tab3） ═══════════════
const submitForm = ref({ name: '', description: '', version: '', category: 'tool', submitter: '' })
const submitJar = ref<File | null>(null)
const submitting = ref(false)
const mySubs = ref<any[]>([])

const STATUS_LABEL: Record<string, { text: string; type: 'default' | 'info' | 'success' | 'error' | 'warning' }> = {
  PENDING: { text: '审核中', type: 'warning' },
  APPROVED: { text: '已上架', type: 'success' },
  REJECTED: { text: '已拒绝', type: 'error' }
}

async function loadMySubs() {
  try {
    mySubs.value = await api.mySubmissions()
  } catch { /* 不影响 */ }
}

function onJarChange(e: Event) {
  const input = e.target as HTMLInputElement
  submitJar.value = input.files?.[0] || null
}

async function doSubmit() {
  if (!submitForm.value.name || !submitForm.value.version) {
    message.warning('请填写插件名称和版本')
    return
  }
  if (!submitJar.value) {
    message.warning('请选择要上传的 jar 包')
    return
  }
  submitting.value = true
  try {
    const fd = new FormData()
    fd.append('name', submitForm.value.name)
    fd.append('description', submitForm.value.description || '')
    fd.append('version', submitForm.value.version)
    fd.append('category', submitForm.value.category || 'other')
    fd.append('submitter', submitForm.value.submitter || '')
    fd.append('jar', submitJar.value)
    const r = await api.marketSubmit(fd)
    if (r.status === 'ok') {
      message.success('提交成功，等待审核')
      submitForm.value = { name: '', description: '', version: '', category: 'tool', submitter: '' }
      submitJar.value = null
      await loadMySubs()
    } else {
      message.error('提交失败：' + (r.message || '未知错误'))
    }
  } catch (e: any) {
    message.error('提交失败：' + (e?.message ?? e))
  } finally {
    submitting.value = false
  }
}

// ═══════════════ 审核台（Tab4） ═══════════════
const pendingList = ref<any[]>([])
const auditLoading = ref(false)
const rejectModal = ref(false)
const rejectTarget = ref<any>(null)
const rejectReason = ref('')
const approveOfficial = ref(false)
// 管理员令牌验证门：验证通过才显示审核内容与操作
const adminVerified = ref(false)
const adminTokenInput = ref('')
const adminVerifying = ref(false)
const auditLog = ref<any[]>([])

async function verifyAdmin() {
  if (!adminTokenInput.value) {
    message.warning('请输入管理员令牌')
    return
  }
  adminVerifying.value = true
  try {
    const r = await api.marketVerifyAdmin(adminTokenInput.value)
    if (r.status === 'ok') {
      adminVerified.value = true
      message.success('管理员验证通过')
      await loadPending()
      await loadAudit()
    } else {
      adminVerified.value = false
      message.error('管理员令牌错误，无审核权限')
    }
  } catch (e: any) {
    message.error('验证失败：' + (e?.message ?? e))
  } finally {
    adminVerifying.value = false
  }
}

async function loadPending() {
  auditLoading.value = true
  try {
    pendingList.value = await api.marketPending()
  } catch { /* 不影响 */ } finally {
    auditLoading.value = false
  }
}

async function loadAudit() {
  try {
    auditLog.value = await api.marketAudit()
  } catch { /* 不影响 */ }
}

function pendingDownloadUrl(p: any) {
  return `/xuanji/api/v1/console/market/pending/${encodeURIComponent(p.submissionId)}/download?adminToken=${encodeURIComponent(adminTokenInput.value)}`
}

async function doApprove(p: any) {
  try {
    const r = await api.marketApprove(p.submissionId, approveOfficial.value, adminTokenInput.value)
    if (r.status === 'ok') {
      message.success('已上架：' + (p.name || p.pluginId))
      await loadPending()
      await loadMarket()
      await loadAudit()
    } else {
      message.error('操作失败：' + (r.message || '未知错误'))
    }
  } catch (e: any) {
    message.error('操作失败：' + (e?.message ?? e))
  }
}

function openReject(p: any) {
  rejectTarget.value = p
  rejectReason.value = ''
  rejectModal.value = true
}

async function doReject() {
  try {
    const r = await api.marketReject(rejectTarget.value.submissionId, rejectReason.value, adminTokenInput.value)
    if (r.status === 'ok') {
      message.success('已拒绝：' + (rejectTarget.value.name || rejectTarget.value.pluginId))
      rejectModal.value = false
      await loadPending()
      await loadAudit()
    } else {
      message.error('操作失败：' + (r.message || '未知错误'))
    }
  } catch (e: any) {
    message.error('操作失败：' + (e?.message ?? e))
  }
}

// ═══════════════ 市场状态（内置配置，无需设置页） ═══════════════
const marketStatus = ref({ enabled: true, repoUrl: '', hasUploadToken: true, hasAdminToken: true })

async function loadStatus() {
  try {
    marketStatus.value = await api.marketSettings()
  } catch { /* 不影响 */ }
}

onMounted(() => {
  load()
  loadCmdStat()
  loadMarket()
  loadMySubs()
  loadPending()
  loadStatus()
})
</script>

<template>
  <div>
    <PageHero title="插件市场" subtitle="本地插件管理 + 中央插件库（浏览 / 上传 / 审核 / 安装）" :icon="StorefrontOutline" />

    <NTabs type="line" animated>
      <!-- ══════ Tab1 本地插件 ══════ -->
      <NTabPane name="local" tab="本地插件">
        <NSpace align="center" style="margin-bottom: 12px">
          <NButton type="primary" :loading="scanning" @click="scan">
            <template #icon><NIcon><RefreshOutline /></NIcon></template>
            扫描新插件
          </NButton>
          <NInput v-model:value="keyword" placeholder="搜索插件名 / ID / 描述" clearable style="width: 220px">
            <template #prefix><NIcon :component="SearchOutline" /></template>
          </NInput>
          <NSelect v-model:value="category" :options="CATEGORIES" size="small" style="width: 140px" />
        </NSpace>

        <NEmpty v-if="!loading && !filtered.length" description="暂无插件，点「扫描新插件」或把 jar 放入 plugins/ 目录" style="padding: 60px 0" />

        <NGrid :cols="24" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 16px">
          <NGi span="24 s:12 m:8 l:6">
            <StatCard :icon="PlayOutline" color="#5b5bd6" :value="Number(cmdStat.execCount ?? 0)" label="命令执行次数（进程累计）" />
          </NGi>
          <NGi span="24 s:12 m:8 l:6">
            <StatCard :icon="StopOutline" color="#e5484d" :value="Number(cmdStat.failCount ?? 0)" label="命令执行异常" />
          </NGi>
          <NGi span="24 s:12 m:8 l:6">
            <StatCard :icon="PlayOutline" color="#18a058" :value="(cmdStat.successRate ?? 100) + '%'" label="命令成功率" :animate="false" />
          </NGi>
          <NGi span="24 s:12 m:8 l:6">
            <StatCard :icon="SettingsOutline" color="#f0a020" :value="Number(cmdStat.rateLimitHits ?? 0)" label="命令限速命中（@Command rateLimit）" />
          </NGi>
        </NGrid>

        <NGrid :cols="24" :x-gap="16" :y-gap="16" responsive="screen" item-responsive class="grid">
          <NGi v-for="p in filtered" :key="p.id" span="24 m:12 l:8 xl:6">
            <NCard hoverable class="p-card" @click="openPage(p)">
              <div class="p-head">
                <div class="p-icon"><NIcon size="20"><CubeOutline /></NIcon></div>
                <div class="p-title">
                  <div class="p-name">
                    <NText strong>{{ p.name || p.id }}</NText>
                    <NTag size="small" :bordered="false" :type="p.running ? 'success' : 'default'" round>{{ p.running ? '运行中' : '已停用' }}</NTag>
                    <NTag size="small" :bordered="false" :type="p.origin === 'official' ? 'info' : 'warning'" round>{{ p.origin === 'official' ? '官方' : '社区' }}</NTag>
                    <NTag size="small" :bordered="false" type="primary" round>{{ CATEGORY_LABEL[p.category] || '其他' }}</NTag>
                  </div>
                  <NText depth="3" style="font-size: 12px">{{ p.id }} · v{{ p.version }}</NText>
                </div>
              </div>
              <NText depth="3" class="p-desc">{{ p.description || '—' }}</NText>
              <div class="p-author">作者：{{ p.provider || '—' }}</div>
              <div class="p-ops" @click.stop>
                <NButton
                  size="small" block
                  :type="p.running ? 'warning' : 'primary'"
                  :disabled="loading"
                  class="op-main"
                  @click="toggle(p)"
                >
                  <template #icon><NIcon size="14"><component :is="p.running ? StopOutline : PlayOutline" /></NIcon></template>
                  {{ p.running ? '停用' : '启用' }}
                </NButton>
                <NGrid :cols="3" :x-gap="6" class="op-sub">
                  <NGi><NButton size="small" secondary block @click="reload(p)">
                    <template #icon><NIcon size="14"><RefreshOutline /></NIcon></template>重载
                  </NButton></NGi>
                  <NGi><NButton size="small" secondary block @click="openPage(p)">
                    <template #icon><NIcon size="14"><SettingsOutline /></NIcon></template>配置
                  </NButton></NGi>
                  <NGi><NButton size="small" type="error" ghost block @click="askUnload(p)">
                    <template #icon><NIcon size="14"><TrashOutline /></NIcon></template>卸载
                  </NButton></NGi>
                </NGrid>
              </div>
            </NCard>
          </NGi>
        </NGrid>
      </NTabPane>

      <!-- ══════ Tab2 插件市场 ══════ -->
      <NTabPane name="store" tab="插件市场">
        <NSpace align="center" style="margin-bottom: 12px">
          <NButton :loading="marketLoading" @click="loadMarket">
            <template #icon><NIcon><RefreshOutline /></NIcon></template>
            刷新市场
          </NButton>
          <NText depth="3" style="font-size: 12px">
            {{ marketStatus.repoUrl || '官方市场仓库' }} · 已上架 {{ marketPlugins.length }} 个插件
          </NText>
        </NSpace>
        <NEmpty v-if="!marketLoading && !marketPlugins.length" description="市场暂无已上架插件" style="padding: 60px 0" />
        <NGrid :cols="24" :x-gap="16" :y-gap="16" responsive="screen" item-responsive>
          <NGi v-for="p in marketPlugins" :key="p.pluginId + '@' + p.version" span="24 m:12 l:8 xl:6">
            <NCard hoverable class="p-card">
              <div class="p-head">
                <div class="p-icon"><NIcon size="20"><CubeOutline /></NIcon></div>
                <div class="p-title">
                  <div class="p-name">
                    <NText strong>{{ p.name || p.pluginId }}</NText>
                    <NTag v-if="p.official" size="small" :bordered="false" type="info" round>官方</NTag>
                    <NTag size="small" :bordered="false" type="primary" round>{{ CATEGORY_LABEL[p.category] || '其他' }}</NTag>
                  </div>
                  <NText depth="3" style="font-size: 12px">{{ p.pluginId }} · v{{ p.version }}</NText>
                </div>
              </div>
              <NText depth="3" class="p-desc">{{ p.description || '—' }}</NText>
              <div class="p-author">作者：{{ p.author || '—' }}</div>
              <NButton
                type="primary" size="small" block
                :loading="installingId === p.pluginId + '@' + p.version"
                @click="install(p)"
              >
                <template #icon><NIcon size="14"><DownloadOutline /></NIcon></template>
                一键安装
              </NButton>
            </NCard>
          </NGi>
        </NGrid>
      </NTabPane>

      <!-- ══════ Tab3 上传插件 ══════ -->
      <NTabPane name="submit" tab="上传插件">
        <NGrid :cols="24" :x-gap="20" responsive="screen" item-responsive>
          <NGi span="24 l:12">
            <NCard title="提交插件到市场" size="small">
              <NForm label-placement="left" label-width="80">
                <NFormItem label="插件名称"><NInput v-model:value="submitForm.name" placeholder="如：接口测试插件" /></NFormItem>
                <NFormItem label="版本"><NInput v-model:value="submitForm.version" placeholder="如：1.3.1" style="width: 200px" /></NFormItem>
                <NFormItem label="分类">
                  <NRadioGroup v-model:value="submitForm.category">
                    <NRadioButton value="entertainment">娱乐</NRadioButton>
                    <NRadioButton value="tool">工具</NRadioButton>
                    <NRadioButton value="group-admin">群管</NRadioButton>
                    <NRadioButton value="service">服务</NRadioButton>
                    <NRadioButton value="other">其他</NRadioButton>
                  </NRadioGroup>
                </NFormItem>
                <NFormItem label="作者"><NInput v-model:value="submitForm.submitter" placeholder="昵称（选填）" style="width: 240px" /></NFormItem>
                <NFormItem label="描述"><NInput v-model:value="submitForm.description" type="textarea" :rows="3" placeholder="插件功能说明（选填）" /></NFormItem>
                <NFormItem label="jar 包">
                  <input type="file" accept=".jar" class="jar-input" @change="onJarChange" />
                  <NText v-if="submitJar" depth="3" style="font-size: 12px; margin-left: 8px">
                    {{ submitJar.name }}（{{ (submitJar.size / 1024).toFixed(1) }} KB）
                  </NText>
                </NFormItem>
                <NSpace>
                  <NButton type="primary" :loading="submitting" @click="doSubmit">
                    <template #icon><NIcon><AddOutline /></NIcon></template>
                    提交审核
                  </NButton>
                  <NText depth="3" style="font-size: 12px">提交后状态为「审核中」，管理员通过后自动上架</NText>
                </NSpace>
              </NForm>
            </NCard>
          </NGi>
          <NGi span="24 l:12">
            <NCard title="我的提交" size="small">
              <NEmpty v-if="!mySubs.length" description="还没有提交过插件" style="padding: 40px 0" />
              <div v-for="s in mySubs" :key="s.submissionId" class="sub-row">
                <div class="sub-info">
                  <NText strong>{{ s.name || s.pluginId }}</NText>
                  <NText depth="3" style="font-size: 12px; margin-left: 8px">{{ s.pluginId }} · v{{ s.version }}</NText>
                </div>
                <NTag size="small" :bordered="false" :type="STATUS_LABEL[s.status]?.type || 'default'" round>
                  {{ STATUS_LABEL[s.status]?.text || s.status }}
                </NTag>
                <NText v-if="s.status === 'REJECTED' && s.rejectReason" depth="3" style="font-size: 12px; display: block; margin-top: 2px">
                  拒绝理由：{{ s.rejectReason }}
                </NText>
              </div>
              <NButton v-if="mySubs.length" size="small" quaternary @click="loadMySubs">
                <template #icon><NIcon><RefreshOutline /></NIcon></template>刷新状态
              </NButton>
            </NCard>
          </NGi>
        </NGrid>
      </NTabPane>

      <!-- ══════ Tab4 审核台 ══════ -->
      <NTabPane name="audit" tab="审核台">
        <!-- 管理员令牌验证门 -->
        <NAlert v-if="!adminVerified" type="warning" :show-icon="true" style="max-width: 640px; margin-bottom: 12px"
                title="需要管理员令牌">
          审核台仅管理员可用。请输入管理员令牌（设置页配置的 market.admin_token）验证权限，
          验证通过后才能查看待审插件并执行通过/拒绝操作。
        </NAlert>
        <NCard v-if="!adminVerified" size="small" style="max-width: 640px">
          <NSpace align="center">
            <NInput v-model:value="adminTokenInput" type="password" show-password-on="click"
                    placeholder="输入管理员令牌" style="width: 320px" />
            <NButton type="primary" :loading="adminVerifying" @click="verifyAdmin">
              <template #icon><NIcon><CheckmarkOutline /></NIcon></template>
              验证身份
            </NButton>
          </NSpace>
        </NCard>

        <template v-if="adminVerified">
          <NSpace align="center" style="margin-bottom: 12px">
            <NButton :loading="auditLoading" @click="loadPending">
              <template #icon><NIcon><RefreshOutline /></NIcon></template>
              刷新待审
            </NButton>
            <NButton quaternary @click="loadAudit">
              <template #icon><NIcon><RefreshOutline /></NIcon></template>
              刷新记录
            </NButton>
            <NText depth="3" style="font-size: 12px">管理员：下载 jar → 隔离 bot 测试 → 通过上架 / 拒绝</NText>
          </NSpace>
          <NEmpty v-if="!auditLoading && !pendingList.length" description="暂无待审核的插件提交" style="padding: 40px 0" />
          <NCard v-for="p in pendingList" :key="p.submissionId" size="small" class="pending-card">
            <div class="pending-row">
              <div class="sub-info">
                <NText strong>{{ p.name || p.pluginId }}</NText>
                <NTag size="small" :bordered="false" type="warning" round style="margin-left: 8px">审核中</NTag>
                <NTag size="small" :bordered="false" type="primary" round>{{ CATEGORY_LABEL[p.category] || '其他' }}</NTag>
                <div style="margin-top: 2px">
                  <NText depth="3" style="font-size: 12px">{{ p.pluginId }} · v{{ p.version }} · 提交人 {{ p.submitter || 'unknown' }} · {{ (p.submittedAt || '').replace('T', ' ').slice(0, 19) }}</NText>
                </div>
                <NText v-if="p.description" depth="3" style="font-size: 12px; display: block; margin-top: 4px">{{ p.description }}</NText>
              </div>
              <div class="pending-ops">
                <NSwitch v-model:value="approveOfficial" size="small" style="margin-right: 8px">
                  <template #checked>官方</template>
                  <template #unchecked>社区</template>
                </NSwitch>
                <NButton size="small" tag="a" :href="pendingDownloadUrl(p)" target="_blank" download secondary>
                  <template #icon><NIcon size="14"><DownloadOutline /></NIcon></template>下载测试
                </NButton>
                <NButton size="small" type="success" @click="doApprove(p)">
                  <template #icon><NIcon size="14"><CheckmarkOutline /></NIcon></template>通过
                </NButton>
                <NButton size="small" type="error" ghost @click="openReject(p)">
                  <template #icon><NIcon size="14"><CloseOutline /></NIcon></template>拒绝
                </NButton>
              </div>
            </div>
          </NCard>

          <!-- 审核记录 -->
          <NCard title="审核记录" size="small" style="margin-top: 16px">
            <NEmpty v-if="!auditLog.length" description="暂无审核记录" style="padding: 24px 0" />
            <div v-for="a in auditLog" :key="a.submissionId + a.time" class="sub-row">
              <div class="sub-info">
                <NText strong>{{ a.name || a.pluginId }}</NText>
                <NText depth="3" style="font-size: 12px; margin-left: 8px">{{ a.pluginId }} · v{{ a.version }}</NText>
                <NTag size="small" :bordered="false" :type="a.action === 'APPROVED' ? 'success' : 'error'" round style="margin-left: 8px">
                  {{ a.action === 'APPROVED' ? '已上架' : '已拒绝' }}
                </NTag>
                <NTag v-if="a.official" size="small" :bordered="false" type="info" round style="margin-left: 4px">官方</NTag>
              </div>
              <NText depth="3" style="font-size: 12px; display: block; margin-top: 2px">
                {{ (a.time || '').replace('T', ' ').slice(0, 19) }}{{ a.reason ? ' · 理由：' + a.reason : '' }}
              </NText>
            </div>
          </NCard>
        </template>

        <NModal v-model:show="rejectModal" preset="card" title="拒绝插件" style="width: 420px; max-width: 92vw" :bordered="false">
          <NInput v-model:value="rejectReason" type="textarea" :rows="3" placeholder="拒绝理由（开发者可见，选填）" />
          <template #footer>
            <NSpace justify="end">
              <NButton size="small" @click="rejectModal = false">取消</NButton>
              <NButton size="small" type="error" @click="doReject">确认拒绝</NButton>
            </NSpace>
          </template>
        </NModal>
      </NTabPane>

    </NTabs>

    <!-- 卸载确认弹窗（本地插件） -->
    <NModal
      v-model:show="showUnloadModal"
      preset="card"
      :title="`确认卸载插件 · ${confirmPlugin?.name || confirmPlugin?.id || ''}`"
      style="width: 500px; max-width: 92vw"
      :bordered="false"
    >
      <NAlert v-if="confirmPlugin" type="warning" :show-icon="true" title="此操作不可恢复">
        即将卸载插件「{{ confirmPlugin.name || confirmPlugin.id }}」，将执行以下操作：
      </NAlert>
      <NText tag="div" depth="3" style="display: block; margin: 12px 0 4px; font-size: 13px">影响范围：</NText>
      <ul class="unload-list">
        <li>关闭插件的 Spring 子容器</li>
        <li>反注册全部命令与事件处理器</li>
        <li>删除插件 jar 文件（plugins/&lt;id&gt;.jar）</li>
        <li>清除该插件的持久化数据（xuanji_plugin_kv 全部记录）</li>
      </ul>
      <template #footer>
        <NSpace justify="end" :size="8">
          <NButton size="small" @click="showUnloadModal = false">取消</NButton>
          <NButton size="small" type="error" @click="doUnload">确认卸载</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.grid { margin-top: 4px; }
.p-card { height: 100%; cursor: pointer; }
.p-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.p-icon {
  width: 36px; height: 36px; border-radius: 10px; flex-shrink: 0;
  background: #e6f1fb; color: #185fa5;
  display: flex; align-items: center; justify-content: center;
}
.p-title { min-width: 0; flex: 1; }
.p-name { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.p-desc {
  display: block; font-size: 12px; min-height: 36px; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.p-author { font-size: 12px; color: var(--n-text-color-3); margin: 6px 0 12px; }
.p-ops { display: flex; flex-direction: column; gap: 6px; }
.op-main { border-radius: 7px; }
.op-sub :deep(.n-button) { border-radius: 6px; padding-left: 6px; padding-right: 6px; }
.unload-list { margin: 0; padding-left: 20px; line-height: 1.9; font-size: 13px; color: var(--n-text-color-2); }
.jar-input { font-size: 13px; }
.sub-row { display: flex; flex-direction: column; padding: 10px 0; border-bottom: 1px dashed var(--n-border-color); }
.sub-info { display: flex; align-items: center; flex-wrap: wrap; }
.pending-card { margin-bottom: 12px; }
.pending-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.pending-ops { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
</style>
