<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import {
  NCard, NTag, NIcon, NButton,
  NDrawer, NDrawerContent, NDescriptions, NDescriptionsItem,
  NAlert, NEmpty, NText, NModal, NForm, NFormItem, NInput, NSwitch,
  NRadioGroup, NRadioButton
} from 'naive-ui'
import type { DataTableColumns, FormInst, FormRules } from 'naive-ui'
import { RocketOutline, CopyOutline, ChevronForwardOutline, AddOutline, LinkOutline } from '@vicons/ionicons5'
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

const detail = ref<any>(null)
const showDetail = ref(false)
const detailAvatarErr = ref(false)
async function openDetail(botKey: string) {
  detailAvatarErr.value = false
  try {
    detail.value = await api.getBot(botKey)
  } catch (e: any) {
    detail.value = { error: e.message }
  }
  showDetail.value = true
}

function copy(text: string) {
  navigator.clipboard?.writeText(text)
  message.success('已复制：' + text)
}

const actionLoading = ref('')
function onStop(b: any) {
  dialog.warning({
    title: '确认停止机器人',
    content: `确定要停止「${b.name || b.botKey}」机器人吗？停止后将断开其连接。`,
    positiveText: '确认停止',
    negativeText: '取消',
    onPositiveClick: () => doStop(b)
  })
}
async function doStop(b: any) {
  actionLoading.value = b.botKey
  try {
    await api.stopBot(b.botKey)
    message.success(`已停止「${b.name || b.botKey}」机器人`)
    patchBot(b.botKey, { status: 'OFFLINE' })
  } catch (e: any) {
    message.error('停止失败：' + e.message)
  } finally {
    actionLoading.value = ''
  }
}
async function onStart(b: any) {
  actionLoading.value = b.botKey
  try {
    await api.startBot(b.botKey)
    message.success(`已启用「${b.name || b.botKey}」机器人`)
    patchBot(b.botKey, { status: 'ONLINE' })
  } catch (e: any) {
    message.error('启用失败：' + e.message)
  } finally {
    actionLoading.value = ''
  }
}

// ===== 添加机器人（表单 + 弹窗）=====
const showAdd = ref(false)
const addLoading = ref(false)
const addFormRef = ref<FormInst | null>(null)
const addForm = reactive({
  appId: '',
  clientSecret: '',
  sandbox: false,
  connectionMethod: 'websocket',
  domain: ''
})
const addRules: FormRules = {
  appId: { required: true, message: '请输入 AppID', trigger: ['input', 'blur'] },
  clientSecret: { required: true, message: '请输入 AppSecret', trigger: ['input', 'blur'] },
  domain: {
    validator: (_rule, value: string) => {
      if (addForm.connectionMethod === 'webhook' && (!value || !value.trim())) {
        return new Error('Webhook 方式需填写回调域名')
      }
      return true
    },
    trigger: ['input', 'blur']
  }
}

function openAdd() {
  addForm.appId = ''
  addForm.clientSecret = ''
  addForm.sandbox = false
  addForm.connectionMethod = 'websocket'
  addForm.domain = ''
  showAdd.value = true
}

async function onAdd() {
  try {
    await addFormRef.value?.validate()
  } catch {
    return
  }
  addLoading.value = true
  try {
    const body: Record<string, string> = {
      appId: addForm.appId.trim(),
      clientSecret: addForm.clientSecret.trim(),
      sandbox: addForm.sandbox ? 'true' : 'false',
      connectionMethod: addForm.connectionMethod,
      domain: addForm.connectionMethod === 'webhook' ? addForm.domain.trim() : ''
    }
    const r = await api.saveBot(body)
    if (r.error) {
      message.error('保存失败：' + r.error)
      addLoading.value = false
      return
    }
    try {
      const rr = await api.reloadBots()
      if (rr.error) message.warning('配置已保存，但自动启用失败：' + rr.error)
      else message.success(`已添加并启用机器人 ${body.appId}`)
    } catch (e: any) {
      message.warning('配置已保存，但自动启用失败：' + e.message)
    }
    showAdd.value = false
    await load()
  } catch (e: any) {
    message.error('添加失败：' + e.message)
  } finally {
    addLoading.value = false
  }
}

