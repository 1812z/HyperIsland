import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadAppVersion, loadReleaseHistory } from '../docs/.vitepress/data/releases.mjs'

/**
 * 把仓库里的人工数据源编译成站点上的静态 JSON，供 App 直接消费。
 *
 * 两个产物（`changelog.json` / `donors.json`）都写到 `docs/public/data/`
 * （VitePress 会把 public 原样拷进 dist），再同步一份到
 * `android/app/src/main/assets/site-data/` 作为 App 的离线基线：
 * 首装无网时更新日志和捐赠名单不是白屏，而是随安装包发布那一刻的内容。
 *
 * 不再单独产出 `latest.json`：最新版本就是 `changelog.releases[0]`，
 * 单独一份「最新版摘要」只会多出一个会和主数据走偏的真相。
 *
 * 这个脚本必须挂在站点构建命令上（`yarn docs:build` 前置），不能靠本地手动
 * 生成后提交 —— 若 CI 只跑 `vitepress build`，App 拉到的就是上次提交时的旧数据，
 * 而且过期了也不会有任何报错。
 */

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const PUBLIC_DIR = join(ROOT, 'docs', 'public', 'data')
const ASSETS_DIR = join(ROOT, 'android', 'app', 'src', 'main', 'assets', 'site-data')
const DONORS_SOURCE = join(ROOT, 'docs', '.vitepress', 'data', 'donors.json')

/** 累计金额按「分」累加，避免 0.1 + 0.2 这类浮点误差。 */
function toCents(amount) {
  const match = /^-?\d+(?:\.\d{1,2})?$/.exec(String(amount).trim())
  if (!match) return 0
  const [yuan, fraction = ''] = match[0].split('.')
  return Number(yuan) * 100 + Number(fraction.padEnd(2, '0'))
}

function writeJson(target, payload) {
  mkdirSync(dirname(target), { recursive: true })
  writeFileSync(target, `${JSON.stringify(payload, null, 2)}\n`, 'utf8')
}

function main() {
  const generatedAt = new Date().toISOString()
  const appVersion = loadAppVersion()
  const releases = loadReleaseHistory()
  const donors = JSON.parse(readFileSync(DONORS_SOURCE, 'utf8'))

  if (!releases.length) throw new Error('docs/CHANGELOG.md 里没有解析到任何版本')
  if (!Array.isArray(donors) || !donors.length) throw new Error('donors.json 里没有捐赠记录')

  // 只做一致性校验，不再单独产出 latest.json —— 最新版就是 releases[0]，
  // 客户端读 changelog.json 的第一条即可，多一个文件就多一份会走偏的真相。
  const current = releases.find((item) => item.version === appVersion)
  if (!current) {
    throw new Error(
      `android/gradle.properties 的 appVersionName=${appVersion} 在 docs/CHANGELOG.md 里找不到对应版本`
    )
  }

  const totalCents = donors.reduce((sum, donor) => sum + toCents(donor.amount), 0)

  const files = {
    'changelog.json': {
      schema: 1,
      generatedAt,
      latest: releases[0].version,
      releases
    },
    'donors.json': {
      schema: 1,
      generatedAt,
      count: donors.length,
      total: (totalCents / 100).toFixed(2),
      donors
    }
  }

  for (const [name, payload] of Object.entries(files)) {
    const body = { ...payload }
    writeJson(join(PUBLIC_DIR, name), body)
    writeJson(join(ASSETS_DIR, name), body)
  }

  console.log(
    `站点数据已生成：${releases.length} 个版本 / ${donors.length} 位捐赠者 / 累计 ¥${(totalCents / 100).toFixed(2)}（最新 v${releases[0].version}，当前安装 v${current.version}）`
  )
  console.log(`  → ${PUBLIC_DIR}`)
  console.log(`  → ${ASSETS_DIR}`)
}

main()
