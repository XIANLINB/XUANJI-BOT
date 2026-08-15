<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NInput, NGrid, NGi, NEmpty,
  NModal, NAlert, useMessage, NSelect, NForm, NFormItem, NSpin, NDivider,
  NDrawer, NDrawerContent, NPopconfirm, NTooltip, NScrollbar, NList, NListItem
} from 'naive-ui'
import {
  StorefrontOutline, RefreshOutline, SearchOutline, SettingsOutline, TrashOutline, CubeOutline,
  PlayOutline, StopOutline, DownloadOutline, AddOutline, CheckmarkOutline, DocumentOutline,
  CloudUploadOutline, TimeOutline
} from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import StatCard from '../components/StatCard.vue'
import EmptyState from '../components/EmptyState.vue'
import api from '../api'
import dayjs from 'dayjs'

const router = useRouter()
const message = useMessage()

// ═══════════════ 本地插件 ═══════════════
const rows = ref<any[]>([])
const keyword = ref('')
const category = ref('all')
const sourceFilter = ref('all') // all / local / market
const loading = ref(false)
const scanning = ref(false)

const CATEGORIES = [
  { value: 'all', label: '全部分类' },
  { value: 'entertainment', label: '娱乐' },
  { value: 'tool', label: '工具' },
  { value: 'group-admin', label: '群管' },
  { value: 'service', label: '服务' },
  { value: 'other', label: '其他' }
]
const SOURCE_OPTIONS = [
  { value: 'all', label: '全部来源' },
  { value: 'local', label: '仅本地' },
  { value: 'market', label: '仅市场' }
]
const CATEGORY_LABEL: Record<string, string> = {
  entertainment: '娱乐', tool: '工具', 'group-admin': '群管', service: '服务', other: '其他'
}

const cmdStat = ref<Record<string, any>>({ execCount: 0, failCount: 0, successRate: 100, rateLimitHits: 0 })
async function loadCmdStat() {
  try {
    cmdStat.value = await api.pluginCommandStats()
  } catch { /* 统计失败不影响列表 */ }
}

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

// ═══════════════ 插件市场（已上架） ═══════════════
const marketPlugins = ref<any[]>([])
const marketLoading = ref(false)
const installingId = ref('')
// 本地已装插件映射（pluginId → version），用于市场列表标记 已安装/有更新
const localInstalled = computed<Record<string, string>>(() => {
  const m: Record<string, string> = {}
  for (const p of rows.value) m[p.id] = String(p.version)
  return m
})

async function loadMarket() {
  marketLoading.value = true
  try {
    marketPlugins.value = await api.marketReleased()
  } catch (e: any) {
    message.error('拉取插件市场失败：' + (e?.message ?? e))
  } finally {
    marketLoading.value = false
  }
}

function localState(p: any): 'update' | null {
  const v = localInstalled.value[p.pluginId]
  return (v !== undefined && v !== String(p.version)) ? 'update' : null
}

async function install(p: any) {
  installingId.value = p.pluginId + '@' + p.version
  try {
    const r = await api.marketInstall(p.pluginId, p.version)
    if (r.status !== 'ok') {
      message.error('安装失败：' + (r.message || '未知错误'))
      return
    }
    const scan = await api.scanPlugins()
    const loaded = (scan?.loaded || []).length
    message.success(loaded > 0
      ? `已安装 ${p.pluginId}@${p.version}，加载完成，可到「本地」分类查看`
      : `已下载 ${p.pluginId}@${p.version}，但扫描未发现新插件（可能已装同版本）`)
    await load()
  } catch (e: any) {
    message.error('安装失败：' + (e?.message ?? e))
  } finally {
    installingId.value = ''
  }
}

// ═══════════════ 合并视图（只展示本地 + 已上架） ═══════════════
const merged = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const matchCategory = (c: string) => category.value === 'all' || c === category.value
  const matchKw = (name: string, id: string, desc: string) =>
    !kw || (name || '').toLowerCase().includes(kw) ||
    (id || '').toLowerCase().includes(kw) || (desc || '').toLowerCase().includes(kw)
  const items: any[] = []
  // 本地插件
  if (sourceFilter.value !== 'market') {
    for (const p of rows.value) {
      if (!matchCategory(p.category)) continue
      if (!matchKw(p.name, p.id, p.description)) continue
      items.push({ ...p, _source: 'local', _key: 'local:' + p.id })
    }
  }
  // 已上架插件（排除已下架 + 同版本已装去重）
  if (sourceFilter.value !== 'local') {
    for (const p of marketPlugins.value) {
      if (p.status && p.status !== '已上架') continue
      if (!matchCategory(p.category)) continue
      if (!matchKw(p.name, p.pluginId, p.description)) continue
      const lv = localInstalled.value[p.pluginId]
      if (lv !== undefined && lv === String(p.version)) continue
      items.push({ ...p, _source: 'market', _key: 'market:' + p.pluginId + '@' + p.version })
    }
  }
  return items
})

