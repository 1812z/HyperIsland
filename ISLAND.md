# HyperOS 超级岛图层与 View 结构

本文记录 HyperIsland 开发过程中通过 SystemUI JADX、实机日志和当前 Hook 代码确认的超级岛 View 层级、状态映射、背景渲染、焦点通知和 fake 过渡层行为。

文档重点是区分两条渲染路径：

- 稳定状态由真实内容 View 绘制。
- 状态切换、窗口动画和部分手势动画由 `DynamicIslandContentFakeView` 绘制。

如果只修改真实 View，稳定状态可能正确，但缩放和移动动画仍会出现黑块、错位或模糊突然消失。

## 1. 关键类

| 用途 | 类 |
| --- | --- |
| 岛内容基类 | `miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView` |
| 正常内容实例 | `miui.systemui.dynamicisland.window.content.DynamicIslandContentView` |
| 动画替身实例 | `miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView` |
| 外层岛背景 | `miui.systemui.dynamicisland.DynamicIslandBackgroundView` |
| 大岛内容 | `miui.systemui.dynamicisland.view.DynamicIslandBigIslandView` |
| 焦点/展开内容 | `miui.systemui.dynamicisland.view.DynamicIslandExpandedView` |
| 动画控制 | `miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate` |
| 事件和窗口动画协调 | `miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator` |
| 岛状态 | `miui.systemui.dynamicisland.event.DynamicIslandState` |
| 模糊兼容层 | `miui.systemui.util.MiBlurCompat` 或 `miui.util.MiBlurCompat` |
| 原生实时背景模糊 | `com.android.internal.graphics.drawable.BackgroundBlurDrawable` |

## 2. 稳定状态 View 层级

根据 `DynamicIslandViewBinding`、`DynamicIslandBaseContentView` 字段和实机 View 日志，核心层级可抽象为：

```text
DynamicIslandBackgroundView (id=island_container)
└── DynamicIslandContentView (id=island_content)
    ├── FrameLayout (id=container)
    ├── FrameLayout (id=small_island_view)
    ├── DynamicIslandBigIslandView (id=big_island_view)
    ├── ViewStub (expandedViewStub)
    └── DynamicIslandExpandedView (id=expanded_view, ViewStub inflate 后存在)
```

### 2.1 各层职责

| View | 状态 | 职责 |
| --- | --- | --- |
| `small_island_view` | `SMALL` | 小岛稳定状态内容和背景宿主 |
| `big_island_view` | `BIG` | 大岛稳定状态内容和背景宿主 |
| `expanded_view` | `EXPAND` | 焦点通知、展开卡片的真实内容和背景宿主 |
| `container` | 共享 | 内容布局容器，不应直接当作某一种状态的唯一背景目标 |
| `island_container` | 共享 | 绘制系统外层岛轮廓、黑色背景或自定义背景 |

### 2.2 焦点通知

本文中的焦点通知对应 `EXPAND` 状态，真实背景目标是：

```text
DynamicIslandExpandedView (id=expanded_view)
```

它不是：

- `DynamicIslandBackgroundView`
- `container`
- `fake_expanded_view`
- `expandedViewStub`

`expanded_view` 可能在 ViewStub inflate 后才存在。它也可能在状态已经回到 `BIG` 时被 SystemUI 再次测量或布局，因此不能只根据 View 类名判断它当前是否应该启用焦点模糊，还要检查所属 `DynamicIslandContentView.state`。

需要区分“形状/遮罩参考 View”和“最终外层 Drawable 宿主”：

- `expanded_view` 是焦点稳定态的真实内容 View，也是类型和圆角参考。
- HyperIsland 自定义焦点图片最终写入 `DynamicIslandBackgroundView.drawable`，不写入 `expanded_view`。
- HyperIsland 焦点 BlurDrawable 也最终写入同一个 `DynamicIslandBackgroundView.drawable`，不写入 `expanded_view`。
- 图片和模糊共享同一个外层 Drawable 槽位，因此同一类型按设计互斥；存在自定义焦点背景时不会创建焦点 BlurDrawable。

