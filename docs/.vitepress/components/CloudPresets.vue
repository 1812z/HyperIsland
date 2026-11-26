<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { presetIdPattern } from '../data/cloud-presets'

interface Preset {
  id: string
  name: string
  description: string
  author: string
  version: string
  publishedAt: string | null
  updatedAt: string | null
  downloads: number
}

const { english = false } = defineProps<{ english?: boolean }>()
const label = (zh: string, en: string) => english ? en : zh
const hub = 'https://hyperisland-hub.1812z.top'
const presets = ref<Preset[]>([])
const search = ref('')
const sortBy = ref<'latest' | 'popular'>('popular')
const pageSize = 50
const page = ref(1)
let sharedId: string | undefined
const loading = ref(true)
const listError = ref(false)
const sharedPresetUnavailable = ref(false)
const selected = ref<Preset>()
const dialog = ref<HTMLDialogElement>()
const shareStatus = ref('')
const manualShareUrl = ref('')
const androidChrome = ref(false)
const controller = new AbortController()
const dateFormat = new Intl.DateTimeFormat(english ? 'en' : 'zh-CN', { dateStyle: 'medium' })
const numberFormat = new Intl.NumberFormat(english ? 'en' : 'zh-CN')

const searchText = (value: string) => value.normalize('NFKC').toLowerCase()
const publishedTime = (preset: Preset) => Date.parse(preset.publishedAt || preset.updatedAt || '') || 0

