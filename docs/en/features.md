# Features

HyperIsland provides rich Super Island notification enhancement features for HyperOS 3, making your notification experience more modern.

## App Features

### App Adaptation

Enable Super Island functionality for any app, with individual configuration per app.

- **Search**: Quickly search by app name or package name
- **Individual Switch**: Control enable/disable per app independently
- **Bulk Management**: View the number of enabled apps at a glance

![App Adaptation](../images/Screenshot_2026-04-05-00-00-06-698_io.github.hype.jpg){style="width: 50%;"}

### Toast Interception

Switch to **Toast** mode at the top of the adaptation page to intercept and handle standard text toasts per app:

- **Forward Standard Toast**: convert app toast text to HyperIsland Focus Notification + Super Island
- **Block Original Toast**: suppress the system's original toast popup after forwarding (enabled by default)
- **Show as Notification**: keep forwarded content visible in Notification Center
- **Show Super Island Icon**: control the left-side icon on the large island
- **Batch Settings**: select multiple apps and apply toast rules from the top-right action

::: tip Notes
Only standard text toasts are handled; custom toast views are ignored.
:::

### Notification Channel Management

For apps supporting multiple notification channels (like QQ), configure each channel separately:

- **Instant Messages**: Chat and messaging notifications
- **System Push**: MiPush push notifications

![Channel Settings](../images/Screenshot_2026-04-05-00-00-04-279_io.github.hype.jpg){style="width: 50%;"}

Each channel can independently set templates and styles.

## Super Island Customization

### Template Selection

Choose the appropriate Super Island template for each app/channel:

| Template | Description |
|:---------|:------------|
| Notification Super Island | Convert any notification to Focus Notification + Super Island |
| Download | Auto-detect download status and convert to Super Island |
| AI Notification Super Island | AI simplifies left and right sides |

### Style Selection

| Style | Description |
|:------|:------------|
| New Icon-Text Component + Bottom Text Buttons | Bottom text buttons, supports up to 2 buttons |
| Cover Component + Auto Wrap | Supports 2-line Focus Notification display with bottom text buttons |
| New Icon-Text Component + Right Text Button | Right text button, supports only 1 button |

## Island Customization

- **Island Icon**: Auto or custom icon selection
- **Large Island Icon**: Toggle large island icon display
- **Initial Expand**: Whether Super Island auto-expands to Focus Notification on first display
- **Update Expand**: Whether Super Island auto-expands to Focus Notification on notification update
- **Message Scroll**: Toggle text scrolling within the island
- **Auto Dismiss**: Set seconds before Super Island auto-hides
- **Highlight Color**: Custom highlight color (supports HEX values)
- **Dynamic Highlight Color Extraction**: Dynamically extract the highlight color from the Super Island icon.
- **Text Highlight**: Choose left or right text to display with highlight color
- **Narrow Font**: When enabled, switch the Dynamic Island to the narrow font (this will cause “.” to be converted to “:”)

### Filter Rules Page

In **Settings > Filter Rules**, you can control notification behavior for special states **per foreground app**.

In **Settings > Other > Filter Rules**, you can control notification behavior for special states **globally by system scenario**.

The `1 Do Not Disturb`, `2 Fullscreen`, and `3 Landscape` tabs at the top indicate the rule matching order. The system checks rules from left to right, and once it hits the first available rule, that rule is applied directly without continuing to match later rules.

Each rule can choose a handling mode on the right:

- **Default**: No extra handling when matched; continue using the default behavior
- **Fallback to Normal Notification**: Do not use Super Island when matched; restore the notification to a normal notification
- **Disable Expansion**: Only show the small island when matched; do not auto-expand to Focus Notification
- **Auto Expand Notification**: Auto-expand to Focus Notification when matched

Recommended usage:

- Avoid large-island interruptions during fullscreen games or videos: set **When Fullscreen** to **Disable Expansion** or **Fallback to Normal Notification**
- Keep only small-island prompts in landscape apps: set **When Landscape** to **Disable Expansion**
- Keep notifications as quiet as possible after enabling system Do Not Disturb: set **When Do Not Disturb** to **Fallback to Normal Notification** or **Disable Expansion**

::: tip Notes
The fullscreen rule has priority over the landscape rule. If an app is both fullscreen and landscape, the fullscreen rule is used first.
:::

