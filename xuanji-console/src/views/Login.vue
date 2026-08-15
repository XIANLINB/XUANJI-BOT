<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import {
  NCard, NForm, NFormItem, NInput, NButton, NText, NIcon, NAlert,
  NGradientText, NDivider, NTag, useMessage
} from 'naive-ui'
import {
  RocketOutline, KeyOutline, ArrowForwardOutline, LockClosedOutline,
  SparklesOutline, ServerOutline, FlashOutline, GitNetworkOutline
} from '@vicons/ionicons5'
import api from '../api'
import { brand } from '../theme'
import { usePreferencesStore } from '../stores/preferences'

const message = useMessage()
const router = useRouter()
const prefs = usePreferencesStore()

const form = reactive({ pin: '' })
const pinError = ref('')
const loading = ref(false)

function onPinInput(value: string) {
  form.pin = value.replace(/\D/g, '').slice(0, 6)
  pinError.value = ''
}

async function login() {
  if (!/^\d{6}$/.test(form.pin)) {
    pinError.value = '请输入 6 位数字访问口令'
    return
  }
  loading.value = true
  try {
    const r = await api.authLogin({ pin: form.pin })
    if (r.error) {
      pinError.value = r.error
      return
    }
    message.success('登录成功')
    // 默认首页：读个性化配置（登录后拉取 preferences 并跳转）
    try {
      await prefs.load()
      router.replace('/' + (prefs.prefs.homeRoute || 'dashboard'))
    } catch {
      router.replace('/dashboard')
    }
  } catch (e: any) {
    pinError.value = '登录失败：' + (e?.message ?? e)
  } finally {
    loading.value = false
  }
}

const features = [
  { icon: RocketOutline, text: '多机器人统一管控' },
  { icon: FlashOutline, text: '实时事件处理流水' },
  { icon: ServerOutline, text: '权限/黑名单/审计' },
  { icon: GitNetworkOutline, text: 'QQ 官方开放平台协议' }
]
</script>

<template>
  <div class="login-root">
    <div class="login-bg">
      <div class="bg-blob bg-blob-1" />
      <div class="bg-blob bg-blob-2" />
      <div class="bg-blob bg-blob-3" />
    </div>

    <NCard class="login-card" :bordered="false">
      <div class="brand-row">
        <div class="brand-icon">
          <NIcon size="32" :color="brand.primary"><RocketOutline /></NIcon>
        </div>
        <div class="brand-text">
          <NGradientText :gradient="{ deg: 90, from: '#5b5bd6', to: '#2090e0' }" :size="20" style="font-weight: 800; letter-spacing: 1px">
            璇玑控制台
          </NGradientText>
          <NText depth="3" style="display: block; font-size: 12px; margin-top: 2px">
            Xuanji Bot Framework · Console
          </NText>
        </div>
      </div>

      <NDivider style="margin: 16px 0 18px" />

      <div class="hero-row">
        <NIcon size="20" :color="brand.primary"><LockClosedOutline /></NIcon>
        <NText strong style="font-size: 15px">输入访问口令登录</NText>
      </div>

      <NForm class="form" @submit.prevent="login">
        <NFormItem label="访问口令（6 位数字）">
          <NInput
            :value="form.pin"
            @update:value="onPinInput"
            type="password"
            show-password-on="click"
            placeholder="例如 123456"
            :maxlength="6"
            size="large"
            :input-props="{ inputmode: 'numeric' }"
            @keyup.enter="login"
          >
            <template #prefix>
              <NIcon :color="brand.primary"><KeyOutline /></NIcon>
            </template>
          </NInput>
        </NFormItem>
        <NAlert v-if="pinError" type="error" :show-icon="true" style="margin-bottom: 12px">{{ pinError }}</NAlert>
        <NButton
          type="primary"
          size="large"
          block
          :loading="loading"
          @click="login"
        >
          登录
          <template #icon><NIcon><ArrowForwardOutline /></NIcon></template>
        </NButton>
      </NForm>

      <NDivider style="margin: 18px 0 14px">
        <NText depth="3" style="font-size: 12px">能力一览</NText>
      </NDivider>

      <div class="features">
        <div v-for="(f, i) in features" :key="i" class="feature-item">
          <NIcon size="14" :color="brand.primary"><component :is="f.icon" /></NIcon>
          <span>{{ f.text }}</span>
        </div>
      </div>

      <NText depth="3" class="footer-tip">
        控制台已启用访问保护，请输入初始化时设置的 6 位访问口令。
      </NText>
    </NCard>

    <div class="login-footer">
      <NIcon size="12" color="#86909c"><SparklesOutline /></NIcon>
      <NText depth="3" style="font-size: 11.5px">璇玑机器人框架 · 多平台 · Spring Boot · H2 嵌入式</NText>
    </div>
  </div>
</template>

<style scoped>
.login-root {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: linear-gradient(135deg, #f6f8fc 0%, #eef1f7 100%);
}
.login-bg {
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
.login-card {
  position: relative;
  z-index: 1;
  width: 440px;
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
.hero-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.form { margin-top: 4px; }
.features {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 14px;
  margin-top: 4px;
}
.feature-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--n-text-color-2);
}
.footer-tip {
  display: block;
  text-align: center;
  line-height: 1.6;
  margin-top: 16px;
  font-size: 12px;
}
.login-footer {
  position: relative;
  z-index: 1;
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  opacity: 0.7;
}
</style>