实机曾观察到：

```text
view=DynamicIslandExpandedView(expanded_view)
type=EXPAND
state=BIG
size=1001x115
```

这种调用属于过渡或陈旧更新，不应重新激活稳定状态的焦点 BlurDrawable。

## 3. 状态映射

当前主要状态映射如下：

| `DynamicIslandState` 子类 | HyperIsland 类型 |
| --- | --- |
| `SmallIsland` | `SMALL` |
| `BigIsland` | `BIG` |
| `ShowOnceBigIsland` | `BIG` |
| `Expanded` | `EXPAND` |
| `AppExpanded` | `EXPAND` |
| `MiniWindowExpanded` | `EXPAND` |
| `SubAppExpanded` | `EXPAND` |
| `SubMiniWindowExpanded` | `EXPAND` |

主要状态识别入口是：

```java
DynamicIslandBaseContentView.updateDarkLightMode(
    DynamicIslandState state,
    String source,
    boolean arg1,
    boolean arg2
)
```

注意：状态字段、真实 View 可见性和动画 View 可见性在过渡期间可能短暂不同步。状态只表示目标或逻辑状态，不一定表示当前屏幕上正在绘制的 View。

## 4. fake 动画层

### 4.1 层级

`DynamicIslandContentFakeView` 使用 `dynamic_island_fake_view` 布局，其绑定类是 `DynamicIslandFakeViewBinding`。核心结构为：

```text
DynamicIslandContentFakeView / fake_container
├── FrameLayout (id=fake_small_island_view)
├── FrameLayout (id=fake_big_island_view)
├── FrameLayout (id=fake_expanded_view)
├── View (id=fake_island_mask)
└── View (id=mini_window_bar)
```

对应 `DynamicIslandBaseContentView` 字段：

```text
fakeView
fakeContainer
fakeSmallIsland
fakeBigIsland
fakeExpandedView
fakeMask
miniBar
```

### 4.2 fake View 不是无用占位符

`DynamicIslandContentFakeView` 是 SystemUI 的实际动画载体，承担：

- 小岛、大岛、展开态之间的窗口动画。
- 应用开关动画。
- 自由窗和 Mini Window 动画。
- 连续位置、宽高、缩放和透明度变化。
- 动态圆角 Outline 更新。
- 真实 View 与 fake View 的可见性交接。

因此不能简单删除整个 fake View，也不建议永久设置为 `GONE`。

`DynamicIslandContentFakeView.setVisibility(int)` 包含额外副作用，包括：

- 恢复真实内容 View。
- 恢复 `DynamicIslandBackgroundView`。
- 同步 Lottie 进度。
- 更新窗口动画运行状态。
- 结束动画生命周期延长。

直接修改整个 fake View 的可见性可能破坏 SystemUI 动画状态机。

### 4.3 动画期间真实 View 会被隐藏

在 `onTrackingFakeViewStart()` 中，SystemUI 会执行等价逻辑：

```text
fakeView.visibility = VISIBLE
fakeView.alpha = 1
realView.visibility = INVISIBLE
realView.backgroundView.visibility = INVISIBLE
```

这意味着动画期间：

- 真实 `small_island_view`、`big_island_view`、`expanded_view` 上的 BlurDrawable 不负责屏幕上的连续形变。
- 只更新真实 View 的 Drawable bounds 无法解决动画缩放。
- 过渡模糊必须依附 SystemUI 正在动画的 fake 层。

### 4.4 fake 展开容器

`DynamicIslandContentFakeView.onFinishInflate()` 会执行：

```java
setFakeExpandedView(findViewById(fake_expanded_view));
updateBackgroundBg(fakeExpandedView, false);
```

