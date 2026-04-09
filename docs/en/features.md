# Features

HyperIsland provides rich Super Island notification enhancement features for HyperOS 3, making your notification experience more modern.

## App Features

### App Adaptation

Enable Super Island functionality for any app, with individual configuration per app.

- **Search**: Quickly search by app name or package name
- **Individual Switch**: Control enable/disable per app independently
- **Bulk Management**: View the number of enabled apps at a glance

![App Adaptation](../images/Screenshot_2026-04-05-00-00-06-698_io.github.hype.jpg){style="width: 50%;"}

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
| Notification Super Island - Lite | Auto-remove "x new messages" and duplicate fields |
| Download | Auto-detect download status and convert to Super Island |
| Download - Lite | Super Island shows only icon + progress ring |
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

::: warning
When Focus Notification is disabled, the Super Island is sent by **System UI** on behalf of the app, which may have compatibility issues.
:::

### Focus Notification Bypass

::: danger Built-in Bypass
The app includes a built-in whitelist bypass. It doesn't support safe mode and may cause System UI to crash infinitely. Make sure you can recover your device before enabling.
:::

Through HyperCeiler or built-in bypass, you can:
- Remove Focus Notification whitelist restrictions
- Unlock Focus Notification whitelist verification
- Enable any app's notifications to display as Focus Notifications

## Download Manager Extension

Intercept HyperOS download manager notifications and display them in Super Island style with filename and progress.

::: tip Core Features
- Support **Pause**, **Resume**, **Cancel** operations
- After pausing, a resume download notification is shown (requires Download Manager Hook enabled)
:::

::: tip How to Enable
Download Island is disabled by default. Go to the app, enable **"Show System Apps"**, and check **"Download Manager"**.
:::
