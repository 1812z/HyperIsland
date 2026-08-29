package io.github.hyperisland.compose.page.onboarding

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyperisland.R
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.service.OnboardingService
import io.github.hyperisland.compose.service.OnboardingStatus
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private enum class OnboardingDialog { Environment, FocusUnlock }

@Composable
internal fun OnboardingPage(
    prefs: FlutterPrefsRepository,
    showCloseButton: Boolean,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val service = remember(context) { OnboardingService(context) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { STEP_COUNT })
    val snackbarState = remember { SnackbarHostState() }
    var status by remember { mutableStateOf<OnboardingStatus?>(null) }
    var checking by remember { mutableStateOf(false) }
    var activeDialog by remember { mutableStateOf<OnboardingDialog?>(null) }
    var focusActionTaken by remember {
        mutableStateOf(
            prefs.getBoolean(KEY_UNLOCK_ALL_FOCUS, false) &&
                prefs.getBoolean(KEY_UNLOCK_FOCUS_AUTH, false),
        )
    }
    var defaultFocusNotification by remember {
        mutableStateOf(prefs.getBoolean(KEY_DEFAULT_FOCUS_NOTIFICATION, true))
    }
    var unlockAllFocus by remember { mutableStateOf(prefs.getBoolean(KEY_UNLOCK_ALL_FOCUS, false)) }
    var unlockFocusAuth by remember { mutableStateOf(prefs.getBoolean(KEY_UNLOCK_FOCUS_AUTH, false)) }
    var enablingUnlock by remember { mutableStateOf(false) }

    fun checkEnvironment(onResult: ((OnboardingStatus) -> Unit)? = null) {
        if (checking) return
        checking = true
        scope.launch {
            val result = service.checkStatus()
            status = result
            checking = false
            onResult?.invoke(result)
        }
    }

    fun goToPage(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page.coerceIn(0, STEP_COUNT - 1)) }
    }

    fun finish() {
        prefs.putBoolean(KEY_DEFAULT_FOCUS_NOTIFICATION, defaultFocusNotification)
        prefs.putBoolean(KEY_ONBOARDING_COMPLETED, true)
        onFinished()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { checkEnvironment() }

    LaunchedEffect(Unit) { checkEnvironment() }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == ENVIRONMENT_STEP &&
            service.supportsMiuiAppListPermission() &&
            !service.hasAppListPermission()
        ) {
            permissionLauncher.launch(OnboardingService.APP_LIST_PERMISSION)
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarState) },
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(bottom = scaffoldPadding.calculateBottomPadding()),
            ) {
                OnboardingHeader(
                    currentPage = pagerState.currentPage,
                    showCloseButton = showCloseButton,
                    onClose = ::finish,
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    userScrollEnabled = false,
                    beyondViewportPageCount = 0,
                ) { page ->
                    OnboardingStepPage(
                        page = page,
                        checking = checking,
                        status = status,
                        defaultFocusNotification = defaultFocusNotification,
                        unlockAllFocus = unlockAllFocus,
                        unlockFocusAuth = unlockFocusAuth,
                        enablingUnlock = enablingUnlock,
                        onFocusNotificationChanged = { defaultFocusNotification = it },
                        onOpenTutorial = {
                            focusActionTaken = true
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FOCUS_TUTORIAL_URL)))
                        },
                        onEnableEmbedded = {
                            if (!enablingUnlock) {
                                focusActionTaken = true
                                enablingUnlock = true
                                scope.launch {
                                    runCatching {
                                        service.enableEmbeddedFocusUnlock()
                                        prefs.putBoolean(KEY_UNLOCK_ALL_FOCUS, true)
                                        prefs.putBoolean(KEY_UNLOCK_FOCUS_AUTH, true)
                                        unlockAllFocus = true
                                        unlockFocusAuth = true
                                    }.onSuccess {
                                        snackbarState.showSnackbar(
                                            context.getString(R.string.onboarding_focus_success),
                                        )
                                    }.onFailure {
                                        snackbarState.showSnackbar(
                                            context.getString(
                                                R.string.onboarding_focus_failed,
                                                it.message ?: it.javaClass.simpleName,
                                            ),
                                        )
                                    }
                                    enablingUnlock = false
                                }
                            }
                        }
                    )
                }
                OnboardingControls(
                    currentPage = pagerState.currentPage,
                    nextEnabled = !checking,
                    onPrevious = { goToPage(pagerState.currentPage - 1) },
                    onNext = {
                        when (pagerState.currentPage) {
                            STEP_COUNT - 1 -> finish()
                            ENVIRONMENT_STEP -> checkEnvironment { result ->
                                if (result.requirementsMet) goToPage(FOCUS_UNLOCK_STEP)
                                else activeDialog = OnboardingDialog.Environment
                            }
                            FOCUS_UNLOCK_STEP -> {
                                if (focusActionTaken) goToPage(NOTIFICATION_STYLE_STEP)
                                else activeDialog = OnboardingDialog.FocusUnlock
                            }
                            else -> goToPage(pagerState.currentPage + 1)
                        }
                    },
                )
            }
        }
    }

    EnvironmentDialog(
        show = activeDialog == OnboardingDialog.Environment,
        onDismiss = { activeDialog = null },
        onContinue = {
            activeDialog = null
            goToPage(FOCUS_UNLOCK_STEP)
        },
        onRetry = {
            activeDialog = null
            checkEnvironment()
        },
    )
    FocusUnlockDialog(
        show = activeDialog == OnboardingDialog.FocusUnlock,
        onDismiss = { activeDialog = null },
        onContinue = {
            activeDialog = null
            goToPage(NOTIFICATION_STYLE_STEP)
        },
    )
}