// ===== 删除机器人 =====
function onDelete(b: any) {
  dialog.error({
    title: '确认删除机器人',
    content: `即将删除「${b.name || b.botKey}」(AppID: ${b.appId})。\n\n该操作不可恢复，将同时删除此机器人的全部数据，包括：\n• 机器人配置与连接\n• 独立数据库（群 / 成员 / 好友 / 消息 / 事件等全部记录）\n• 运行日志\n\n确定要彻底删除吗？`,
    positiveText: '确认彻底删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const r = await api.deleteBot(b.appId)
        if (r.error) { message.error('删除失败：' + r.error); return }
        message.success(`已彻底删除机器人 ${b.appId}`)
        await load()
      } catch (e: any) {
        message.error('删除失败：' + e.message)
      }
    }
  })
}

function statusLabel(s: string): string {
  return s === 'ONLINE' ? '在线' : s
}
function statusType(s: string): 'success' | 'warning' | 'error' | 'default' | 'info' {
  switch (s) {
    case 'ONLINE': return 'success'
    case 'CONNECTING':
    case 'IDENTIFYING': return 'warning'
    default: return 'default'
  }
}

const columns: DataTableColumns<any> = [
  { title: 'AppId', key: 'appId' },
  { title: '平台', key: 'platform' },
  { title: '状态', key: 'status' },
  { title: '群数', key: 'groupsTotal' },
  { title: '好友数', key: 'friendsTotal' },
  { title: '今日消息', key: 'todayMessages' },
  { title: '操作', key: 'op' }
]
</script>