// ═══════════════ 上传插件（弹窗） ═══════════════
const showUpload = ref(false)
const submitForm = ref({
  id: '', name: '', version: '', author: '', description: '',
  permissions: '', dependsOn: '', rateLimit: '', platforms: ''
})
const submitJar = ref<File | null>(null)
const submitting = ref(false)
const extractingJar = ref(false)
const declaredMeta = ref<any>(null)

function resetSubmit() {
  submitForm.value = { id: '', name: '', version: '', author: '', description: '', permissions: '', dependsOn: '', rateLimit: '', platforms: '' }
  submitJar.value = null
  declaredMeta.value = null
}

function onJarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0] || null
  submitJar.value = file
  declaredMeta.value = null
  if (!file) return
  extractingJar.value = true
  const fd = new FormData()
  fd.append('jar', file)
  api.marketExtract(fd).then((decl: any) => {
    if (decl && decl.declared) {
      declaredMeta.value = decl
      submitForm.value.id = decl.id || submitForm.value.id
      submitForm.value.name = decl.name || submitForm.value.name
      submitForm.value.version = decl.version || submitForm.value.version
      submitForm.value.author = decl.author || submitForm.value.author
      submitForm.value.description = decl.description || submitForm.value.description
      submitForm.value.permissions = decl.permissions || submitForm.value.permissions
      submitForm.value.dependsOn = decl.dependsOn || submitForm.value.dependsOn
      submitForm.value.rateLimit = decl.rateLimit || submitForm.value.rateLimit
      submitForm.value.platforms = decl.platforms || submitForm.value.platforms
      message.success('已读取插件声明，表单已自动填充并锁定（与 @XuanJiPlugin 一致，不可修改）')
    } else {
      declaredMeta.value = null
      message.warning('未能读取插件声明：' + (decl?.error || '不是合法插件包') + '（仍可手动填写，但提交将校验一致性）')
    }
  }).catch((err: any) => {
    declaredMeta.value = null
    message.warning('读取插件声明失败：' + (err?.message ?? err))
  }).finally(() => {
    extractingJar.value = false
    // 重置 file input，便于重新选择同一文件
    ;(e.target as HTMLInputElement).value = ''
  })
}

