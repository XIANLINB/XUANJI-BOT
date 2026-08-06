<script setup lang="ts">
import { ref, computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import {
  darkTheme,
  lightTheme,
  NConfigProvider,
  NMessageProvider,
  NDialogProvider,
  NNotificationProvider,
  NLoadingBarProvider
} from 'naive-ui'
import { lightOverrides, darkOverrides } from './theme'
import ConsoleLayout from './components/ConsoleLayout.vue'

// 首启引导页为全屏独立布局，不套用控制台侧边栏。
// Setup 懒加载：引导页不常进，避免拖慢首屏。
// ⚠️ Vue 3 陷阱：模板中直接使用 () => import() 工厂会被当函数式组件调用，
// 返回的 Promise 被渲染成 [object Promise]（Vue 2 旧写法失效），必须 defineAsyncComponent 包裹。
const Setup = defineAsyncComponent(() => import('./views/Setup.vue'))
const route = useRoute()
const isSetup = computed(() => route.name === 'setup')

// 用户要求：控制台默认亮色。
const isDark = ref(false)
const theme = computed(() => (isDark.value ? darkTheme : lightTheme))
const overrides = computed(() => (isDark.value ? darkOverrides : lightOverrides))

function toggleTheme() {
  isDark.value = !isDark.value
}
</script>

<template>
  <NConfigProvider :theme="theme" :theme-overrides="overrides">
    <NLoadingBarProvider>
      <NMessageProvider>
        <NDialogProvider>
          <NNotificationProvider>
            <Setup v-if="isSetup" />
            <ConsoleLayout v-else :is-dark="isDark" @toggle-theme="toggleTheme" />
          </NNotificationProvider>
        </NDialogProvider>
      </NMessageProvider>
    </NLoadingBarProvider>
  </NConfigProvider>
</template>
