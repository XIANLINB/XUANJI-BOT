<script setup lang="ts">
import { NCard, NTabs, NTabPane, NTag } from 'naive-ui'
import PageHero from '../components/PageHero.vue'
import { renderMarkdown } from '../utils/markdown'
import guideRaw from '../docs/plugin-guide/guide.md?raw'
import eventsRaw from '../docs/plugin-guide/events.md?raw'
import actionsRaw from '../docs/plugin-guide/actions.md?raw'
import annotationsRaw from '../docs/plugin-guide/annotations.md?raw'

const GUIDE_DOC = guideRaw
const EVENTS_DOC = eventsRaw
const ACTIONS_DOC = actionsRaw
const ANNOTATIONS_DOC = annotationsRaw

/** 文档最后更新（与 SDK 能力对齐的日期） */
const LAST_UPDATED = '2026-08-15'
</script>

<template>
  <div>
    <PageHero title="插件开发指南" subtitle="开发指南 / 事件 / 动作 / 注解 — 璇玑插件开发全能力说明">
      <NTag :bordered="false" type="info" size="small">最后更新 {{ LAST_UPDATED }}</NTag>
    </PageHero>
    <NCard size="small">
      <NTabs type="line" animated>
        <NTabPane name="guide" tab="开发指南">
          <div class="guide" v-html="renderMarkdown(GUIDE_DOC, { breaks: false })" />
        </NTabPane>
        <NTabPane name="events" tab="事件">
          <div class="guide" v-html="renderMarkdown(EVENTS_DOC, { breaks: false })" />
        </NTabPane>
        <NTabPane name="actions" tab="动作">
          <div class="guide" v-html="renderMarkdown(ACTIONS_DOC, { breaks: false })" />
        </NTabPane>
        <NTabPane name="annotations" tab="注解">
          <div class="guide" v-html="renderMarkdown(ANNOTATIONS_DOC, { breaks: false })" />
        </NTabPane>
      </NTabs>
    </NCard>
  </div>
</template>

<style scoped>
.guide { font-size: 14px; line-height: 1.8; }
.guide :deep(h1) { font-size: 22px; border-bottom: 1px solid #eee; padding-bottom: 8px; margin: 24px 0 12px; }
.guide :deep(h2) { font-size: 18px; margin: 20px 0 8px; }
.guide :deep(h3) { font-size: 15px; margin: 16px 0 6px; }
.guide :deep(p) { margin: 6px 0; }
.guide :deep(code) { background: #f2f3f5; padding: 1px 6px; border-radius: 4px; font-size: 13px; }
.guide :deep(pre) { background: #1d2129; color: #e6e8eb; padding: 14px; border-radius: 8px; overflow: auto; margin: 10px 0; }
.guide :deep(pre code) { background: transparent; color: inherit; padding: 0; }
.guide :deep(a) { color: #2080f0; }
.guide :deep(ul), .guide :deep(ol) { padding-left: 22px; margin: 6px 0; }
.guide :deep(table) { border-collapse: collapse; margin: 10px 0; }
.guide :deep(th), .guide :deep(td) { border: 1px solid #e5e6eb; padding: 6px 12px; }
.guide :deep(blockquote) { border-left: 3px solid #2080f0; padding-left: 12px; color: #666; margin: 8px 0; background: #f5f9ff; padding: 8px 12px; border-radius: 4px; }
</style>
