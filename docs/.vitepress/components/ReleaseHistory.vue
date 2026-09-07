<script setup lang="ts">
import { computed } from 'vue'
import { useData } from 'vitepress'

interface ReleaseSection {
  title: string
  items: string[]
}

interface ReleaseEntry {
  version: string
  date: string
  downloadUrl: string
  sections: ReleaseSection[]
}

const { frontmatter } = useData()
const releases = computed(() => frontmatter.value.releaseHistory as ReleaseEntry[])

const props = withDefaults(defineProps<{ language?: 'zh' | 'en' }>(), {
  language: 'zh'
})

const labels = computed(() => props.language === 'en'
  ? { version: 'Version', updatedAt: 'Updated', early: 'Early release', download: 'Download APK' }
  : { version: '版本', updatedAt: '更新于', early: '早期版本', download: '下载 APK' })

const sectionLabels: Record<string, string> = {
  '功能': 'Features',
  '功能特性': 'Features',
  '功能&优化': 'Features & Improvements',
  '更新内容': 'Changes',
  '新增': 'Added',
  '✨ 新增': '✨ Added',
  '变更': 'Changes',
  '🔄 变更': '🔄 Changes',
  '优化': 'Improvements',
  '优化改进': 'Improvements',
  '改进': 'Improvements',
  '优化 & 修复': 'Improvements & Fixes',
  '优化&修复': 'Improvements & Fixes',
  '修复&优化': 'Fixes & Improvements',
  '修复': 'Fixes',
  '🛠 修复': '🛠 Fixes',
  'BUG 修复': 'Bug fixes',
  '已知问题': 'Known issues',
  '移除': 'Removed',
  '🗑 移除': '🗑 Removed'
}

function plainText(value: string): string {
  return value
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/`(.+?)`/g, '$1')
    .replace(/\[([^\]]+)]\([^)]+\)/g, '$1')
}

function sectionTitle(title: string): string {
  const plain = plainText(title)
  return props.language === 'en' ? (sectionLabels[plain] ?? plain) : plain
}

</script>

<template>
  <div class="release-history">
    <article v-for="release in releases" :key="release.version" class="release-card">
      <header class="release-header">
        <div>
          <span class="release-label">{{ labels.version }}</span>
          <h2>HyperIsland v{{ release.version }}</h2>
          <span class="release-date">{{ release.date ? `${labels.updatedAt} ${release.date}` : labels.early }}</span>
        </div>
        <a class="VPButton medium brand" :href="release.downloadUrl">{{ labels.download }}</a>
      </header>

      <div class="release-content">
        <section v-for="section in release.sections" :key="section.title">
          <h3>{{ sectionTitle(section.title) }}</h3>
          <ul v-if="section.items.length">
            <li v-for="item in section.items" :key="item">{{ plainText(item) }}</li>
          </ul>
        </section>
      </div>
    </article>
  </div>
</template>

<style scoped>
.release-history {
  display: grid;
  gap: 24px;
  margin-top: 24px;
}

.release-card {
  overflow: hidden;
  border: 1px solid var(--vp-c-divider);
  border-radius: 16px;
  background: var(--vp-c-bg-soft);
}

.release-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 22px;
  border-bottom: 1px solid var(--vp-c-divider);
}

.release-label,
.release-date {
  display: block;
  color: var(--vp-c-text-2);
  font-size: 13px;
}

.release-header h2 {
  margin: 2px 0 4px;
  border: 0;
  padding: 0;
  font-size: 21px;
}

.release-content {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 22px;
  padding: 20px 22px 22px;
}

.release-content section:has(h3:only-child) {
  grid-column: 1 / -1;
  border-left: 3px solid var(--vp-c-warning-1);
  padding-left: 12px;
}

.release-content h3 {
  margin: 0 0 8px;
  font-size: 16px;
}

.release-content section:has(h3:only-child) h3 {
  margin: 0;
  color: var(--vp-c-text-2);
  font-weight: 500;
}

.release-content ul {
  margin: 0;
  padding-left: 20px;
}

.release-content li + li {
  margin-top: 5px;
}

@media (max-width: 640px) {
  .release-header {
    align-items: stretch;
    flex-direction: column;
  }

  .release-header .VPButton {
    text-align: center;
  }
}
</style>
