# Quick Start

::: warning Prerequisites
- Device must have **Root** access
- **LSPosed** framework installed with **API version >= 101**
- System running **HyperOS 3**
- **HyperCeiler** installed
:::

::: tip Resources & Discussion
- QQ Group: **1045114341**
:::

## Step 1: Install the Module

1. Download the latest APK from [GitHub Releases](https://github.com/1812z/HyperIsland/releases)
2. Install the APK on your device

## Step 2: Activate the Module in LSPosed

1. Open **LSPosed** Manager, go to the "Modules" list
2. Find **HyperIsland** and enable it
3. In the module scope, check the recommended apps:
   - **Download Manager Extension**: check "Download Manager"
   - **Super Island**: check "System UI"
   - **Focus Notification Crack**: check "Xiaomi Services Framework"
4. Save and tap the **restart button** in the top-right corner (or restart your phone)

![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=flat-square)

## Step 3: Enable Focus Notification Bypass in HyperCeiler

> 💡 Super Island style notifications require HyperCeiler's "Focus Notification" bypass to display correctly. If your HyperCeiler version is too old, you may not find the corresponding options — please update to the latest version.

1. Open **HyperCeiler**, go to "System UI" or "Xiaomi Services Framework" settings
2. Find **"Remove Focus Notification Whitelist"** and **"Unlock Focus Notification Whitelist Verification"**
   ![Whitelist Bypass](../images/focus_whitelist_en.png){style="width: 50%;"}
3. Enable and restart the scope

::: danger Built-in Bypass
The app includes a built-in whitelist bypass, but it doesn't support safe mode and may cause System UI to crash infinitely. Make sure you can recover your device before enabling.
:::

## Step 4: Configure the Module

### App Adaptation
Open the **HyperIsland** app and enable the switches for the apps you need. It's not recommended to enable all.

::: tip Download Island Note
Download Island is disabled by default. Go to the app and enable **"Show System Apps"**, then check **"Download Manager"**.
:::

- **Notification Super Island**: Convert any notification to Focus Notification + Super Island display
- **Download**: Auto-detect download status and convert to Focus Notification + Super Island
- **AI Notification Super Island**: AI simplifies left and right sides of the Super Island

### AI Summary Configuration

- AI summary only supports **non-reasoning** models. Do not use reasoning models. For DeepSeek, use **deepseek-chat**
- URL must be fully filled in. For DeepSeek models, fill in `https://api.deepseek.com/v1/chat/completions` — **do not omit `/chat/completions`**
- If summary fails, it will automatically fall back to normal notifications. If not working, check your configuration
- If still failing, check if your **account balance** is sufficient

## FAQ

::: details No effect after installation?
Please confirm:
1. Module is enabled in LSPosed with correct scope settings
2. Scope has been restarted
3. Focus Notification whitelist bypass is enabled in HyperCeiler
4. System is HyperOS 3 with LSPosed API 101
5. Tap the test button on the app's main page — if no notification appears, LSPosed module is not working; if a standard Android notification appears, Focus Notification bypass is not working
:::

::: details Notifications not showing in Super Island style?
- Make sure the app sends **standard Android notifications** — custom notification styles are not supported
- Check if the app has Focus Notification permission in system notification settings
- Confirm HyperCeiler Focus Notification bypass is correctly configured
:::

::: details Configuration changes not taking effect?
- Scope needs to be restarted after app updates
- Try restarting System UI
:::

::: What if both floating notifications and focus notifications appear simultaneously?
- Please try disabling floating notifications in the system's native notification settings.
:::

::: danger Found a BUG?
Please upload LSPosed logs along with reproduction steps or a complete video. Though realistically, there's a good chance it won't get fixed ):
:::
