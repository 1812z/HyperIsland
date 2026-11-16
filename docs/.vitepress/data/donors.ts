import donorsData from './donors.json'

export interface DonorItem {
  name: string
  amount: string
  date: string
  message: string
}

/**
 * 捐赠名单的唯一数据源是 `donors.json`。
 *
 * 数据体单独放 JSON 而不是留在这里的 TS 字面量，是为了让构建期的
 * `scripts/gen-site-data.mjs` 能直接用 `JSON.parse` 读取 —— 那个脚本跑在 Node 里，
 * 解析不了 TS 源码。网页侧仍从本文件 import，两边拿的是同一份数据。
 */
export const donors: DonorItem[] = donorsData