@Composable
private fun OnboardingHeader(
    currentPage: Int,
    showCloseButton: Boolean,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (currentPage != 0) {
            Text(
                text = stringResource(R.string.onboarding_app_name),
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.weight(1f))
        if (showCloseButton) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.onboarding_close),
                )
            }
        }
    }
}

@Composable
private fun OnboardingStepPage(
    page: Int,
    checking: Boolean,
    status: OnboardingStatus?,
    defaultFocusNotification: Boolean,
    unlockAllFocus: Boolean,
    unlockFocusAuth: Boolean,
    enablingUnlock: Boolean,
    onFocusNotificationChanged: (Boolean) -> Unit,
    onOpenTutorial: () -> Unit,
    onEnableEmbedded: () -> Unit,
) {
    if (page == 0 || page == STEP_COUNT - 1) {
        CenteredStep(page)
        return
    }
    val step = onboardingStep(page)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepHeading(step)
        Spacer(Modifier.height(22.dp))
        when (page) {
            ENVIRONMENT_STEP -> EnvironmentPanel(checking, status)
            FOCUS_UNLOCK_STEP -> FocusUnlockPanel(
                unlockAllFocus = unlockAllFocus,
                unlockFocusAuth = unlockFocusAuth,
                enabling = enablingUnlock,
                onOpenTutorial = onOpenTutorial,
                onEnableEmbedded = onEnableEmbedded,
            )
            NOTIFICATION_STYLE_STEP -> NotificationStylePanel(
                defaultFocusNotification = defaultFocusNotification,
                onChanged = onFocusNotificationChanged,
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun CenteredStep(page: Int) {
    val isWelcome = page == 0
    val step = onboardingStep(page)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isWelcome) {
            WelcomeLogo()
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.onboarding_app_name),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        } else {
            Icon(
                imageVector = step.icon!!,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(step.title),
                style = MiuixTheme.textStyles.title1,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(step.subtitle!!),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 460.dp),
            )
        }
    }
}

@Composable
private fun WelcomeLogo() {
    Image(
        painter = painterResource(R.drawable.ic_launcher),
        contentDescription = null,
        modifier = Modifier.size(116.dp),
    )
}

