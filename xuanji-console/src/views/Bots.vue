<script setup lang="ts">
/**
 * QQBOT 机器人列表（2026-08-08 重写）
 * - 卡片整体比例放大 1.4×（头像 72px / 字号 17px / padding 18），但按钮排版保持原状（停用/启用/删除，无「详情」按钮）
 * - 详情改 NDrawer（560px 宽，三大模块：基本信息 / 环境配置 / 数据卡片），点击整张卡片打开
 * - QQ 平台启动后 ws 握手 READY 自动同步 union_openid / share_url / welcome_msg
 */
import { ref, reactive, computed, onMounted, h } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import {
  NCard, NTag, NIcon, NButton,
  NDrawer, NDrawerContent, NDescriptions, NDescriptionsItem,
  NAlert, NEmpty, NText, NModal, NForm, NFormItem, NInput, NSwitch,
  NRadioGroup, NRadioButton, NSpace,
  NStatistic, NNumberAnimation, NDivider, NPopconfirm,
  NQrCode, NSpin
} from 'naive-ui'
import type { FormInst, FormRules } from 'naive-ui'
import {
  RocketOutline, CopyOutline, AddOutline, LinkOutline,
  ArrowBackOutline, RefreshOutline, Power, StopCircleOutline, TrashOutline, RadioOutline,
  PeopleOutline, PersonOutline, ChatbubblesOutline, ServerOutline,
  CheckmarkCircleOutline, CloseCircleOutline, DownloadOutline,
  PlanetOutline, RibbonOutline, TimeOutline, ArchiveOutline
} from '@vicons/ionicons5'
import api from '../api'
import PageHero from '../components/PageHero.vue'

const message = useMessage()
const dialog = useDialog()
const bots = ref<any[]>([])
const err = ref('')
const loading = ref(false)

function patchBot(botKey: string, patch: Record<string, any>) {
  const t = bots.value.find((x) => x.botKey === botKey)
  if (t) Object.assign(t, patch)
}

