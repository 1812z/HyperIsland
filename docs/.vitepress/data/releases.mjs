import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/**
 * 更新日志解析的**唯一实现**。
 *
 * 站点页面（`config.mts` 的 `transformPageData`）和构建期 JSON 产物
 * （`scripts/gen-site-data.mjs`）都从这里取数据。分成两份实现是绝对不行的：
 * 一旦 CHANGELOG 的标题写法变了，两份正则必然有一份先失效，
 * 表现是「网页正常、App 空白」这种最难查的错位。
 *
 * `import.meta.url` 保证路径与执行时的 cwd 无关，脚本从仓库根跑也一样。
 */
export const changelogPath = fileURLToPath(new URL('../../CHANGELOG.md', import.meta.url))
export const gradlePropertiesPath = fileURLToPath(
  new URL('../../../android/gradle.properties', import.meta.url)
)

// 版本标题有两种写法：# V3.2.1(2026-10-17) 与 # V3.1.9 (2026-10-11)，括号前空格可有可无
const versionPattern = /^# V([^\s(]+)/
// 日期单独提取，兼容括号内外任意空白与分隔符写法
const datePattern = /(\d{4})-(\d{1,2})-(\d{1,2})/

export function normalizeDate(match) {
  if (!match) return ''
  const [, year, month, day] = match
  return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`
}

/**
 * 解析 CHANGELOG.md，返回按文件顺序（新 → 旧）排列的版本数组。
 *
 * `sections` 保留 `##` 分段结构；没有 `##` 标题的裸列表归入「更新内容」。
 */
export function loadReleaseHistory() {
  const changelog = readFileSync(changelogPath, 'utf8')
  const headingPattern = /^# V[^\n]*$/gm
  const matches = [...changelog.matchAll(headingPattern)]

  return matches.map((match, index) => {
    const heading = match[0]
    const version = heading.match(versionPattern)?.[1]
    if (!version) throw new Error(`docs/CHANGELOG.md 无法解析版本标题：${heading}`)

    const bodyStart = match.index + heading.length
    const bodyEnd = matches[index + 1]?.index ?? changelog.length
    const body = changelog.slice(bodyStart, bodyEnd)
    const sections = []
    let current

    for (const rawLine of body.split(/\r?\n/)) {
      const line = rawLine.trim()
      if (line.startsWith('## ')) {
        current = { title: line.slice(3).trim(), items: [] }
        sections.push(current)
      } else if (line.startsWith('- ')) {
        if (!current) {
          current = { title: '更新内容', items: [] }
          sections.push(current)
        }
        current.items.push(line.slice(2).trim())
      }
    }

    const normalizedDate = normalizeDate(heading.match(datePattern))

    return {
      version,
      date: normalizedDate,
      downloadUrl: `https://github.com/1812z/HyperIsland/releases/download/v${version}/HyperIsland-v${version}.apk`,
      sections
    }
  })
}

/** 当前构建版本（`android/gradle.properties` 的 `appVersionName`）。 */
export function loadAppVersion() {
  const properties = readFileSync(gradlePropertiesPath, 'utf8')
  const version = properties.match(/^appVersionName=(.+)$/m)?.[1]?.trim()
  if (!version) throw new Error('android/gradle.properties 中缺少 appVersionName')
  return version
}
