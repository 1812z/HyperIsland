<div align="center">

<img src="https://github.com/user-attachments/assets/dc034ec0-90cf-4371-9ab0-132ca2527b32" width="120" height="120" style="border-radius: 24px;" alt="HyperIsland Icon"/>

# HyperIsland

**Dynamic Island–style progress notifications for HyperOS 3, powered by LSPosed**

[![GitHub Release](https://img.shields.io/github/v/release/1812z/HyperIsland?style=flat-square&logo=github&color=black)](https://github.com/1812z/HyperIsland/releases)
[![License](https://img.shields.io/github/license/1812z/HyperIsland?style=flat-square&color=orange)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://android.com)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![HyperOS](https://img.shields.io/badge/ROM-HyperOS3-orange?style=flat-square)](https://hyperos.mi.com)
[![Build](https://img.shields.io/badge/Build-Flutter-02569B?style=flat-square&logo=flutter)](https://flutter.dev)

**English** | **[简体中文](README.md)** | **[日本語](README_JA.md)**

</div>

---

## ✨ Features

<table>
<tr>
<td width="50%">

### 📥 Download Manager Extension
Intercepts HyperOS Download Manager notifications and displays them in Dynamic Island style, showing file name and progress with **pause, resume, and cancel** controls.

</td>
<td width="50%">

### 🏝️ Dynamic Island + Focus Notification
Intercepts standard Android notifications from any app and renders them in Dynamic Island + Focus Notification style, preserving the original action buttons.

</td>
</tr>
<tr>
<td width="50%">

### 🚫 Notification Blacklist
Apps on the blacklist will not trigger pop-up notifications — only the Dynamic Island indicator is shown (auto-hidden with the status bar in fullscreen).

</td>
<td width="50%">

### 🔥 Hot Reload Support
Configuration changes take effect **without restarting**. Only scope restarts are required after installing or updating apps.

</td>
</tr>
</table>

---

## 📋 Setup Guide

### Step 1 — Activate the Module in LSPosed

> ⚠️ This module requires the **LSPosed** framework. Your device must be rooted and LSPosed must be installed.

1. Open **LSPosed Manager** and navigate to the **Modules** tab.
2. Find **HyperIsland** and enable the toggle.
3. In the module scope, check the recommended apps:
    - **Download notifications**: check **Download Manager**
    - **Universal adapter**: check **System UI**
4. Save and tap the **restart button** in the top-right corner to restart the affected scope (or reboot your device) to activate the hook.

---

### Step 2 — Enable Focus Notification Whitelist in HyperCeiler

> 💡 Dynamic Island–style notifications require the "Focus Notification" permission granted through HyperCeiler.  
> If you can't find the relevant option, please update HyperCeiler to the latest version.

1. Open **HyperCeiler** and navigate to **System UI** or **Xiaomi Service Framework** settings.
2. Find **"Remove Focus Notification Whitelist"**.
3. Enable the toggle and restart the scope.

---

## ⚠️ Important Notes

| Item | Details |
|------|---------|
| Framework | Requires **LSPosed** and a **rooted** device |
| Restart Timing | Restart scope after installing/updating apps; config changes generally support hot reload |
| Notification Compatibility | Universal adapter only handles **standard Android notifications**; custom notification styles are not supported |
| ROM Compatibility | Tested on **HyperOS 3**; other ROMs may have compatibility issues |

---

## 🔨 Build

Make sure Flutter is installed, then run:

```bash
flutter build apk --target-platform=android-arm64
```

---

## 📄 License

This project is open source under the [MIT License](LICENSE). Issues and PRs are welcome.

<div align="center">

Made with ❤️ for HyperOS users

[![Star History](https://img.shields.io/github/stars/1812z/HyperIsland?style=social)](https://github.com/1812z/HyperIsland)

</div>