async function load() {
  loading.value = true
  try {
    bots.value = await api.getBots()
    err.value = ''
  } catch (e: any) {
    err.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

const STATUS_LABELS: Record<string, string> = { ONLINE: '在线', OFFLINE: '停用', PAUSED: '暂停', ERROR: '异常' }
const STATUS_TYPES: Record<string, 'success' | 'default' | 'warning' | 'error'> = {
  ONLINE: 'success', OFFLINE: 'default', PAUSED: 'warning', ERROR: 'error'
}

// ============ 卡片点击：打开 NDrawer 详情 ============
const showDetail = ref(false)
const detailLoading = ref(false)
const detail = ref<any>({})
const detailAvatarErr = ref(false)
async function openDetail(botKey: string) {
  detailAvatarErr.value = false
  detail.value = {}
  showDetail.value = true
  detailLoading.value = true
  try {
    detail.value = await api.getBot(botKey)
  } catch (e: any) {
    detail.value = { error: e.message }
  } finally {
    detailLoading.value = false
  }
}

// ============ 工具 ============
function fmtTime(ts: number | string): string {
  const n = Number(ts)
  if (!isFinite(n) || n <= 0) return '—'
  const d = new Date(n * 1000)
  const pad = (x: number) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function copy(text: string) {
  if (!text) { message.warning('内容为空'); return }
  navigator.clipboard?.writeText(text).then(
    () => message.success('已复制'),
    () => message.error('复制失败')
  )
}
function safeNum(v: any, dflt = 0): number {
  const n = Number(v)
  return isFinite(n) ? n : dflt
}
function msgStats() {
  return detail.value.messageStats || {
    today: { in: 0, out: 0 }, week: { in: 0, out: 0 }, month: { in: 0, out: 0 }
  }
}
const webhookUrl = computed(() => {
  const d = String(detail.value.domain || '')
  if (!d) return ''
  return d.startsWith('http') ? d : `https://${d}/webhook/${detail.value.appId}`
})

// ============ 模块1：欢迎语编辑 ============
const editingWelcome = ref(false)
const welcomeDraft = ref('')
const welcomeSaving = ref(false)
function startEditWelcome() {
  welcomeDraft.value = detail.value.welcomeMsg || ''
  editingWelcome.value = true
}
async function saveWelcome() {
  welcomeSaving.value = true
  try {
    const r: any = await api.putGlobalConfig({ welcome_msg: welcomeDraft.value })
    if (r.error) {
      message.error(r.error)
    } else {
      message.success('欢迎语已保存')
      detail.value.welcomeMsg = welcomeDraft.value
      editingWelcome.value = false
    }
  } catch (e: any) { message.error('保存失败：' + (e?.message ?? e)) }
  finally { welcomeSaving.value = false }
}

// ============ 模块2：启停 + 切换连接方式 ============
const switching = ref(false)
const switchingError = ref('')
const showConnModal = ref(false)
const targetMode = ref<'websocket' | 'webhook'>('websocket')
function openConnModal() {
  const cur = String(detail.value.connectionType || '').toLowerCase()
  targetMode.value = cur === 'webhook' ? 'websocket' : 'webhook'
  switchingError.value = ''
  showConnModal.value = true
}
const webhookMissing = computed(() => targetMode.value === 'webhook' && !detail.value.domain)
async function confirmConnSwitch() {
  switching.value = true
  switchingError.value = ''
  try {
    const r: any = await api.updateConnMode(detail.value.botKey, targetMode.value)
    if (r.error) { switchingError.value = r.error }
    else { message.success(r.msg || '已切换'); showConnModal.value = false; await load(); await reloadDetail() }
  } catch (e: any) { switchingError.value = e?.message ?? String(e) }
  finally { switching.value = false }
}

const showWebhookModal = ref(false)
const webhookDraft = ref('')
const webhookSaving = ref(false)
function openWebhookConfig() {
  webhookDraft.value = String(detail.value.domain || '')
  showWebhookModal.value = true
  showConnModal.value = false
}
async function saveWebhook() {
  if (!webhookDraft.value.trim()) { message.warning('请填写回调域名'); return }
  webhookSaving.value = true
  try {
    const r: any = await api.saveBotConfig(detail.value.botKey, { webhookUrl: webhookDraft.value.trim() })
    if (r.error) { message.error(r.error) }
    else { message.success('webhook 回调域名已保存'); showWebhookModal.value = false; await reloadDetail() }
  } catch (e: any) { message.error('保存失败：' + (e?.message ?? e)) }
  finally { webhookSaving.value = false }
}

async function startBot() {
  try {
    const r: any = await api.startBot(detail.value.botKey)
    if (r.error) message.error(r.error)
    else { message.success('已启动'); await reloadDetail(); await load() }
  } catch (e: any) { message.error('启动失败：' + (e?.message ?? e)) }
}
async function stopBot() {
  try {
    const r: any = await api.stopBot(detail.value.botKey)
    if (r.error) message.error(r.error)
    else { message.success('已停用'); await reloadDetail(); await load() }
  } catch (e: any) { message.error('停用失败：' + (e?.message ?? e)) }
}
async function reloadDetail() {
  detailLoading.value = true
  try { detail.value = await api.getBot(detail.value.botKey) }
  finally { detailLoading.value = false }
}

// ============ 卡片操作：停止 / 删除 / 启停切换 ============
async function stopCard(b: any, ev: Event) {
  ev.stopPropagation()
  try {
    const r: any = await api.stopBot(b.botKey)
    if (r.error) message.error(r.error)
    else { message.success('已停用'); patchBot(b.botKey, { status: 'OFFLINE' }) }
  } catch (e: any) { message.error('停用失败：' + (e?.message ?? e)) }
}
async function startCard(b: any, ev: Event) {
  ev.stopPropagation()
  try {
    const r: any = await api.startBot(b.botKey)
    if (r.error) message.error(r.error)
    else { message.success('已启动'); await load() }
  } catch (e: any) { message.error('启动失败：' + (e?.message ?? e)) }
}
async function confirmDelete(b: any) {
  try {
    const r: any = await api.deleteBot(b.appId)
    if (r.error) message.error(r.error)
    else { message.success('已删除并归档（30 天内可在回收站恢复）'); await load() }
  } catch (e: any) { message.error('删除失败：' + (e?.message ?? e)) }
}

// ============ 回收站（删除并归档，30 天可恢复） ============
const archives = ref<any[]>([])
const showArchive = ref(false)
const archiveLoading = ref(false)
async function openArchives() {
  showArchive.value = true
  archiveLoading.value = true
  try { archives.value = await api.archives() }
  catch (e: any) { message.error('加载回收站失败：' + (e?.message ?? e)) }
  finally { archiveLoading.value = false }
}
async function restoreBot(rec: any) {
  try {
    const r: any = await api.restoreBot(rec.id)
    if (r.error) message.error(r.error)
    else {
      message.success('已恢复 ' + (rec.bot_name || rec.instance_id))
      archives.value = await api.archives()
      await load()
    }
  } catch (e: any) { message.error('恢复失败：' + (e?.message ?? e)) }
}

// ============ 添加机器人 ============
const showAdd = ref(false)
const addLoading = ref(false)
const addFormRef = ref<FormInst | null>(null)
const addForm = reactive({
  appId: '', clientSecret: '', sandbox: false, connectionMethod: 'websocket', domain: ''
})
const addRules: FormRules = {
  appId: [{ required: true, message: '请填写 AppID', trigger: 'blur' }],
  clientSecret: [{ required: true, message: '请填写 AppSecret', trigger: 'blur' }]
}
function openAdd() {
  Object.assign(addForm, { appId: '', clientSecret: '', sandbox: false, connectionMethod: 'websocket', domain: '' })
  showAdd.value = true
}
async function onAdd() {
  if (!addFormRef.value) return
  try { await addFormRef.value.validate() } catch { return }
  addLoading.value = true
  try {
    const body: Record<string, string> = {
      appId: addForm.appId.trim(),
      clientSecret: addForm.clientSecret.trim(),
      sandbox: String(addForm.sandbox),
      connectionMethod: addForm.connectionMethod,
      status: 'ONLINE'
    }
    if (addForm.connectionMethod === 'webhook') {
      if (!addForm.domain.trim()) { message.warning('Webhook 模式请填写回调域名'); addLoading.value = false; return }
      body.domain = addForm.domain.trim()
    }
    const r: any = await api.saveBot(body)
    if (r.error) message.error(r.error)
    else { message.success('已保存并启用，正在重启连接…'); showAdd.value = false; await load() }
  } catch (e: any) { message.error('保存失败：' + (e?.message ?? e)) }
  finally { addLoading.value = false }
}
</script>

<template>
  <div>
    <PageHero title="QQBOT" subtitle="查看与管理已注册的 QQ 机器人实例（详情请进入单独页面）" :icon="RocketOutline">
      <NButton secondary :loading="loading" @click="load">
        <template #icon><NIcon><RocketOutline /></NIcon></template>
        刷新
      </NButton>
      <NButton secondary @click="openArchives">
        <template #icon><NIcon><ArchiveOutline /></NIcon></template>
        回收站
      </NButton>
      <NButton type="primary" @click="openAdd">
        <template #icon><NIcon><AddOutline /></NIcon></template>
        添加机器人
      </NButton>
    </PageHero>

    <!-- 回收站（删除并归档，30 天可恢复） -->
    <NModal v-model:show="showArchive" preset="card" title="回收站（已删除机器人，30 天内可恢复）"
      style="width: 640px">
      <NAlert type="info" style="margin-bottom: 12px">
        删除机器人会将数据目录移入归档，保留 30 天。到期未恢复将自动清理；恢复后档案与数据完整还原。
      </NAlert>
      <div v-if="archiveLoading" style="text-align:center;padding:24px"><NSpin /></div>
      <NEmpty v-else-if="!archives.length" description="回收站为空" />
      <div v-else style="max-height:420px;overflow:auto">
        <NCard v-for="a in archives" :key="a.id" size="small" style="margin-bottom:8px">
          <div style="display:flex;justify-content:space-between;align-items:center">
            <div>
              <b>{{ a.bot_name || a.instance_id }}</b>
              <div style="color:#888;font-size:12px;margin-top:4px">
                {{ a.platform }} / {{ a.instance_id }}
                <NDivider vertical />
                删除于 {{ a.archive_time }}
                <NDivider vertical />
                {{ a.data_size > 0 ? (a.data_size / 1024 / 1024).toFixed(1) + ' MB' : '无数据' }}
              </div>
              <div style="color:#999;font-size:12px;margin-top:2px">过期时间 {{ a.expire_at }}（超过后自动清除）</div>
            </div>
            <NButton type="primary" size="small" @click="restoreBot(a)">恢复</NButton>
          </div>
        </NCard>
      </div>
    </NModal>

    <NAlert v-if="err" type="error" :title="'加载失败'" style="margin-bottom: 16px">{{ err }}</NAlert>

    <!-- 卡片列表（整体比例放大 1.4×） -->
    <div v-if="bots.length" class="bot-grid">
      <NCard
        v-for="b in bots"
        :key="b.botKey"
        hoverable
        class="bot-card"
        @click="openDetail(b.botKey)"
      >
        <div class="bot-card-inner">
          <div class="bot-avatar">
            <img
              v-if="b.avatar && !b._avatarErr"
              :src="b.avatar"
              class="bot-avatar-img"
              :alt="b.name || b.botKey"
              @error="b._avatarErr = true"
            />
            <div v-else class="bot-avatar-fallback">
              <NIcon size="32"><RocketOutline /></NIcon>
            </div>
          </div>
          <div class="bot-info">
            <div class="bot-line1">
              <span class="bot-name">{{ b.name || b.botKey }}</span>
              <span class="bot-appid">AppId: {{ b.appId }}</span>
            </div>
            <div class="bot-line2">
              <NTag size="small" round :bordered="true" :type="STATUS_TYPES[b.status] || 'default'">
                <template #icon>
                  <span class="status-dot" :class="'dot-' + (b.status || '').toLowerCase()" />
                </template>
                {{ STATUS_LABELS[b.status] || b.status }}
              </NTag>
              <NTag size="small" round :bordered="true" type="info">{{ b.platform }}</NTag>
              <NTag
                v-if="b.connectionType"
                size="small" round :bordered="true"
                :type="b.connectionType === 'websocket' ? 'success' : 'warning'"
              >
                <template #icon>
                  <NIcon size="11">
                    <component :is="b.connectionType === 'websocket' ? RadioOutline : LinkOutline" />
                  </NIcon>
                </template>
                {{ b.connectionType === 'websocket' ? 'WebSocket' : 'Webhook' }}
              </NTag>
            </div>
            <div
              v-if="b.connectionType === 'webhook' && b.domain"
              class="bot-callback"
              @click.stop="copy(`https://${b.domain}/webhook/${b.appId}`)"
              :title="`点击复制完整回调地址 https://${b.domain}/webhook/${b.appId}`"
            >
              <NIcon size="12"><LinkOutline /></NIcon>
              <span class="callback-text">https://{{ b.domain }}/webhook/{{ b.appId }}</span>
              <NIcon size="11"><CopyOutline /></NIcon>
            </div>
          </div>
          <div class="bot-right" @click.stop>
            <NSpace :size="6">
              <NButton
                v-if="(b.status || '').toUpperCase() === 'ONLINE' || (b.status || '').toUpperCase() === 'ACTIVE'"
                size="small"
                quaternary
                type="warning"
                @click.stop="stopCard(b, $event)"
              >
                <template #icon><NIcon size="14"><StopCircleOutline /></NIcon></template>
                停用
              </NButton>
              <NButton
                v-else
                size="small"
                type="primary"
                @click.stop="startCard(b, $event)"
              >
                <template #icon><NIcon size="14"><Power /></NIcon></template>
                启用
              </NButton>
              <NPopconfirm @positive-click="confirmDelete(b)">
                <template #trigger>
                  <NButton size="small" type="error">
                    <template #icon><NIcon size="14"><TrashOutline /></NIcon></template>
                    删除
                  </NButton>
                </template>
                确认删除机器人 {{ b.name || b.botKey }}（AppID {{ b.appId }}）？删除后进入回收站，30 天内可恢复。
              </NPopconfirm>
            </NSpace>
          </div>
        </div>
      </NCard>
    </div>

    <NEmpty
      v-else-if="!loading"
      description="暂无机器人，请点击右上「添加机器人」注册"
      style="padding: 60px 0"
    />

    <!-- ═══════════ 详情抽屉（三模块） ═══════════ -->
    <NDrawer v-model:show="showDetail" :width="620" placement="right">
      <NDrawerContent
        :title="detail?.botKey ? `机器人详情 · ${detail.botKey}` : '机器人详情'"
        :native-scrollbar="false"
      >
        <NAlert v-if="detail?.error" type="error" style="margin-bottom: 12px">{{ detail.error }}</NAlert>
        <NSpin v-else-if="!detail?.appId && detailLoading" description="加载中…" />
        <template v-else-if="detail?.appId">
          <!-- 顶部操作条 -->
          <NSpace :size="8" style="margin-bottom: 12px">
            <NButton size="small" secondary @click="reloadDetail" :loading="detailLoading">
              <template #icon><NIcon><RefreshOutline /></NIcon></template>
              刷新
            </NButton>
            <NPopconfirm
              v-if="String(detail.status || '').toUpperCase() === 'ONLINE'"
              @positive-click="stopBot"
            >
              <template #trigger>
                <NButton size="small" type="warning" secondary>
                  <template #icon><NIcon><Power /></NIcon></template>
                  停用
                </NButton>
              </template>
              确定停用机器人 {{ detail.appId }}？
            </NPopconfirm>
            <NPopconfirm v-else @positive-click="startBot">
              <template #trigger>
                <NButton size="small" type="primary">
                  <template #icon><NIcon><Power /></NIcon></template>
                  启用
                </NButton>
              </template>
              确定启用机器人 {{ detail.appId }}？
            </NPopconfirm>
          </NSpace>

          <!-- 模块1：基本信息 -->
          <NCard :bordered="true" size="small" style="margin-bottom: 12px">
            <template #header>
              <NSpace align="center" :size="6">
                <NIcon size="16" color="#1E88E5"><RibbonOutline /></NIcon>
                <span style="font-weight: 600">基本信息</span>
              </NSpace>
            </template>

            <!-- 顶部：4 个字段紧凑 2 列网格 -->
            <NGrid :cols="2" :x-gap="16" :y-gap="0" responsive="screen">
              <NGi :span="2" :xs="1" :s="1">
                <div class="info-field">
                  <div class="info-label">AppID</div>
                  <div class="info-value">
                    <NSpace align="center" :size="6">
                      <NTag :bordered="false" type="primary" size="small">{{ detail.appId }}</NTag>
                      <NButton quaternary size="tiny" @click="copy(detail.appId)">
                        <template #icon><NIcon size="14"><CopyOutline /></NIcon></template>
                        复制
                      </NButton>
                    </NSpace>
                  </div>
                </div>
              </NGi>
              <NGi :span="2" :xs="1" :s="1">
                <div class="info-field">
                  <div class="info-label">开放平台ID</div>
                  <div class="info-value">
                    <NSpace v-if="detail.botId" align="center" :size="6">
                      <NTag :bordered="false" type="success" size="small">{{ detail.botId }}</NTag>
                      <NButton quaternary size="tiny" @click="copy(detail.botId)">
                        <template #icon><NIcon size="14"><CopyOutline /></NIcon></template>
                        复制
                      </NButton>
                    </NSpace>
                    <NText v-else depth="3" style="font-size: 12px">（暂无）</NText>
                  </div>
                </div>
              </NGi>
              <NGi :span="2" :xs="1" :s="1">
                <div class="info-field">
                  <div class="info-label">当前环境</div>
                  <div class="info-value">
                    <NTag
                      :bordered="false"
                      :type="String(detail.env || '').toUpperCase() === 'SANDBOX' ? 'warning' : 'success'"
                      size="small"
                    >
                      {{ String(detail.env || 'PRODUCTION').toUpperCase() === 'SANDBOX' ? '沙箱 (SANDBOX)' : '正式 (PRODUCTION)' }}
                    </NTag>
                  </div>
                </div>
              </NGi>
              <NGi :span="2" :xs="1" :s="1">
                <div class="info-field">
                  <div class="info-label">创建时间</div>
                  <div class="info-value">
                    <NSpace align="center" :size="6">
                      <NIcon size="14" color="#9aa0a6"><TimeOutline /></NIcon>
                      <span>{{ fmtTime(detail.createTime) }}</span>
                    </NSpace>
                  </div>
                </div>
              </NGi>
            </NGrid>

            <NDivider style="margin: 12px 0" />

            <!-- 欢迎语（独立段） -->
            <div class="welcome-section">
              <div class="section-head">
                <span class="section-title">欢迎语</span>
                <NSpace :size="6">
                  <NButton v-if="!editingWelcome" quaternary size="tiny" @click="startEditWelcome">编辑</NButton>
                  <template v-else>
                    <NButton quaternary size="tiny" @click="editingWelcome = false">取消</NButton>
                    <NButton size="tiny" type="primary" :loading="welcomeSaving" @click="saveWelcome">保存</NButton>
                  </template>
                </NSpace>
              </div>
              <NInput
                v-if="editingWelcome"
                v-model:value="welcomeDraft"
                type="textarea"
                placeholder="新成员加入时自动发送的欢迎内容"
                :autosize="{ minRows: 2, maxRows: 6 }"
              />
              <div v-else class="welcome-text">{{ detail.welcomeMsg || '（未设置）' }}</div>
            </div>

            <NDivider style="margin: 12px 0" />

            <!-- 分享链接 + 二维码 -->
            <div class="share-section">
              <div class="section-head">
                <span class="section-title">分享链接</span>
                <NSpace v-if="detail.shareUrl" :size="6">
                  <NInput :value="detail.shareUrl" readonly size="small" style="width: 260px" />
                  <NButton size="tiny" @click="copy(detail.shareUrl)">
                    <template #icon><NIcon size="14"><CopyOutline /></NIcon></template>
                    复制
                  </NButton>
                </NSpace>
                <NText v-else depth="3" style="font-size: 12px">未返回（QQ 开放平台未下发）</NText>
              </div>
              <div class="qrcode-area">
                <div class="qrcode-wrap">
                  <NQrCode
                    v-if="detail.shareUrl"
                    :value="detail.shareUrl"
                    :size="140"
                    type="svg"
                    error-correction-level="M"
                  />
                  <div v-else class="qrcode-empty">二维码<br/>未生成</div>
                </div>
              </div>
            </div>
          </NCard>

          <!-- 模块2：环境配置 -->
          <NCard :bordered="true" size="small" style="margin-bottom: 12px">
            <template #header>
              <NSpace align="center" :size="6">
                <NIcon size="16" color="#fa8c16"><PlanetOutline /></NIcon>
                <span style="font-weight: 600">环境配置</span>
              </NSpace>
            </template>
            <template #header-extra>
              <NButton size="small" type="primary" tertiary @click="openConnModal">切换连接方式</NButton>
            </template>
            <NDescriptions :column="1" size="small" label-placement="left">
              <NDescriptionsItem label="当前连接方式">
                <NTag
                  :bordered="false"
                  :type="String(detail.connectionType || '').toLowerCase() === 'webhook' ? 'warning' : 'success'"
                  size="small"
                >
                  {{ String(detail.connectionType || '未设置').toUpperCase() }}
                </NTag>
              </NDescriptionsItem>
              <NDescriptionsItem
                v-if="String(detail.connectionType || '').toLowerCase() === 'websocket'"
                label="WebSocket 端点"
              >
                <span style="font-family: monospace; font-size: 12.5px">
                  {{ detail.env === 'SANDBOX' ? 'sandbox.api.bot.qq.com/websocket' : 'api.bot.qq.com/websocket' }}
                </span>
              </NDescriptionsItem>
              <NDescriptionsItem
                v-if="String(detail.connectionType || '').toLowerCase() === 'webhook'"
                label="Webhook 回调地址"
              >
                <NSpace align="center" :size="8">
                  <span style="font-family: monospace; font-size: 12.5px">{{ webhookUrl || '—（未配置）' }}</span>
                  <NButton quaternary size="tiny" @click="openWebhookConfig">
                    {{ detail.domain ? '修改域名' : '配置域名' }}
                  </NButton>
                  <NButton v-if="webhookUrl" quaternary size="tiny" tag="a" :href="webhookUrl" target="_blank">
                    <template #icon><NIcon size="14"><DownloadOutline /></NIcon></template>
                    打开
                  </NButton>
                </NSpace>
              </NDescriptionsItem>
            </NDescriptions>
            <NAlert v-if="!detail.domain" type="warning" :show-icon="true" style="margin-top: 10px">
              当前 webhook 回调域名未配置，点击右上「配置域名」设置后再切换到 webhook 模式。
            </NAlert>
          </NCard>

          <!-- 模块3：数据卡片 -->
          <NCard :bordered="true" size="small">
            <template #header>
              <NSpace align="center" :size="6">
                <NIcon size="16" color="#07c160"><ChatbubblesOutline /></NIcon>
                <span style="font-weight: 600">数据概览</span>
              </NSpace>
            </template>

            <!-- 第一行：基础数据（统一高度，避免错位） -->
            <NGrid :cols="3" :x-gap="10" :y-gap="10" responsive="screen" item-responsive>
              <NGi :span="3" :m="1">
                <div class="data-card">
                  <div class="data-row">
                    <span class="data-icon" style="background: #e8f4ff; color: #2090e0">
                      <NIcon size="20"><PeopleOutline /></NIcon>
                    </span>
                    <div class="data-meta">
                      <div class="data-label">群聊总数</div>
                      <NNumberAnimation :from="0" :to="safeNum(detail.groupsTotal)" :duration="900" style="font-size: 22px; font-weight: 700; color: #2090e0" />
                    </div>
                  </div>
                </div>
              </NGi>
              <NGi :span="3" :m="1">
                <div class="data-card">
                  <div class="data-row">
                    <span class="data-icon" style="background: #f4ecff; color: #722ed1">
                      <NIcon size="20"><PersonOutline /></NIcon>
                    </span>
                    <div class="data-meta">
                      <div class="data-label">好友总数</div>
                      <NNumberAnimation :from="0" :to="safeNum(detail.friendsTotal)" :duration="900" style="font-size: 22px; font-weight: 700; color: #722ed1" />
                    </div>
                  </div>
                </div>
              </NGi>
              <NGi :span="3" :m="1">
                <div class="data-card">
                  <div class="data-row">
                    <span class="data-icon" style="background: #e8f7ee; color: #07c160">
                      <NIcon size="20"><ChatbubblesOutline /></NIcon>
                    </span>
                    <div class="data-meta">
                      <div class="data-label">今日消息（总）</div>
                      <NNumberAnimation :from="0" :to="safeNum(msgStats().today.in) + safeNum(msgStats().today.out)" :duration="900" style="font-size: 22px; font-weight: 700; color: #07c160" />
                    </div>
                  </div>
                </div>
              </NGi>
            </NGrid>

            <!-- 居中分隔标题 -->
            <div class="section-divider">
              <span class="section-divider-line"></span>
              <span class="section-divider-text">消息统计（收 ↓ / 发 ↑）</span>
              <span class="section-divider-line"></span>
            </div>

            <!-- 第二行：时段统计（统一高度） -->
            <NGrid :cols="3" :x-gap="10" :y-gap="10" responsive="screen" item-responsive>
              <NGi :span="3" :m="1" v-for="(p, idx) in ['today', 'week', 'month']" :key="idx">
                <div class="data-card data-card-stat">
                  <div class="data-period-title">📅 {{ ({today:'今日', week:'本周', month:'本月'})[p] }}</div>
                  <div class="data-row">
                    <span class="data-icon-in"><NIcon size="16"><CheckmarkCircleOutline /></NIcon></span>
                    <div class="data-meta">
                      <div class="data-label">收 ↓</div>
                      <NNumberAnimation :from="0" :to="safeNum(msgStats()[p].in)" :duration="900" style="font-size: 18px; font-weight: 700" />
                    </div>
                  </div>
                  <div class="data-row" style="margin-top: 4px">
                    <span class="data-icon-out"><NIcon size="16"><CloseCircleOutline /></NIcon></span>
                    <div class="data-meta">
                      <div class="data-label">发 ↑</div>
                      <NNumberAnimation :from="0" :to="safeNum(msgStats()[p].out)" :duration="900" style="font-size: 18px; font-weight: 700" />
                    </div>
                  </div>
                </div>
              </NGi>
            </NGrid>
          </NCard>
        </template>
      </NDrawerContent>
    </NDrawer>

    <!-- 切换连接方式 modal -->
    <NModal v-model:show="showConnModal" preset="card" title="切换连接方式" style="width: 460px; max-width: 92vw" :bordered="false">
      <NForm label-placement="top">
        <NFormItem label="切换至">
          <NRadioGroup v-model:value="targetMode">
            <NSpace>
              <NRadioButton value="websocket">WebSocket（实时双向）</NRadioButton>
              <NRadioButton value="webhook">Webhook（HTTP回调）</NRadioButton>
            </NSpace>
          </NRadioGroup>
        </NFormItem>
        <NAlert v-if="webhookMissing" type="warning" :show-icon="true" style="margin: 12px 0">
          当前 webhook 回调域名未配置，请先
          <NButton text type="primary" size="small" @click="openWebhookConfig">配置域名</NButton>
          设置后再切换。
        </NAlert>
        <NAlert v-else-if="targetMode === 'webhook'" type="info" :show-icon="true" style="margin: 12px 0">
          切换到 webhook 后将通过回调地址 <span style="font-family: monospace">{{ webhookUrl }}</span> 接收事件。
        </NAlert>
        <NAlert v-else type="info" :show-icon="true" style="margin: 12px 0">
          切换到 websocket 将建立长连接到 QQ 开放平台网关。
        </NAlert>
        <NAlert v-if="switchingError" type="error" :show-icon="true" style="margin-top: 8px">
          {{ switchingError }}
        </NAlert>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showConnModal = false">取消</NButton>
          <NPopconfirm :disabled="webhookMissing" @positive-click="confirmConnSwitch">
            <template #trigger>
              <NButton type="primary" :loading="switching" :disabled="webhookMissing">确认切换</NButton>
            </template>
            将 stop+start 重启连接，确认切换到 {{ targetMode }} ？
          </NPopconfirm>
        </NSpace>
      </template>
    </NModal>

    <!-- 配置 webhook 域名 modal -->
    <NModal v-model:show="showWebhookModal" preset="card" title="配置 Webhook 回调域名" style="width: 460px; max-width: 92vw" :bordered="false">
      <NAlert type="info" :show-icon="true" style="margin-bottom: 12px">
        请填写公网可访问的域名（不带 https://），例如 <span style="font-family: monospace">bot.example.com</span>。<br/>
        完整回调地址：<span style="font-family: monospace">https://{{ webhookDraft || 'your-domain' }}/webhook/{{ detail.appId }}</span>
      </NAlert>
      <NFormItem label="回调域名">
        <NInput v-model:value="webhookDraft" placeholder="bot.example.com" />
      </NFormItem>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showWebhookModal = false">取消</NButton>
          <NButton type="primary" :loading="webhookSaving" @click="saveWebhook">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- 添加机器人弹窗 -->
    <NModal
      v-model:show="showAdd"
      title="添加机器人"
      preset="card"
      style="width: 520px; max-width: 92vw"
      :bordered="false"
      :mask-closable="false"
    >
      <NForm ref="addFormRef" :model="addForm" :rules="addRules" label-placement="top">
        <NFormItem label="AppID" path="appId">
          <NInput v-model:value="addForm.appId" placeholder="QQ 开放平台 AppID" :disabled="addLoading" clearable />
        </NFormItem>
        <NFormItem label="AppSecret" path="clientSecret">
          <NInput v-model:value="addForm.clientSecret" type="password" show-password-on="click" placeholder="QQ 开放平台 AppSecret" :disabled="addLoading" />
        </NFormItem>
        <NFormItem label="环境">
          <div style="display: flex; align-items: center; gap: 10px">
            <NSwitch v-model:value="addForm.sandbox" :disabled="addLoading" />
            <NText depth="3" style="font-size: 12px">{{ addForm.sandbox ? '沙箱环境（测试用）' : '正式环境' }}</NText>
          </div>
        </NFormItem>
        <NFormItem label="连接方式">
          <NRadioGroup v-model:value="addForm.connectionMethod" :disabled="addLoading">
            <NRadioButton value="websocket">WebSocket</NRadioButton>
            <NRadioButton value="webhook">Webhook</NRadioButton>
          </NRadioGroup>
        </NFormItem>
        <template v-if="addForm.connectionMethod === 'webhook'">
          <NFormItem label="回调域名" path="domain">
            <NInput v-model:value="addForm.domain" placeholder="例如 xuanji.com" :disabled="addLoading" clearable />
          </NFormItem>
          <NAlert type="info" :show-icon="true" style="margin-bottom: 14px">
            <div style="line-height: 1.7">
              回调地址将自动生成为：<br/>
              <code class="cb-url">https://{{ addForm.domain || '你的域名' }}/webhook/{{ addForm.appId || 'APPID' }}</code>
            </div>
          </NAlert>
        </template>
      </NForm>
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 10px">
          <NButton :disabled="addLoading" @click="showAdd = false">取消</NButton>
          <NButton type="primary" :loading="addLoading" @click="onAdd">保存并启用</NButton>
        </div>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.bot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(520px, 1fr));
  gap: 18px;
}
.bot-card {
  cursor: pointer;
  transition: box-shadow 0.15s ease;
}
.bot-card:hover {
  box-shadow: 0 6px 22px rgba(20, 30, 60, 0.10);
}
.bot-card-inner {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 4px 0;
}
.bot-avatar {
  flex-shrink: 0;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
  background: #f0f0f5;
  display: flex;
  align-items: center;
  justify-content: center;
}
.bot-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.bot-avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #aaa;
}
.bot-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.bot-line1 {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}
.bot-line2 {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.bot-callback {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #888;
  cursor: pointer;
  max-width: 100%;
}
.bot-callback .callback-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bot-callback:hover {
  color: var(--n-color-primary, #5b5bd6);
}
.bot-name {
  font-weight: 600;
  font-size: 17px;
  color: var(--n-text-color-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 1;
  min-width: 0;
}
.bot-appid {
  font-size: 13px;
  color: var(--n-text-color-3);
  flex-shrink: 0;
}
.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 2px;
  background: #aaa;
}
.status-dot.dot-online { background: #07c160; }
.status-dot.dot-offline { background: #c2c2c2; }
.status-dot.dot-paused { background: #fa8c16; }
.status-dot.dot-error { background: #e5484d; }
.bot-right {
  flex-shrink: 0;
}
.cb-url {
  background: #f0f0f5;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: Consolas, monospace;
  font-size: 12px;
}

/* 详情抽屉内数据卡 */
.data-card {
  border: 1px solid #EEF0F3;
  border-radius: 8px;
  padding: 12px 14px;
  height: 100%;
  min-height: 76px;          /* 统一第一行三张基础数据卡高度，避免错位 */
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #fafbfc 0%, #ffffff 100%);
}
.data-card-stat {
  min-height: 0;             /* 第二行时段卡高度由内容决定 */
  align-items: stretch;
  flex-direction: column;
}
.data-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.data-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  flex-shrink: 0;
}
.data-meta { flex: 1; min-width: 0; }
.data-label {
  font-size: 11.5px;
  color: #6b7280;
  margin-bottom: 2px;
}
.data-sub {
  font-size: 11px;
  color: #9aa0a6;
  margin-top: 6px;
}
.data-period-title {
  font-size: 12.5px;
  font-weight: 600;
  color: #1f2329;
  margin-bottom: 6px;
}
.data-icon-in,
.data-icon-out {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  flex-shrink: 0;
}
.data-icon-in { background: #e8f7ee; color: #07c160; }
.data-icon-out { background: #fdf4e3; color: #fa8c16; }

.qrcode-wrap {
  width: 148px;
  height: 148px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1px solid #EEF0F3;
  border-radius: 8px;
  padding: 4px;
}
.qrcode-empty {
  color: #9aa0a6;
  font-size: 12px;
  text-align: center;
  line-height: 1.6;
}

/* 基本信息卡片：紧凑字段 */
.info-field {
  padding: 6px 0;
}
.info-label {
  font-size: 11.5px;
  color: #9aa0a6;
  margin-bottom: 4px;
}
.info-value {
  font-size: 13px;
  color: #1f2329;
}

/* 分段标题行（欢迎语 / 分享链接） */
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  flex-wrap: wrap;
  gap: 8px;
}
.section-title {
  font-size: 12.5px;
  font-weight: 600;
  color: #1f2329;
}
.welcome-text {
  white-space: pre-wrap;
  font-size: 12.5px;
  color: #4b5563;
  line-height: 1.6;
  min-height: 24px;
}
.share-section {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 16px;
  align-items: center;
}

/* 数据概览分隔线（左右线 + 居中标题） */
.section-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 14px 0 10px;
}
.section-divider-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, #e5e7eb, transparent);
}
.section-divider-text {
  font-size: 12px;
  color: #6b7280;
  font-weight: 500;
  white-space: nowrap;
}

/* 分享链接区二维码居中 */
.qrcode-area {
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>