`updateExpandedView()` 会把数据提供的临时 View 放进 `fake_expanded_view`：

```text
fakeExpandedView.removeAllViews()
fakeExpandedView.addView(dynamicIslandData.fakeView)
```

因此 `fake_expanded_view` 是过渡期展开/焦点内容和背景的实际宿主，不是普通的稳定状态 `expanded_view`。

这里的“背景宿主”指 SystemUI 自己通过 `updateBackgroundBg(fakeExpandedView, ...)` 设置的内层 stock background、MiBlur 和 BlendColor。当前 HyperIsland 实现没有把自定义焦点图片或 `BackgroundBlurDrawable` 设置到 `fake_expanded_view`：

```text
稳定态自定义焦点背景 -> DynamicIslandBackgroundView.drawable
稳定态焦点实时模糊   -> DynamicIslandBackgroundView.drawable
过渡态 fake 内容/系统遮罩 -> fake_expanded_view
```

`onTrackingFakeViewStart()` 会隐藏真实 `DynamicIslandBackgroundView` 和真实内容 View，动画结束后 `updateExpandedFakeViewToReal()` 才重新显示它们。因此当前代码在 fake 动画期间只是清理可能盖住外层效果的系统 fake 遮罩，并没有把自定义图片或 BlurDrawable 迁移到 fake View。若动画期间观察到焦点自定义背景/模糊消失，这属于宿主切换造成的现有限制，不能把稳定态宿主误记为 `fake_expanded_view`。

## 5. fake 层几何和圆角

### 5.1 连续几何更新

手势和动画期间，`onTrackingFakeViewUpdate(float)` 会更新：

```text
alpha
left
right
top
bottom
```

更新后调用 `onFakeViewTrackingParamsUpdated()`，再调用：

```java
updateOutline(height, width, false)
```

### 5.2 动态 Outline

`DynamicIslandContentFakeView.updateOutline()` 会：

- 根据动画高度和宽度计算圆角矩形。
- 设置 `ViewOutlineProvider`。
- 调用 `setClipToOutline(true)`。
- 更新 `fake_expanded_view` 的模糊 Outline。
- 更新 Mini Window bar 的位置。

系统使用的轮廓类似：

```java
outline.setRoundRect(left, top, right, bottom, radius);
```

因此 fake 层能够连续缩放和裁剪，而真实 View 的宽高可能只在动画结束时发生离散切换。

### 5.3 `updateExpandViewBlur()`

`DynamicIslandContentFakeView.updateExpandViewBlur(int, boolean, boolean)` 会给 `fake_expanded_view` 设置专用 OutlineProvider，并根据动画阶段修改 RenderNode 位置及圆角矩形。

fake 层仍负责内容动画和 Outline，但当前模糊实现不在 fake 子 View 上创建额外 BlurDrawable。模糊几何统一复用 `DynamicIslandBackgroundView` 的 `actual*` 参数。

## 6. 系统背景渲染

### 6.1 `DynamicIslandBackgroundView`

共享外层背景由：

```text
DynamicIslandBackgroundView (id=island_container)
```

负责。它持有和使用的关键成员包括：

```text
drawable
backgroundAlpha
stokeWidth
scheduleUpdate()
alphaAnimation(float)
onDraw(Canvas)
setDrawable(Drawable)
```

该层适合：

- 系统稳定状态外层黑色岛轮廓。
- 自定义图片或 GIF 背景。
- 当前实时模糊 `BackgroundBlurDrawable`。

`onDraw()` 每帧执行等价逻辑：

```java
drawable.setBounds(
    actualLeft - stokeWidth,
    actualTop - stokeWidth,
    actualWidth + stokeWidth,
    actualHeight + stokeWidth
);
drawable.draw(canvas);
```

`actualLeft`、`actualTop`、`actualWidth` 和 `actualHeight` 由 `containerScheduleUpdate()` 根据系统动画更新。因此把 BlurDrawable 放在这个 `drawable` 字段中，可以直接跟随单岛、双岛、缩放、位移和消失动画，无需自行推算坐标。

