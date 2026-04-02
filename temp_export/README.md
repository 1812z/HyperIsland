<div align="center">

<img src="https://github.com/user-attachments/assets/dc034ec0-90cf-4371-9ab0-132ca2527b32" width="120" height="120" style="border-radius: 24px;" alt="HyperIsland Icon"/>

# HyperIsland

**为澎湃 OS3 打造的超级岛进度通知增强模块**

[![GitHub Release](https://img.shields.io/github/v/release/1812z/HyperIsland?style=flat-square&logo=github&color=black)](https://github.com/1812z/HyperIsland/releases)
![Downloads](https://img.shields.io/github/downloads/1812z/HyperIsland/total?style=flat-square)
[![License](https://img.shields.io/github/license/1812z/HyperIsland?style=flat-square&color=orange)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://android.com)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![HyperOS](https://img.shields.io/badge/ROM-澎湃OS3-orange?style=flat-square)](https://hyperos.mi.com)
[![Build](https://img.shields.io/badge/Build-Flutter-02569B?style=flat-square&logo=flutter)](https://flutter.dev)


**[English](README_EN.md)** | **简体中文** | **[日本語](README_JA.md)** | **[Türkçe](README_TR.md)**

</div>

---

## ✨ 功能介绍

<table>
<tr>
<td width="50%">

### 📥 下载管理器拓展
拦截 HyperOS 下载管理器的通知，以超级岛样式展示文件名与下载进度，支持**暂停、继续、取消**操作。

</td>
<td width="50%">

### 🏝️ 超级岛 + 焦点通知适配
拦截任意 App 发出的标准安卓通知，处理后以超级岛 + 焦点通知样式展示，按钮来自原通知。

</td>
</tr>
<tr>
<td width="50%">

### 🚫 通知黑名单
黑名单应用不会弹出通知，仅显示超级岛（全屏状态下随状态栏自动隐藏）。

</td>
<td width="50%">

### 🔥 热加载支持
修改配置后**无需重启**即可生效，安装或更新软件后重启作用域即可。

</td>
</tr>
</table>

---

## 📋 使用说明

### 第一步：在 LSPosed 中激活模块

> ⚠️ 本模块依赖 **LSPosed** 框架，需要设备已获取 Root 权限并安装 LSPosed。

1. 打开 **LSPosed** 管理器，进入「模块」列表。
2. 找到 **HyperIsland** 并启用开关。
3. 在模块的作用域中，勾选推荐的应用：
   - **下载通知**：勾选「下载管理器」
   - **通用适配**：勾选「系统界面」
4. 确认保存后，点击**软件右上角重启按钮**，重启对应作用域内的应用（或直接重启手机）使 Hook 生效。

---

### 第二步：在 HyperCeiler 中开启焦点通知白名单

> 💡 超级岛样式的通知需要经过 HyperCeiler 的「焦点通知」权限才能正常显示。  
> 如果你的 HyperCeiler 版本过旧可能找不到相应入口，请自行更新版本。

1. 打开 **HyperCeiler**，进入「系统界面」或「小米服务框架」相关设置。
2. 找到「**移除焦点通知白名单**」。
3. 打开开关并重启作用域。


---

## 模板说明

| 模板       | 说明                                      |
|----------|-----------------------------------------|
| 通知超级岛    | 支持任意通知转为焦点通知+超级岛显示                      |
| 通知超级岛-精简 | 自动去除通知中的"x条新消息"和重复字段，节约超级岛空间            |
| 下载       | 自动识别下载状态并转为焦点通知+超级岛，岛左边显示状态，右边显示文件名和进度圈 |
| 下载-Lite  | 同上，但超级岛只显示图标+进度圈                        |
| AI 通知超级岛  | 超级岛左右交给AI精简，确保内容不会过长                        |

---
## ⚠️ 注意事项

| 事项 | 说明 |
|------|------|
| 框架依赖 | 本模块依赖 **LSPosed** 框架，需设备已 Root |
| 重启时机 | 安装或更新软件后需重启作用域；修改配置一般支持热加载 |
| 通知兼容性 | 通用适配仅处理**标准安卓通知**，自定义通知样式不受支持 |
| ROM 兼容性 | 本模块在**澎湃 OS3** 环境下测试，其他 ROM 可能存在兼容性问题 |

---

## 🔨 构建

确保已安装 Flutter 开发环境，然后运行：

```bash
flutter build apk --target-platform=android-arm64
```

---

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源，欢迎 Issue 与 PR。

<div align="center">

Made with ❤️ for HyperOS users

[![Star History](https://img.shields.io/github/stars/1812z/HyperIsland?style=social)](https://github.com/1812z/HyperIsland)

</div>