async function doSubmit() {
  if (!submitForm.value.id || !submitForm.value.name || !submitForm.value.version) {
    message.warning('请填写插件 ID、名称和版本')
    return
  }
  if (!submitJar.value) {
    message.warning('请选择要上传的 jar 包')
    return
  }
  submitting.value = true
  try {
    // id/permissions/dependsOn/rateLimit/platforms 由后端从 jar 声明重新提取并写入 meta.json（jar 为权威源），
    // 表单仅提交 name/description/version/author（后端声明校验一致），故无需重复提交上述锁定字段。
    const fd = new FormData()
    fd.append('name', submitForm.value.name)
    fd.append('description', submitForm.value.description || '')
    fd.append('version', submitForm.value.version)
    fd.append('author', submitForm.value.author || '')
    fd.append('jar', submitJar.value)
    const r = await api.marketSubmit(fd)
    if (r.status === 'ok') {
      message.success('提交成功，等待审核（分类由管理员审核时选择）')
      resetSubmit()
      showUpload.value = false
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

// ═══════════════ 上传记录（抽屉 = 我的提交） ═══════════════
const showRecords = ref(false)
const mySubs = ref<any[]>([])

const STATUS_LABEL: Record<string, { text: string; type: 'default' | 'info' | 'success' | 'error' | 'warning' }> = {
  审核中: { text: '审核中', type: 'warning' },
  已上架: { text: '已上架', type: 'success' },
  拒绝上架: { text: '已拒绝', type: 'error' }
}

async function loadMySubs() {
  try {
    mySubs.value = await api.mySubmissions()
  } catch { /* 不影响 */ }
}

/** 提交时间（后端存 Instant UTC ISO，如 2026-08-15T02:09:55Z）→ UTC+8 展示。 */
function fmtSubTime(v: string): string {
  if (!v) return '—'
  const d = dayjs(v)
  return d.isValid() ? d.utcOffset(8).format('YYYY-MM-DD HH:mm:ss') : v
}

const marketStatus = ref({ enabled: true, repoUrl: '', hasUploadToken: true })
async function loadStatus() {
  try {
    marketStatus.value = await api.marketSettings()
  } catch { /* 不影响 */ }
}

async function reloadAll() {
  await Promise.all([load(), loadMarket(), loadCmdStat(), loadStatus(), loadMySubs()])
}

onMounted(() => {
  reloadAll()
})
</script>

<template>
  <div>
    <PageHero title="插件市场" subtitle="本地插件 + 中央插件库（浏览 / 上传 / 审核 / 安装）" :icon="StorefrontOutline" />

    <!-- 命令统计 -->
    <NGrid :cols="24" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 14px">
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

    <!-- 工具栏 -->
    <NCard size="small" :bordered="false" class="toolbar">
      <NSpace vertical :size="10">
        <NSpace align="center" justify="space-between" wrap>
          <NSpace align="center">
            <NButton type="primary" @click="showUpload = true">
              <template #icon><NIcon><CloudUploadOutline /></NIcon></template>
              上传插件
            </NButton>
            <NButton @click="showRecords = true">
              <template #icon><NIcon><DocumentOutline /></NIcon></template>
              上传记录
            </NButton>
          </NSpace>
          <NSpace align="center">
            <NButton :loading="scanning" secondary @click="scan">
              <template #icon><NIcon><RefreshOutline /></NIcon></template>扫描
            </NButton>
            <NButton :loading="loading || marketLoading" secondary @click="reloadAll">
              <template #icon><NIcon><RefreshOutline /></NIcon></template>刷新
            </NButton>
          </NSpace>
        </NSpace>
        <NSpace align="center" wrap>
          <NInput v-model:value="keyword" placeholder="搜索名称 / ID / 描述" clearable style="width: 240px">
            <template #prefix><NIcon :component="SearchOutline" /></template>
          </NInput>
          <NSelect v-model:value="category" :options="CATEGORIES" size="small" style="width: 150px" />
          <NSelect v-model:value="sourceFilter" :options="SOURCE_OPTIONS" size="small" style="width: 140px" />
          <NText depth="3" style="font-size: 12px">共 {{ merged.length }} 个插件</NText>
        </NSpace>
      </NSpace>
    </NCard>

    <NSpin :show="loading || marketLoading" style="margin-top: 14px">
      <EmptyState v-if="!merged.length" :description="loading || marketLoading ? '加载中…' : '暂无插件'" />

      <div v-else class="grid">
        <NGrid :cols="24" :x-gap="16" :y-gap="16" responsive="screen" item-responsive>
          <NGi v-for="p in merged" :key="p._key" span="24 m:12 l:8 xl:6">
            <!-- ── 本地插件卡片 ── -->
            <NCard v-if="p._source === 'local'" hoverable class="p-card" @click="openPage(p)">
              <div class="p-head">
                <div class="p-icon"><NIcon size="20"><CubeOutline /></NIcon></div>
                <div class="p-title">
                  <div class="p-name">
                    <NText strong>{{ p.name || p.id }}</NText>
                    <NTag size="small" :bordered="false" type="success" round>本地</NTag>
                    <NTag size="small" :bordered="false" :type="p.running ? 'success' : 'default'" round>{{ p.running ? '运行中' : '已停用' }}</NTag>
                    <NTag size="small" :bordered="false" :type="p.origin === 'official' ? 'info' : 'warning'" round>{{ p.origin === 'official' ? '官方' : '社区' }}</NTag>
                    <NTag size="small" :bordered="false" type="primary" round>{{ CATEGORY_LABEL[p.category] || '其他' }}</NTag>
                  </div>
                  <NText depth="3" style="font-size: 12px">{{ p.id }} · v{{ p.version }}</NText>
                </div>
              </div>
              <NText depth="3" class="p-desc">{{ p.description || '—' }}</NText>
              <div class="p-author">作者：{{ p.author || p.provider || '—' }}</div>
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
                  <NGi>
                    <NButton size="small" type="error" ghost block @click="askUnload(p)">
                      <template #icon><NIcon size="14"><TrashOutline /></NIcon></template>卸载
                    </NButton>
                  </NGi>
                </NGrid>
              </div>
            </NCard>

            <!-- ── 市场插件卡片 ── -->
            <NCard v-else hoverable class="p-card">
              <div class="p-head">
                <div class="p-icon"><NIcon size="20"><CubeOutline /></NIcon></div>
                <div class="p-title">
                  <div class="p-name">
                    <NText strong>{{ p.name || p.pluginId }}</NText>
                    <NTag size="small" :bordered="false" type="info" round>市场</NTag>
                    <NTag v-if="p.official" size="small" :bordered="false" type="info" round>官方</NTag>
                    <NTag size="small" :bordered="false" type="primary" round>{{ CATEGORY_LABEL[p.category] || '其他' }}</NTag>
                    <NTag v-if="localState(p) === 'update'" size="small" :bordered="false" type="warning" round>有更新</NTag>
                  </div>
                  <NText depth="3" style="font-size: 12px">{{ p.pluginId }} · v{{ p.version }}</NText>
                </div>
              </div>
              <NText depth="3" class="p-desc">{{ p.description || '—' }}</NText>
              <div class="p-author">
                作者：{{ p.author || '—' }}
                <span v-if="localState(p) === 'update'" style="color: var(--n-text-color-3)">· 本地已装 v{{ localInstalled[p.pluginId] }}</span>
              </div>
              <div class="p-ops" @click.stop>
                <NButton v-if="localState(p) === 'update'" size="small" block disabled type="default">
                  已装旧版 v{{ localInstalled[p.pluginId] }}，请先卸载
                </NButton>
                <NButton
                  v-else type="primary" size="small" block
                  :loading="installingId === p.pluginId + '@' + p.version"
                  @click="install(p)"
                >
                  <template #icon><NIcon size="14"><DownloadOutline /></NIcon></template>
                  一键安装
                </NButton>
              </div>
            </NCard>
          </NGi>
        </NGrid>
      </div>
    </NSpin>

    <!-- 上传插件弹窗 -->
    <NModal
      v-model:show="showUpload"
      preset="card"
      title="上传插件"
      style="width: 680px; max-width: 94vw"
      :bordered="false"
      @after-leave="resetSubmit"
    >
      <NAlert type="info" :show-icon="true" style="margin-bottom: 14px">
        提交后状态为「审核中」，管理员通过后自动上架。分类由管理员在审核时选择，此处无需填写。
      </NAlert>
      <NForm label-placement="left" label-width="92">
        <NFormItem label="jar 包">
          <div>
            <input type="file" accept=".jar" class="jar-input" @change="onJarChange" :disabled="extractingJar" />
            <NSpace align="center" :size="6" style="margin-top: 8px">
              <NText v-if="extractingJar" depth="3" style="font-size: 12px">正在读取插件声明…</NText>
              <NText v-else-if="submitJar" depth="3" style="font-size: 12px">
                {{ submitJar.name }}（{{ (submitJar.size / 1024).toFixed(1) }} KB）
              </NText>
              <NTag v-if="declaredMeta" size="small" :bordered="false" type="success" round>已读取声明</NTag>
            </NSpace>
            <NText v-if="declaredMeta" depth="3" style="font-size: 12px; display: block; margin-top: 4px">
              字段已锁定，如需修改请重新选择 jar 包
            </NText>
          </div>
        </NFormItem>

        <template v-if="submitJar">
          <NGrid :cols="2" :x-gap="12">
            <NGi><NFormItem label="插件ID"><NInput v-model:value="submitForm.id" placeholder="须与 @XuanJiPlugin.id 一致" :disabled="!!declaredMeta" /></NFormItem></NGi>
            <NGi><NFormItem label="插件名称"><NInput v-model:value="submitForm.name" placeholder="如：接口测试插件" :disabled="!!declaredMeta" /></NFormItem></NGi>
            <NGi><NFormItem label="插件版本"><NInput v-model:value="submitForm.version" placeholder="如：1.3.1" :disabled="!!declaredMeta" /></NFormItem></NGi>
            <NGi><NFormItem label="插件作者"><NInput v-model:value="submitForm.author" placeholder="须与 @XuanJiPlugin.author 一致" :disabled="!!declaredMeta" /></NFormItem></NGi>
          </NGrid>
          <NFormItem label="插件描述"><NInput v-model:value="submitForm.description" type="textarea" :rows="3" placeholder="插件功能说明（选填）" :disabled="!!declaredMeta" /></NFormItem>
          <NGrid :cols="2" :x-gap="12">
            <NGi><NFormItem label="权限声明"><NInput v-model:value="submitForm.permissions" placeholder="GROUP_ADMIN, NETWORK" :disabled="!!declaredMeta" /></NFormItem></NGi>
            <NGi><NFormItem label="依赖能力"><NInput v-model:value="submitForm.dependsOn" placeholder="EconomyService" :disabled="!!declaredMeta" /></NFormItem></NGi>
            <NGi><NFormItem label="频率限制"><NInput v-model:value="submitForm.rateLimit" placeholder="秒，0=不限制" :disabled="!!declaredMeta" /></NFormItem></NGi>
            <NGi><NFormItem label="插件平台"><NInput v-model:value="submitForm.platforms" placeholder="qq, discord（空=全部）" :disabled="!!declaredMeta" /></NFormItem></NGi>
          </NGrid>
        </template>
      </NForm>
      <template #footer>
        <NSpace justify="end" :size="8">
          <NButton size="small" @click="showUpload = false">取消</NButton>
          <NButton size="small" type="primary" :loading="submitting" :disabled="!submitJar" @click="doSubmit">
            <template #icon><NIcon><AddOutline /></NIcon></template>
            提交审核
          </NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- 上传记录抽屉（我的提交） -->
    <NDrawer v-model:show="showRecords" :width="440" placement="right">
      <NDrawerContent :native-scrollbar="false">
        <template #header>
          <NSpace align="center">
            <NIcon><DocumentOutline /></NIcon>
            <span>上传记录（我的提交）</span>
          </NSpace>
        </template>
        <NSpace align="center" justify="space-between" style="margin-bottom: 12px">
          <NText depth="3" style="font-size: 12px">本机发起的全部上传及当前状态</NText>
          <NButton size="small" quaternary @click="loadMySubs">
            <template #icon><NIcon><RefreshOutline /></NIcon></template>刷新
          </NButton>
        </NSpace>
        <EmptyState v-if="!mySubs.length" description="还没有提交过插件" />
        <NScrollbar v-else style="max-height: calc(100vh - 120px)">
          <NList>
            <NListItem v-for="s in mySubs" :key="s.submissionId" class="rec-item">
              <div class="rec-main">
                <div class="rec-title">
                  <NText strong>{{ s.name || s.pluginId }}</NText>
                  <NText depth="3" style="font-size: 12px"> · {{ s.pluginId }} · v{{ s.version }}</NText>
                </div>
                <NTag size="small" :bordered="false" :type="STATUS_LABEL[s.status]?.type || 'default'" round>
                  {{ STATUS_LABEL[s.status]?.text || s.status }}
                </NTag>
              </div>
              <div class="rec-meta">
                <NIcon size="13" style="vertical-align: -2px"><TimeOutline /></NIcon>
                <span>{{ fmtSubTime(s.submittedAt) }}</span>
                <NTag v-if="s.category" size="tiny" :bordered="false" type="default" round style="margin-left: 6px">{{ CATEGORY_LABEL[s.category] || s.category }}</NTag>
              </div>
              <NText v-if="s.status === '拒绝上架' && s.rejectReason" depth="3" style="font-size: 12px; color: #e5484d; display: block; margin-top: 4px">
                拒绝理由：{{ s.rejectReason }}
              </NText>
            </NListItem>
          </NList>
        </NScrollbar>
      </NDrawerContent>
    </NDrawer>

    <!-- 卸载确认弹窗 -->
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
.toolbar { margin-bottom: 14px; background: var(--n-color-modal); }
.grid { margin-top: 4px; }
.p-card { height: 100%; }
.p-card:hover { transform: translateY(-2px); transition: transform .15s ease; }
.p-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.p-icon {
  width: 36px; height: 36px; border-radius: 10px; flex-shrink: 0;
  background: #e6f1fb; color: #185fa5;
  display: flex; align-items: center; justify-content: center;
}
.p-title { min-width: 0; flex: 1; }
.p-name { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
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
.rec-item { display: block; }
.rec-main { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.rec-title { min-width: 0; }
.rec-meta { font-size: 12px; color: var(--n-text-color-3); margin-top: 4px; display: flex; align-items: center; gap: 4px; }
</style>