### 6.2 `updateBackgroundBg(View, boolean)`

稳定 View 和 `fake_expanded_view` 都会经过：

```java
DynamicIslandBaseContentView.updateBackgroundBg(View view, boolean promoted)
```

系统行为分为两条路径。

背景模糊不可用或 View 没有 parent 时：

```text
setMiViewBlurModeCompat(view, 0)
clearMiBackgroundBlendColorCompat(view)
view.background = dynamic_island_background 或 dynamic_island_liveupdate_background
```

背景模糊可用时：

```text
setMiViewBlurModeCompat(view, 1)
clearMiBackgroundBlendColorCompat(view)
setMiBackgroundBlendColors(...)
view.background = null
```

系统默认 BlendColor 会形成深色或黑色视觉层。只清空 `background` 不足以移除黑色效果，还要区分：

- View background Drawable。
- MiBlur mode。
- MiBlur BlendColor。
- `DynamicIslandBackgroundView` 的外层 Drawable。
- `fake_island_mask`。

## 7. 模糊渲染策略

### 7.1 当前实现

三种状态共用每个岛实例自己的动态背景宿主，但按当前状态独立更新配置：

| 状态 | 形状参考 | BlurDrawable 宿主 |
| --- | --- | --- |
| `SMALL` | `small_island_view` | `DynamicIslandBackgroundView.drawable` |
| `BIG` | `big_island_view` | `DynamicIslandBackgroundView.drawable` |
| `EXPAND` | `expanded_view` | `DynamicIslandBackgroundView.drawable` |

创建流程：

```text
DynamicIslandBackgroundView.getViewRootImpl()
ViewRootImpl.createBackgroundBlurDrawable()
BackgroundBlurDrawable.setBlurRadius(radius)
BackgroundBlurDrawable.setCornerRadius(tl, tr, br, bl)
BackgroundBlurDrawable.setColor(argb)
DynamicIslandBackgroundView.drawable = drawable
DynamicIslandBackgroundView.onDraw() 动态设置 bounds 并绘制
```

配置仍按 `SMALL`、`BIG`、`EXPAND` 独立保存。状态切换时旧 BlurDrawable 半径归零、callback 断开，再创建或更新当前类型 Drawable。

### 7.2 过渡状态

过渡期间仍使用同一个 `DynamicIslandBackgroundView.drawable`。SystemUI 自己更新动态背景的 `actual*` 几何，BlurDrawable 随系统背景边界连续变化。

当前策略：

```text
稳定和动画状态：DynamicIslandBackgroundView.drawable + BackgroundBlurDrawable
几何和动画：SystemUI actualLeft/Top/Width/Height
内容动画：真实 View 和 DynamicIslandContentFakeView
```

不推荐：

- 删除 `DynamicIslandContentFakeView`。
- 永久隐藏 `fake_expanded_view`。
- 把 fake View 当作普通 `EXPAND` 稳定目标。
- 仅在真实 View 上逐帧修改 Drawable bounds 来模拟窗口动画。
- 在普通子 View 中插入 BlurDrawable。该方式会退化为 ViewRoot 范围，可能导致整个状态栏或屏幕模糊。
- 在内容 View 外部创建 overlay 并手工追踪坐标。双岛和 Folme 动画下容易错位。
- 使用 `MiBackgroundBlurMode` 直接替代 BlurDrawable。当前设备上只得到灰色混色层，没有有效背景采样。

### 7.3 fake 层黑色遮罩

当前 Hook 保留 fake View 的生命周期和内容动画，但清理 fake 根层、fake 三种内容容器、`fakeContainer` 和 `fake_island_mask` 的 stock background、MiBlur mode 与 BlendColor，避免它们叠在外层实时模糊上形成黑块。不会删除整个 `DynamicIslandContentFakeView`。

