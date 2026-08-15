<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  NCard, NButton, NSpace, NIcon, NText, NTag, NModal, NAlert,
  useMessage, NSwitch, NSpin, NInput, NDivider, NPagination, NPopconfirm
} from 'naive-ui'
import {
  ShieldCheckmarkOutline, RefreshOutline, DownloadOutline, CheckmarkOutline, CloseOutline
} from '@vicons/ionicons5'
import PageHero from '../components/PageHero.vue'
import EmptyState from '../components/EmptyState.vue'
import api from '../api'
import dayjs from 'dayjs'
import { BASE } from '../api/http'

const message = useMessage()

// ═══════════════ 审核台（独立页面） ═══════════════
const pendingList = ref<any[]>([])
const auditLoading = ref(false)
const rejectModal = ref(false)
const rejectTarget = ref<any>(null)
const rejectReason = ref('')
// 管理员令牌验证门：验证通过才显示审核内容与操作
const adminVerified = ref(false)
const adminTokenInput = ref('')
const adminVerifying = ref(false)
const auditLog = ref<any[]>([])
// 已上架（含已下架）插件列表：便于管理员下架
const releasedList = ref<any[]>([])
const releasedLoading = ref(false)
const delistModal = ref(false)
const delistTarget = ref<any>(null)
const delistReason = ref('')
// 重新上架（已下架插件可重新上架）
const relistModal = ref(false)
const relistTarget = ref<any>(null)

const CATEGORY_LABEL: Record<string, string> = {
  entertainment: '娱乐', tool: '工具', 'group-admin': '群管', service: '服务', other: '其他'
}
const CATEGORY_OPTIONS = [
  { value: 'entertainment', label: '娱乐' },
  { value: 'tool', label: '工具' },
  { value: 'group-admin', label: '群管' },
  { value: 'service', label: '服务' },
  { value: 'other', label: '其他' }
]

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
      message.success('令牌可访问正式仓库，验证通过')
      await loadPending()
      await loadAudit()
      await loadReleased()
    } else {
      adminVerified.value = false
      message.error('令牌无效或无法访问正式仓库（上传测试文件失败）')
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
    const list = await api.marketPending()
    // 每卡片独立保存「官方/社区」开关，避免多个待审卡片共享同一状态互相联动
    pendingList.value = (list || []).map((p: any) => ({ ...p, official: p.official ?? false }))
  } catch { /* 不影响 */ } finally {
    auditLoading.value = false
  }
}

async function loadAudit() {
  try {
    auditLog.value = await api.marketAudit()
  } catch { /* 不影响 */ }
}

async function loadReleased() {
  releasedLoading.value = true
  try {
    releasedList.value = await api.marketReleased()
  } catch { /* 不影响 */ } finally {
    releasedLoading.value = false
  }
}

function releasedDownloadUrl(p: any) {
  return `${BASE}/console/market/download?pluginId=${encodeURIComponent(p.pluginId)}&version=${encodeURIComponent(p.version)}`
}

/** 时间（后端 Instant UTC ISO）→ UTC+8 展示。 */
function fmtSubTime(v: string): string {
  if (!v) return '—'
  const d = dayjs(v)
  return d.isValid() ? d.utcOffset(8).format('YYYY-MM-DD HH:mm:ss') : v
}

// 分页（待审 / 已上架 / 审核记录）
const pendingPage = ref(1); const pendingSize = ref(10)
const releasedPage = ref(1); const releasedSize = ref(10)
const auditPage = ref(1); const auditSize = ref(10)
function pageSlice<T>(list: T[], page: number, size: number): T[] {
  return list.slice((page - 1) * size, page * size)
}
const pagedPending = computed(() => pageSlice(pendingList.value, pendingPage.value, pendingSize.value))
const pagedReleased = computed(() => pageSlice(releasedList.value, releasedPage.value, releasedSize.value))
const pagedAudit = computed(() => pageSlice(auditLog.value, auditPage.value, auditSize.value))

function openDelist(p: any) {
  delistTarget.value = p
  delistReason.value = ''
  delistModal.value = true
}

async function doDelist() {
  try {
    const r = await api.marketDelist(delistTarget.value.pluginId, delistReason.value, adminTokenInput.value)
    if (r.status === 'ok') {
      message.success('已下架：' + (delistTarget.value.name || delistTarget.value.pluginId))
      delistModal.value = false
      await loadReleased()
      await loadAudit()
    } else {
      message.error('操作失败：' + (r.message || '未知错误'))
    }
  } catch (e: any) {
    message.error('操作失败：' + (e?.message ?? e))
  }
}

function openRelist(p: any) {
  relistTarget.value = p
  relistModal.value = true
}

