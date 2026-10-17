package io.github.hyperisland.compose.page.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.findViewTreeNavigationEventDispatcherOwner
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.ConfigSectionTree
import io.github.hyperisland.compose.component.DetailGridPage
import io.github.hyperisland.compose.data.ConfigPreset
import io.github.hyperisland.compose.data.ConfigSection
import io.github.hyperisland.compose.data.ConfigSectionGroups
import io.github.hyperisland.compose.data.HUB_STATUS_PENDING
import io.github.hyperisland.compose.data.HUB_STATUS_PUBLISHED
import io.github.hyperisland.compose.data.HUB_STATUS_REJECTED
import io.github.hyperisland.compose.data.InstalledAppsRepository
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.PRESET_AUTHOR_MAX
import io.github.hyperisland.compose.data.PRESET_CONTENT_MAX
import io.github.hyperisland.compose.data.PRESET_TITLE_MAX
import io.github.hyperisland.compose.data.PresetSortOrder
import io.github.hyperisland.compose.data.PresetStore
import io.github.hyperisland.compose.data.findConfigSection
import io.github.hyperisland.compose.data.isRemoteVersionNewer
import io.github.hyperisland.compose.data.parseAppConfigLeafId
import io.github.hyperisland.compose.data.sortPresets
import io.github.hyperisland.compose.service.HubClient
import io.github.hyperisland.compose.service.HubException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.menu.OverlayIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

/** 手机（窄屏）下预设卡片高度。 */
private val PresetCardHeight = 144.dp

/** 平板等宽屏下卡片更宽，正文多显示一行，高度相应加高一行（body2 单行约 20dp + 一点余量）。 */
private val PresetCardWideExtraHeight = 28.dp

/** 卡片背景水印的边长：够大才有「背景」感；它不占布局，不会挤到正文。 */
private val PresetBadgeSize = 76.dp

/** 中性水印（已上传 / 云端有新版本）的不透明度：只是背景气氛，压到近乎透明。 */
private const val PresetBadgeNeutralAlpha = 0.10f

/** 状态水印（审核中 / 已被拒）的不透明度：靠色相区分，但不能盖过压在上面的正文。 */
private const val PresetBadgeStatusAlpha = 0.22f

/**
 * 卡片背景水印的内容：图标 + 已解析的取色。
 *
 * 中性状态（已上传 / 云端有新版本）用黑白水印；审核中用警告色、被拒用错误色，
 * 色相与 App 内其余「警告 / 错误」提示保持一致。
 */
internal data class PresetCardBadge(val icon: ImageVector, val tint: Color)

/**
 * 水印的语义色调。
 *
 * 判定只产出色调、不产出颜色：具体颜色要在组合期按深浅色主题解析（见 [presetBadgeColor]），
 * 所以这一层刻意保持非组合，可以被任意列表项直接调用。
 */
private enum class PresetBadgeTone { Neutral, Warning, Error }

/** 未解析颜色的水印描述。 */
private data class PresetBadgeSpec(val icon: ImageVector, val tone: PresetBadgeTone)

/**
 * 卡片状态水印的判定。
 *
 * 本地预设：没上传过 → 无；审核中 → `Timer`（警告色）；被拒 → `Report`（错误色）；
 * 已发布 → `CloudFill`（黑白水印）。
 *
 * 云端预设：仅当本机缓存过的正文版本落后于列表版本时显示 `Update`（黑白水印）。
 * 从没缓存过正文的那条不显示——本机没有旧内容需要更新。
 */
private fun ConfigPreset.presetBadgeSpec(cachedVersion: String?): PresetBadgeSpec? = when {
    local -> when {
        hubId.isBlank() -> null
        hubStatus == HUB_STATUS_REJECTED -> PresetBadgeSpec(MiuixIcons.Report, PresetBadgeTone.Error)
        hubStatus == HUB_STATUS_PUBLISHED -> PresetBadgeSpec(MiuixIcons.CloudFill, PresetBadgeTone.Neutral)
        else -> PresetBadgeSpec(MiuixIcons.Timer, PresetBadgeTone.Warning)
    }
    cachedVersion != null && isRemoteVersionNewer(version, cachedVersion) ->
        PresetBadgeSpec(MiuixIcons.Update, PresetBadgeTone.Neutral)
    else -> null
}

/** 把语义色调解析成最终颜色（含透明度），随深浅色主题变化。 */
@Composable
private fun presetBadgeColor(tone: PresetBadgeTone): Color = when (tone) {
    PresetBadgeTone.Neutral -> presetWatermarkColor()
    PresetBadgeTone.Warning -> presetWarningColor()
    PresetBadgeTone.Error -> MiuixTheme.colorScheme.error.copy(alpha = PresetBadgeStatusAlpha)
}

/** 中性水印色：深色主题取纯白、浅色主题取纯黑，再压到几乎透明。 */
@Composable
private fun presetWatermarkColor(): Color =
    (if (isSystemInDarkTheme()) Color.White else Color.Black).copy(alpha = PresetBadgeNeutralAlpha)

/**
 * 警告色基准：Miuix 主题没有 warning 槽位，沿用 OverviewPage / OnboardingPage 的琥珀配色
 * （浅色 `#704D00` / 深色 `#FFD978`），保证 App 内「警告」始终是同一个色。
 */
@Composable
private fun presetWarningTint(): Color =
    if (MiuixTheme.colorScheme.background.luminance() > 0.5f) Color(0xFF704D00) else Color(0xFFFFD978)

/** 警告色水印：在 [presetWarningTint] 基础上压到接近透明。 */
@Composable
private fun presetWarningColor(): Color =
    presetWarningTint().copy(alpha = PresetBadgeStatusAlpha)

/** 屏幕短边达到该值即按宽屏（平板 / 折叠屏展开 / 桌面模式）处理，手机维持现状。 */
private val PresetWideScreenMinWidth = 600.dp

/** 宽屏基准列数：竖屏 3 列、横屏 4 列（单卡约为手机单卡 180dp 的 1.4~1.7 倍）。 */
private const val PresetWidePortraitColumns = 3
private const val PresetWideLandscapeColumns = 4

/** 宽屏下单卡宽度上限，超过就继续增列（超宽窗口 / 桌面模式不会出现超宽卡片）。 */
private val PresetWideCardMaxWidth = 480.dp

/** 手机端网格列规则：内容宽度自适应，从 128dp 起分列（保持原有显示效果）。 */
private val PresetPhoneColumns = GridCells.Adaptive(minSize = 128.dp)

/**
 * 预设网格列数。
 *
 * 手机沿用 [PresetPhoneColumns]；宽屏（短边 ≥ [PresetWideScreenMinWidth]）竖屏 3 列、横屏 4 列，
 * 单卡宽度比手机略大且不再随屏幕变宽而拉伸；窗口实在太宽时按 [PresetWideCardMaxWidth] 增列。
 */
private fun presetGridColumns(widthDp: Int, heightDp: Int): GridCells {
    if (minOf(widthDp, heightDp).dp < PresetWideScreenMinWidth) return PresetPhoneColumns
    val base = if (widthDp < heightDp) PresetWidePortraitColumns else PresetWideLandscapeColumns
    // DetailGridPage 左右各 16dp 内容内边距，这里用屏幕宽度近似可用宽度
    val needed = ceil((widthDp - 32) / PresetWideCardMaxWidth.value).toInt()
    return GridCells.Fixed(maxOf(base, needed))
}

@Composable
private fun ResetConfigDialog(
    show: Boolean,
    sections: List<ConfigSection>,
    counts: Map<String, Int>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    var selected by remember(show) { mutableStateOf(emptySet<String>()) }
    WindowDialog(
        show = show,
        title = stringResource(R.string.preset_reset_config),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.preset_reset_summary))
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                item {
                    ConfigSectionTree(
                        sections = sections,
                        selectedLeafIds = selected,
                        onSelectedLeafIdsChange = { selected = it },
                        counts = counts,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onApply(selected) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.apply)) }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

/** 应用配置 bottom sheet 的层级：详情 → 配置内容 → 全屏编辑。 */
private enum class PresetSheetView { Detail, Content, Editor }

/**
 * 云端列表的进程级状态：跨越页面返回 / 再次进入保持，避免每次进入都重新拉取。
 * 首次进入自动拉取一次；之后只有下拉刷新才强制重新拉取。
 */