该清理只能在至少一种实时模糊启用时执行。`updateFakeViewAnimState()`、`onFinishInflate()` 和 `setVisibility(VISIBLE)` 即使未启用模块背景功能也会由 SystemUI 正常调用，因此这些 Hook 入口不能无条件清空 fake background 或隐藏 `fake_island_mask`。三种模糊全部关闭时，`IslandBlurHook` 必须保留系统纯黑过渡遮罩并对 fake 层零修改；自定义图片的遮罩处理继续由 `IslandBackgroundHook` 按类型负责。

`DynamicIslandBackgroundView.onDraw()` 也不能在当前实例没有 active `OuterBlur` 时被跳过。外层系统纯黑 Drawable 正是由该方法绘制；跳过后只剩内层 MiBlur/BlendColor，视觉上会成为灰色岛。inactive 实例必须始终执行原始 `onDraw()`，只有 active 实例需要先把 `drawable` 字段纠正为对应 BlurDrawable。

## 8. 圆角

### 8.1 小岛和大岛

小岛、大岛通常使用：

```text
com.android.systemui:dimen/island_radius
```

如果资源不存在，可回退到：

```text
view.height / 2
```

### 8.2 焦点通知

不能直接使用共享岛 Outline 的半径。实机曾读取到：

```text
Outline.radius = 77dp
```

这是胶囊形岛轮廓，不是焦点卡片圆角矩形的真实半径。焦点稳定状态需要使用焦点专用背景/资源的半径；当前代码曾使用 `32dp` 作为回退值，但它不是已证明的系统真实值。

### 8.3 fake 动画

fake 动画层应使用 SystemUI 的 `updateOutline()` 和 `updateExpandViewBlur()`，不要用固定焦点圆角覆盖动画 Outline。

## 9. 多岛实例

双岛场景不能假设只有一个内容实例。

当前安全建模方式：

```text
DynamicIslandBackgroundView 实例 -> OuterBlur
DynamicIslandContentView/View -> 弱引用刷新目标
```

每个 `OuterBlur` 独立保存：

- 当前类型的 `BackgroundBlurDrawable`
- 系统原始 Drawable
- active 状态

Map 使用弱键，按具体 `DynamicIslandBackgroundView` 实例隔离。View detach 时必须：

- 将模糊半径设为 `0`。
- 断开 Drawable callback。
- 从缓存移除 `OuterBlur`。
- 移除 attach-state listener。

## 10. 主动刷新

SystemUI 不一定会为小岛和大岛自然调用 `updateBackgroundBg()`。做法是主动获取具体 View：

```java
getSmallIslandView()
getBigIslandView()
updateBackgroundBg(view, false)
```

该调用不是 SystemUI 在每次 `updateDarkLightMode()` 后必然执行的原始流程，而是模糊 Hook 为安装外层 BlurDrawable 追加的刷新。因此只能在目标类型的 `BlurConfig.isActive=true` 时调用。若模糊全关仍主动调用，支持背景模糊的设备会进入 SystemUI 的 MiBlur 分支：设置 blur mode 和 BlendColor、清空 View background，结果是仅加载 Hook 就把原生纯黑岛变成灰色。

主动刷新必须检查内容实例当前状态，只刷新匹配类型：

```text
current state == refresh target type
```

否则历史 `expanded_view`、旧大岛或另一个岛的 View 可能被重新激活。

## 11. 当前 Hook 文件职责

### `IslandBlurHook.kt`

路径：

```text
android/app/src/main/kotlin/io/github/hyperisland/xposed/hook/SystemUI/IslandBlurHook.kt
```

职责：

