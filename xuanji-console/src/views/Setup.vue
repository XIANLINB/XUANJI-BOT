<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import {
  NCard, NSteps, NStep, NForm, NFormItem, NInput, NSwitch, NRadioGroup,
  NRadioButton, NButton, NAlert, NText, NIcon, NDivider, NGradientText,
  NSpace
} from 'naive-ui'
import {
  RocketOutline, KeyOutline, CheckmarkCircleOutline,
  OptionsOutline, ArrowForwardOutline, SparklesOutline, ShieldCheckmarkOutline
} from '@vicons/ionicons5'
import api from '../api'
import { brand } from '../theme'

const message = useMessage()
const router = useRouter()

const current = ref(1)
const pinLoading = ref(false)
const botLoading = ref(false)
const finishing = ref(false)

// ===== 第一步：6 位数字访问口令 =====
const pinForm = reactive({ pin: '', confirm: '' })
const pinError = ref('')

function onPinInput(value: string, field: 'pin' | 'confirm') {
  const cleaned = value.replace(/\D/g, '').slice(0, 6)
  pinForm[field] = cleaned
  pinError.value = ''
}

const pinValid = computed(() => /^\d{6}$/.test(pinForm.pin) && pinForm.pin === pinForm.confirm)

async function nextFromPin() {
  if (!/^\d{6}$/.test(pinForm.pin)) {
    pinError.value = '请输入 6 位数字口令'
    return
  }
  if (pinForm.pin !== pinForm.confirm) {
    pinError.value = '两次输入的口令不一致'
    return
  }
  pinLoading.value = true
  try {
    const r = await api.setupPin({ pin: pinForm.pin })
    if (r.error) {
      message.error(r.error)
      return
    }
    current.value = 2
  } catch (e: any) {
    message.error('设置失败：' + (e?.message ?? e))
  } finally {
    pinLoading.value = false
  }
}

// ===== 第二步：可选绑定机器人 =====
const botForm = reactive({
  appId: '',
  clientSecret: '',
  sandbox: false,
  connectionMethod: 'websocket',
  domain: ''
})

async function finish() {
  finishing.value = true
  try {
    const r = await api.setupComplete()
    if (r.error) {
      message.error(r.error)
      return
    }
    ;(window as any).__xuanjiSetupDone = true
    try {
      const login = await api.authLogin({ pin: pinForm.pin })
      if (login.error) throw new Error(login.error)
    } catch (e: any) {
      message.warning('初始化完成，但自动登录失败，请手动登录')
      router.replace('/login')
      return
    }
    message.success('初始化完成，进入控制台')
    router.replace('/dashboard')
  } catch (e: any) {
    message.error('完成失败：' + (e?.message ?? e))
  } finally {
    finishing.value = false
  }
}

async function skipBot() {
  await finish()
}

async function saveAndEnter() {
  if (!botForm.appId.trim() || !botForm.clientSecret.trim()) {
    message.warning('请填写 AppID 与 AppSecret，或点击「跳过」')
    return
  }
  if (botForm.connectionMethod === 'webhook' && !botForm.domain.trim()) {
    message.warning('Webhook 方式需填写回调域名，或点击「跳过」')
    return
  }
  botLoading.value = true
  try {
    const r = await api.setupBot({
      appId: botForm.appId.trim(),
      clientSecret: botForm.clientSecret.trim(),
      sandbox: botForm.sandbox ? 'true' : 'false',
      connectionMethod: botForm.connectionMethod,
      domain: botForm.connectionMethod === 'webhook' ? botForm.domain.trim() : ''
    })
    if (r.error) {
      message.error(r.error)
      return
    }
    try {
      await api.reloadBots()
      message.success('机器人已写入并启用，正在进入控制台')
    } catch {
      message.success('机器人已写入，正在进入控制台')
    }
    await finish()
  } catch (e: any) {
    message.error('保存失败：' + (e?.message ?? e))
  } finally {
    botLoading.value = false
  }
}

const setupSteps = [
  { title: '设置访问口令', description: '必填' },
  { title: '绑定机器人', description: '可选' }
]
</script>

