<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  darkTheme,
  lightTheme,
  zhCN, dateZhCN,
  NConfigProvider,
  NMessageProvider,
  NDialogProvider,
  NNotificationProvider,
  NLoadingBarProvider,
  NGlobalStyle
} from 'naive-ui'
import { buildThemeOverrides } from './theme'
import { usePreferencesStore } from './stores/preferences'
import ConsoleLayout from './components/ConsoleLayout.vue'

// 首启引导页为全屏独立布局，不套用控制台侧边栏。
// Setup 懒加载：引导页不常进，避免拖慢首屏。
// Vue 3 陷阱：模板中直接使用 () => import() 工厂会被当函数式组件调用，
// 返回的 Promise 被渲染成 [object Promise]，必须 defineAsyncComponent 包裹。
const Setup = defineAsyncComponent(() => import('./views/Setup.vue'))
const Login = defineAsyncComponent(() => import('./views/Login.vue'))
const route = useRoute()
const isSetup = computed(() => route.name === 'setup')
const isLogin = computed(() => route.name === 'login')

// 个性化设置：主题/品牌/字号/密度/动效全部由 preferences store 驱动
const prefs = usePreferencesStore()
const theme = computed(() => (prefs.isDark ? darkTheme : lightTheme))
const overrides = computed(() => buildThemeOverrides(prefs.prefs, prefs.isDark))
// naive-ui 全局中文 locale（影响 NDatePicker/NCalendar/NTimePicker 等日期组件）
const locale = computed(() => (prefs.isDark ? null : zhCN))
const dateLocale = computed(() => (prefs.isDark ? null : dateZhCN))

// 顶栏快捷切换（亮↔暗二态；三态在个性化设置页配置）
const isDark = computed(() => prefs.isDark)
function toggleTheme() {
  prefs.update({ themeMode: prefs.isDark ? 'light' : 'dark' })
}

// 动效：motion='off' 时全局禁用过渡/动画（性能 & 偏好）
watch(() => prefs.prefs.motion, (m) => {
  const id = 'xuanji-motion-off-style'
  const existing = document.getElementById(id)
  if (m === 'off') {
    if (!existing) {
      const s = document.createElement('style')
      s.id = id
      s.textContent = '*, *::before, *::after { transition: none !important; animation: none !important; }'
      document.head.appendChild(s)
    }
  } else if (existing) {
    existing.remove()
  }
}, { immediate: true })

// 系统主题偏好监听（themeMode='system' 时跟随系统）
onMounted(async () => {
  await prefs.load()
  const mq = window.matchMedia('(prefers-color-scheme: dark)')
  prefs.systemDark = mq.matches
  mq.addEventListener('change', (e) => { prefs.systemDark = e.matches })
})
</script>

<template>
  <NConfigProvider
    :theme="theme"
    :theme-overrides="overrides"
    :locale="locale"
    :date-locale="dateLocale"
  >
    <NGlobalStyle />
    <NLoadingBarProvider>
      <NMessageProvider>
        <NDialogProvider>
          <NNotificationProvider>
            <Setup v-if="isSetup" />
            <Login v-else-if="isLogin" />
            <ConsoleLayout v-else :is-dark="isDark" @toggle-theme="toggleTheme" />
          </NNotificationProvider>
        </NDialogProvider>
      </NMessageProvider>
    </NLoadingBarProvider>
  </NConfigProvider>
</template>

<style>
/* 全局锁定：html/body 高度 100% + overflow hidden，强制各页面在可视区内布局，
   防止 .chat-page 等内部容器被内容撑大后导致 body 出现滚动条。 */
html, body, #app { height: 100%; margin: 0; padding: 0; overflow: hidden; }
</style>
