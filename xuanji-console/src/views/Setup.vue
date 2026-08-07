<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import {
  NCard, NSteps, NStep, NForm, NFormItem, NInput, NSwitch, NRadioGroup,
  NRadioButton, NButton, NAlert, NText, NIcon, NResult, NSpace
} from 'naive-ui'
import {
  ColorPaletteOutline, KeyOutline, RocketOutline, CheckmarkCircleOutline,
  OptionsOutline, ArrowForwardOutline
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
  // 仅允许数字，最多 6 位
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
    // 告知路由守卫：已完成，放行
    ;(window as any).__xuanjiSetupDone = true
    // 初始化刚设的 PIN 直接用于登录，下发会话 cookie，避免再手动输一次
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
    // 向导写入的是数据库，需触发 reload 端点从库拉起 WS（与机器人管理页保存后的行为一致）
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
</script>

<template>
  <div class="setup-root">
    <div class="setup-bg" />
    <NCard class="setup-card" :bordered="false">
      <!-- 品牌头 -->
      <div class="brand">
        <NIcon size="26" :color="brand.primary"><ColorPaletteOutline /></NIcon>
        <span class="brand-text">璇玑控制台 · 初始化引导</span>
      </div>

      <NSteps :current="current" class="steps" size="small">
        <NStep title="设置访问口令" :description="'必填'" />
        <NStep title="绑定机器人" :description="'可选'" />
      </NSteps>

      <!-- 第一步 -->
      <div v-if="current === 1" class="step-body">
        <div class="step-head">
          <NIcon size="22" :color="brand.primary"><KeyOutline /></NIcon>
          <NText strong style="font-size: 16px">设置 6 位访问口令</NText>
        </div>
        <NText depth="3" class="lead">
          该口令用于控制台访问，系统会自动生成随机盐并使用 PBKDF2 加盐哈希后存储，原始口令不会落盘。
        </NText>

        <NForm class="form">
          <NFormItem label="访问口令（6 位数字）">
            <NInput
              :value="pinForm.pin"
              @update:value="(v: string) => onPinInput(v, 'pin')"
              type="password"
              show-password-on="click"
              placeholder="例如 123456"
              :maxlength="6"
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
              :input-props="{ inputmode: 'numeric' }"
            />
          </NFormItem>
          <NAlert v-if="pinError" type="error" :show-icon="true">{{ pinError }}</NAlert>
        </NForm>

        <div class="actions">
          <NButton
            type="primary"
            size="large"
            block
            :loading="pinLoading"
            @click="nextFromPin"
          >
            下一步
            <template #icon><NIcon><ArrowForwardOutline /></NIcon></template>
          </NButton>
        </div>
      </div>

      <!-- 第二步 -->
      <div v-else class="step-body">
        <div class="step-head">
          <NIcon size="22" :color="brand.primary"><RocketOutline /></NIcon>
          <NText strong style="font-size: 16px">绑定机器人（可选）</NText>
        </div>
        <NText depth="3" class="lead">
          现在可以绑定一个 QQ/OneBot 机器人，也可以直接跳过，稍后在「机器人管理」页面添加。
        </NText>

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
        <NIcon size="14" :color="brand.success"><CheckmarkCircleOutline /></NIcon>
        <NText depth="3" style="font-size: 12px">框架开发阶段 · 数据目录可随时清空重走流程</NText>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.setup-root {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--n-color);
}
.setup-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(900px 500px at 15% 10%, rgba(91, 91, 214, 0.12), transparent 60%),
    radial-gradient(800px 500px at 90% 90%, rgba(32, 144, 224, 0.12), transparent 60%);
}
.setup-card {
  position: relative;
  width: 480px;
  max-width: 92vw;
  border-radius: 18px;
  box-shadow: 0 18px 50px rgba(20, 30, 60, 0.14);
  padding: 26px 26px 18px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}
.brand-text {
  font-weight: 800;
  font-size: 17px;
  letter-spacing: 0.5px;
  background: linear-gradient(90deg, #5b5bd6, #2090e0);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.steps {
  margin-bottom: 18px;
}
.step-body {
  padding: 4px 0;
}
.step-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.lead {
  display: block;
  line-height: 1.7;
  margin-bottom: 16px;
}
.form {
  margin-top: 6px;
}
.actions {
  margin-top: 18px;
}
.foot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--n-border-color);
}
</style>