<template>
  <div class="setup-root">
    <div class="setup-bg">
      <div class="bg-blob bg-blob-1" />
      <div class="bg-blob bg-blob-2" />
      <div class="bg-blob bg-blob-3" />
    </div>

    <NCard class="setup-card" :bordered="false">
      <div class="brand-row">
        <div class="brand-icon">
          <NIcon size="32" :color="brand.primary"><RocketOutline /></NIcon>
        </div>
        <div class="brand-text">
          <NGradientText :gradient="{ deg: 90, from: '#5b5bd6', to: '#2090e0' }" :size="20" style="font-weight: 800; letter-spacing: 1px">
            璇玑控制台 · 初始化引导
          </NGradientText>
          <NText depth="3" style="display: block; font-size: 12px; margin-top: 2px">
            Xuanji Bot Framework · First-time Setup
          </NText>
        </div>
      </div>

      <NDivider style="margin: 14px 0 18px" />

      <NSteps :current="current" class="steps" size="small">
        <NStep v-for="(s, i) in setupSteps" :key="i" :title="s.title" :description="s.description" />
      </NSteps>

      <!-- 第一步 -->
      <div v-if="current === 1" class="step-body">
        <div class="step-head">
          <div class="step-icon" style="background: rgba(91, 91, 214, 0.12); color: #5b5bd6">
            <NIcon size="18"><KeyOutline /></NIcon>
          </div>
          <div>
            <NText strong style="font-size: 15px; display: block">设置 6 位访问口令</NText>
            <NText depth="3" style="font-size: 12px">系统自动生成随机盐 + PBKDF2 加盐哈希，原始口令不会落盘</NText>
          </div>
        </div>

        <NForm class="form" @submit.prevent="nextFromPin">
          <NFormItem label="访问口令（6 位数字）">
            <NInput
              :value="pinForm.pin"
              @update:value="(v: string) => onPinInput(v, 'pin')"
              type="password"
              show-password-on="click"
              placeholder="例如 123456"
              :maxlength="6"
              size="large"
              :input-props="{ inputmode: 'numeric' }"
            />
          </NFormItem>
          <NFormItem label="确认口令">
            <NInput
              :value="pinForm.confirm"
              @update:value="(v: string) => onPinInput(v, 'confirm')"
              type="password"
              show-password-on="click"
              placeholder="再次输入 6 位数字"
              :maxlength="6"
              size="large"
              :input-props="{ inputmode: 'numeric' }"
            />
          </NFormItem>
          <NAlert v-if="pinError" type="error" :show-icon="true" style="margin-bottom: 10px">{{ pinError }}</NAlert>
          <NAlert v-else-if="pinForm.confirm && pinValid" type="success" :show-icon="true" style="margin-bottom: 10px">
            两次输入一致，可以继续
          </NAlert>
        </NForm>

        <div class="actions">
          <NButton
            type="primary"
            size="large"
            block
            :loading="pinLoading"
            @click="nextFromPin"
          >
            下一步：绑定机器人
            <template #icon><NIcon><ArrowForwardOutline /></NIcon></template>
          </NButton>
        </div>
      </div>

      <!-- 第二步 -->
      <div v-else class="step-body">
        <div class="step-head">
          <div class="step-icon" style="background: rgba(32, 144, 224, 0.12); color: #2090e0">
            <NIcon size="18"><RocketOutline /></NIcon>
          </div>
          <div>
            <NText strong style="font-size: 15px; display: block">绑定机器人（可选）</NText>
            <NText depth="3" style="font-size: 12px">现在绑定或跳过，稍后在「机器人管理」页面添加</NText>
          </div>
        </div>

        <NForm class="form">
          <NFormItem label="AppID">
            <NInput v-model:value="botForm.appId" placeholder="QQ 开放平台 AppID" clearable />
          </NFormItem>
          <NFormItem label="AppSecret">
            <NInput
              v-model:value="botForm.clientSecret"
              type="password"
              show-password-on="click"
              placeholder="QQ 开放平台 AppSecret"
            />
          </NFormItem>
          <NFormItem label="环境">
            <div style="display: flex; align-items: center; gap: 10px">
              <NSwitch v-model:value="botForm.sandbox" />
              <NText depth="3" style="font-size: 12px">
                {{ botForm.sandbox ? '沙箱环境（测试用）' : '正式环境' }}
              </NText>
            </div>
          </NFormItem>
          <NFormItem label="连接方式">
            <NRadioGroup v-model:value="botForm.connectionMethod">
              <NRadioButton value="websocket">WebSocket</NRadioButton>
              <NRadioButton value="webhook">Webhook</NRadioButton>
            </NRadioGroup>
          </NFormItem>
          <NFormItem v-if="botForm.connectionMethod === 'webhook'" label="回调域名">
            <NInput v-model:value="botForm.domain" placeholder="例如 xuanji.com" clearable />
          </NFormItem>
          <NFormItem
            v-if="botForm.connectionMethod === 'webhook' && botForm.domain && botForm.appId"
            label="回调地址"
          >
            <NAlert type="info" :show-icon="false" style="width: 100%">
              <NText code style="font-size: 12px">
                https://{{ botForm.domain }}/webhook/{{ botForm.appId }}
              </NText>
              <NText depth="3" style="font-size: 12px; display: block; margin-top: 6px">
                复制此地址填到 QQ 开放平台「事件订阅」的回调地址（POST /webhook/{appId}）
              </NText>
            </NAlert>
          </NFormItem>
        </NForm>

        <div class="actions">
          <NSpace vertical :size="10">
            <NButton
              type="primary"
              size="large"
              block
              :loading="botLoading"
              @click="saveAndEnter"
            >
              保存并进入控制台
            </NButton>
            <NButton
              size="large"
              block
              :loading="finishing"
              :disabled="botLoading"
              @click="skipBot"
            >
              <template #icon><NIcon><OptionsOutline /></NIcon></template>
              跳过，直接进入控制台
            </NButton>
          </NSpace>
        </div>
      </div>

      <div class="foot">
        <NIcon size="13" color="#18a058"><ShieldCheckmarkOutline /></NIcon>
        <NText depth="3" style="font-size: 12px">框架开发阶段 · 数据目录可随时清空重走流程</NText>
      </div>
    </NCard>

    <div class="setup-footer">
      <NIcon size="12" color="#86909c"><SparklesOutline /></NIcon>
      <NText depth="3" style="font-size: 11.5px">璇玑机器人框架 · 多平台 · Spring Boot · H2 嵌入式</NText>
    </div>
  </div>
