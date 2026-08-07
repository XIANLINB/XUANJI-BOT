// 机器人下拉选项共享逻辑（批次11 重构）：底层数据收拢到 Pinia stores/bots，
// 本 composable 保持旧接口（{ bots, loading, loadBots }）供 Events / Messages 等页面零改动复用。
import { computed } from 'vue'
import { useBotsStore } from '../stores/bots'

export function useBots() {
  const store = useBotsStore()
  const bots = computed(() => store.botOptions)
  const loading = computed(() => store.loading)

  async function loadBots(force = false) {
    await store.loadBots(force)
  }

  return { bots, loading, loadBots }
}