@Composable
private fun StepHeading(step: OnboardingStep) {
    Icon(
        imageVector = step.icon!!,
        contentDescription = null,
        tint = MiuixTheme.colorScheme.primary,
        modifier = Modifier.size(70.dp),
    )
    Spacer(Modifier.height(18.dp))
    Text(
        text = stringResource(step.title),
        style = MiuixTheme.textStyles.title1,
        textAlign = TextAlign.Center,
    )
    step.subtitle?.let {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(it),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp),
        )
    }
}

@Composable
private fun EnvironmentPanel(checking: Boolean, status: OnboardingStatus?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PermissionCard(
            title = R.string.onboarding_lsposed,
            passed = status?.lsposedActive,
            failure = stringResource(R.string.onboarding_lsposed_failed),
            checking = checking,
        )
        PermissionCard(
            title = R.string.onboarding_root,
            passed = status?.rootGranted,
            failure = stringResource(R.string.onboarding_root_failed),
            checking = checking,
        )
        PermissionCard(
            title = R.string.onboarding_app_list,
            passed = status?.appListGranted,
            failure = stringResource(R.string.onboarding_app_list_failed),
            checking = checking,
        )
        val protocol = status?.protocolVersion
        PermissionCard(
            title = R.string.onboarding_protocol,
            passed = protocol?.let { it >= 3 },
            failure = protocol?.let {
                stringResource(R.string.onboarding_protocol_failed, it)
            } ?: stringResource(R.string.onboarding_checking),
            checking = checking,
        )
        val sdk = status?.androidSdkVersion
        PermissionCard(
            title = R.string.onboarding_android,
            passed = sdk?.let { it >= 35 },
            failure = sdk?.let {
                stringResource(R.string.onboarding_android_failed, it)
            } ?: stringResource(R.string.onboarding_checking),
            checking = checking,
        )
    }
}

@Composable
private fun PermissionCard(title: Int, passed: Boolean?, failure: String, checking: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(title),
            summary = failure.takeIf { passed == false },
            endActions = {
                when {
                    checking || passed == null -> CircularProgressIndicator(size = 22.dp)
                    passed -> Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                    )
                    else -> Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.error,
                    )
                }
            },
        )
    }
}

@Composable
private fun FocusUnlockPanel(
    unlockAllFocus: Boolean,
    unlockFocusAuth: Boolean,
    enabling: Boolean,
    onOpenTutorial: () -> Unit,
    onEnableEmbedded: () -> Unit,
) {
    val enabled = unlockAllFocus && unlockFocusAuth
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                title = stringResource(R.string.onboarding_focus_hyperceiler),
                summary = stringResource(R.string.onboarding_focus_hyperceiler_summary),
            )
            TextButton(
                text = stringResource(R.string.onboarding_view_tutorial),
                onClick = onOpenTutorial,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                title = stringResource(R.string.onboarding_focus_embedded),
                summary = stringResource(
                    if (enabled) R.string.onboarding_focus_embedded_enabled
                    else R.string.onboarding_focus_embedded_summary,
                ),
            )
            Button(
                onClick = onEnableEmbedded,
                enabled = !enabled && !enabling,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Text(
                    stringResource(
                        if (enabled) R.string.onboarding_focus_enabled
                        else R.string.onboarding_focus_enable,
                    ),
                )
            }
        }
    }
}

@Composable
private fun NotificationStylePanel(
    defaultFocusNotification: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NotificationStyleCard(
            title = stringResource(R.string.onboarding_focus_notification),
            assetPath = FOCUS_NOTIFICATION_ASSET,
            selected = defaultFocusNotification,
            onClick = { onChanged(true) },
        )
        NotificationStyleCard(
            title = stringResource(R.string.onboarding_normal_notification),
            assetPath = NORMAL_NOTIFICATION_ASSET,
            selected = !defaultFocusNotification,
            onClick = { onChanged(false) },
        )
    }
}

@Composable
private fun NotificationStyleCard(
    title: String,
    assetPath: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val preview = remember(assetPath) {
        runCatching {
            context.assets.open(assetPath).use(BitmapFactory::decodeStream).asImageBitmap()
        }.getOrNull()
    }
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        BasicComponent(
            title = title,
            endActions = {
                RadioButton(selected = selected, onClick = onClick)
            },
        )
        preview?.let {
            Image(
                bitmap = it,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .aspectRatio(it.width.toFloat() / it.height.toFloat()),
            )
        }
    }
}