## Focus Notification Customization

- **Focus Icon**: Choose icon in the Focus Notification panel
  - Auto: Use app's default icon
  - Custom: Manually select icon
- **Focus Notification**: Control Focus Notification display mode
  - Default (On): Normal Focus Notification display
  - Off: Restore notification to normal style, only show Super Island
- **Status Bar Icon**: Toggle status bar icon display
- **Lock Screen Restore**: Restore normal notification style on lock screen to use system's built-in privacy management
- **Outer Ring Glow Effect**: When enabled, a dynamic glow effect appears around focus notifications

### Expression Customization (Advanced)

In **Advanced Focus Customization** and **Advanced Island Customization**, you can use expressions to rebuild display text.

- Placeholder format: `${variable}`
- Function format: `${function(arg1, arg2, ...)}`
- Expression length limit is about 320 characters; keep it concise

**Common placeholders**

- `${title}`: current title
- `${subtitle}`: current subtitle/content
- `${subtitle_or_title}`: fallback to title when subtitle is empty
- `${raw_title}` / `${raw_subtitle}`: original notification title/content
- `${pkg}`: app package name
- `${channel_id}`: notification channel ID
- `${progress_text}%`: progress value (0-100, useful for download templates)

**Built-in functions**

- `trim(text)`: remove leading/trailing whitespace
- `regex(text, pattern, group)`: regex extraction; `group` defaults to 0
- `replace(text, pattern, replacement)`: regex replacement

**Examples**

- Remove group-chat prefix and keep message body only:
  - `${replace(subtitle_or_title, "^\[\d+条]\s*[^:：]+[:：]\s*", "")}`
- Extract order ID (for example `id12345`):
  - `${regex(subtitle, "(id\d+)", 1)}`
- Trim surrounding whitespace:
  - `${trim(subtitle_or_title)}`
- Combine app + channel:
  - `${pkg} · ${channel_id}`
- Show download progress:
  - `${progress_text}%`

**Troubleshooting**

- If an expression is invalid, output falls back to empty or original text; first check paired parentheses and quotes
- Start with plain placeholders (like `${title}`), then add functions step by step

::: warning
When Focus Notification is disabled, the Super Island is sent by **System UI** on behalf of the app, which may have compatibility issues.
:::

## Notification Filtering Rules
Used to control whether notifications appear on the island.
Supports blacklist mode and whitelist mode.

- **Blacklist Mode:** Notifications containing keywords will not be displayed as a super island.
- **Whitelist Mode:** Only notifications matching a whitelist keyword and no blacklist keyword will appear as a Super Island. If both match, the blacklist takes priority.

## Focus Notification Bypass

::: danger Built-in Bypass
The app includes a built-in whitelist bypass. It doesn't support safe mode and may cause System UI to crash infinitely. Make sure you can recover your device before enabling.
:::

Through HyperCeiler or built-in bypass, you can:
- Remove Focus Notification whitelist restrictions
- Unlock Focus Notification whitelist verification
- Enable any app's notifications to display as Focus Notifications

## Custom Background
- Support setting Small Island/Large Island/Focus notification backgrounds
- Support customizing transparency/blur

## Download Manager Extension

Intercept HyperOS download manager notifications and display them in Super Island style with filename and progress.

::: tip Core Features
- Support **Pause**, **Resume**, **Cancel** operations
- After pausing, a resume download notification is shown (requires Download Manager Hook enabled)
:::

::: tip How to Enable
Download Island is disabled by default. Go to the app, enable **"Show System Apps"**, and check **"Download Manager"**.
:::

## Screen Recording Island

Enable **Screen recording island** under **Settings → Hook extensions → Screen recording** to replace the official recorder floating window with Super Island and an independent Miuix dialog. Pause, resume, and stop controls are supported. You can enable **Motion photo** for the next recording; motion-photo recording stops automatically after 30 seconds, the result is written to the original recording directory, the MP4 is removed after successful packaging, and retained if packaging fails. Restart Screen Recorder after changing the main switch; **Record immediately** is hot-reloaded and skips the confirmation dialog when the Quick Settings tile is tapped.

Compatibility may vary by app version. The currently supported version is [4.16.2.6.1](https://1848933255.share.123pan.cn/123pan/9T69vd-ej0wd?pwd=kAf1#).
