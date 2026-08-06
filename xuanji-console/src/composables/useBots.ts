// 机器人下拉选项共享逻辑：Events / Messages 等页面按 Bot 过滤时复用。
import { ref } from 'vue'
import api from '../api'

export function useBots() {
  const bots = ref<{ label: string; value: string }[]>([])
  const loading = ref(false)

  async function loadBots() {
    loading.value = true
    try {
      const bs: any[] = await api.getBots()
      bots.value = bs.map((b) => ({ label: 'Bot #' + b.appId, value: b.appId }))
    } catch {
      bots.value = []
    } finally {
      loading.value = false
    }
  }

  return { bots, loading, loadBots }
}