private object PresetCloudState {
    var presets by mutableStateOf<List<ConfigPreset>>(emptyList())
    var loaded by mutableStateOf(false)
    var refreshing by mutableStateOf(false)

    suspend fun ensureLoaded() {
        if (loaded) return
        refresh()
    }

    suspend fun refresh() {
        refreshing = true
        try {
            presets = HubClient.listAll()
            loaded = true
        } finally {
            refreshing = false
        }
    }
}

/**
 * 一条本地预设的云端状态快照（自查接口 / 公开列表快照二选一得来）。
 *
 * 四个字段要一起比对才能判断「有没有需要写盘的变化」，所以打包成一个类型，
 * 而不是继续往 `Triple` 上叠 —— 四个元素的元组在调用点全靠 `.first/.second`
 * 定位，加一个字段就得全文改一遍。
 */
private data class HubStatusSnapshot(
    val status: String,
    val downloads: Long,
    val rejectReason: String,
    val version: String,
)

/**
 * 本地预设的云端状态查询节流。
 *
 * 条目状态（审核中 / 已发布 / 被拒）与下载量都靠自查接口拿，为不让每次进入页面都为它们
 * 重复请求，同一 id 在 [MIN_INTERVAL_MS] 内只查一次；用户主动下拉刷新时忽略节流。
 * 只活在进程内存里——重启 App 后重新查一遍是期望行为（那时状态很可能真的变了）。
 */
private object PresetHubStatusState {
    private const val MIN_INTERVAL_MS = 10 * 60_000L
    private val checkedAt = mutableMapOf<String, Long>()

    fun shouldCheck(hubId: String, now: Long): Boolean {
        val last = checkedAt[hubId] ?: return true
        return now - last >= MIN_INTERVAL_MS
    }

    fun markChecked(hubId: String, now: Long) {
        checkedAt[hubId] = now
    }

    /** 刚上传过的条目作废节流，让随后的刷新能立刻反映最新状态。 */
    fun forget(hubId: String) {
        checkedAt.remove(hubId)
    }
}

/**
 * 一键配置预设页面。
 *
 * 本地 + 云端（HyperIsland-Hub）预设统一展示。顶栏 More 菜单可新建配置 / 切换排序；
 * 点击卡片打开应用配置 bottom sheet，云端预设会在打开时下载正文。
 *
 * 布局：手机保持原样（128dp 自适应，通常 2 列）；平板等宽屏竖屏 3 列、横屏 4 列，
 * 卡片高度加高一行正文（见 [presetGridColumns] / [PresetConfigCard]）。
 */
