<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import {
  NCard, NForm, NFormItem, NInput, NButton, NText, NIcon, NAlert
} from 'naive-ui'
import {
  ColorPaletteOutline, KeyOutline, ArrowForwardOutline
} from '@vicons/ionicons5'
import api from '../api'
import { brand } from '../theme'

const message = useMessage()
const router = useRouter()

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
    router.replace('/dashboard')
  } catch (e: any) {
    pinError.value = '登录失败：' + (e?.message ?? e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-root">
    <div class="login-bg" />
    <NCard class="login-card" :bordered="false">
      <div class="brand">
        <NIcon size="26" :color="brand.primary"><ColorPaletteOutline /></NIcon>
        <span class="brand-text">璇玑控制台 · 登录</span>
      </div>

      <div class="head">
        <NIcon size="22" :color="brand.primary"><KeyOutline /></NIcon>
        <NText strong style="font-size: 16px">输入访问口令</NText>
      </div>
      <NText depth="3" class="lead">
        控制台已启用访问保护，请输入初始化时设置的 6 位访问口令。
      </NText>

      <NForm class="form" @submit.prevent="login">
        <NFormItem label="访问口令（6 位数字）">
          <NInput
            :value="form.pin"
            @update:value="onPinInput"
            type="password"
            show-password-on="click"
            placeholder="例如 123456"
            :maxlength="6"
            :input-props="{ inputmode: 'numeric' }"
            @keyup.enter="login"
          />
        </NFormItem>
        <NAlert v-if="pinError" type="error" :show-icon="true">{{ pinError }}</NAlert>
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
    </NCard>
  </div>
</template>

<style scoped>
.login-root {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--n-color);
}
.login-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(900px 500px at 15% 10%, rgba(91, 91, 214, 0.12), transparent 60%),
    radial-gradient(800px 500px at 90% 90%, rgba(32, 144, 224, 0.12), transparent 60%);
}
.login-card {
  position: relative;
  width: 420px;
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
.head {
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
</style>