@Composable
private fun OnboardingControls(
    currentPage: Int,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onPrevious,
            enabled = currentPage > 0,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.onboarding_previous))
        }
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(
                stringResource(
                    if (currentPage == STEP_COUNT - 1) R.string.onboarding_done
                    else R.string.onboarding_next,
                ),
            )
        }
    }
}

@Composable
private fun EnvironmentDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = stringResource(R.string.onboarding_environment_title),
        onDismissRequest = onDismiss,
    ) {
        DialogBody(
            message = stringResource(R.string.onboarding_environment_dialog_message),
            leftText = stringResource(R.string.onboarding_continue),
            rightText = stringResource(R.string.onboarding_retry),
            onLeft = onContinue,
            onRight = onRetry,
        )
    }
}

@Composable
private fun FocusUnlockDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = stringResource(R.string.onboarding_focus_title),
        onDismissRequest = onDismiss,
    ) {
        DialogBody(
            message = stringResource(R.string.onboarding_focus_dialog_message),
            leftText = stringResource(R.string.onboarding_continue),
            rightText = stringResource(R.string.cancel),
            onLeft = onContinue,
            onRight = onDismiss,
        )
    }
}

@Composable
private fun DialogBody(
    message: String,
    leftText: String,
    rightText: String,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = message,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = leftText,
                onClick = onLeft,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onRight,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(rightText)
            }
        }
    }
}

private fun onboardingStep(page: Int): OnboardingStep = when (page) {
    0 -> OnboardingStep(R.string.onboarding_app_name, null, null)
    1 -> OnboardingStep(
        R.string.onboarding_environment_title,
        R.string.onboarding_environment_subtitle,
        MiuixIcons.Settings,
    )
    2 -> OnboardingStep(
        R.string.onboarding_focus_title,
        R.string.onboarding_focus_subtitle,
        MiuixIcons.Lock,
    )
    3 -> OnboardingStep(
        R.string.onboarding_style_title,
        R.string.onboarding_style_subtitle,
        MiuixIcons.Help,
    )
    else -> OnboardingStep(
        R.string.onboarding_finish_title,
        R.string.onboarding_finish_subtitle,
        MiuixIcons.Ok,
    )
}

private data class OnboardingStep(val title: Int, val subtitle: Int?, val icon: ImageVector?)

private const val STEP_COUNT = 5
private const val ENVIRONMENT_STEP = 1
private const val FOCUS_UNLOCK_STEP = 2
private const val NOTIFICATION_STYLE_STEP = 3
private const val KEY_ONBOARDING_COMPLETED = "pref_onboarding_completed"
private const val KEY_DEFAULT_FOCUS_NOTIFICATION = "pref_default_focus_notif"
private const val KEY_UNLOCK_ALL_FOCUS = "pref_unlock_all_focus"
private const val KEY_UNLOCK_FOCUS_AUTH = "pref_unlock_focus_auth"
private const val FOCUS_NOTIFICATION_ASSET = "images/notification1.png"
private const val NORMAL_NOTIFICATION_ASSET = "images/notification2.png"
private const val FOCUS_TUTORIAL_URL =
    "https://hyperisland.1812z.top/getting-started.html#%E7%AC%AC%E4%B8%89%E6%AD%A5-%E5%9C%A8-hyperceiler-%E4%B8%AD%E5%BC%80%E5%90%AF%E3%80%8C%E7%A7%BB%E9%99%A4%E7%84%A6%E7%82%B9%E9%80%9A%E7%9F%A5%E7%99%BD%E5%90%8D%E5%8D%95%E3%80%8D%E5%92%8C%E3%80%8C%E8%A7%A3%E9%94%81%E7%84%A6%E7%82%B9%E9%80%9A%E7%9F%A5%E7%99%BD%E5%90%8D%E5%8D%95%E9%AA%8C%E8%AF%81%E3%80%8D"