@Composable
internal fun PresetConfigPage(
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 宽屏（平板 / 折叠屏展开 / 桌面模式）判定：只影响卡片尺寸与网格列数
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val wideScreen = minOf(screenWidthDp, screenHeightDp).dp >= PresetWideScreenMinWidth
    val presetColumns = remember(screenWidthDp, screenHeightDp) {
        presetGridColumns(screenWidthDp, screenHeightDp)
    }
    val snackbarState = remember { SnackbarHostState() }
    var configRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val removeListener = prefs.addChangeListener { configRevision++ }
        onDispose { removeListener() }
    }
    // 页面进入时后台准备分类和名称，sheet / dialog 共用；显示与收起均不触发重新加载。
    val sectionTree by produceState(ConfigSectionGroups, configRevision) {
        value = withContext(Dispatchers.IO) { PresetStore.appConfigSectionTree(context) }
    }
    val sectionCounts by produceState(emptyMap<String, Int>(), configRevision) {
        value = withContext(Dispatchers.IO) { PresetStore.sectionKeyCounts(context) }
    }
    LaunchedEffect(sectionTree) {
        val repository = InstalledAppsRepository(context.applicationContext)
        sectionTree.flatMap { it.leafIds }
            .mapNotNull { parseAppConfigLeafId(it)?.second }.distinct().forEach { packageName ->
                withContext(Dispatchers.IO) { repository.loadIcon(packageName) }
                delay(16)
            }
    }

    var reload by remember { mutableIntStateOf(0) }
    // 默认按下载量排序：云端预设里下载量最能反映「用的人多」，比按时间或名称更有参考价值。
    var sortOrder by remember { mutableStateOf(PresetSortOrder.Downloads) }
    val localPresets = remember(reload, context) { PresetStore.loadLocal(context) }
    val cloudPresets = PresetCloudState.presets

    val cloudLoadFailed = stringResource(R.string.preset_cloud_load_failed)

    /**
     * 回填本地预设的云端状态（审核中 / 已发布 / 被拒）与下载量。
     *
     * 有 `hubId` 的条目逐条走 [HubClient.myConfig] 自查——它同时给出状态与**实时**下载量，
     * 而且不累加计数；查询按 [PresetHubStatusState] 节流，同一 id 十分钟内最多一次，
     * 下拉刷新（[force]）忽略节流。
     *
     * 没查（未到节流时间）或查失败时才退回公开列表的快照值，但那条只在已发布时成立；
     * 列表里本来就没有的条目（审核中 / 被拒）保持原状，等下一次能查的时候再更新。
     */
    suspend fun refreshHubStatuses(force: Boolean) {
        val targets = withContext(Dispatchers.IO) { PresetStore.loadLocal(context) }
            .filter { it.hubId.isNotBlank() }
        if (targets.isEmpty()) return
        val now = System.currentTimeMillis()
        // 设备 id 首次使用时要生成并落盘，放 IO 线程；一条循环里只取一次。
        val deviceId = withContext(Dispatchers.IO) { PresetStore.deviceId(context) }
        // 刻意读对象属性而非组合期的快照：下拉刷新刚更新过列表，必须用最新那份。
        val listedById = PresetCloudState.presets.associateBy { it.id }
        var changed = false
        for (preset in targets) {
            val listed = listedById[HubClient.cloudKey(preset.hubId)]
            // 优先用自查接口：列表里的下载量是快照（靠 sync / 定时归并刷新，可能落后一天），
            // 而本地卡片要显示的是「我上传的那条现在被下了多少」。按节流查询，查不到再退回快照。
            val queried = if (force || PresetHubStatusState.shouldCheck(preset.hubId, now)) {
                runCatching { HubClient.myConfig(preset.hubId, deviceId) }.fold(
                    onSuccess = { body ->
                        body.takeIf { it.optString("status").isNotBlank() }
                            ?.also { PresetHubStatusState.markChecked(preset.hubId, now) }
                    },
                    onFailure = { error ->
                        // 404：云端这条已经不存在了（被管理员删掉）。就地摘掉在线 id 降级成新的本地
                        // 配置——留着它，卡片会一直顶着「审核中」水印，而且再也更新不出去。
                        if (error.isGoneOnHub()) {
                            PresetStore.detachLocalHub(context, preset.hubId)
                            PresetHubStatusState.forget(preset.hubId)
                            changed = true
                        }
                        null
                    },
                )
            } else {
                null
            }
            val resolved = when {
                queried != null -> HubStatusSnapshot(
                    queried.optString("status"),
                    queried.optLong("downloads", 0L),
                    // 未过审时服务端下发的是 JSON null，而 org.json 的 optString 会把 JSON null
                    // 变成字符串 "null" —— 那不是理由，必须当空串处理，否则会显示「原因: null」。
                    if (queried.isNull("rejectReason")) "" else queried.optString("rejectReason"),
                    queried.optString("version"),
                )
                // 退回列表快照时拿不到拒绝原因，沿用本地已有的那份，不凭空清空。
                listed != null -> HubStatusSnapshot(
                    HUB_STATUS_PUBLISHED,
                    listed.downloads,
                    preset.hubRejectReason,
                    listed.version,
                )
                else -> null
            } ?: continue
            if (
                preset.hubStatus == resolved.status &&
                preset.downloads == resolved.downloads &&
                preset.hubRejectReason == resolved.rejectReason &&
                preset.version == resolved.version
            ) {
                continue
            }
            PresetStore.updateLocalHub(
                context,
                preset.hubId,
                resolved.status,
                resolved.downloads,
                resolved.rejectReason,
                resolved.version,
            )
            changed = true
        }
        if (changed) reload++
    }

    fun refreshCloud() {
        scope.launch {
            runCatching { PresetCloudState.refresh() }
                .onFailure { snackbarState.showSnackbar(cloudLoadFailed) }
            refreshHubStatuses(force = true)
        }
    }

    // 从剪贴板导入复制的预设 JSON（与「复制」输出的格式一致）到本地预设。
    fun importFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        val preset = text?.takeIf { it.isNotBlank() }
            ?.let { runCatching { ConfigPreset.fromJson(JSONObject(it)) }.getOrNull() }
            ?.takeIf { it.title.isNotBlank() }
        when {
            text.isNullOrBlank() -> scope.launch {
                snackbarState.showSnackbar(context.getString(R.string.preset_import_empty))
            }
            preset == null -> scope.launch {
                snackbarState.showSnackbar(context.getString(R.string.preset_import_failed))
            }
            else -> {
                // hubId / hubStatus 属于「上传它的那台设备」，导入的一律清掉：
                // 否则卡片会误显示云朵/审核中水印，点上传还会拿着别人的 id 去更新（403）。
                val local = preset.copy(
                    id = UUID.randomUUID().toString(),
                    local = true,
                    downloads = 0L,
                    hubId = "",
                    hubStatus = "",
                )
                PresetStore.saveLocal(context, local)
                reload++
                scope.launch {
                    snackbarState.showSnackbar(context.getString(R.string.preset_import_success, local.title))
                }
            }
        }
    }
    // 仅在软件本次启动后首次进入页面时自动拉取；再次进入复用进程内列表，手动下拉才刷新。
    LaunchedEffect(Unit) {
        runCatching { PresetCloudState.ensureLoaded() }
            .onFailure { snackbarState.showSnackbar(cloudLoadFailed) }
        // 列表到位后再回填本地条目的云端状态（审核中 / 被拒的靠自查接口）。
        refreshHubStatuses(force = false)
    }

    val localSorted = remember(localPresets, sortOrder) { sortPresets(localPresets, sortOrder) }
    val otherSorted = remember(cloudPresets, sortOrder) {
        sortPresets(cloudPresets, sortOrder)
    }

    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val keyword = searchQuery.trim()
    val localFiltered = remember(localSorted, keyword) {
        if (keyword.isBlank()) localSorted else localSorted.filter { it.matchesQuery(keyword) }
    }
    val otherFiltered = remember(otherSorted, keyword) {
        if (keyword.isBlank()) otherSorted else otherSorted.filter { it.matchesQuery(keyword) }
    }

    var showNewSheet by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var applyTarget by remember { mutableStateOf<ConfigPreset?>(null) }
    var applySheetShown by remember { mutableStateOf(false) }
    // 「修改配置」的目标（新建配置 sheet 的复用入口）。
    var modifyTarget by remember { mutableStateOf<ConfigPreset?>(null) }
    var modifySheetShown by remember { mutableStateOf(false) }
    // 应用成功后待弹出的提示条数；等 bottom sheet 关闭动画结束再展示。
    var appliedCount by remember { mutableStateOf<Int?>(null) }
    // 上传 / 复制成功后待弹出到页面底部的提示；等 sheet 关闭动画结束再展示。
    var sheetMessage by remember { mutableStateOf<String?>(null) }

    // 云端正文缓存会随「点开某条云端预设」而变化，用它驱动角标重算。
    // 缓存是磁盘上的 JSON，放 IO 线程解析：列表每次重组都可能重算，不能压在组合线程上。
    var hubCacheRevision by remember { mutableIntStateOf(0) }
    val hubCachedVersions by produceState(emptyMap<String, String>(), cloudPresets, hubCacheRevision) {
        value = withContext(Dispatchers.IO) { PresetStore.hubCachedVersions(context) }
    }

    val newConfigLabel = stringResource(R.string.preset_new_config)
    val importLabel = stringResource(R.string.preset_import_clipboard)
    val resetLabel = stringResource(R.string.preset_reset_config)
    val sortLabel = stringResource(R.string.preset_sort)
    val nameLabel = stringResource(R.string.preset_sort_name)
    val downloadsLabel = stringResource(R.string.preset_sort_downloads)
    val dateLabel = stringResource(R.string.preset_sort_date)
    val menuEntries = remember(newConfigLabel, importLabel, resetLabel, sortLabel, nameLabel, downloadsLabel, dateLabel, sortOrder) {
        listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(text = newConfigLabel, onClick = { showNewSheet = true }),
                    DropdownItem(text = importLabel, onClick = { importFromClipboard() }),
                    DropdownItem(text = resetLabel, onClick = { showResetDialog = true }),
                ),
            ),
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = sortLabel,
                        children = listOf(
                            DropdownItem(
                                text = nameLabel,
                                selected = sortOrder == PresetSortOrder.Name,
                                onClick = { sortOrder = PresetSortOrder.Name },
                            ),
                            DropdownItem(
                                text = downloadsLabel,
                                selected = sortOrder == PresetSortOrder.Downloads,
                                onClick = { sortOrder = PresetSortOrder.Downloads },
                            ),
                            DropdownItem(
                                text = dateLabel,
                                selected = sortOrder == PresetSortOrder.Date,
                                onClick = { sortOrder = PresetSortOrder.Date },
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    DetailGridPage(
        title = stringResource(R.string.preset_page_title),
        onBack = onBack,
        columns = presetColumns,
        actions = {
            OverlayIconCascadingDropdownMenu(entries = menuEntries) {
                Icon(MiuixIcons.More, stringResource(R.string.list_actions))
            }
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        isRefreshing = PresetCloudState.refreshing,
        onRefresh = { refreshCloud() },
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchBar(
                inputField = {
                    InputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = {},
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        label = stringResource(R.string.preset_search),
                    )
                },
                onExpandedChange = { searchExpanded = it },
                expanded = searchExpanded,
                outsideEndAction = {
                    Text(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clickable(interactionSource = null, indication = null) {
                                searchExpanded = false
                            },
                        text = stringResource(R.string.cancel),
                        color = MiuixTheme.colorScheme.primary,
                    )
                },
            ) {}
        }
        // 本地配置置顶。上传过的显示云端下载量（没上传那栏没有意义）。
        items(localFiltered, key = { it.id }) { preset ->
            PresetConfigCard(
                preset = preset,
                showDownloads = preset.hubId.isNotBlank(),
                wide = wideScreen,
                badge = preset.presetBadgeSpec(hubCachedVersions[preset.id])
                    ?.let { PresetCardBadge(it.icon, presetBadgeColor(it.tone)) },
                onClick = {
                    applyTarget = preset
                    applySheetShown = true
                },
            )
        }
        if (localFiltered.isNotEmpty() && otherFiltered.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        items(otherFiltered, key = { it.id }) { preset ->
            PresetConfigCard(
                preset = preset,
                showDownloads = true,
                wide = wideScreen,
                badge = preset.presetBadgeSpec(hubCachedVersions[preset.id])
                    ?.let { PresetCardBadge(it.icon, presetBadgeColor(it.tone)) },
                onClick = {
                    applyTarget = preset
                    applySheetShown = true
                },
            )
        }
    }

    NewPresetBottomSheet(
        show = showNewSheet,
        sectionTree = sectionTree,
        sectionCounts = sectionCounts,
        onDismiss = { showNewSheet = false },
        onSave = { title, content, author, selectedLeafIds ->
            val preset = ConfigPreset(
                id = UUID.randomUUID().toString(),
                title = title,
                content = content,
                author = author,
                downloads = 0L,
                local = true,
                createdAt = System.currentTimeMillis(),
                sections = PresetStore.snapshot(context, selectedLeafIds),
            )
            PresetStore.saveLocal(context, preset)
            reload++
            showNewSheet = false
        },
    )

    ResetConfigDialog(
        show = showResetDialog,
        sections = sectionTree,
        counts = sectionCounts,
        onDismiss = { showResetDialog = false },
        onApply = { selected ->
            PresetStore.reset(context, selected)
            showResetDialog = false
            scope.launch { snackbarState.showSnackbar(context.getString(R.string.preset_reset_success)) }
        },
    )

    applyTarget?.let { preset ->
        ApplyPresetBottomSheet(
            show = applySheetShown,
            preset = preset,
            onDismiss = { applySheetShown = false },
            onDismissFinished = {
                applyTarget = null
                // sheet 里可能刚缓存过云端正文（或反之），角标要跟着重算。
                hubCacheRevision++
                appliedCount?.let { count ->
                    appliedCount = null
                    scope.launch {
                        snackbarState.showSnackbar(context.getString(R.string.preset_applied_count, count))
                    }
                }
                sheetMessage?.let { message ->
                    sheetMessage = null
                    scope.launch { snackbarState.showSnackbar(message) }
                }
            },
            onDeleteConfirmed = {
                PresetStore.deleteLocal(context, preset.id)
                reload++
                applySheetShown = false
            },
            onModifyRequest = { target ->
                modifyTarget = target
                modifySheetShown = true
                applySheetShown = false
            },
            onCopy = { target ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(target.title, target.toJson().toString(2)),
                )
                // 复制后收起 sheet，并在页面底部提示。
                sheetMessage = context.getString(R.string.preset_copy_success)
                applySheetShown = false
            },
            onApply = { effective, selectedSectionIds ->
                PresetStore.apply(context, effective, selectedSectionIds)
                appliedCount = effective.sections
                    .filterKeys { it in selectedSectionIds }
                    .values
                    .sumOf { it.length() }
                applySheetShown = false
            },
            onSaveContent = { updated ->
                PresetStore.saveLocal(context, updated)
                reload++
            },
            onUploaded = { updated ->
                // 记下服务端签发的在线 id 与待审状态：下次点上传自动变成更新，卡片也会显示状态水印。
                PresetStore.saveLocal(context, updated)
                reload++
            },
            onHubDetached = { deadHubId ->
                // 云端那条已被移除：就地摘掉在线 id，把它降级成全新的本地配置。
                // 用 detachLocalHub 而不是 saveLocal——后者是新建语义，会把条目挪到列表最前。
                PresetStore.detachLocalHub(context, deadHubId)
                reload++
            },
            onSheetMessage = { message ->
                sheetMessage = message
                applySheetShown = false
            },
        )
    }

    // 「修改配置」：与新建配置同一套勾选界面，保存时用当前配置的实时值整体覆盖这条预设。
    modifyTarget?.let { target ->
        NewPresetBottomSheet(
            show = modifySheetShown,
            sectionTree = sectionTree,
            sectionCounts = sectionCounts,
            initial = target,
            titleRes = R.string.preset_modify_config,
            onDismiss = { modifySheetShown = false },
            onSave = { title, content, author, selectedLeafIds ->
                PresetStore.saveLocal(
                    context,
                    target.copy(
                        title = title,
                        content = content,
                        author = author,
                        // 重新从当前配置快照：选中的分节被整体覆盖，没选中的直接去掉。
                        sections = PresetStore.snapshot(context, selectedLeafIds),
                    ),
                )
                reload++
                modifySheetShown = false
                scope.launch {
                    snackbarState.showSnackbar(context.getString(R.string.preset_modify_success, title))
                }
            },
        )
    }
}

