# Features

HyperIsland brings regular notifications, toasts, downloads, and screen-recording status into Super Island on HyperOS 3/4. It also lets you customize appearance, expansion behavior, and filtering rules.

## Feature Directory

| Category | Main capabilities | Best used for |
|:---|:---|:---|
| [Apps & Channels](#apps-channels) | Per-app enablement, toast forwarding, channel settings, batch actions | Decide which notifications may enter Super Island |
| [Super Island Display](#super-island-display) | Templates, layouts, icons, expansion, scrolling, timeout | Control island content and interaction |
| [Focus Notifications](#focus-notifications) | Focus panel, status bar, lock screen, glow | Control expanded system notification behavior |
| [Appearance & Materials](#appearance-materials) | Highlight colors, backgrounds, blur, glass materials | Create a consistent visual style |
| [Filters & Context Rules](#filters-context-rules) | Keywords, DND, fullscreen, and landscape rules | Reduce unwanted alerts and interruptions |
| [Advanced Text Expressions](#advanced-text-expressions) | Placeholders, regex extraction, replacement | Clean up or rebuild notification text |
| [Hook Extensions](#hook-extensions) | Download Manager and Screen Recording Island | Enhance supported system components |
| [Focus Notification Bypass](#focus-notification-bypass) | Remove system allowlist restrictions | Enable Focus Notifications for more apps |

::: tip Recommended setup order
Select apps first, configure their templates and display styles, then add filters, appearance changes, or advanced expressions only where needed.
:::

## Apps & Channels

### App Adaptation

Enable Super Island for individual apps, search by app name or package name, and apply settings to multiple apps at once. Enabling **Show system apps** also exposes components such as Download Manager.

### Toast Forwarding

Switch to **Toast** mode at the top of App Adaptation to handle standard text toasts per app:

- Convert toast text into a Focus Notification and Super Island
- Suppress the original toast after forwarding to avoid duplicate prompts
- Choose whether to keep it in Notification Center or show the large-island icon
- Apply the same rules to several apps in one batch

::: info Scope
Only standard text toasts are supported. Custom toast views are not intercepted.
:::

### Notification Channels

Apps such as QQ may expose multiple notification channels. Instant messages, system push notifications, and other channels can each use independent enablement, templates, styles, and filtering rules.

## Super Island Display

### Templates & Layouts

| Option | Purpose |
|:---|:---|
| Notification Super Island | Converts a regular notification into a Focus Notification and Super Island |
| Download | Detects the filename and progress, and exposes download actions |
| AI Notification Super Island | Uses AI to simplify left- and right-side content |
| New icon-text + bottom text buttons | Shows up to two bottom buttons |
| Cover + automatic wrapping | Supports a two-line Focus Notification and bottom buttons |
| New icon-text + right text button | Shows one text button on the right |

### Display & Expansion

- **Super Island icon**: use the app icon automatically or select a custom icon
- **Large-island icon**: control the icon on the expanded island
- **Initial / update expansion**: choose whether a new or updated notification expands automatically
- **Message scrolling**: scroll long text inside the island
- **Auto dismiss**: set when the island hides automatically
- **Narrow font**: use the narrow island font; periods may be rendered as colons

## Focus Notifications

- **Focus icon**: use the app icon or a custom icon
- **Focus Notification**: disabling it keeps Super Island but restores the source notification to normal style
- **Status-bar icon**: control the notification icon in the status bar
- **Lock-screen restore**: use a regular notification on the lock screen for system privacy behavior
- **Outer glow**: show a dynamic glow around the Focus Notification

::: warning Note
When Focus Notification is disabled, System UI sends Super Island on the app's behalf. The default **Intercept heads-up notification** option suppresses the original heads-up prompt after a notification reaches the island, while its Notification Center entry remains available.
:::

## Appearance & Materials

### Colors & Backgrounds

- Set a custom HEX highlight color
- Extract the highlight color dynamically from the island icon
- Apply the highlight color to the left or right text
- Set separate backgrounds for the small island, large island, and Focus Notification
- Adjust background opacity and blur

### Island Materials

The small island, large island, and expanded state can independently use Default, Gaussian Blur, Highlight Glass, Liquid Glass, or Soft Glass. The smaller states may follow the large island or use their own lighting, refraction, background, blending, and highlight parameters.

- On HyperOS 4, Soft Glass uses the native Bionics material interface
- On HyperOS 3, Soft Glass falls back to Highlight Glass
- Custom backgrounds and glass materials are mutually exclusive
- HDR highlights, Liquid Glass sampling, and gyroscope lighting are global settings

## Filters & Context Rules

### Keyword Filtering

| Mode | Behavior |
|:---|:---|
| Blacklist | A notification does not enter Super Island when it matches any blacklist keyword |
| Whitelist | A notification enters only when it matches the whitelist and does not match the blacklist |

The blacklist wins when both lists match.

### DND, Fullscreen & Landscape

Use **Settings → Filter rules** for per-foreground-app behavior, and **Settings → Other → Filter rules** for global defaults. Rules are checked in the order **Do Not Disturb → Fullscreen → Landscape**, stopping at the first applicable rule.

| Action | Result after a match |
|:---|:---|
| Default | Apply no additional handling |
| Fall back to regular notification | Skip Super Island and restore a normal notification |
| Disable expansion | Keep the small island without automatically expanding |
| Auto-expand notification | Automatically expand the Focus Notification |

::: tip Common setups
For fullscreen games or videos, use **Disable expansion** or **Fall back to regular notification**. To keep only the small island in landscape, use **Disable expansion**. Fullscreen takes priority when both fullscreen and landscape match.
:::

## Advanced Text Expressions

**Advanced Focus customization** and **Advanced Island customization** can rebuild displayed text with expressions up to approximately 320 characters.

### Common Variables

| Variable | Content |
|:---|:---|
| `${title}` | Current title |
| `${subtitle}` | Current subtitle or body |
| `${subtitle_or_title}` | Title when the subtitle is empty |
| `${raw_title}` / `${raw_subtitle}` | Original notification title and body |
| `${pkg}` | App package name |
| `${channel_id}` | Notification channel ID |
| `${progress_text}%` | Progress from 0–100 |

### Built-in Functions

| Function | Purpose |
|:---|:---|
| `trim(text)` | Remove leading and trailing whitespace |
| `regex(text, pattern, group)` | Extract a regex match; `group` defaults to 0 |
| `replace(text, pattern, replacement)` | Replace text with a regular expression |

Common examples:

```text
${trim(subtitle_or_title)}
${regex(subtitle, "(id\d+)", 1)}
${pkg} · ${channel_id}
${progress_text}%
```

If an expression fails, test a single variable such as `${title}` first, then add functions gradually and check that parentheses and quotes are paired.

## Hook Extensions

### Download Manager

Intercept HyperOS Download Manager notifications to show the filename and progress in Super Island, with **Pause, Resume, and Cancel** actions. A resume notification can also appear after pausing.

Download Island is disabled by default. Enable **Show system apps**, select **Download Manager** in App Adaptation, then enable its Hook extension.

### Screen Recording Island

Enable it under **Settings → Hook extensions → Screen recording** to replace the official recording overlay with Super Island and a standalone Miuix dialog. Pause, resume, and stop actions are supported.

- **Motion photo**: applies to the next recording only and runs for up to 30 seconds; the source MP4 is deleted after successful packaging and retained on failure
- **Record immediately**: skips the confirmation dialog when the Quick Settings tile is tapped and supports hot reload
- Restart the Screen Recorder app after changing the main island switch

System component versions must match the OS. If compatibility issues occur, download the System UI and Screen Recorder resources from [Resource Downloads](/en/downloads#system-software).

## Focus Notification Bypass

HyperCeiler or the built-in bypass can remove the Focus Notification allowlist restriction and enable more apps to display Focus Notifications.

::: danger Before enabling
The built-in bypass does not support safe mode. Configuration or compatibility problems may cause repeated System UI crashes, so enable it only if you can recover the device.
:::
