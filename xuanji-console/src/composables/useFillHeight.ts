import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 计算可用于铺满视口的内容高度（像素）。
 * 默认已扣除：顶栏 60px + 内容内边距 44px(上下各 22) + 页面头约 56px。
 * 调用方可按页面实际情况用 offset 追加额外高度（如多行工具栏、卡片间距等）。
 */
export function useFillHeight(extra = 0) {
  const fillHeight = ref(600)
  const compute = () => {
    const reserved = 60 + 44 + 56 + extra
    fillHeight.value = Math.max(260, window.innerHeight - reserved)
  }
  onMounted(() => {
    compute()
    window.addEventListener('resize', compute)
  })
  onUnmounted(() => window.removeEventListener('resize', compute))
  return { fillHeight }
}
