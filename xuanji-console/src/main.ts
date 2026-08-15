import { createApp } from 'vue'
import { createPinia } from 'pinia'
import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import App from './App.vue'
import { router } from './router'

// 全局加载 utc 插件：使 dayjs().utcOffset(8) 返回 Dayjs 实例（而非 number），
// 支撑全站 UTC+8 时间统一（.utcOffset(8).format/.startOf/.unix 等链式调用）。
dayjs.extend(utc)

createApp(App).use(createPinia()).use(router).mount('#app')