- 加载三种状态模糊配置。
- 识别状态和具体 View 类型。
- 在 `DynamicIslandBackgroundView.drawable` 中创建和管理 `BackgroundBlurDrawable`。
- 保存并恢复系统外层原 Drawable。
- 按具体背景 View 实例隔离多个岛。
- 主动刷新小岛和大岛 View。
- 复用 SystemUI `onDraw()` 的动态 bounds 绘制模糊。
- 在 View detach 时释放 callback 和模糊资源。
- 排除 `fake_expanded_view`，避免把过渡层当作稳定焦点 View。

### `IslandBackgroundHook.kt`

路径：

```text
android/app/src/main/kotlin/io/github/hyperisland/xposed/hook/SystemUI/IslandBackgroundHook.kt
```

职责：

- 替换 `DynamicIslandBackgroundView.drawable` 为图片或 GIF。
- 处理系统 View background、MiBlur mode 和 BlendColor。
- 在自定义背景模式下清除遮罩。
- 协调实时模糊，避免具体内容和 fake 层叠加 stock 黑色背景。
- 对 `fake_expanded_view` 禁用第二层原生模糊和默认 BlendColor。
- 限制图片/GIF 解码尺寸并使用弱 Drawable callback，避免 SystemUI OOM 和 View 泄漏。

### 其他相关 Hook

| 文件 | 关系 |
| --- | --- |
| `IslandOuterGlowHook.kt` | 使用 `getBigIslandView()` 获取具体大岛 View，可作为反射参考 |
| `TextShadeHook.kt` | 大岛自定义背景或大岛模糊开启时禁用文本滚动阴影 |
| `SmoothIslandHook.kt` | 处理岛描边和背景重绘，可能与背景 Drawable 生命周期交叉 |
| `IslandDimenHook.kt` | 修改岛尺寸，可能影响圆角和模糊 bounds |
| `IslandTopOffsetHook.kt` | 修改位置，可能影响窗口动画和最终几何位置 |

## 12. 配置键

### 小岛

```text
pref_island_blur_small_enabled
pref_island_blur_small_radius
pref_island_blur_small_color
pref_island_bg_small_path
```

### 大岛

```text
pref_island_blur_big_enabled
pref_island_blur_big_radius
pref_island_blur_big_color
pref_island_bg_big_path
```

### 焦点/展开

```text
pref_island_blur_expand_enabled
pref_island_blur_expand_radius
pref_island_blur_expand_color
pref_island_bg_expand_path
```

模糊半径当前限制为 `0-275`。颜色使用 ARGB。

## 13. 已知问题和常见错误

### 13.1 过渡出现黑块

可能来源：

- `DynamicIslandBackgroundView` 外层 Drawable 未抑制。
- `fake_expanded_view` 的系统 BlendColor。
- `fake_island_mask`。
- 原始 `dynamic_island_background` Drawable。
- 旧 View 恢复了错误或空的 stock background。
- 新旧模糊层交接之间出现一帧空档。

### 13.2 模糊缩小时不连续

应确认 BlurDrawable 是否仍由 `DynamicIslandBackgroundView.onDraw()` 使用 `actual*` 参数设置 bounds。不要切回具体内容 View、普通内部子 View或外部 overlay。

### 13.3 模糊停留在旧位置

可能来源：

- `DynamicIslandBackgroundView.drawable` 被其他 Hook 或 SystemUI 覆盖。
- `actualLeft/Top/Width/Height` 没有在动画帧更新。
- 多个背景 View 实例共用了错误的 `OuterBlur`。
- 旧 BlurDrawable callback 未断开。

### 13.4 焦点变大岛后残留

`expanded_view` 在 `state != EXPAND` 下的更新必须视为陈旧更新并忽略，不能用它关闭当前大岛的外层 BlurDrawable。`fake_expanded_view` 仍不能作为稳定焦点类型目标。

### 13.5 类型判断顺序

识别 View 时应先检查资源名：

```text
fake_expanded_view -> 过渡层，不是稳定 EXPAND
```

再检查类名。否则类名包含 `ExpandedView` 的 fake/包装 View 可能被误判。

### 13.6 功能全关时缺少纯黑遮罩