/**
 * 设置页顶部的预设入口卡片。使用 Miuix 默认（信息）卡片配色，不指定颜色。
 */
@Composable
internal fun PresetEntryCard(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        showIndication = true,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MiuixIcons.Help,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.body1,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(width = 10.dp, height = 16.dp),
            )
        }
    }
}

/**
 * 单个预设卡片：固定高度，标题、副标题、分割线、作者与下载量。
 *
 * [wide] 为 true 时（平板等宽屏，卡片比手机宽）再加高一行并让正文多显示一行。
 *
 * [badge] 非空时在**整张卡片的右下角**画一枚背景水印（[PresetCardWatermark]）：
 * 先画图标、正文压在其上，只表达状态，不参与布局。
 */
@Composable
internal fun PresetConfigCard(
    preset: ConfigPreset,
    showDownloads: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    badge: PresetCardBadge? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(if (wide) PresetCardHeight + PresetCardWideExtraHeight else PresetCardHeight),
        showIndication = true,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 水印先画、正文压在其上：位置固定在整张卡片的右下角，不进布局、不挤占正文。
            // 允许与底部的作者 / 下载量一行重叠——它是很淡的背景，不会盖住文字。
            badge?.let { PresetCardWatermark(icon = it.icon, tint = it.tint) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Text(
                    text = preset.title,
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = preset.content,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = if (wide) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PresetMetaItem(
                        icon = MiuixIcons.Contacts,
                        text = preset.author,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (showDownloads) {
                        Spacer(Modifier.width(8.dp))
                        PresetMetaItem(
                            icon = MiuixIcons.Download,
                            text = formatDownloadCount(preset.downloads),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 卡片背景水印：一个图标，铺在正文之下、贴在**整张卡片的右下角**。
 *
 * 刻意画得很大又很淡——它是背景而不是角标，正文压在上面必须照样清楚。
 * 复用方式就是 `PresetCardWatermark(icon = MiuixIcons.Xxx, tint = ...)`。
 */
@Composable
private fun BoxScope.PresetCardWatermark(
    icon: ImageVector,
    tint: Color,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(PresetBadgeSize),
    )
}

@Composable
private fun PresetMetaItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** bottom sheet 内容底部统一留出导航条（小白条）高度。 */
@Composable
private fun sheetBottomPadding(extra: Dp = 12.dp): Dp {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return extra + navBar
}

/**
 * 以下配色全部用 Miuix 语义 token，禁止硬编码，保证深色模式自动切换。
 *
 * 浅色：sheet = 淡灰（与默认按钮同色），卡片 / 输入框 / 普通按钮 = 白。
 * 深色：把两者互换，让 sheet 更暗、卡片更亮，符合深色层级。
 */
@Composable
private fun presetSheetColor(): Color =
    if (isSystemInDarkTheme()) MiuixTheme.colorScheme.surfaceContainer
    else MiuixTheme.colorScheme.secondaryVariant

@Composable
private fun presetSurfaceColor(): Color =
    if (isSystemInDarkTheme()) MiuixTheme.colorScheme.secondaryVariant
    else MiuixTheme.colorScheme.surfaceContainer

@Composable
private fun presetSurfaceContentColor(): Color =
    if (isSystemInDarkTheme()) MiuixTheme.colorScheme.onSecondaryVariant
    else MiuixTheme.colorScheme.onSurfaceContainer

@Composable
private fun presetCardColors() = CardDefaults.defaultColors(
    color = presetSurfaceColor(),
    contentColor = presetSurfaceContentColor(),
)

/**
 * sheet 内的普通按钮配色：用卡片同款的白 / 黑色（`surfaceContainer`），随深浅色主题自动切换。
 * 不要硬编码颜色，否则深色模式不会跟随。
 */
@Composable
private fun presetButtonColors() = ButtonDefaults.buttonColors(
    color = presetSurfaceColor(),
    contentColor = presetSurfaceContentColor(),
)

@Composable
private fun presetTextFieldColors() = TextFieldDefaults.textFieldColors(
    backgroundColor = presetSurfaceColor(),
    labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
)

/**
 * 通用确认弹窗：左侧「确认」、右侧「取消」，取消用强调色。
 *
 * 删除与覆盖上传都用它，只换标题与说明文案。刻意不强调「确认」，
 * 因为这两个操作都是不可逆的破坏性动作，把强调色留给「取消」更安全。
 *
 * 它由 bottom sheet 的调用方组合在 sheet 之外的同一层，因此是两个独立的
 * 窗口，直接叠在 sheet 之上，不需要嵌进 sheet 的 Dialog 内容里。
 */
@Composable
private fun PresetConfirmDialog(
    show: Boolean,
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = title,
        summary = message,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 刻意不传 colors：弹窗背景是 background（浅色为纯白），
            // 用 sheet 里的 presetButtonColors()（浅色也是白）会与背景糊成一片。
            // 默认按钮色 = secondaryVariant，浅 / 深色都能看清。
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.preset_confirm))
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

/**
 * 新建配置 bottom sheet：顶部配置信息（标题 / 内容 / 作者）+ 配置分类选择树。
 *
 * [initial] 非空即「修改配置」：标题 / 内容 / 作者与已包含的分节都预填该预设，
 * 保存时由调用方用**当前配置的实时值**重新快照选中的分节整体覆盖（而不是新建一条）。
 */
@Composable
private fun NewPresetBottomSheet(
    show: Boolean,
    sectionTree: List<ConfigSection>,
    sectionCounts: Map<String, Int>,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, author: String, selectedLeafIds: Set<String>) -> Unit,
    initial: ConfigPreset? = null,
    @StringRes titleRes: Int = R.string.preset_new_config,
) {
    var titleInput by remember(show, initial) { mutableStateOf(initial?.title.orEmpty()) }
    var contentInput by remember(show, initial) { mutableStateOf(initial?.content.orEmpty()) }
    var authorInput by remember(show, initial) { mutableStateOf(initial?.author.orEmpty()) }
    var selectedLeafIds by remember(show, initial) {
        mutableStateOf(initial?.sections?.keys?.toSet() ?: emptySet<String>())
    }

    val canSave = titleInput.isNotBlank() && selectedLeafIds.isNotEmpty()
    fun save() {
        onSave(titleInput.trim(), contentInput.trim(), authorInput.trim(), selectedLeafIds)
    }

    WindowBottomSheet(
        show = show,
        title = stringResource(titleRes),
        backgroundColor = presetSheetColor(),
        insideMargin = DpSize(12.dp, 0.dp),
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(MiuixIcons.Basic.Close, contentDescription = stringResource(R.string.close))
            }
        },
        endAction = {
            IconButton(onClick = ::save, enabled = canSave) {
                Icon(MiuixIcons.Basic.Check, contentDescription = stringResource(R.string.save))
            }
        },
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = sheetBottomPadding(16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SmallTitle(stringResource(R.string.preset_info_section))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextField(
                        value = titleInput,
                        onValueChange = { if (it.length <= PRESET_TITLE_MAX) titleInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.preset_field_title),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        colors = presetTextFieldColors(),
                    )
                    TextField(
                        value = contentInput,
                        onValueChange = { if (it.length <= PRESET_CONTENT_MAX) contentInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.preset_field_content),
                        useLabelAsPlaceholder = true,
                        singleLine = false,
                        maxLines = 3,
                        colors = presetTextFieldColors(),
                    )
                    TextField(
                        value = authorInput,
                        onValueChange = { if (it.length <= PRESET_AUTHOR_MAX) authorInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.preset_field_author),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        colors = presetTextFieldColors(),
                    )
                }
            }
            item {
                SmallTitle(stringResource(R.string.config))
                Card(modifier = Modifier.fillMaxWidth(), colors = presetCardColors()) {
                    ConfigSectionTree(
                        sections = sectionTree,
                        selectedLeafIds = selectedLeafIds,
                        onSelectedLeafIdsChange = { selectedLeafIds = it },
                        counts = sectionCounts,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = presetButtonColors(),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = ::save,
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

/**
 * 应用配置 bottom sheet：介绍 + 分节多选（显示条数）+ 底部操作。
 *
 * - 上「介绍」下「配置」，中间用分割线隔开；不显示作者。
 * - 本地预设有修改配置 / 编辑 / 复制 / 上传（或更新）/ 删除 / 应用；云端预设有查看 / 取消 / 应用。
 * - 云端预设打开时自动下载正文（详情接口会累加下载量），命中本地缓存则不下载。
 * - 「查看 / 编辑」进入配置内容视图，本地可改值并保存；「修改配置」用当前配置的值整体覆盖分节。
 * - 上传分两条路：从未上传过（[ConfigPreset.hubId] 为空）直接提交；上传过则先弹窗确认，
 *   确认后带 `configId` 走更新，服务端会把它重新打回待审。
 */
@Composable
private fun ApplyPresetBottomSheet(
    show: Boolean,
    preset: ConfigPreset,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onModifyRequest: (ConfigPreset) -> Unit,
    onCopy: (ConfigPreset) -> Unit,
    onApply: (ConfigPreset, Set<String>) -> Unit,
    onSaveContent: (ConfigPreset) -> Unit,
    onUploaded: (ConfigPreset) -> Unit,
    /** 云端那条已经不存在时回调，参数是失效的在线 id：由页面摘掉关联并刷新列表。 */
    onHubDetached: (String) -> Unit,
    onSheetMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val needsDownload = HubClient.isHubId(preset.id) && preset.sections.isEmpty()
    var resolved by remember(preset) { mutableStateOf<ConfigPreset?>(null) }
    var downloading by remember(preset) { mutableStateOf(needsDownload) }
    var downloadFailed by remember(preset) { mutableStateOf(false) }
    var downloadAttempt by remember(preset) { mutableIntStateOf(0) }

    // 本机缓存正文的版本号：只有它落后于列表版本时才提供手动「更新配置」入口，
    // 不再打开即自动替换（见 [hasUpdate] / updateContent）。
    var cachedVersion by remember(preset) { mutableStateOf("") }
    var updating by remember(preset) { mutableStateOf(false) }

    var uploading by remember(preset) { mutableStateOf(false) }

    // 云端在线 id：初始取本地记录，上传成功后立刻回填，同一次会话里再点上传即走更新。
    var hubId by remember(preset) { mutableStateOf(preset.hubId) }
    // 内容视图里保存过的版本。保存只写本地，不会回传给上层，所以这里自己记一份，
    // 否则「编辑后立刻上传」会上传编辑前的旧内容。
    var editedPreset by remember(preset) { mutableStateOf<ConfigPreset?>(null) }

    var showDeleteConfirm by remember(preset) { mutableStateOf(false) }
    var showUpdateConfirm by remember(preset) { mutableStateOf(false) }

    var view by remember(preset) { mutableStateOf(PresetSheetView.Detail) }
    var savedMessage by remember(preset) { mutableStateOf(false) }
    val draft = remember(preset) { mutableStateMapOf<String, String>() }
    // 选中 / 删除 / 正在全屏编辑的条目（draftRef）。
    var selectedEntry by remember(preset) { mutableStateOf<String?>(null) }
    var deletedKeys by remember(preset) { mutableStateOf(emptySet<String>()) }
    var editingEntry by remember(preset) { mutableStateOf<String?>(null) }
    var editingText by remember(preset) { mutableStateOf("") }

    LaunchedEffect(preset, downloadAttempt) {
        if (!needsDownload) return@LaunchedEffect
        // 命中本地缓存就不再联网，避免每次点进来都下载。缓存序列化放到 IO 线程。
        // 刻意不把列表版本传进去：缓存版本落后时保留旧正文，由用户点「更新配置」手动更新，
        // 而不是一打开 sheet 就被静默换成新版本。
        val cached = withContext(Dispatchers.IO) {
            PresetStore.cachedHubPreset(context, preset.id)
        }
        if (cached != null) {
            cachedVersion = cached.version
            resolved = cached
            downloading = false
            return@LaunchedEffect
        }
        downloading = true
        downloadFailed = false
        runCatching { HubClient.detail(preset) }
            .onSuccess { detail ->
                withContext(Dispatchers.IO) { PresetStore.cacheHubPreset(context, detail) }
                resolved = detail
            }
            .onFailure { downloadFailed = true }
        downloading = false
    }

    val effective = editedPreset ?: resolved ?: preset
    // 空 JSON / 空配置的分节直接隐藏，不参与展示与应用。
    val includedIds = remember(effective) {
        effective.sections.filterValues { it.length() > 0 }.keys.toList()
    }
    var selectedIds by remember(effective) { mutableStateOf(includedIds.toSet()) }
    // 应用配置也复用新建配置的可展开树，通知 / Toast 的应用级叶子挂在对应分组下。
    val sectionTree by produceState(emptyList<ConfigSection>(), includedIds) {
        value = withContext(Dispatchers.IO) {
            PresetStore.presetSectionTree(context, includedIds.toSet())
        }
    }
    val sectionCounts = remember(effective) {
        effective.sections.mapValues { (id, values) -> appSectionCount(id, values) }
    }
    // 云端列表 / 详情返回的是 ISO8601 字符串，HubClient 已转为 epoch 毫秒；这里再转本地可读时间。
    val createdLabel = remember(preset.createdAt) { formatPresetTime(preset.createdAt) }
    // 本机缓存正文落后于云端列表版本：只在详情里给一个手动更新入口，
    // 不再自动把缓存换成新版本（用户可能就是想沿用旧配置）。
    val hasUpdate = !preset.local && cachedVersion.isNotBlank() &&
        isRemoteVersionNewer(preset.version, cachedVersion)

    fun openContent() {
        draft.clear()
        effective.sections.forEach { (sectionId, values) ->
            values.keys().forEach { key ->
                draft[draftKey(sectionId, key)] = displayValue(values.opt(key))
            }
        }
        savedMessage = false
        view = PresetSheetView.Content
    }

    /**
     * 手动把云端预设更新到列表里的最新版本：重新下载正文并覆盖本地缓存。
     *
     * 缓存版本落后时不再自动更新（见 [hasUpdate]），必须由用户点「更新配置(旧→新)」触发，
     * 避免只想看一眼旧配置却被静默换成新版本。
     */
    fun updateContent() {
        if (updating) return
        scope.launch {
            updating = true
            runCatching { HubClient.detail(preset) }
                .onSuccess { detail ->
                    withContext(Dispatchers.IO) { PresetStore.cacheHubPreset(context, detail) }
                    resolved = detail
                    cachedVersion = detail.version
                    editedPreset = null
                    downloadFailed = false
                }
                .onFailure { onSheetMessage(context.getString(R.string.preset_update_failed)) }
            updating = false
        }
    }

    /**
     * 提交上传。[hubId] 为空走新建，否则带 `configId` 走更新（服务端会重新打回待审）。
     *
     * 成功后把服务端签发的 id 回填进本地预设，下次点上传就自动变成更新。
     */
    fun submitUpload() {
        if (uploading) return
        scope.launch {
            uploading = true
            val deviceId = withContext(Dispatchers.IO) { PresetStore.deviceId(context) }
            val target = hubId.takeIf { it.isNotBlank() }
            val message = runCatching {
                HubClient.upload(effective, deviceId, target)
            }.fold(
                onSuccess = { response ->
                    response.optString("id").takeIf { it.isNotBlank() }?.let { onlineId ->
                        // 保留当前（可能已编辑过的）内容，只补上在线 id、服务端定下的版本号与待审状态：
                        // 无论新建还是更新，服务端都会重新审核，所以卡片随即显示「审核中」水印，
                        // 上一次的拒绝原因也一并清掉（服务端已作废那次审核结论）。
                        // 下载量原样带过去 —— 更新只是让这条配置回到待审，历史累计并没有清零，
                        // 服务端也照旧认它（见 api/my-configs/[id].js）。
                        val updated = effective.copy(
                            hubId = onlineId,
                            version = response.optString("version").ifBlank { effective.version },
                            hubStatus = HUB_STATUS_PENDING,
                            hubRejectReason = "",
                        )
                        hubId = onlineId
                        editedPreset = updated
                        // 作废该 id 的查询节流，让随后的刷新立刻能拿到最新状态。
                        PresetHubStatusState.forget(onlineId)
                        onUploaded(updated)
                    }
                    context.getString(
                        if (target != null) R.string.preset_update_success else R.string.preset_upload_success,
                    )
                },
                onFailure = { error ->
                    // 404：云端这条已经不存在了（多半被管理员删掉）。本地记的在线 id 从此是死链——
                    // 留着它，卡片会一直顶着云朵 / 审核中水印，而且每次点上传都会拿它去更新、必然失败。
                    // 于是就地摘掉这层关联，降级回一条全新的本地配置，下次上传即走新建。
                    if (target != null && error.isGoneOnHub()) {
                        val detached = effective.copy(
                            hubId = "",
                            hubStatus = "",
                            hubRejectReason = "",
                            version = "",
                            downloads = 0L,
                        )
                        hubId = ""
                        editedPreset = detached
                        // 该 id 已作废，顺手丢掉它的查询节流记录。
                        PresetHubStatusState.forget(target)
                        onHubDetached(target)
                        context.getString(R.string.preset_update_removed)
                    } else {
                        uploadErrorMessage(context, error)
                    }
                },
            )
            uploading = false
            onSheetMessage(message)
        }
    }

    WindowBottomSheet(
        show = show,
        title = preset.title,
        backgroundColor = presetSheetColor(),
        insideMargin = DpSize(12.dp, 0.dp),
        startAction = {
            // 仅本地预设显示左上角关闭图标。
            if (preset.local) {
                IconButton(onClick = onDismiss) {
                    Icon(MiuixIcons.Basic.Close, contentDescription = stringResource(R.string.close))
                }
            }
        },
        // 本地预设：右上角为 error 色删除图标（删除按钮从底部移到这里）；仅在详情视图显示。
        // 删除必须先过一次确认弹窗，避免误触后连云端那条也再也改不动。
        endAction = {
            if (preset.local && view == PresetSheetView.Detail) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = stringResource(R.string.preset_delete),
                        tint = MiuixTheme.colorScheme.error,
                    )
                }
            }
        },
        // 非详情视图下返回 / 点击外部逐级返回，而不是直接关闭整个 bottom sheet。
        onDismissRequest = {
            if (view == PresetSheetView.Detail) onDismiss() else view = PresetSheetView.Detail
        },
        onDismissFinished = onDismissFinished,
    ) {
        // 预测性返回手势会先让 sheet 自身滑出，无法通过 onDismissRequest 拦截；
        // 这里在非详情视图时抢先注册返回处理器，使返回逐级回退。
        ContentBackInterceptor(enabled = view != PresetSheetView.Detail) {
            view = if (view == PresetSheetView.Editor) PresetSheetView.Content else PresetSheetView.Detail
        }
        AnimatedContent(
            targetState = view,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally { it } + fadeIn(tween(300, easing = FastOutSlowInEasing))) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut(tween(200, easing = FastOutSlowInEasing)))
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn(tween(300, easing = FastOutSlowInEasing))) togetherWith
                        (slideOutHorizontally { it } + fadeOut(tween(200, easing = FastOutSlowInEasing)))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "presetSheetContent",
        ) { targetView ->
            when (targetView) {
                PresetSheetView.Editor -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = sheetBottomPadding(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        TextField(
                            value = editingText,
                            onValueChange = { editingText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            label = editingEntry?.substringAfter('|').orEmpty(),
                            useLabelAsPlaceholder = true,
                            singleLine = false,
                            minLines = 2,
                            colors = presetTextFieldColors(),
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = {
                                    view = PresetSheetView.Content
                                    editingEntry = null
                                },
                                modifier = Modifier.weight(1f),
                                colors = presetButtonColors(),
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                            Button(
                                onClick = {
                                    editingEntry?.let { ref -> draft[ref] = editingText }
                                    view = PresetSheetView.Content
                                    editingEntry = null
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                Text(stringResource(R.string.save))
                            }
                        }
                    }
                }
                PresetSheetView.Content -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = sheetBottomPadding(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    effective.sections.filterValues { it.length() > 0 }.forEach { (sectionId, values) ->
                        item(key = "title_$sectionId") {
                            // 与 SmallTitle 同样的字号 / 颜色，仅改为居中。
                            Text(
                                text = configSectionTitle(sectionId),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp, vertical = 8.dp),
                                style = MiuixTheme.textStyles.subtitle,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        item(key = "card_$sectionId") {
                            Card(modifier = Modifier.fillMaxWidth(), colors = presetCardColors()) {
                                Column {
                                    values.keys().forEach { key ->
                                        val draftRef = draftKey(sectionId, key)
                                        if (draftRef in deletedKeys) return@forEach
                                        key(draftRef) {
                                            ContentEntryRow(
                                                entryKey = key,
                                                value = draft[draftRef] ?: displayValue(values.opt(key)),
                                                editable = effective.local,
                                                onSelect = { selectedEntry = draftRef },
                                                onValueChange = { draft[draftRef] = it },
                                            )
                                            AnimatedVisibility(
                                                visible = effective.local && selectedEntry == draftRef,
                                                enter = fadeIn() + expandVertically(),
                                                exit = fadeOut() + shrinkVertically(),
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                ) {
                                                    // 浅色沿用 TextButton 默认的 secondaryVariant，深色改用 sheet 背景色，
                                                    // 两种主题下都与卡片形成对比。
                                                    Button(
                                                        onClick = {
                                                            deletedKeys = deletedKeys + draftRef
                                                            selectedEntry = null
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        colors = ButtonDefaults.buttonColors(
                                                            color = presetSheetColor(),
                                                            contentColor = MiuixTheme.colorScheme.error,
                                                        ),
                                                    ) {
                                                        Text(stringResource(R.string.preset_delete))
                                                    }
                                                    Button(
                                                        onClick = {
                                                            editingEntry = draftRef
                                                            editingText = prettyJson(
                                                                draft[draftRef] ?: displayValue(values.opt(key)),
                                                            )
                                                            view = PresetSheetView.Editor
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        colors = ButtonDefaults.buttonColors(
                                                            color = presetSheetColor(),
                                                            contentColor = MiuixTheme.colorScheme.onBackground,
                                                        ),
                                                    ) {
                                                        Text(stringResource(R.string.preset_edit))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            savedMessage.takeIf { it }?.let {
                                Text(
                                    text = stringResource(R.string.preset_saved),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Button(
                                    onClick = { view = PresetSheetView.Detail },
                                    modifier = Modifier.weight(1f),
                                    colors = presetButtonColors(),
                                ) {
                                    Text(stringResource(R.string.back))
                                }
                                if (effective.local) {
                                    Button(
                                        onClick = {
                                            // 保存只落到本地；同时在 sheet 内留一份，
                                            // 保证紧接着点「上传」传的是编辑后的内容。
                                            val updated = buildEdited(effective, draft, deletedKeys)
                                            editedPreset = updated
                                            onSaveContent(updated)
                                            savedMessage = true
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColorsPrimary(),
                                    ) {
                                        Text(stringResource(R.string.save))
                                    }
                                }
                            }
                        }
                    }
                }
                PresetSheetView.Detail -> {
                    // 详情卡片的条目：左列标签、右列内容、可选的强调色（只有状态 / 原因需要着色）。
                    // 顺序固定为「介绍 → 时间 → 版本 → 状态 → 原因」，缺失的条目直接不进列表。
                    val detailRows = ArrayList<Triple<String, String, Color?>>()
                    if (preset.content.isNotBlank()) {
                        detailRows += Triple(
                            stringResource(R.string.preset_field_description),
                            preset.content,
                            null,
                        )
                    }
                    if (createdLabel.isNotBlank()) {
                        detailRows += Triple(stringResource(R.string.preset_field_time), createdLabel, null)
                    }
                    // 版本独立成一行，紧跟在时间下面；从未上传过的本地预设没有服务端版本，
                    // 按「新建即 1」的约定显示 1。
                    detailRows += Triple(
                        stringResource(R.string.preset_field_version),
                        effective.version.ifBlank { "1" },
                        null,
                    )
                    // 状态只表示审核结论，版本已经单独成行，这里不再重复拼括号。
                    if (preset.local) {
                        val statusText = if (preset.hubId.isBlank()) {
                            stringResource(R.string.preset_status_not_uploaded)
                        } else {
                            when (preset.hubStatus) {
                                HUB_STATUS_PENDING -> stringResource(R.string.preset_status_pending)
                                HUB_STATUS_PUBLISHED -> stringResource(R.string.preset_status_published)
                                HUB_STATUS_REJECTED -> stringResource(R.string.preset_status_rejected)
                                else -> stringResource(R.string.preset_status_unknown)
                            }
                        }
                        val statusTint = when (preset.hubStatus) {
                            HUB_STATUS_PENDING -> presetWarningTint()
                            HUB_STATUS_REJECTED -> MiuixTheme.colorScheme.error
                            else -> null
                        }
                        detailRows += Triple(
                            stringResource(R.string.preset_field_status),
                            statusText,
                            statusTint,
                        )
                        // 拒绝理由：只有「审核失败」且后台确实填了才显示，没填就整行不出现。
                        if (preset.hubStatus == HUB_STATUS_REJECTED && preset.hubRejectReason.isNotBlank()) {
                            detailRows += Triple(
                                stringResource(R.string.preset_field_reject_reason),
                                preset.hubRejectReason,
                                MiuixTheme.colorScheme.error,
                            )
                        }
                    }

                    // 每行整体排版：左侧标签定宽、右侧内容占剩余宽度。
                    // 不能把两侧拆成两列各自 `Arrangement.spacedBy` —— 右侧内容折成多行时两边行高不同，
                    // 下一行的标签会被顶偏（就是「右侧多行时左右错行」）。
                    // 标签宽度取「最宽标签的实测值」，各行内容因此都从同一列起。
                    val detailLabelStyle = MiuixTheme.textStyles.body1
                    val textMeasurer = rememberTextMeasurer()
                    val density = LocalDensity.current
                    val detailLabelWidth = remember(
                        detailRows.joinToString("|") { it.first },
                        detailLabelStyle,
                        density,
                    ) {
                        val widest = detailRows.maxOfOrNull { row ->
                            textMeasurer.measure("${row.first}:", detailLabelStyle).size.width
                        } ?: 0
                        // 留 2dp 余量：测量是精确值，浮点取整后不留缝可能让标签被迫折行。
                        with(density) { widest.toDp() } + 2.dp
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 600.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = sheetBottomPadding(16.dp)),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), colors = presetCardColors()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    detailRows.forEach { (label, value, tint) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Text(
                                                text = "$label:",
                                                modifier = Modifier.width(detailLabelWidth),
                                                style = detailLabelStyle,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                maxLines = 1,
                                            )
                                            Spacer(Modifier.width(16.dp))
                                            Text(
                                                text = value,
                                                modifier = Modifier.weight(1f),
                                                style = detailLabelStyle,
                                                color = tint ?: MiuixTheme.colorScheme.onSurfaceContainer,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        when {
                        downloading -> item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.preset_downloading),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }

                        downloadFailed -> item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.preset_download_failed),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.error,
                                )
                                Button(
                                    onClick = { downloadAttempt++ },
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                ) {
                                    Text(stringResource(R.string.preset_retry))
                                }
                            }
                        }

                        else -> {
                            if (sectionTree.isNotEmpty()) {
                                item {
                                    Card(modifier = Modifier.fillMaxWidth(), colors = presetCardColors()) {
                                        ConfigSectionTree(
                                            sections = sectionTree,
                                            selectedLeafIds = selectedIds,
                                            onSelectedLeafIdsChange = { selectedIds = it },
                                            counts = sectionCounts,
                                        )
                                    }
                                }
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // 缓存正文落后于云端版本：把「自动更新」改成显式手动更新。
                                    // 大按钮压在下面 4 个操作按钮之上，文案形如「更新配置(1→2)」。
                                    if (hasUpdate) {
                                        Button(
                                            onClick = ::updateContent,
                                            enabled = !updating,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColorsPrimary(),
                                        ) {
                                            if (updating) {
                                                Text(stringResource(R.string.preset_downloading))
                                            } else {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                ) {
                                                    Text(stringResource(R.string.preset_update_config))
                                                    Text("($cachedVersion")
                                                    Icon(
                                                        imageVector = MiuixIcons.Basic.ArrowRight,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(width = 12.dp, height = 12.dp),
                                                    )
                                                    Text("${preset.version})")
                                                }
                                            }
                                        }
                                    }
                                    // 「修改配置」：打开新建配置那套勾选界面，用当前配置的实时值
                                    // 整体覆盖这条预设包含的分节。只对本地预设开放。
                                    if (preset.local) {
                                        Button(
                                            onClick = { onModifyRequest(effective) },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = presetButtonColors(),
                                        ) {
                                            Text(stringResource(R.string.preset_modify_config))
                                        }
                                    }
                                    // 配置有效时才提供查看 / 编辑与复制（复制便于剪贴板导出）。
                                    if (includedIds.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Button(
                                                onClick = ::openContent,
                                                modifier = Modifier.weight(1f),
                                                colors = presetButtonColors(),
                                            ) {
                                                Text(
                                                    stringResource(
                                                        if (preset.local) R.string.preset_edit else R.string.preset_view,
                                                    ),
                                                )
                                            }
                                            Button(
                                                onClick = { onCopy(effective) },
                                                modifier = Modifier.weight(1f),
                                                colors = presetButtonColors(),
                                            ) {
                                                Text(stringResource(R.string.preset_copy))
                                            }
                                        }
                                    }
                                    if (preset.local) {
                                        // 删除改为右上角图标；上传下移到原删除位置。
                                        // 已上传过的先弹窗确认，再走更新（服务端会重新打回待审）。
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Button(
                                                onClick = {
                                                    if (hubId.isBlank()) submitUpload() else showUpdateConfirm = true
                                                },
                                                enabled = !uploading,
                                                modifier = Modifier.weight(1f),
                                                colors = presetButtonColors(),
                                            ) {
                                                Text(
                                                    if (uploading) {
                                                        stringResource(R.string.preset_uploading)
                                                    } else {
                                                        stringResource(R.string.preset_upload)
                                                    },
                                                )
                                            }
                                            Button(
                                                onClick = { onApply(effective, selectedIds) },
                                                enabled = selectedIds.isNotEmpty(),
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColorsPrimary(),
                                            ) {
                                                Text(stringResource(R.string.preset_apply))
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Button(
                                                onClick = onDismiss,
                                                modifier = Modifier.weight(1f),
                                                colors = presetButtonColors(),
                                            ) {
                                                Text(stringResource(R.string.cancel))
                                            }
                                            Button(
                                                onClick = { onApply(effective, selectedIds) },
                                                enabled = selectedIds.isNotEmpty(),
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColorsPrimary(),
                                            ) {
                                                Text(stringResource(R.string.preset_apply))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
    }

    // 两个确认弹窗与 bottom sheet 处于同一层（不是嵌进 sheet 的 Dialog 内容里），
    // 所以它们各自是独立窗口，直接叠在 sheet 之上。
    PresetConfirmDialog(
        show = showDeleteConfirm,
        title = stringResource(R.string.preset_confirm_delete_title),
        message = stringResource(R.string.preset_confirm_delete_message),
        onConfirm = {
            showDeleteConfirm = false
            onDeleteConfirmed()
        },
        onDismiss = { showDeleteConfirm = false },
    )

    PresetConfirmDialog(
        show = showUpdateConfirm,
        title = stringResource(R.string.preset_confirm_update_title),
        message = stringResource(R.string.preset_confirm_update_message),
        onConfirm = {
            showUpdateConfirm = false
            submitUpload()
        },
        onDismiss = { showUpdateConfirm = false },
    )
}

/** 搜索匹配：标题 / 说明 / 作者任一包含关键字即命中（忽略大小写）。 */
private fun ConfigPreset.matchesQuery(keyword: String): Boolean =
    title.contains(keyword, ignoreCase = true) ||
        content.contains(keyword, ignoreCase = true) ||
        author.contains(keyword, ignoreCase = true)

/** 把 epoch 毫秒转成本地可读时间；解析失败或未设置时返回空串。 */
private fun formatPresetTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return runCatching {
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrDefault("")
}

/**
 * 更新 / 自查时服务端答「这条配置不存在」（`404 not_found`）——通常是被管理员在云端删掉了。
 *
 * 只认服务端下发的 `error` 码而不看 HTTP 状态：平台自己也会对未知路径回 404，但正文不是
 * 我们这套 JSON 信封（`error` 为空），那种情况绝不能当成「配置被移除」去摘本地关联。
 * 与 403 `not_owner`（配置还在，只是归属别的设备）区分开：只有前者能降级成新的本地配置。
 */
private fun Throwable.isGoneOnHub(): Boolean =
    this is HubException && error == "not_found"

private fun uploadErrorMessage(context: Context, error: Throwable): String = when {
    error is HubException && error.code == 429 -> {
        val detail = error.detail as? JSONObject
        context.getString(
            R.string.preset_upload_quota,
            detail?.optInt("used") ?: 0,
            detail?.optInt("limit") ?: 0,
        )
    }
    error is HubException && error.code == 422 && error.detail is String -> error.detail
    // 409：同一来源已经有内容完全相同的配置，服务端会给出可直接展示的中文说明。
    error is HubException && error.code == 409 -> {
        val message = (error.detail as? JSONObject)?.optString("message").orEmpty()
        message.ifBlank { context.getString(R.string.preset_upload_duplicate) }
    }
    // 403：配置还在，但不属于这台设备（本地记的归属已经对不上了）。
    error is HubException && error.error == "not_owner" ->
        context.getString(R.string.preset_update_not_owner)
    // 404：云端这条已经不存在了。提交路径上已就地摘掉关联，这里是兜底文案。
    error is HubException && error.isGoneOnHub() ->
        context.getString(R.string.preset_update_removed)
    else -> context.getString(R.string.preset_upload_failed)
}

/** 草稿 key：sectionId + 配置键。 */
private fun draftKey(sectionId: String, key: String): String = "$sectionId|$key"

private fun displayValue(value: Any?): String = when (value) {
    null, JSONObject.NULL -> ""
    is String -> value
    else -> value.toString()
}

/** 按原类型把编辑后的文本还原成配置值，避免把布尔 / 数字写成字符串。 */
private fun coerceValue(original: Any?, text: String): Any = when (original) {
    is Boolean -> text.trim().equals("true", ignoreCase = true)
    is Int -> text.trim().toIntOrNull() ?: original
    is Long -> text.trim().toLongOrNull() ?: original
    is Double -> text.trim().toDoubleOrNull() ?: original
    is Float -> text.trim().toFloatOrNull() ?: original
    // 应用级叶子（通知 / Toast）以子对象 JSON 形式存放，编辑后需解析回 JSON。
    is JSONObject -> runCatching { JSONObject(text) }.getOrDefault(original)
    else -> text
}

/** 应用级叶子不显示「xx 条配置」，只显示应用名。 */
private fun appSectionCount(id: String, values: JSONObject?): Int =
    if (parseAppConfigLeafId(id) != null) 0 else values?.length() ?: 0

/** 分节标题：应用级叶子显示为「通知 / Toast · 应用名」，其余用注册表标题。 */
@Composable
private fun configSectionTitle(id: String): String {
    val context = LocalContext.current
    parseAppConfigLeafId(id)?.let { (kind, packageName) ->
        val label = remember(packageName) { PresetStore.appLabel(context, packageName) }
        return stringResource(kind.titleRes) + " · " + label
    }
    return findConfigSection(id)?.let { stringResource(it.titleRes) } ?: id
}

private fun buildEdited(
    base: ConfigPreset,
    draft: Map<String, String>,
    deletedKeys: Set<String> = emptySet(),
): ConfigPreset {
    val sections = LinkedHashMap<String, JSONObject>()
    base.sections.forEach { (sectionId, values) ->
        val edited = JSONObject()
        values.keys().forEach { key ->
            val draftRef = draftKey(sectionId, key)
            if (draftRef in deletedKeys) return@forEach
            val original = values.opt(key)
            val text = draft[draftRef] ?: displayValue(original)
            edited.put(key, coerceValue(original, text))
        }
        if (edited.length() > 0) sections[sectionId] = edited
    }
    return base.copy(sections = sections)
}

/** 尽量把值格式化成可读 JSON；不是 JSON 时原样返回。 */
private fun prettyJson(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return text
    return runCatching {
        when {
            trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
            trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
            else -> text
        }
    }.getOrDefault(text)
}

/**
 * 内容视图激活时注册导航事件返回处理器，抢在 [WindowBottomSheet] 自身的返回处理之前接管返回。
 *
 * WindowBottomSheet 的预测性返回会先把 sheet 滑出视口再回调 onDismissRequest，
 * 因此只能在导航事件层面提前拦截，才能实现逐级返回详情。
 */
@Composable
private fun ContentBackInterceptor(enabled: Boolean, onBack: () -> Unit) {
    val view = LocalView.current
    val currentOnBack by rememberUpdatedState(onBack)
    val dispatcher = remember(view) {
        view.findViewTreeNavigationEventDispatcherOwner()?.navigationEventDispatcher
    }
    // 订阅后只注册一次；用 isBackEnabled 控制是否抢占返回，避免无法从 dispatcher 注销。
    val handler = remember(dispatcher) {
        dispatcher?.let {
            object : NavigationEventHandler<NavigationEventInfo>(
                initialInfo = NavigationEventInfo.None,
                isBackEnabled = enabled,
            ) {
                override fun onBackCompleted() {
                    currentOnBack()
                }
            }
        }
    }
    DisposableEffect(dispatcher, handler) {
        // 用 OVERLAY 优先级抢在 bottom sheet 自身的默认返回处理之前。
        if (dispatcher != null && handler != null) {
            dispatcher.addHandler(handler, NavigationEventDispatcher.PRIORITY_OVERLAY)
        }
        onDispose { handler?.remove() }
    }
    SideEffect {
        handler?.isBackEnabled = enabled
    }
}

@Composable
private fun ContentEntryRow(
    entryKey: String,
    value: String,
    editable: Boolean,
    onSelect: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = entryKey,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(4.dp))
        if (editable) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) onSelect() },
                singleLine = true,
                colors = TextFieldDefaults.textFieldColors(
                    // 与 sheet 背景同色，才能在白色 / 黑色卡片上形成对比（深色模式尤其明显）。
                    backgroundColor = presetSheetColor(),
                    labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                ),
            )
        } else {
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
            )
        }
    }
}

private fun formatDownloadCount(count: Long): String =
    if (count < 1000) {
        count.toString()
    } else {
        String.format(java.util.Locale.ROOT, "%.1fk", count / 1000.0)
    }