const matchingPresets = computed(() => {
  const keyword = searchText(search.value.trim())
  return presets.value
    .filter(preset => [preset.name, preset.author, preset.description].some(value => searchText(value).includes(keyword)))
    .sort((a, b) => {
      const latestFirst = publishedTime(b) - publishedTime(a)
      return (sortBy.value === 'popular' ? b.downloads - a.downloads : 0) || latestFirst || a.id.localeCompare(b.id)
    })
})
const pages = computed(() => Math.max(1, Math.ceil(matchingPresets.value.length / pageSize)))
const visiblePresets = computed(() => matchingPresets.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch([search, sortBy], () => { page.value = 1 })

const importLink = computed(() => {
  if (!selected.value) return ''
  const query = 'id=' + selected.value.id + (english ? '&lang=en' : '')
  const fallback = '/import-preset.html?' + query
  if (!androidChrome.value) return fallback
  return 'intent://import-preset?' + query +
    '#Intent;scheme=hyperisland;package=io.github.hyperisland;S.browser_fallback_url=' +
    encodeURIComponent('https://hyperisland.1812z.top' + fallback) + ';end'
})

function dateOf(preset: Preset) {
  const date = Date.parse(preset.updatedAt || preset.publishedAt || '')
  return Number.isFinite(date) ? dateFormat.format(date) : ''
}

async function fetchPage(target: number) {
  const response = await fetch(hub + '/api/configs?page=' + target, { signal: controller.signal })
  if (!response.ok) throw new Error('Unable to load presets')
  const data = await response.json()
  if (!Array.isArray(data.items)) throw new Error('Invalid preset list')
  return {
    items: data.items.filter((preset: Preset) => preset && presetIdPattern.test(preset.id)) as Preset[],
    pages: Math.max(1, Number(data.pages) || 1),
  }
}

async function loadPresets() {
  loading.value = true
  listError.value = false
  try {
    const first = await fetchPage(1)
    const catalogue = new Map(first.items.map(preset => [preset.id, preset]))
    for (let index = 2; index <= first.pages; index++) {
      const result = await fetchPage(index)
      for (const preset of result.items) catalogue.set(preset.id, preset)
    }
    presets.value = [...catalogue.values()]
    if (sharedId) {
      const match = catalogue.get(sharedId)
      sharedPresetUnavailable.value = !match
      if (match) await openPreset(match)
    }
  } catch {
    if (!controller.signal.aborted) listError.value = true
  } finally {
    loading.value = false
  }
}

async function openPreset(preset: Preset) {
  selected.value = preset
  shareStatus.value = ''
  manualShareUrl.value = ''
  await nextTick()
  if (dialog.value && !dialog.value.open) dialog.value.showModal()
}

async function sharePreset() {
  if (!selected.value) return
  const url = new URL(window.location.href)
  url.search = ''
  url.searchParams.set('preset', selected.value.id)
  url.hash = ''
  try {
    await navigator.clipboard.writeText(url.href)
    shareStatus.value = label('链接已复制', 'Link copied')
  } catch {
    manualShareUrl.value = url.href
  }
}

onMounted(() => {
  androidChrome.value = /Android/i.test(navigator.userAgent) && /Chrome\//.test(navigator.userAgent)
  const id = new URLSearchParams(window.location.search).get('preset')
  if (id && presetIdPattern.test(id)) sharedId = id.toLowerCase()
  sharedPresetUnavailable.value = !!id && !sharedId
  void loadPresets()
})
onUnmounted(() => controller.abort())
</script>

<template>
  <section class="cloud-presets" :aria-label="label('云端预设列表', 'Cloud preset list')">
    <p v-if="sharedPresetUnavailable" class="feedback" role="status">
      {{ label('这条分享的预设暂时无法查看。', 'This shared preset is unavailable.') }}
    </p>

    <div class="preset-toolbar">
      <input v-model="search" class="preset-search" type="search"
        :aria-label="label('搜索预设', 'Search presets')"
        :placeholder="label('搜索名称、作者或简介', 'Search name, author, or description')">
      <div class="sort-controls" role="group" :aria-label="label('排序方式', 'Sort presets')">
        <button class="action" type="button" :aria-pressed="sortBy === 'latest'" @click="sortBy = 'latest'">{{ label('最新', 'Latest') }}</button>
        <button class="action" type="button" :aria-pressed="sortBy === 'popular'" @click="sortBy = 'popular'">{{ label('热门', 'Popular') }}</button>
      </div>
    </div>

    <p v-if="loading" class="feedback" role="status">
      {{ label('正在加载预设…', 'Loading presets…') }}
    </p>
    <p v-else-if="!loading && !listError && !presets.length" class="feedback">
      {{ label('暂无已发布的预设。', 'No presets have been published.') }}
    </p>
    <p v-else-if="!listError && !matchingPresets.length" class="feedback" role="status">
      {{ label('没有匹配的预设。', 'No matching presets.') }}
    </p>

    <div v-if="visiblePresets.length" class="preset-grid" :aria-busy="loading">
      <button v-for="preset in visiblePresets" :key="preset.id" type="button" class="preset-card" @click="openPreset(preset)">
        <span class="preset-name">{{ preset.name }}</span>
        <span v-if="preset.description" class="preset-description">{{ preset.description }}</span>
        <span class="preset-meta">
          <span>{{ preset.author }}</span>
          <span class="preset-count">{{ numberFormat.format(preset.downloads) }} {{ label('次下载', 'downloads') }}</span>
        </span>
      </button>
    </div>

    <p v-if="listError" class="feedback" role="status">
      {{ label('加载失败，请稍后重试。', 'Could not load presets.') }}
      <button class="action" type="button" @click="loadPresets">{{ label('重试', 'Retry') }}</button>
    </p>

    <nav v-if="pages > 1" class="pagination" :aria-label="label('预设分页', 'Preset pages')">
      <button class="action" type="button" :disabled="page <= 1" @click="page--">{{ label('上一页', 'Previous') }}</button>
      <span>{{ page }} / {{ pages }}</span>
      <button class="action" type="button" :disabled="page >= pages" @click="page++">{{ label('下一页', 'Next') }}</button>
    </nav>

    <dialog ref="dialog" class="preset-dialog" aria-labelledby="preset-dialog-title" @click.self="dialog?.close()">
      <div v-if="selected" class="dialog-content">
        <header class="dialog-header">
          <h2 id="preset-dialog-title">{{ selected.name }}</h2>
          <button class="action" type="button" autofocus @click="dialog?.close()">{{ label('关闭', 'Close') }}</button>
        </header>
        <p v-if="selected.description" class="dialog-description">{{ selected.description }}</p>
        <dl class="dialog-meta">
          <dt>{{ label('作者', 'Author') }}</dt><dd>{{ selected.author }}</dd>
          <dt>{{ label('版本', 'Version') }}</dt><dd>{{ selected.version }}</dd>
          <template v-if="dateOf(selected)">
            <dt>{{ label('更新时间', 'Updated') }}</dt><dd>{{ dateOf(selected) }}</dd>
          </template>
          <dt>{{ label('下载量', 'Downloads') }}</dt><dd>{{ numberFormat.format(selected.downloads) }}</dd>
        </dl>
        <footer class="dialog-actions">
          <button class="action" type="button" @click="sharePreset">{{ label('复制分享链接', 'Copy share link') }}</button>
          <a :href="importLink" class="action action-primary">{{ label('导入模块', 'Import into HyperIsland') }}</a>
        </footer>
        <div v-if="shareStatus" class="feedback" role="status">{{ shareStatus }}</div>
        <input v-if="manualShareUrl" class="share-url" :aria-label="label('分享链接', 'Share link')" :value="manualShareUrl" readonly @focus="($event.target as HTMLInputElement).select()">
      </div>
    </dialog>
  </section>
</template>

<style scoped>
.cloud-presets { margin-top: 24px; }
.preset-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px 12px; margin-bottom: 16px; }
.preset-search { flex: 1 1 230px; min-width: 0; padding: 8px 12px; border: 1px solid var(--vp-c-divider); border-radius: 8px; background: var(--vp-c-bg-soft); font-size: 14px; }
.preset-search:focus-visible { outline: 2px solid var(--vp-c-brand-1); outline-offset: 1px; }
.sort-controls { display: flex; gap: 6px; }
.sort-controls .action[aria-pressed="true"] { border-color: var(--vp-c-brand-1); background: var(--vp-c-brand-soft); color: var(--vp-c-brand-1); }
.preset-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 210px), 1fr)); gap: 14px; }
.preset-card { display: flex; flex-direction: column; gap: 10px; padding: 18px; min-height: 122px; text-align: start; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-divider); border-radius: 12px; cursor: pointer; }
.preset-card:hover, .preset-card:focus-visible { border-color: var(--vp-c-brand-1); }
.preset-name { font-size: 16px; font-weight: 650; line-height: 1.45; overflow-wrap: anywhere; }
.preset-description { display: -webkit-box; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; font-size: 14px; line-height: 1.55; color: var(--vp-c-text-2); overflow-wrap: anywhere; }
.preset-meta { margin-top: auto; display: flex; flex-wrap: wrap; justify-content: space-between; gap: 6px 12px; color: var(--vp-c-text-2); font-size: 13px; overflow-wrap: anywhere; }
.preset-count { white-space: nowrap; }
.feedback, .pagination { color: var(--vp-c-text-2); font-size: 14px; }
.pagination { display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 20px; }
.preset-dialog { position: fixed; inset: 0; margin: auto; padding: 0; width: min(520px, calc(100% - 32px)); max-height: calc(100% - 32px); overflow: auto; color: var(--vp-c-text-1); background: var(--vp-c-bg); border: 1px solid var(--vp-c-divider); border-radius: 16px; }
.preset-dialog::backdrop { background: rgb(0 0 0 / 55%); }
.dialog-content { padding: clamp(16px, 5vw, 24px); }
.dialog-header { display: flex; align-items: start; gap: 16px; justify-content: space-between; }
.dialog-header h2 { margin: 0; padding: 0; border: 0; font-size: 22px; line-height: 1.35; overflow-wrap: anywhere; }
.dialog-description { white-space: pre-wrap; overflow-wrap: anywhere; }
.dialog-meta { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 8px 16px; margin: 16px 0; font-size: 14px; }
.dialog-meta dt { color: var(--vp-c-text-2); }
.dialog-meta dd { margin: 0; overflow-wrap: anywhere; }
.dialog-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; margin-top: 22px; }
.action { white-space: nowrap; padding: 6px 12px; border: 1px solid var(--vp-c-divider); border-radius: 8px; font-size: 14px; cursor: pointer; }
.action:enabled:hover { border-color: var(--vp-c-brand-1); }
.action:disabled { opacity: .5; cursor: default; }
.action.action-primary { background: var(--vp-button-brand-bg); color: var(--vp-button-brand-text); border-color: transparent; text-decoration: none; }
.action-primary:hover { background: var(--vp-button-brand-hover-bg); }
.share-url { width: 100%; margin-top: 12px; border: 1px solid var(--vp-c-divider); border-radius: 6px; padding: 6px 8px; }
</style>