检查两条路径：

- `IslandBlurHook.applyTransitionBlur()` 是否在 `anyBlurEnabled=false` 时仍清空 fake 根层和子 View background，或隐藏 `fake_island_mask`。这些过渡回调始终会运行，清理函数必须自行检查模糊总开关。
- `IslandBlurHook.hookBackgroundDrawing()` 是否在当前背景实例没有 active `OuterBlur` 时跳过原始 `onDraw()`。这会删除外层纯黑 Drawable，只留下灰色 MiBlur/BlendColor。
- `IslandBlurHook.hookIslandState()` 是否在对应模糊关闭时仍主动调用具体 View 的 `updateBackgroundBg()`。这是额外调用，不是透明观察；它会让 SystemUI 重新配置 MiBlur 和 BlendColor。

## 14. 调试日志建议

稳定状态更新至少记录：

```text
contentView identity
backgroundView identity
state
target type
target View class/id
target size
target visibility
target translationX/translationY
target scaleX/scaleY
BlurDrawable identity
BlurDrawable active
stock background identity
```

fake 动画至少记录：

```text
fakeView visibility/alpha
fakeExpandedView visibility/size/scale/translation
fakeExpandedView blur mode
fakeExpandedView background
fakeMask visibility/alpha/background
realView visibility
backgroundView visibility
realView.state
realView.lastState
```

建议重点测试转换：

```text
SMALL -> BIG
BIG -> SMALL
BIG -> EXPAND
EXPAND -> BIG
SMALL -> EXPAND
EXPAND -> SMALL
双岛 SMALL + BIG
双岛状态互换
焦点通知出现和消失
应用开关动画
Mini Window 手势
```

## 15. 已确认结论

1. 当前实时模糊应放在每个岛实例的 `DynamicIslandBackgroundView.drawable` 中。
2. `DynamicIslandBackgroundView.onDraw()` 的 `actual*` bounds 是跟随双岛、缩放、位移和消失动画的关键。
3. 小岛、大岛和 `expanded_view` 用于状态识别与圆角参考，不再各自持有 BlurDrawable。
4. `expanded_view` 是真实焦点 View，`fake_expanded_view` 是动画内容宿主，两者不可混用。
5. fake View 是 SystemUI 动画状态机的一部分，不能简单删除，但其 stock 黑底、BlendColor 和第二层模糊需要清理。
6. `updateBackgroundBg()` 同时控制背景 Drawable、MiBlur mode 和 BlendColor。
7. 黑色效果不一定来自单一 Drawable，必须分别检查外层背景、具体 View、MiBlur BlendColor 和 fake mask。
8. 多岛按具体 `DynamicIslandBackgroundView` 实例隔离 `OuterBlur`。
9. SystemUI 不一定主动刷新小岛和大岛背景，必要时要调用具体 getter 和 `updateBackgroundBg()`。
10. 普通内部子 View BlurDrawable 可能扩大到整个 ViewRoot，不能用于本实现。
11. 三种模糊全部关闭时不得清理 fake 层，系统纯黑过渡遮罩必须完整保留。
12. inactive 背景实例必须执行 SystemUI 原始 `onDraw()`；不得通过跳过外层绘制来清理内层遮罩。
13. 主动 `updateBackgroundBg()` 只允许用于 active 模糊类型；模糊全关时不得追加任何背景刷新。

## 16. 信息来源和可信度

### SystemUI JADX 已确认

- `DynamicIslandBaseContentView` 字段和关键方法。
- `DynamicIslandContentFakeView` 层级和生命周期。
- `onFinishInflate()` 对 `fake_expanded_view` 调用 `updateBackgroundBg()`。
- `onTrackingFakeViewStart()` 隐藏真实 View 和外层背景。
- `onTrackingFakeViewUpdate()` 更新动画几何。
- `updateOutline()` 和 `updateExpandViewBlur()` 更新圆角和 RenderNode。
- `updateExpandedFakeViewToReal()` 从 fake View 交回真实 View。
- 系统 `updateBackgroundBg()` 的 Drawable、MiBlur 和 BlendColor 行为。
- `DynamicIslandBackgroundView.onDraw()` 每帧按 `actual*` 设置 Drawable bounds。
- `containerScheduleUpdate()` 更新动态背景几何。