</template>

<style scoped>
.setup-root {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: linear-gradient(135deg, #f6f8fc 0%, #eef1f7 100%);
}
.setup-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}
.bg-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.35;
}
.bg-blob-1 {
  width: 520px;
  height: 520px;
  top: -160px;
  left: -120px;
  background: radial-gradient(circle, #5b5bd6, transparent);
}
.bg-blob-2 {
  width: 460px;
  height: 460px;
  bottom: -180px;
  right: -100px;
  background: radial-gradient(circle, #2090e0, transparent);
}
.bg-blob-3 {
  width: 360px;
  height: 360px;
  top: 40%;
  right: 30%;
  background: radial-gradient(circle, #18a058, transparent);
  opacity: 0.22;
}
.setup-card {
  position: relative;
  z-index: 1;
  width: 520px;
  max-width: 92vw;
  border-radius: 18px;
  box-shadow: 0 24px 60px rgba(20, 30, 60, 0.12), 0 2px 8px rgba(20, 30, 60, 0.04);
  padding: 28px 28px 22px;
}
.brand-row {
  display: flex;
  align-items: center;
  gap: 14px;
}
.brand-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(91, 91, 214, 0.12), rgba(32, 144, 224, 0.12));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.brand-text { min-width: 0; }
.steps { margin-bottom: 18px; }
.step-body { padding: 4px 0; }
.step-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
  padding: 12px 14px;
  background: rgba(128, 128, 128, 0.04);
  border-radius: 10px;
}
.step-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.form { margin-top: 4px; }
.actions { margin-top: 18px; }
.foot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--n-border-color);
}
.setup-footer {
  position: relative;
  z-index: 1;
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  opacity: 0.7;
}
</style>