async function doRelist() {
  try {
    const r = await api.marketRelist(relistTarget.value.pluginId, adminTokenInput.value)
    if (r.status === 'ok') {
      message.success('已重新上架：' + (relistTarget.value.name || relistTarget.value.pluginId))
      relistModal.value = false
      await loadReleased()
      await loadAudit()
    } else {
      message.error('操作失败：' + (r.message || '未知错误'))
    }
  } catch (e: any) {
    message.error('操作失败：' + (e?.message ?? e))
  }
}

function pendingDownloadUrl(p: any) {
  return `${BASE}/console/market/pending/${encodeURIComponent(p.submissionId)}/download?adminToken=${encodeURIComponent(adminTokenInput.value)}`
}

async function doApprove(p: any) {
  try {
    const r = await api.marketApprove(p.submissionId, p.official, p.category || 'other', adminTokenInput.value)
    if (r.status === 'ok') {
      message.success('已上架：' + (p.name || p.pluginId))
      await loadPending()
      await loadAudit()
      await loadReleased()
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

</script>

<template>
  <div>
    <PageHero title="插件审核" subtitle="审核开发者提交的插件（通过 / 拒绝），通过后自动上架到插件市场" :icon="ShieldCheckmarkOutline" />

    <!-- 管理员令牌验证门 -->
    <NCard v-if="!adminVerified" size="small" style="max-width: 560px">
      <NSpace align="center">
        <NInput v-model:value="adminTokenInput" type="password" show-password-on="click"
                placeholder="输入管理员令牌" style="width: 320px" />
        <NButton type="primary" :loading="adminVerifying" @click="verifyAdmin">
          <template #icon><NIcon><CheckmarkOutline /></NIcon></template>
          验证身份
        </NButton>
      </NSpace>
      <NText depth="3" style="font-size: 12px; display: block; margin-top: 10px">
        审核操作需要正式仓库管理员令牌；系统会拿该令牌向正式仓库上传并删除一个测试文件，以真实验证其能否访问仓库。
      </NText>
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
        <NButton quaternary @click="loadReleased">
          <template #icon><NIcon><RefreshOutline /></NIcon></template>
          刷新已上架
        </NButton>
      </NSpace>
      <EmptyState v-if="!auditLoading && !pendingList.length" description="暂无待审核的插件提交" />
      <NCard v-for="p in pagedPending" :key="p.submissionId" size="small" class="pending-card">
        <div class="pending-row">
          <div class="sub-info">
            <NText strong>{{ p.name || p.pluginId }}</NText>
            <NTag size="small" :bordered="false" type="warning" round style="margin-left: 8px">审核中</NTag>
            <NTag size="small" :bordered="false" type="primary" round>{{ CATEGORY_LABEL[p.category] || '其他' }}</NTag>
            <div style="margin-top: 2px">
              <NText depth="3" style="font-size: 12px">{{ p.pluginId }} · v{{ p.version }} · 作者 {{ p.author || 'unknown' }} · {{ fmtSubTime(p.submittedAt) }}</NText>
            </div>
            <NText v-if="p.description" depth="3" style="font-size: 12px; display: block; margin-top: 4px">{{ p.description }}</NText>
          </div>
          <div class="pending-ops">
            <NSwitch v-model:value="p.official" size="small" style="margin-right: 8px">
              <template #checked>官方</template>
              <template #unchecked>社区</template>
            </NSwitch>
            <NSelect v-model:value="p.category" :options="CATEGORY_OPTIONS" size="small"
                     placeholder="选择分类" style="width: 130px" />
            <NButton size="small" tag="a" :href="pendingDownloadUrl(p)" target="_blank" download secondary>
              <template #icon><NIcon size="14"><DownloadOutline /></NIcon></template>下载测试
            </NButton>
            <NPopconfirm @positive-click="doApprove(p)">
              <template #trigger>
                <NButton size="small" type="success">
                  <template #icon><NIcon size="14"><CheckmarkOutline /></NIcon></template>通过
                </NButton>
              </template>
              确认通过「{{ p.name || p.pluginId }}」并上架到插件市场？
            </NPopconfirm>
            <NButton size="small" type="error" ghost @click="openReject(p)">
              <template #icon><NIcon size="14"><CloseOutline /></NIcon></template>拒绝
            </NButton>
          </div>
        </div>
      </NCard>

      <NSpace v-if="pendingList.length > pendingSize" justify="end" style="margin-top: 8px">
        <NPagination v-model:page="pendingPage" :page-size="pendingSize" :item-count="pendingList.length" size="small" />
      </NSpace>

      <!-- 已上架插件（含已下架，便于下架管理） -->
      <NCard title="已上架插件" size="small" style="margin-top: 16px">
        <template #header-extra>
          <NButton size="small" quaternary :loading="releasedLoading" @click="loadReleased">
            <template #icon><NIcon size="14"><RefreshOutline /></NIcon></template>刷新
          </NButton>
        </template>
        <EmptyState v-if="!releasedLoading && !releasedList.length" description="正式仓库暂无已上架插件" />
        <div v-for="p in pagedReleased" :key="p.pluginId" class="sub-row">
          <div class="sub-info">
            <NText strong>{{ p.name || p.pluginId }}</NText>
            <NText depth="3" style="font-size: 12px; margin-left: 8px">{{ p.pluginId }} · v{{ p.version }} · 作者 {{ p.author || 'unknown' }}</NText>
            <NTag v-if="p.status === '已下架'" size="small" :bordered="false" type="default" round style="margin-left: 8px">已下架</NTag>
            <NTag v-else size="small" :bordered="false" type="success" round style="margin-left: 8px">已上架</NTag>
            <NTag size="small" :bordered="false" type="primary" round>{{ CATEGORY_LABEL[p.category] || '其他' }}</NTag>
          </div>
          <NText v-if="p.status === '已下架' && p.delistReason" depth="3" style="font-size: 12px; display: block; margin-top: 2px">
            下架理由：{{ p.delistReason }} · {{ fmtSubTime(p.delistedAt) }}
          </NText>
          <div class="pending-ops" style="margin-top: 6px">
            <NButton size="small" tag="a" :href="releasedDownloadUrl(p)" target="_blank" download secondary>
              <template #icon><NIcon size="14"><DownloadOutline /></NIcon></template>下载
            </NButton>
            <NButton v-if="p.status !== '已下架'" size="small" type="warning" ghost @click="openDelist(p)">
              <template #icon><NIcon size="14"><CloseOutline /></NIcon></template>下架
            </NButton>
            <NButton v-else size="small" type="success" @click="openRelist(p)">
              <template #icon><NIcon size="14"><CheckmarkOutline /></NIcon></template>重新上架
            </NButton>
          </div>
        </div>
        <NSpace v-if="releasedList.length > releasedSize" justify="end" style="margin-top: 8px">
          <NPagination v-model:page="releasedPage" :page-size="releasedSize" :item-count="releasedList.length" size="small" />
        </NSpace>
      </NCard>

      <!-- 审核记录 -->
      <NCard title="审核记录" size="small" style="margin-top: 16px">
        <EmptyState v-if="!auditLog.length" description="暂无审核记录" />
        <div v-for="(a, i) in pagedAudit" :key="(a.submissionId || '') + '-' + (a.time || i)" class="sub-row">
          <div class="sub-info">
            <NText strong>{{ a.name || a.pluginId }}</NText>
            <NText depth="3" style="font-size: 12px; margin-left: 8px">{{ a.pluginId }} · v{{ a.version }}</NText>
            <NTag size="small" :bordered="false" :type="a.action === 'APPROVED' ? 'success' : 'error'" round style="margin-left: 8px">
              {{ a.action === 'APPROVED' ? '已上架' : '已拒绝' }}
            </NTag>
            <NTag v-if="a.official" size="small" :bordered="false" type="info" round style="margin-left: 4px">官方</NTag>
          </div>
          <NText depth="3" style="font-size: 12px; display: block; margin-top: 2px">
            {{ fmtSubTime(a.time) }}{{ a.reason ? ' · 理由：' + a.reason : '' }}
          </NText>
        </div>
        <NSpace v-if="auditLog.length > auditSize" justify="end" style="margin-top: 8px">
          <NPagination v-model:page="auditPage" :page-size="auditSize" :item-count="auditLog.length" size="small" />
        </NSpace>
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

    <NModal v-model:show="delistModal" preset="card" title="下架插件" style="width: 420px; max-width: 92vw" :bordered="false">
      <NText depth="3" style="font-size: 12px; display: block; margin-bottom: 8px">
        将插件「{{ delistTarget?.name || delistTarget?.pluginId }}」标记为已下架（保留 jar 与清单，仅状态变更）。下架后用户将无法在公开市场安装。
      </NText>
      <NInput v-model:value="delistReason" type="textarea" :rows="3" placeholder="下架理由（选填）" />
      <template #footer>
        <NSpace justify="end">
          <NButton size="small" @click="delistModal = false">取消</NButton>
          <NButton size="small" type="warning" @click="doDelist">确认下架</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal v-model:show="relistModal" preset="card" title="重新上架插件" style="width: 420px; max-width: 92vw" :bordered="false">
      <NText depth="3" style="font-size: 12px; display: block; margin-bottom: 8px">
        将插件「{{ relistTarget?.name || relistTarget?.pluginId }}」状态由「已下架」恢复为「已上架」，清下架理由后重新在公开市场提供安装。jar 与清单沿用原文件，无需重传。
      </NText>
      <template #footer>
        <NSpace justify="end">
          <NButton size="small" @click="relistModal = false">取消</NButton>
          <NButton size="small" type="success" @click="doRelist">确认重新上架</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.pending-card { margin-bottom: 12px; }
.pending-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.pending-ops { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.sub-row { display: flex; flex-direction: column; padding: 10px 0; border-bottom: 1px dashed var(--n-border-color); }
.sub-info { display: flex; align-items: center; flex-wrap: wrap; }
</style>