### 背景实现已确认

- 通过 `getSmallIslandView()`、`getBigIslandView()` 获取具体 View。
- 主动调用 `updateBackgroundBg(view, false)` 刷新小岛和大岛。
- 具体 View getter 用于触发状态背景刷新和提供状态/形状参考。

### 实机日志已确认

- `BackgroundBlurDrawable` 可创建并成功设置半径、圆角和颜色。
- 焦点真实 View 为 `DynamicIslandExpandedView(expanded_view)`。
- 同一时段可能存在多个 ContentView 和多个背景 View 实例。
- `expanded_view` 会在 `state=BIG` 时继续收到过渡布局更新。
- 双岛时小岛和大岛可同时存在并分别绘制。
- 外层动态 Drawable 方案已解决内容缩放而模糊固定、双岛错位和突然消失问题。

### 仍需实机确认

- 焦点通知真实圆角资源或专用背景 Drawable 的准确来源；当前使用 `32dp` 回退值。
- 不同 HyperOS 版本是否保持相同的 `actual*` 字段和 `onDraw()` 行为。

## 17. 生命周期、安全和性能约束

### 17.1 实时模糊

- `OuterBlur` 使用 `WeakHashMap<DynamicIslandBackgroundView, OuterBlur>` 按实例缓存。
- BlurDrawable callback 使用弱 View 代理，避免 `Map -> Drawable -> View` 强引用环。
- 系统原 Drawable在模糊启用期间强引用保存，以保证关闭模糊时可恢复；背景 View detach 或模糊停用时立即移除整个 `OuterBlur`，避免长期保留。
- 背景 View detach 时将模糊半径设为 `0`、断开 callback、移除缓存和 listener。
- 状态关闭或切换到自定义图片时立即释放当前 `OuterBlur`。
- `onDraw()` 热路径只读取预计算的 `anyBlurEnabled` 和弱缓存，不再创建状态集合或扫描历史 View。
- 所有反射读写都应失败降级；反射异常不能传播到 SystemUI 绘制线程。

### 17.2 自定义图片和 GIF

- 静态图按最长边进行采样，避免极端宽高比图片以原始尺寸进入 SystemUI。
- GIF 使用 `ImageDecoder.setTargetSize()` 限制最长边，避免大尺寸动画造成 OOM。
- 解码失败时保留旧缓存，不回收仍在显示的 Drawable。
- 已交给 View 的 Bitmap 不手动 `recycle()`，由 GC 在没有引用后回收，避免绘制线程访问 recycled bitmap。
- 静态图片每个背景 View 使用独立的轻量 Drawable wrapper，共享只读 Bitmap，避免多岛互相覆盖 bounds。
- 缓存 Drawable 使用弱 callback，不强引用 SystemUI View 树。
- Hook 去重使用实际目标 Class 的弱集合，不使用可能碰撞的 `identityHashCode`。

### 17.3 残余风险

- GIF 的 `AnimatedImageDrawable` 当前仍是单缓存实例；多个同时可见岛可能共享动画 bounds。若后续出现双岛 GIF 错位，应改为缓存源文件/解码数据并为每个 View 创建独立动画实例。
- `IslandBackgroundHook` 仍有少量文件存在性和 `lastModified()` 检查发生在 UI 调用链。配置读取已有缓存，但后续可把文件变更检测完全移到配置更新线程。
- SystemUI 私有字段和方法可能随 HyperOS 版本变化。关键反射成员缺失时必须回退系统原行为，不能继续清空背景。
