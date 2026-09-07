import { defineConfig } from 'vitepress'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

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

const changelogPath = fileURLToPath(new URL('../CHANGELOG.md', import.meta.url))
const gradlePropertiesPath = fileURLToPath(new URL('../../android/gradle.properties', import.meta.url))

function loadReleaseHistory(): ReleaseEntry[] {
  const changelog = readFileSync(changelogPath, 'utf8')
  const headingPattern = /^# V([^\s(]+)(?:\s+\(([^)]+)\))?.*$/gm
  const matches = [...changelog.matchAll(headingPattern)]

  return matches.map((match, index) => {
    const version = match[1]
    const bodyStart = match.index! + match[0].length
    const bodyEnd = matches[index + 1]?.index ?? changelog.length
    const body = changelog.slice(bodyStart, bodyEnd)
    const sections: ReleaseSection[] = []
    let current: ReleaseSection | undefined

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

    const normalizedDate = (match[2] ?? '')
      .split('-')
      .map((part, partIndex) => partIndex === 0 ? part : part.padStart(2, '0'))
      .join('-')

    return {
      version,
      date: normalizedDate,
      downloadUrl: `https://github.com/1812z/HyperIsland/releases/download/v${version}/HyperIsland-v${version}.apk`,
      sections
    }
  })
}

function loadLatestRelease(): ReleaseEntry {
  const properties = readFileSync(gradlePropertiesPath, 'utf8')
  const version = properties.match(/^appVersionName=(.+)$/m)?.[1]?.trim()
  if (!version) throw new Error('android/gradle.properties 中缺少 appVersionName')

  const release = loadReleaseHistory().find((item) => item.version === version)
  if (!release) throw new Error(`docs/CHANGELOG.md 中找不到 V${version} 的更新日志`)
  return release
}

export default defineConfig({
  title: 'HyperIsland',
  description: '为澎湃 OS3 打造的超级岛通知增强模块',

  transformPageData(pageData) {
    if (pageData.relativePath === 'CHANGELOG.md' || pageData.relativePath === 'en/CHANGELOG.md') {
      pageData.frontmatter.outline = false
      pageData.frontmatter.releaseHistory = loadReleaseHistory()
    }
    if (pageData.relativePath === 'downloads.md' || pageData.relativePath === 'en/downloads.md') {
      pageData.frontmatter.latestRelease = loadLatestRelease()
    }
  },

  head: [
    ['link', { rel: 'icon', type: 'image/png', href: 'https://github.com/user-attachments/assets/dc034ec0-90cf-4371-9ab0-132ca2527b32' }]
  ],

  // /en/ 路径重写到 en/ 目录下的英文文件
  rewrites: {
    'en/getting-started.md': 'en/getting-started.md',
    'en/faq.md': 'en/faq.md',
    'en/features.md': 'en/features.md',
    'en/build.md': 'en/build.md',
    'en/contribute.md': 'en/contribute.md',
    'en/donors.md': 'en/donors.md',
    'en/downloads.md': 'en/downloads.md',
    'en/CHANGELOG.md': 'en/CHANGELOG.md',
    'en/index.md': 'en/index.md'
  },

  locales: {
    root: {
      label: '简体中文',
      lang: 'zh-CN',
      themeConfig: {
        nav: nav('zh'),
        sidebar: sidebar('zh'),
        editLink: {
          pattern: 'https://github.com/1812z/HyperIsland/edit/main/docs/:path',
          text: '在 GitHub 上编辑此页面'
        }
      }
    },
    en: {
      label: 'English',
      lang: 'en-US',
      themeConfig: {
        nav: nav('en'),
        sidebar: sidebar('en'),
        editLink: {
          pattern: 'https://github.com/1812z/HyperIsland/edit/main/docs/:path',
          text: 'Edit this page on GitHub'
        }
      }
    }
  },

  themeConfig: {
    logo: 'https://github.com/user-attachments/assets/dc034ec0-90cf-4371-9ab0-132ca2527b32',
    socialLinks: [
      { icon: 'github', link: 'https://github.com/1812z/HyperIsland' }
    ],
    footer: {
      message: '基于 MIT 许可证发布',
      copyright: 'Copyright 2026-present 1812z'
    }
  }
})

function nav(lang: string) {
  if (lang === 'zh') {
    return [
      { text: '快速上手', link: '/getting-started', activeMatch: '/getting-started' },
      { text: '功能介绍', link: '/features', activeMatch: '/features' },
      { text: '更新日志', link: '/CHANGELOG' },
      {
        text: '更多',
        items: [
          { text: '构建指南', link: '/build' },
          { text: '贡献指南', link: '/contribute' },
          { text: '捐赠名单', link: '/donors' }
        ]
      }
    ]
  }
  return [
    { text: 'Quick Start', link: '/en/getting-started', activeMatch: '/en/getting-started' },
    { text: 'Features', link: '/en/features', activeMatch: '/en/features' },
    { text: 'Changelog', link: '/en/CHANGELOG' },
    {
      text: 'More',
      items: [
        { text: 'Build Guide', link: '/en/build' },
        { text: 'Contributing', link: '/en/contribute' },
        { text: 'Donors', link: '/en/donors' }
      ]
    }
  ]
}

function sidebar(lang: string) {
  if (lang === 'zh') {
    return [
      {
        text: '开始使用',
        items: [
          { text: '快速上手', link: '/getting-started' },
          { text: '资源下载', link: '/downloads' },
          { text: '常见问题', link: '/faq' },
          { text: '功能介绍', link: '/features' }
        ]
      },
      {
        text: '深入了解',
        items: [
          { text: '构建指南', link: '/build' },
          { text: '贡献指南', link: '/contribute' },
          { text: '捐赠名单', link: '/donors' }
        ]
      }
    ]
  }
  return [
    {
      text: 'Getting Started',
      items: [
        { text: 'Quick Start', link: '/en/getting-started' },
        { text: 'Resource Downloads', link: '/en/downloads' },
        { text: 'FAQ', link: '/en/faq' },
        { text: 'Features', link: '/en/features' }
      ]
    },
    {
      text: 'Deep Dive',
      items: [
        { text: 'Build Guide', link: '/en/build' },
        { text: 'Contributing', link: '/en/contribute' },
        { text: 'Donors', link: '/en/donors' }
      ]
    }
  ]
}
