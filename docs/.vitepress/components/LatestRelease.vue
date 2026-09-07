<script setup lang="ts">
import { computed } from 'vue'
import { useData } from 'vitepress'

interface ReleaseSection {
  title: string
  items: string[]
}

interface LatestRelease {
  version: string
  date: string
  downloadUrl: string
  sections: ReleaseSection[]
}

const { frontmatter } = useData()
const release = computed(() => frontmatter.value.latestRelease as LatestRelease)

const props = withDefaults(defineProps<{ language?: 'zh' | 'en' }>(), {
  language: 'zh'
})

const labels = computed(() => props.language === 'en'
  ? {
      currentVersion: 'Current version',
      updatedAt: 'Updated',
      downloadSource: 'Download source',
      download: 'Download APK',
      releaseNotes: 'Release notes'
    }
  : {
      currentVersion: '当前版本',
      updatedAt: '更新时间',
      downloadSource: '下载渠道',
      download: '下载 APK',
      releaseNotes: '更新日志'
    })

const sectionLabels: Record<string, string> = {
  '功能': 'Features',
  '优化 & 修复': 'Improvements & Fixes',
  '优化&修复': 'Improvements & Fixes',
  '已知问题': 'Known issues',
  '修复': 'Fixes',
  '优化': 'Improvements'
}

function sectionTitle(title: string): string {
  return props.language === 'en' ? (sectionLabels[title] ?? title) : title
}
</script>

<template>
  <div class="latest-release">
    <div class="release-summary">
      <div class="release-meta">
        <div>
          <span>{{ labels.currentVersion }}</span>
          <strong>v{{ release.version }}</strong>
        </div>
        <div>
          <span>{{ labels.updatedAt }}</span>
          <strong>{{ release.date }}</strong>
        </div>
        <div>
          <span>{{ labels.downloadSource }}</span>
          <strong>GitHub Release</strong>
        </div>
      </div>
      <a class="VPButton medium brand" :href="release.downloadUrl">{{ labels.download }}</a>
    </div>
    <div class="release-notes-wrap">
      <h3>{{ labels.releaseNotes }}</h3>
      <div class="release-notes">
        <section v-for="section in release.sections" :key="section.title">
          <h4>{{ sectionTitle(section.title) }}</h4>
          <ul v-if="section.items.length">
            <li v-for="item in section.items" :key="item">{{ item }}</li>
          </ul>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.latest-release {
  margin: 20px 0 28px;
  overflow: hidden;
  border: 1px solid var(--vp-c-divider);
  border-radius: 14px;
  background: var(--vp-c-bg-soft);
}

.release-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 20px;
  border-bottom: 1px solid var(--vp-c-divider);
}

.release-meta {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(3, minmax(110px, 1fr));
  gap: 20px;
}

.release-meta span,
.release-meta strong {
  display: block;
}

.release-meta span {
  color: var(--vp-c-text-2);
  font-size: 13px;
}

.release-meta strong {
  margin-top: 4px;
  font-size: 16px;
}

.release-notes-wrap {
  padding: 20px;
}

.release-notes-wrap > h3 {
  margin: 0 0 14px;
  font-size: 17px;
}

.release-notes {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 20px;
}

.release-notes h4 {
  margin: 0 0 8px;
  font-size: 15px;
}

.release-notes ul {
  margin: 0;
  padding-left: 20px;
}

@media (max-width: 640px) {
  .release-summary {
    align-items: stretch;
    flex-direction: column;
  }

  .release-meta {
    grid-template-columns: 1fr 1fr;
  }

  .release-meta > div:last-child {
    grid-column: 1 / -1;
  }

  .release-summary .VPButton {
    text-align: center;
  }
}
</style>