<template>
  <div>
    <PageHero
      title="机器人管理"
      subtitle="查看并管理已注册的 QQ/OneBot 机器人实例"
      :icon="RocketOutline"
    >
      <NButton :loading="loading" :disabled="showAdd" @click="load">刷新</NButton>
      <NButton type="primary" @click="openAdd">
        <template #icon><NIcon><AddOutline /></NIcon></template>
        添加机器人
      </NButton>
    </PageHero>

    <NAlert v-if="err" type="error" :title="'加载失败'" style="margin-bottom: 16px">{{ err }}</NAlert>

    <!-- 卡片列表 -->
    <div v-if="bots.length" class="bot-grid">
      <NCard
        v-for="b in bots"
        :key="b.botKey"
        hoverable
        class="bot-card"
        @click="openDetail(b.botKey)"
      >
        <div class="bot-card-inner">
          <!-- 左侧头像 -->
          <div class="bot-avatar">
            <img
              v-if="b.avatar && !b._avatarErr"
              :src="b.avatar"
              class="bot-avatar-img"
              :alt="b.name || b.botKey"
              @error="b._avatarErr = true"
            />
            <div v-else class="bot-avatar-fallback">
              <NIcon size="22"><RocketOutline /></NIcon>
            </div>
          </div>

          <!-- 中间信息 -->
          <div class="bot-info">
            <div class="bot-line1">
              <span class="bot-name">{{ b.name || b.botKey }}</span>
              <span class="bot-appid">AppId: {{ b.appId }}</span>
            </div>
            <div class="bot-line2">
              <NTag size="small" round :bordered="true" :type="statusType(b.status)">
                <template #icon>
                  <span class="status-dot" :class="'dot-' + (b.status || '').toLowerCase()" />
                </template>
                {{ statusLabel(b.status) }}
              </NTag>
              <NTag size="small" round :bordered="true" type="info">{{ b.platform }}</NTag>
              <NTag
                v-if="b.connectionType"
                size="small"
                round
                :bordered="true"
                type="primary"
              >{{ b.connectionType }}</NTag>
            </div>
            <!-- webhook 回调地址（完整回调 = https://{domain}/webhook/{appId}） -->
            <div
              v-if="b.connectionType === 'webhook' && b.domain"
              class="bot-callback"
              title="点击复制回调地址"
              @click.stop="copy(`https://${b.domain}/webhook/${b.appId}`)"
            >
              <NIcon size="13" style="opacity: .6"><LinkOutline /></NIcon>
              <span class="callback-text">https://{{ b.domain }}/webhook/{{ b.appId }}</span>
            </div>
          </div>

          <!-- 右侧操作区 -->
          <div class="bot-right" @click.stop>
            <NButton
              v-if="b.status === 'ONLINE'"
              type="error"
              secondary
              size="small"
              :loading="actionLoading === b.botKey"
              @click="onStop(b)"
            >停止</NButton>
            <NButton
              v-else
              type="primary"
              size="small"
              :loading="actionLoading === b.botKey"
              @click="onStart(b)"
            >启用</NButton>
            <NButton
              text
              size="small"
              type="error"
              @click="onDelete(b)"
            >删除</NButton>
            <NIcon size="18" class="arrow" @click="openDetail(b.botKey)"><ChevronForwardOutline /></NIcon>
          </div>
        </div>
      </NCard>
    </div>

    <NEmpty
      v-else-if="!loading && !err"
      description="暂无机器人，请先在向导或配置中绑定"
      style="padding: 60px 0"
    />

    <!-- 详情抽屉 -->
    <NDrawer v-model:show="showDetail" :width="440" placement="right">
      <NDrawerContent :title="`机器人详情 · ${detail?.botKey ?? ''}`">
        <NAlert v-if="detail?.error" type="error">{{ detail.error }}</NAlert>
        <template v-else>
          <div class="detail-head">
            <div class="detail-avatar">
              <img
                v-if="detail?.avatar && !detailAvatarErr"
                :src="detail.avatar"
                class="detail-avatar-img"
                :alt="detail?.name || detail?.botKey"
                @error="detailAvatarErr = true"
              />
              <div v-else class="detail-avatar-fallback">
                <NIcon size="26"><RocketOutline /></NIcon>
              </div>
            </div>
            <div>
              <NText strong style="font-size: 16px">{{ detail?.name || detail?.botKey }}</NText>
              <div>
                <NTag :type="detail?.status === 'ONLINE' ? 'success' : 'default'" :bordered="false" size="small">
                  {{ detail?.status === 'ONLINE' ? '在线' : detail?.status }}
                </NTag>
              </div>
            </div>
          </div>
          <NDescriptions label-placement="left" :column="1" bordered style="margin-top: 16px">
            <NDescriptionsItem label="AppId">
              <NSpace :size="6" align="center">
                <span>{{ detail?.appId }}</span>
                <NButton text size="tiny" @click="copy(String(detail?.appId))"><NIcon><CopyOutline /></NIcon></NButton>
              </NSpace>
            </NDescriptionsItem>
            <NDescriptionsItem label="平台">{{ detail?.platform }}</NDescriptionsItem>
            <NDescriptionsItem label="连接方式">{{ detail?.connectionType || '—' }}</NDescriptionsItem>
            <NDescriptionsItem
              v-if="detail?.connectionType === 'webhook' && detail?.domain"
              label="回调地址"
            >
              <NSpace :size="6" align="center">
                <span style="word-break: break-all">
                  https://{{ detail.domain }}/webhook/{{ detail.appId }}
                </span>
                <NButton text size="tiny" @click="copy(`https://${detail.domain}/webhook/${detail.appId}`)">
                  <NIcon><CopyOutline /></NIcon>
                </NButton>
              </NSpace>
            </NDescriptionsItem>
            <NDescriptionsItem label="环境">{{ detail?.env }}</NDescriptionsItem>
            <NDescriptionsItem label="AppSecret">
              <NSpace :size="6" align="center">
                <span>{{ detail?.appSecret }}</span>
                <NButton text size="tiny" @click="copy(String(detail?.appSecret))"><NIcon><CopyOutline /></NIcon></NButton>
              </NSpace>
            </NDescriptionsItem>
            <NDescriptionsItem label="群总数">{{ detail?.groupsTotal }}</NDescriptionsItem>
            <NDescriptionsItem label="好友总数">{{ detail?.friendsTotal }}</NDescriptionsItem>
            <NDescriptionsItem label="今日消息">{{ detail?.todayMessages }}</NDescriptionsItem>
          </NDescriptions>
        </template>
      </NDrawerContent>
    </NDrawer>

    <!-- 添加机器人弹窗 -->
    <NModal
      v-model:show="showAdd"
      title="添加机器人"
      preset="card"
      style="width: 520px; max-width: 92vw"
      :bordered="false"
      :mask-closable="false"
    >
      <NForm
        ref="addFormRef"
        :model="addForm"
        :rules="addRules"
        label-placement="top"
      >
        <NFormItem label="AppID" path="appId">
          <NInput
            v-model:value="addForm.appId"
            placeholder="QQ 开放平台 AppID"
            :disabled="addLoading"
            clearable
          />
        </NFormItem>

        <NFormItem label="AppSecret" path="clientSecret">
          <NInput
            v-model:value="addForm.clientSecret"
            type="password"
            show-password-on="click"
            placeholder="QQ 开放平台 AppSecret"
            :disabled="addLoading"
          />
        </NFormItem>

        <NFormItem label="环境">
          <div style="display: flex; align-items: center; gap: 10px">
            <NSwitch v-model:value="addForm.sandbox" :disabled="addLoading" />
            <NText depth="3" style="font-size: 12px">
              {{ addForm.sandbox ? '沙箱环境（测试用）' : '正式环境' }}
            </NText>
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
            <NInput
              v-model:value="addForm.domain"
              placeholder="例如 xuanji.com"
              :disabled="addLoading"
              clearable
            />
          </NFormItem>
          <NAlert type="info" :show-icon="true" style="margin-bottom: 14px">
            <div style="line-height: 1.7">
              回调地址将自动生成为：
              <br />
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
  grid-template-columns: repeat(auto-fill, minmax(420px, 1fr));
  gap: 14px;
}
.bot-card {
  cursor: pointer;
  transition: box-shadow 0.15s ease;
}
.bot-card:hover {
  box-shadow: 0 4px 16px rgba(20, 30, 60, 0.10);
}
.bot-card-inner {
  display: flex;
  align-items: center;
  gap: 14px;
}
.bot-avatar {
  flex-shrink: 0;
  width: 56px;
  height: 56px;
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
.bot-avatar-fallback :deep(.n-icon),
.bot-avatar-fallback .n-icon {
  font-size: 24px;
}
.bot-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.bot-line1 {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}
.bot-line2 {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.bot-callback {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 6px;
  font-size: 11px;
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
  font-size: 15px;
  color: var(--n-text-color-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 1;
  min-width: 0;
}
.bot-appid {
  font-size: 12px;
  color: var(--n-text-color-3);
  flex-shrink: 0;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}
.dot-online {
  background-color: #18a058;
  box-shadow: 0 0 4px rgba(24, 160, 88, 0.45);
}
.dot-connecting,
.dot-identifying {
  background-color: #f0a020;
  box-shadow: 0 0 4px rgba(240, 160, 32, 0.45);
}
.dot-offline,
.dot-disconnected {
  background-color: #999;
}

/* 右侧操作区：按钮 + 箭头 */
.bot-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: 16px;
}
.arrow {
  color: var(--n-text-color-3);
  cursor: pointer;
  padding: 2px;
}
.arrow:hover {
  color: var(--n-text-color-1);
}

/* 详情抽屉 */
.detail-head {
  display: flex;
  align-items: center;
  gap: 14px;
}
.detail-avatar {
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  overflow: hidden;
  background: #f0f0f5;
  display: flex;
  align-items: center;
  justify-content: center;
}
.detail-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.detail-avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #aaa;
}

/* 添加机器人 - 回调地址预览 */
.cb-url {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  color: #d03050;
  word-break: break-all;
  user-select: all;
}
</style>
