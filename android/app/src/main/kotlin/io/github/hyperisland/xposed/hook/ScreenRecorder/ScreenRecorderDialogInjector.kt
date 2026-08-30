package io.github.hyperisland.xposed.hook.ScreenRecorder

import android.app.Dialog
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.setViewTreeNavigationEventDispatcherOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.hyperisland.screenrecorder.RecorderSnapshot
import io.github.hyperisland.screenrecorder.ScreenRecorderContract
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

internal object RecorderOverlayGate {
    @Volatile
    private var allowedUntil = 0L

    fun open(durationMillis: Long = 750L) {
        allowedUntil = SystemClock.uptimeMillis() + durationMillis
    }

    fun close() {
        allowedUntil = 0L
    }

    fun allows(view: View?): Boolean =
        SystemClock.uptimeMillis() <= allowedUntil &&
            view?.javaClass?.name == "com.android.internal.policy.DecorView"
}

internal object ScreenRecorderDialogInjector {
    fun show(
        context: Context,
        resolutions: List<Pair<String, String>>,
        sounds: List<Pair<String, Int>>,
        initialResolution: String,
        initialSound: Int,
        recordingSnapshot: RecorderSnapshot?,
        onCancel: () -> Unit,
        onOpenSettings: () -> Unit,
        onStart: (resolution: String, sound: Int) -> Unit,
        onPause: () -> Unit,
        onResume: () -> Unit,
        onStop: () -> Unit,
    ) {
        val hostDialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        var animatedBackHandler: (() -> Unit)? = null
        val viewTreeOwner = InjectedViewTreeOwner {
            val handler = animatedBackHandler
            if (handler != null) {
                handler()
            } else if (hostDialog.isShowing) {
                hostDialog.cancel()
            }
        }
        lateinit var host: ComposeView

        fun dismissHost() {
            if (hostDialog.isShowing) hostDialog.dismiss()
        }

        host = ComposeView(hostDialog.context).apply {
            setViewTreeLifecycleOwner(viewTreeOwner)
            setViewTreeSavedStateRegistryOwner(viewTreeOwner)
            setViewTreeViewModelStoreOwner(viewTreeOwner)
            setViewTreeNavigationEventDispatcherOwner(viewTreeOwner)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewTreeOwner.lifecycle),
            )
            setContent {
                val controller = remember {
                    ThemeController(colorSchemeMode = ColorSchemeMode.System)
                }
                MiuixTheme(controller = controller) {
                    RecorderWindowDialog(
                        resolutions = resolutions,
                        sounds = sounds,
                        initialResolution = initialResolution,
                        initialSound = initialSound,
                        recordingSnapshot = recordingSnapshot,
                        onCancel = {
                            dismissHost()
                            onCancel()
                        },
                        onOpenSettings = {
                            dismissHost()
                            onOpenSettings()
                        },
                        onStart = { resolution, sound ->
                            dismissHost()
                            onStart(resolution, sound)
                        },
                        onPause = onPause,
                        onResume = onResume,
                        onStop = {
                            dismissHost()
                            onStop()
                        },
                        onBackHandlerChanged = { animatedBackHandler = it },
                    )
                }
            }
        }
        hostDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        hostDialog.setContentView(
            host,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        hostDialog.setCanceledOnTouchOutside(false)
        hostDialog.setOnCancelListener { onCancel() }
        hostDialog.setOnDismissListener {
            viewTreeOwner.destroy()
            host.disposeComposition()
        }
        hostDialog.window?.apply {
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            WindowCompat.setDecorFitsSystemWindows(this, false)
            statusBarColor = AndroidColor.TRANSPARENT
            navigationBarColor = AndroidColor.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
                isStatusBarContrastEnforced = false
            }
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            attributes = attributes.apply {
                dimAmount = 0.22f
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        viewTreeOwner.resume()
        RecorderOverlayGate.open()
        hostDialog.show()
        viewTreeOwner.attachBackInput(hostDialog.onBackInvokedDispatcher)
        hostDialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
        Handler(Looper.getMainLooper()).postDelayed(RecorderOverlayGate::close, 750L)
    }
}

private class InjectedViewTreeOwner(
    private val onBackRequested: () -> Unit,
) :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner,
    NavigationEventDispatcherOwner {

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val models = ViewModelStore()

    override val navigationEventDispatcher = NavigationEventDispatcher()
    private var backInput: OnBackInvokedDefaultInput? = null
    private val rootBackHandler = object : NavigationEventHandler<NavigationEventInfo>(
        initialInfo = NavigationEventInfo.None,
        isBackEnabled = true,
    ) {
        override fun onBackCompleted() {
            onBackRequested()
        }
    }

    init {
        navigationEventDispatcher.addHandler(rootBackHandler)
    }

    override val lifecycle: Lifecycle
        get() = registry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = models

    fun resume() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun attachBackInput(dispatcher: android.window.OnBackInvokedDispatcher) {
        if (backInput != null) return
        backInput = OnBackInvokedDefaultInput(dispatcher).also {
            navigationEventDispatcher.addInput(it)
        }
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            navigationEventDispatcher.dispose()
            backInput = null
            models.clear()
        }
    }
}

