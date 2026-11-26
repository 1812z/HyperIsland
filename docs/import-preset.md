---
layout: page
title: 导入 HyperIsland 预设
---

<script setup>
import { onMounted, ref } from 'vue'
import { presetIdPattern } from './.vitepress/data/cloud-presets'

const appLink = ref('')
const english = ref(false)
onMounted(() => {
  const query = new URLSearchParams(window.location.search)
  english.value = query.get('lang') === 'en'
  const ids = query.getAll('id')
  if (ids.length === 1 && presetIdPattern.test(ids[0])) {
    appLink.value = 'hyperisland://import-preset?id=' + ids[0]
  }
})
</script>

<div class="import-preset-page">
  <h1>{{ english ? 'Import a HyperIsland preset' : '导入 HyperIsland 预设' }}</h1>
  <p v-if="appLink">{{ english ? 'Open the Android app to review and apply this preset.' : '在 Android 应用中查看并确认应用此预设。' }}</p>
  <p v-else>{{ english ? 'This preset link is invalid.' : '此预设链接无效。' }}</p>
  <div class="import-preset-links">
    <a v-if="appLink" :href="appLink">{{ english ? 'Open HyperIsland' : '打开 HyperIsland' }}</a>
    <a :href="english ? '/en/downloads.html' : '/downloads.html'">{{ english ? 'Install or update the app' : '安装或更新应用' }}</a>
    <a :href="english ? '/en/cloud-presets.html' : '/cloud-presets.html'">{{ english ? 'Browse cloud presets' : '返回云端预设' }}</a>
  </div>
</div>

<style scoped>
.import-preset-page { max-width: 640px; margin: 0 auto; padding: 72px 24px; }
h1 { font-size: 30px; font-weight: 650; line-height: 1.3; }
p { margin: 14px 0 22px; color: var(--vp-c-text-2); }
.import-preset-links { display: flex; flex-wrap: wrap; gap: 12px 20px; }
a { color: var(--vp-c-brand-1); text-decoration: underline; }
</style>