@Composable
private fun RecorderWindowDialog(
    resolutions: List<Pair<String, String>>,
    sounds: List<Pair<String, Int>>,
    initialResolution: String,
    initialSound: Int,
    recordingSnapshot: RecorderSnapshot?,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    onStart: (resolution: String, sound: Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onBackHandlerChanged: ((() -> Unit)?) -> Unit,
) {
    val safeResolutions = resolutions.ifEmpty { listOf("当前设置" to initialResolution) }
    val safeSounds = sounds.ifEmpty {
        listOf("无声" to 0, "麦克风" to 1, "设备声音" to 2)
    }
    val itemInsideMargin = PaddingValues(horizontal = 28.dp, vertical = 16.dp)
    var resolutionIndex by remember(safeResolutions, initialResolution) {
        mutableIntStateOf(
            safeResolutions.indexOfFirst { it.second == initialResolution }.coerceAtLeast(0),
        )
    }
    var soundIndex by remember(safeSounds, initialSound) {
        mutableIntStateOf(safeSounds.indexOfFirst { it.second == initialSound }.coerceAtLeast(0))
    }
    val panelProgress = remember { Animatable(1f) }
    val animationScope = rememberCoroutineScope()
    var panelHeight by remember { mutableIntStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    var liveRecorderSnapshot by remember(recordingSnapshot) {
        mutableStateOf(recordingSnapshot)
    }
    val outsideInteractionSource = remember { MutableInteractionSource() }
    val panelInteractionSource = remember { MutableInteractionSource() }
    var nowElapsed by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    DisposableEffect(recordingSnapshot) {
        val removeObserver = if (recordingSnapshot != null) {
            ScreenRecorderControlClient.observe { liveRecorderSnapshot = it }
        } else {
            null
        }
        onDispose { removeObserver?.invoke() }
    }

    LaunchedEffect(liveRecorderSnapshot?.state) {
        while (liveRecorderSnapshot?.isSessionActive == true) {
            nowElapsed = SystemClock.elapsedRealtime()
            delay(250L)
        }
    }

    LaunchedEffect(panelHeight) {
        if (panelHeight > 0 && !closing) {
            panelProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            )
        }
    }

    fun closeAfterAnimation(action: () -> Unit) {
        if (closing) return
        closing = true
        animationScope.launch {
            panelProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
            action()
        }
    }

    LaunchedEffect(recordingSnapshot, liveRecorderSnapshot?.state) {
        if (
            recordingSnapshot != null &&
            liveRecorderSnapshot?.state == ScreenRecorderContract.STATE_IDLE
        ) {
            closeAfterAnimation(onCancel)
        }
    }

    DisposableEffect(onCancel, onBackHandlerChanged) {
        onBackHandlerChanged { closeAfterAnimation(onCancel) }
        onDispose { onBackHandlerChanged(null) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = outsideInteractionSource,
                        indication = null,
                        onClick = { closeAfterAnimation(onCancel) },
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { panelHeight = it.height }
                        .graphicsLayer {
                            translationY = panelHeight * panelProgress.value
                        }
                        .alpha(1f - panelProgress.value)
                        .clickable(
                            interactionSource = panelInteractionSource,
                            indication = null,
                            onClick = {},
                        ),
                    cornerRadius = 32.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = when {
                                recordingSnapshot == null -> "屏幕录制"
                                liveRecorderSnapshot?.state ==
                                    ScreenRecorderContract.STATE_STARTING -> "准备录制"
                                liveRecorderSnapshot?.state ==
                                    ScreenRecorderContract.STATE_PAUSED -> "已暂停"
                                else -> "录制中"
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            style = MiuixTheme.textStyles.headline1,
                            fontSize = (MiuixTheme.textStyles.headline1.fontSize.value + 1f).sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        if (recordingSnapshot != null) {
                            val snapshot = liveRecorderSnapshot ?: recordingSnapshot
                            Text(
                                text =
                                    "已录制 ${formatRecorderDuration(snapshot.durationAt(nowElapsed))}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp, vertical = 12.dp),
                                style = MiuixTheme.textStyles.body1,
                                textAlign = TextAlign.Center,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TextButton(
                                    text = when (snapshot.state) {
                                        ScreenRecorderContract.STATE_STARTING -> "准备中"
                                        ScreenRecorderContract.STATE_PAUSED -> "继续"
                                        else -> "暂停"
                                    },
                                    onClick = {
                                        when (snapshot.state) {
                                            ScreenRecorderContract.STATE_PAUSED -> onResume()
                                            ScreenRecorderContract.STATE_RECORDING -> onPause()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    onClick = { closeAfterAnimation(onStop) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                ) {
                                    Text("结束")
                                }
                            }
                        } else {
                            Column {
                                OverlayDropdownPreference(
                                    title = "分辨率",
                                    summary = safeResolutions[resolutionIndex].first,
                                    items = safeResolutions.map(Pair<String, String>::first),
                                    selectedIndex = resolutionIndex,
                                    insideMargin = itemInsideMargin,
                                    renderInRootScaffold = true,
                                    onSelectedIndexChange = { resolutionIndex = it },
                                )
                                OverlayDropdownPreference(
                                    title = "声音来源",
                                    summary = safeSounds[soundIndex].first,
                                    items = safeSounds.map(Pair<String, Int>::first),
                                    selectedIndex = soundIndex,
                                    insideMargin = itemInsideMargin,
                                    renderInRootScaffold = true,
                                    onSelectedIndexChange = { soundIndex = it },
                                )
                                ArrowPreference(
                                    title = "更多设置",
                                    summary = "打开小米录屏设置",
                                    insideMargin = itemInsideMargin,
                                    onClick = { closeAfterAnimation(onOpenSettings) },
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TextButton(
                                    text = "取消",
                                    onClick = { closeAfterAnimation(onCancel) },
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    onClick = {
                                        closeAfterAnimation {
                                            onStart(
                                                safeResolutions[resolutionIndex].second,
                                                safeSounds[soundIndex].second,
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                ) {
                                    Text("开始录制")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRecorderDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
}
