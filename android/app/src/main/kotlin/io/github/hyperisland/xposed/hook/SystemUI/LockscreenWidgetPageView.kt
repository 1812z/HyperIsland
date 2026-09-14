package io.github.hyperisland.xposed.hook.SystemUI

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserManager
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewOutlineProvider
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.VelocityTracker
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.inputmethod.InputMethodManager
import java.lang.reflect.Method
import java.util.ArrayList
import kotlin.math.roundToInt

/**
 * Lock-screen widget page hosted inside the SystemUI process.
 *
 * SystemUI already holds `android.permission.BIND_APPWIDGET`, so it can create an [AppWidgetHost]
 * directly; a separate app/Activity would be blocked by widget bind restrictions. The view is added
 * as a child of `MiuiKeyguardMoveLeftViewContainer`, which the stock keyguard gesture pipeline
 * translates, blurs and dims frame-by-frame. Only the explicit long-press reorder gesture animates
 * a widget cell locally; page swipes continue to use the stock translation path.
 */
internal class LockscreenWidgetPageView(context: Context) : FrameLayout(context) {

    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val widgetHost = AppWidgetHost(context, HOST_ID)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val store = WidgetStore(context)
    private val cells = ArrayList<WidgetCell>()
    private val column: GridLayout
    private val scroll: ScrollView
    private val emptyHint: TextView
    private val addButton: TextView
    private var deleteZone: TextView? = null
    private var deleteZoneDanger = false
    private var picker: View? = null
    private var listening = false
    private var pickerProviders: List<AppWidgetProviderInfo> = emptyList()
    private val pickerRows = ArrayList<Pair<AppWidgetProviderInfo, View>>()
    private var pickerList: LinearLayout? = null
    private var pickerSearch: EditText? = null
    private var pickerQuery = ""
    private var pickerPackage: String? = null
    private val pickerAppRows = ArrayList<View>()
    private var imeWindowState: Pair<Int, Int>? = null
    private var horizontalGesture = false
    private var horizontalForwarded = false
    private var verticalGesture = false
    private var pageDownX = 0f
    private var pageDownY = 0f
    private var pageChildCancelled = false
    private var touchInScroll = false
    private var touchInSystemGesture = false
    private var scrollDownY = 0f
    private var pageVelocity: VelocityTracker? = null
    private var widgetDragActive = false
    private var pendingOrder: MutableList<WidgetCell>? = null
    private var pendingDragIndex = -1
    private val dragClipStates = ArrayList<Triple<ViewGroup, Boolean, Boolean>>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val unlockRetry = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || !listening) return
            if (widgetsReady()) {
                runCatching { widgetHost.startListening() }
                loadWidgets()
            } else {
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    private val density = resources.displayMetrics.density

    init {
        applyPageBackground()
        // Keep the touch stream even when DOWN lands on page background or header whitespace.
        // Horizontal gestures explicitly return false after classification to reach SystemUI.
        isClickable = true
        isFocusable = false
        clipChildren = false
        clipToPadding = false

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22f), dp(38f), dp(16f), dp(8f))
        }
        val title = TextView(context).apply {
            text = PAGE_TITLE
            setTextColor(primaryTextColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        addButton = TextView(context).apply {
            text = "+"
            setTextColor(primaryTextColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            background = circle(Color.argb(46, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(dp(38f), dp(38f)).apply { marginEnd = dp(10f) }
            setOnClickListener { showPicker() }
        }
        header.addView(title)
        header.addView(addButton)

        emptyHint = TextView(context).apply {
            text = EMPTY_HINT
            setTextColor(secondaryTextColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(24f), dp(56f), dp(24f), dp(56f))
        }

        column = GridLayout(context).apply {
            columnCount = GRID_COLUMNS
            orientation = GridLayout.HORIZONTAL
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
            clipChildren = false
            clipToPadding = false
            setPadding(dp(16f), dp(4f), dp(16f), dp(48f))
            addView(
                emptyHint,
                GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, GRID_COLUMNS),
                ),
            )
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(header, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
            addView(column, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
        }

        scroll = ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }

        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        deleteZone = TextView(context).apply {
            text = "×"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            gravity = Gravity.CENTER
            alpha = 0f
            scaleX = 0.72f
            scaleY = 0.72f
            visibility = INVISIBLE
            elevation = dp(12f).toFloat()
            background = circle(Color.argb(220, 90, 90, 96))
            contentDescription = "拖到这里删除小组件"
        }.also { zone ->
            addView(zone, LayoutParams(dp(64f), dp(64f)).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(30f)
            })
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (listening) return
        listening = true
        if (widgetsReady()) {
            runCatching { widgetHost.startListening() }
            loadWidgets()
        } else {
            emptyHint.visibility = VISIBLE
            mainHandler.postDelayed(unlockRetry, 1000L)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        restoreDragClipping()
        hideDeleteZone(immediate = true)
        restoreImeWindow()
        if (!listening) return
        listening = false
        runCatching { widgetHost.stopListening() }
        mainHandler.removeCallbacksAndMessages(null)
        pageVelocity?.recycle()
        pageVelocity = null
        touchInScroll = false
        touchInSystemGesture = false
        horizontalGesture = false
        horizontalForwarded = false
        verticalGesture = false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (picker != null || widgetDragActive) {
            val handled = super.dispatchTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) resetPageGesture()
            return handled
        }
        if (verticalGesture && event.actionMasked != MotionEvent.ACTION_UP &&
            event.actionMasked != MotionEvent.ACTION_CANCEL
        ) return sendToScroll(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pageDownX = event.rawX
                pageDownY = event.rawY
                horizontalGesture = false
                verticalGesture = false
                pageChildCancelled = false
                // Classify gestures across the whole screen-backed page, including the header
                // and widget cards. Only the bottom system-gesture inset is excluded.
                // Leave the bottom gesture handle/nav strip to SystemUI. It must never start
                // the nested ScrollView, otherwise the white handle becomes unresponsive.
                touchInSystemGesture = event.rawY >= resources.displayMetrics.heightPixels - dp(80f)
                touchInScroll = !touchInSystemGesture
                if (touchInSystemGesture) {
                    // Do not dispatch the DOWN into ScrollView/RemoteViews. Returning false
                    // keeps the bottom edge gesture owned by the SystemUI root (the white
                    // handle), while all other regions remain eligible for page scrolling.
                    resetPageGesture()
                    return false
                }
                val scrollPosition = IntArray(2).also(scroll::getLocationOnScreen)
                scrollDownY = (event.rawY - scrollPosition[1])
                    .coerceIn(1f, (scroll.height - 2).coerceAtLeast(1).toFloat())
                pageVelocity?.recycle()
                pageVelocity = VelocityTracker.obtain().also { it.addMovement(event) }
                // Hold the full stream in the page until its direction is known. This prevents
                // the shade/header interceptor from stealing vertical moves in the upper half.
                // Horizontal confirmation below releases the parent for the stock pager.
                super.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                pageVelocity?.addMovement(event)
                if (horizontalGesture) {
                    LockscreenWidgetPageHook.forwardHorizontal(event, pageDownX, pageDownY)
                    // The stock helper is fed directly below; dispatching this same event
                    // through the view tree would update the translation a second time and
                    // produces visible flicker while the finger is moving.
                    return true
                }
                if (!touchInScroll) return super.dispatchTouchEvent(event)
                val dx = event.rawX - pageDownX
                val dy = event.rawY - pageDownY
                if (kotlin.math.hypot(dx, dy) > touchSlop()) {
                    pageVelocity?.computeCurrentVelocity(1000)
                    val vx = kotlin.math.abs(pageVelocity?.xVelocity ?: 0f)
                    val vy = kotlin.math.abs(pageVelocity?.yVelocity ?: 0f)
                    val vertical = kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.1f ||
                        (vy > vx * 1.35f && kotlin.math.abs(dy) > touchSlop() / 2f)
                    val horizontal = kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.1f ||
                        (vx > vy * 1.35f && kotlin.math.abs(dx) > touchSlop() / 2f)
                    if (vertical && !horizontal) {
                        verticalGesture = true
                        cancelDescendantTouch(event)
                        super.requestDisallowInterceptTouchEvent(true)
                        sendToScroll(event, start = true)
                        return true
                    }
                    if (horizontal && !vertical) {
                        horizontalGesture = true
                        cancelDescendantTouch(event)
                        // Keep the ancestor out of this stream. Horizontal events are forwarded
                        // directly to KeyguardPanelViewInjector to avoid duplicate animation.
                        super.requestDisallowInterceptTouchEvent(true)
                        if (!horizontalForwarded) {
                            val down = MotionEvent.obtain(event).apply {
                                action = MotionEvent.ACTION_DOWN
                                setLocation(pageDownX, pageDownY)
                            }
                            LockscreenWidgetPageHook.forwardHorizontal(down, pageDownX, pageDownY)
                            down.recycle()
                            horizontalForwarded = true
                        }
                        LockscreenWidgetPageHook.forwardHorizontal(event, pageDownX, pageDownY)
                        // Keep forwarding the current event after releasing interception. The
                        // parent can now take over on the next dispatch pass without losing the
                        // first visible horizontal displacement.
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasVertical = verticalGesture
                val wasHorizontal = horizontalGesture
                if (wasVertical) sendToScroll(event)
                if (wasHorizontal && horizontalForwarded) {
                    LockscreenWidgetPageHook.forwardHorizontal(event, pageDownX, pageDownY)
                }
                resetPageGesture()
                if (wasVertical) return true
                if (wasHorizontal) return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun resetPageGesture() {
        horizontalGesture = false
        verticalGesture = false
        pageChildCancelled = false
        touchInScroll = false
        touchInSystemGesture = false
        pageVelocity?.recycle()
        pageVelocity = null
        super.requestDisallowInterceptTouchEvent(false)
    }

    private fun sendToScroll(source: MotionEvent, start: Boolean = false): Boolean {
        val position = IntArray(2).also(scroll::getLocationOnScreen)
        if (start) {
            val down = MotionEvent.obtain(source).apply {
                action = MotionEvent.ACTION_DOWN
                setLocation(pageDownX - position[0], scrollDownY)
            }
            scroll.onTouchEvent(down)
            down.recycle()
        }
        val copy = MotionEvent.obtain(source).apply {
            setLocation(source.rawX - position[0], scrollDownY + source.rawY - pageDownY)
        }
        scroll.onTouchEvent(copy)
        copy.recycle()
        return true
    }

    private fun touchSlop(): Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private fun allowDragOutsideViewport(cell: WidgetCell) {
        restoreDragClipping()
        var ancestor = cell.parent
        while (ancestor is ViewGroup) {
            dragClipStates += Triple(ancestor, ancestor.clipChildren, ancestor.clipToPadding)
            ancestor.clipChildren = false
            ancestor.clipToPadding = false
            ancestor = ancestor.parent
        }
    }

    private fun restoreDragClipping() {
        dragClipStates.forEach { (view, children, padding) ->
            view.clipChildren = children
            view.clipToPadding = padding
        }
        dragClipStates.clear()
    }

    private fun cancelDescendantTouch(source: MotionEvent) {
        if (pageChildCancelled) return
        pageChildCancelled = true
        val cancel = MotionEvent.obtain(source)
        cancel.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // A vertical scroll must stay inside this page; a horizontal swipe must remain visible
        // to the stock KeyguardMoveHelper; a long-press drag owns the stream until it ends.
        if (widgetDragActive || verticalGesture) {
            super.requestDisallowInterceptTouchEvent(true)
        } else {
            // RemoteViews may ask to keep the stream, but the page has not established a drag.
            // Let SystemUI see horizontal swipes until a vertical scroll is confirmed.
            super.requestDisallowInterceptTouchEvent(false)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPageBackground()
        cells.forEach {
            refreshHostView(it)
            it.applyCardBackground()
        }
        rebuildGrid()
    }

    private fun refreshHostView(cell: WidgetCell) {
        val info = safeAppWidgetInfo(cell.appWidgetId) ?: return
        val replacement = runCatching {
            widgetHost.createView(context, cell.appWidgetId, info)
        }.getOrNull() ?: return
        replacement.setPadding(0, 0, 0, 0)
        replacement.clipToOutline = true
        replacement.outlineProvider = roundedOutline(dp(22f).toFloat())
        cell.removeView(cell.hostView)
        cell.hostView = replacement
        cell.addView(replacement, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        cell.materialView.visibility = INVISIBLE
        cell.updateFallbackBackground(replacement)
        WidgetMaterialEffect.apply(replacement)
        replacement.requestLayout()
        replacement.invalidate()
    }

    private fun applyPageBackground() {
        // Keep the stock negative-page blur visible behind the widgets.
        background = null
    }

    private fun isDarkMode(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun primaryTextColor(): Int = if (isDarkMode()) Color.WHITE else Color.rgb(28, 28, 30)

    private fun secondaryTextColor(): Int = if (isDarkMode()) {
        Color.argb(155, 255, 255, 255)
    } else {
        Color.argb(155, 28, 28, 30)
    }

    // ── Widget lifecycle ──────────────────────────────────────────────────────

    private fun loadWidgets() {
        if (!widgetsReady()) {
            mainHandler.removeCallbacks(unlockRetry)
            mainHandler.postDelayed(unlockRetry, 1000L)
            return
        }
        column.removeAllViews()
        cells.clear()
        store.ids().forEach { id ->
            val info = safeAppWidgetInfo(id)
            if (info == null) {
                store.remove(id)
            } else {
                attachExisting(id, info)
            }
        }
        rebuildGrid()
    }

    private fun attachExisting(appWidgetId: Int, info: AppWidgetProviderInfo) {
        val hostView = runCatching { widgetHost.createView(context, appWidgetId, info) }
            .getOrNull() ?: return
        addCell(appWidgetId, info, hostView, persist = false)
    }

    private fun addWidget(info: AppWidgetProviderInfo) {
        if (!widgetsReady()) return
        val provider = info.provider ?: return
        val appWidgetId = runCatching { widgetHost.allocateAppWidgetId() }.getOrElse { return }
        val bound = runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
        }.getOrElse { false }
        if (!bound) {
            runCatching { widgetHost.deleteAppWidgetId(appWidgetId) }
            return
        }
        val hostView = runCatching { widgetHost.createView(context, appWidgetId, info) }.getOrNull()
        if (hostView == null) {
            runCatching { widgetHost.deleteAppWidgetId(appWidgetId) }
            return
        }
        addCell(appWidgetId, info, hostView, persist = true)
        launchConfigureIfNeeded(appWidgetId, info)
    }

    private fun addCell(
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
        hostView: AppWidgetHostView,
        persist: Boolean,
    ) {
        val cell = WidgetCell(appWidgetId)
        cell.hostView = hostView
        val metrics = widgetMetrics(info)
        val cardPadding = dp(8f)
        cell.setPadding(cardPadding, cardPadding, cardPadding, cardPadding)
        hostView.setPadding(0, 0, 0, 0)
        hostView.clipToOutline = true
        hostView.outlineProvider = roundedOutline(dp(22f).toFloat())
        cell.addView(hostView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        cell.materialView.visibility = INVISIBLE
        cell.updateFallbackBackground(hostView)
        WidgetMaterialEffect.apply(hostView)
        cell.applyCardBackground()
        cell.spanX = metrics.spanX
        cell.spanY = metrics.spanY
        cells.add(cell)
        rebuildGrid()
        if (persist) persistOrder()
        refreshEmptyState()
    }

    private fun removeCell(cell: WidgetCell) {
        if (!cells.remove(cell)) return
        column.removeView(cell)
        runCatching { widgetHost.deleteAppWidgetId(cell.appWidgetId) }
        rebuildGrid()
        persistOrder()
        refreshEmptyState()
    }

    private fun launchConfigureIfNeeded(appWidgetId: Int, info: AppWidgetProviderInfo) {
        val configure = info.configure ?: return
        runCatching {
            context.startActivity(
                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    private fun persistOrder() {
        store.save(cells.map { it.appWidgetId })
    }

    private fun refreshEmptyState() {
        emptyHint.visibility = if (cells.isEmpty()) VISIBLE else GONE
    }

    private fun rebuildGrid() {
        column.removeAllViews()
        val slots = computeGridSlots(cells)
        cells.forEach { cell ->
            val slot = slots[cell] ?: return@forEach
            val unit = widgetCellWidth()
            val gap = dp(8f)
            column.addView(
                cell,
                gridParams(
                    cell.spanX,
                    cell.spanY,
                    unit * cell.spanX + gap * (cell.spanX - 1),
                    unit * cell.spanY + gap * (cell.spanY - 1),
                    slot,
                ),
            )
        }
        column.addView(
            emptyHint,
            GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, GRID_COLUMNS),
            ),
        )
        refreshEmptyState()
    }

    private fun gridParams(
        spanX: Int,
        spanY: Int,
        width: Int,
        height: Int,
        slot: Pair<Int, Int>,
    ) =
        GridLayout.LayoutParams(
            GridLayout.spec((slot.second - column.paddingTop) / (widgetCellWidth() + dp(8f)), spanY),
            GridLayout.spec((slot.first - column.paddingLeft) / (widgetCellWidth() + dp(8f)), spanX),
        ).apply {
            this.width = width
            this.height = height
            rightMargin = dp(8f)
            bottomMargin = dp(8f)
        }

    private fun reorderByDrag(cell: WidgetCell, rawX: Float, rawY: Float) {
        val order = pendingOrder ?: return
        val from = pendingDragIndex
        if (from < 0) return
        val slots = computeGridSlots(order)
        val location = IntArray(2).also(column::getLocationOnScreen)
        val draggedLeft = location[0] + cell.left + cell.translationX
        val draggedTop = location[1] + cell.top + cell.translationY
        val targetCell = order.asSequence().filter { it !== cell }.mapNotNull { candidate ->
            val slot = slots[candidate] ?: return@mapNotNull null
            val left = (location[0] + slot.first).toFloat()
            val top = (location[1] + slot.second).toFloat()
            val overlapX = (minOf(draggedLeft + cell.width, left + candidate.width) -
                maxOf(draggedLeft, left)).coerceAtLeast(0f)
            val overlapY = (minOf(draggedTop + cell.height, top + candidate.height) -
                maxOf(draggedTop, top)).coerceAtLeast(0f)
            val overlap = overlapX * overlapY
            if (overlap > candidate.width * candidate.height * 0.35f) candidate to overlap else null
        }.maxByOrNull { it.second }?.first ?: return
        val target = order.indexOf(targetCell)
        if (target < 0 || target == from) return
        val insertion = if (cell.spanX == GRID_COLUMNS) {
            val targetRow = slots[targetCell]?.second
            val rowIndices = order.indices.filter { index ->
                order[index] !== cell && slots[order[index]]?.second == targetRow
            }
            if (rawY >= pageDownY) (rowIndices.maxOrNull() ?: target) + 1
            else rowIndices.minOrNull() ?: target
        } else {
            if (target > from) target + 1 else target
        }
        order.removeAt(from)
        val adjusted = if (insertion > from) insertion - 1 else insertion
        if (adjusted == from) {
            order.add(from, cell)
            return
        }
        order.add(adjusted.coerceIn(0, order.size), cell)
        pendingDragIndex = adjusted.coerceIn(0, order.lastIndex)
        animateAvoidance(cell)
    }

    private fun computeGridSlots(order: List<WidgetCell>): HashMap<WidgetCell, Pair<Int, Int>> {
        val result = HashMap<WidgetCell, Pair<Int, Int>>()
        val occupied = ArrayList<BooleanArray>()
        fun fits(row: Int, col: Int, w: Int, h: Int): Boolean {
            if (col + w > GRID_COLUMNS) return false
            while (occupied.size < row + h) occupied += BooleanArray(GRID_COLUMNS)
            for (r in row until row + h) for (c in col until col + w) if (occupied[r][c]) return false
            return true
        }
        order.forEach { item ->
            var row = 0
            var col = 0
            while (!fits(row, col, item.spanX, item.spanY)) {
                col++
                if (col >= GRID_COLUMNS) { col = 0; row++ }
            }
            while (occupied.size < row + item.spanY) occupied += BooleanArray(GRID_COLUMNS)
            for (r in row until row + item.spanY) for (c in col until col + item.spanX) occupied[r][c] = true
            result[item] = column.paddingLeft + col * (widgetCellWidth() + dp(8f)) to
                column.paddingTop + row * (widgetCellWidth() + dp(8f))
        }
        return result
    }

    private fun widgetCellWidth(): Int {
        val contentWidth = resources.displayMetrics.widthPixels - dp(32f)
        return ((contentWidth - dp(8f) * 3) / GRID_COLUMNS).coerceAtLeast(dp(48f))
    }

    private fun animateAvoidance(dragged: WidgetCell) {
        val order = pendingOrder ?: return
        val slots = computeGridSlots(order)
        // The actual GridLayout hierarchy stays untouched for the entire touch stream.
        // Only siblings move to their provisional slots; this cannot cancel the drag target.
        order.forEach { item ->
            if (item === dragged) return@forEach
            val slot = slots[item] ?: return@forEach
            item.animate().cancel()
            item.animate()
                .translationX((slot.first - item.left).toFloat())
                .translationY((slot.second - item.top).toFloat())
                .setDuration(180L)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.4f))
                .start()
        }
    }

    // ── Widget picker ─────────────────────────────────────────────────────────

    private fun showPicker() {
        if (picker != null || !widgetsReady()) return
        val providers = runCatching { appWidgetManager.installedProviders }
            .getOrDefault(emptyList())
            .filter { it.provider != null }
            .sortedBy { it.provider?.packageName.orEmpty() }
        val overlay = buildPickerOverlay(providers)
        picker = overlay
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        renderPickerRows()
        pickerSearch?.let { search ->
            search.requestFocus()
            search.postDelayed({
                prepareImeWindow(search)
                val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
                inputMethodManager?.restartInput(search)
                inputMethodManager?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
            }, 320L)
            search.setOnClickListener {
                search.requestFocus()
                search.postDelayed({
                    prepareImeWindow(search)
                    val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
                    inputMethodManager?.restartInput(search)
                    inputMethodManager?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
                }, 80L)
            }
        }
    }

    private fun prepareImeWindow(view: View) {
        val root = view.rootView
        view.requestFocus()
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        val original = imeWindowState
        if (original == null) {
            imeWindowState = params.flags to params.softInputMode
        }
        // Numeric PIN keyguard keeps the shade window not-focusable. Clear both flags that
        // prevent an IME target, then commit the change to WindowManager (mutating the copy
        // returned by getLayoutParams() alone is not enough).
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        runCatching {
            context.getSystemService(WindowManager::class.java)?.updateViewLayout(root, params)
        }
    }

    private fun dismissPicker() {
        pickerSearch?.let { search ->
            context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(search.windowToken, 0)
        }
        restoreImeWindow()
        picker?.let(::removeView)
        picker = null
        pickerProviders = emptyList()
        pickerRows.clear()
        pickerList = null
        pickerSearch = null
        pickerQuery = ""
        pickerPackage = null
        pickerAppRows.clear()
    }

    private fun restoreImeWindow() {
        val state = imeWindowState ?: return
        val root = rootView
        val params = root.layoutParams as? WindowManager.LayoutParams ?: run {
            imeWindowState = null
            return
        }
        params.flags = state.first
        params.softInputMode = state.second
        runCatching {
            context.getSystemService(WindowManager::class.java)?.updateViewLayout(root, params)
        }
        imeWindowState = null
    }

    private fun buildPickerOverlay(providers: List<AppWidgetProviderInfo>): View {
        val overlay = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(if (isDarkMode()) DARK_PICKER_BACKGROUND else LIGHT_PICKER_BACKGROUND)
                cornerRadii = floatArrayOf(
                    0f, 0f,
                    dp(26f).toFloat(), dp(26f).toFloat(),
                    dp(26f).toFloat(), dp(26f).toFloat(),
                    0f, 0f,
                )
            }
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = FOCUS_AFTER_DESCENDANTS
        }
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(20f), dp(16f), dp(10f))
        }
        val backButton = TextView(context).apply {
            text = "‹"
            contentDescription = "返回"
            setTextColor(primaryTextColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            gravity = Gravity.CENTER
            background = circle(Color.argb(46, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f)).apply {
                marginEnd = dp(12f)
            }
            setOnClickListener {
                if (pickerPackage == null) {
                    dismissPicker()
                } else {
                    pickerPackage = null
                    pickerQuery = ""
                    pickerSearch?.setText("")
                    renderPickerRows()
                    (picker?.findViewWithTag<View>("picker-title") as? TextView)?.text = PICKER_TITLE
                }
            }
        }
        val title = TextView(context).apply {
            text = PICKER_TITLE
            tag = "picker-title"
            setTextColor(primaryTextColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeButton = TextView(context).apply {
            text = "×"
            contentDescription = "退出添加小组件"
            setTextColor(primaryTextColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            background = circle(Color.argb(46, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(dp(36f), dp(36f))
            setOnClickListener { dismissPicker() }
        }
        header.addView(backButton)
        header.addView(title)
        header.addView(closeButton)
        container.addView(header)

        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        pickerList = list
        pickerProviders = providers
        pickerRows.clear()
        pickerAppRows.clear()
        val search = EditText(context).apply {
            hint = PICKER_SEARCH_HINT
            setTextColor(primaryTextColor())
            setHintTextColor(secondaryTextColor())
            setSingleLine(true)
            isFocusable = true
            isFocusableInTouchMode = true
            showSoftInputOnFocus = true
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(18f), 0, dp(18f), 0)
            background = roundedBackground(
                if (isDarkMode()) Color.argb(34, 255, 255, 255) else Color.argb(28, 0, 0, 0),
                dp(18f).toFloat(),
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46f),
            ).apply { setMargins(dp(16f), 0, dp(16f), dp(12f)) }
        }
        pickerSearch = search
        container.addView(search)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pickerQuery = s?.toString().orEmpty()
                filterPickerRows(pickerQuery)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        val scroll = ScrollView(context).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        container.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        overlay.addView(
            container,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        return overlay
    }

    /** Rebuilds the sheet body when switching between application and widget pages. */
    private fun renderPickerRows() {
        val list = pickerList ?: return
        list.removeAllViews()
        pickerRows.clear()
        pickerAppRows.clear()
        if (pickerPackage == null) {
            pickerProviders.groupBy { it.provider?.packageName.orEmpty() }
                .toSortedMap()
                .forEach { (packageName, providers) ->
                    val row = buildPickerAppRow(packageName, providers.size)
                    pickerAppRows += row
                    list.addView(row)
                }
            if (pickerProviders.isEmpty()) list.addView(pickerEmptyView())
        } else {
            val providers = pickerProviders.filter { it.provider?.packageName == pickerPackage }
            providers.forEach { info ->
                val row = buildPickerRow(info)
                pickerRows += info to row
                list.addView(row)
            }
            if (providers.isEmpty()) list.addView(pickerEmptyView())
        }
        filterPickerRows(pickerQuery)
    }

    private fun pickerEmptyView(): View = TextView(context).apply {
        text = PICKER_EMPTY
        setTextColor(secondaryTextColor())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        setPadding(dp(24f), dp(60f), dp(24f), dp(60f))
    }

    private fun pickerPackageLabel(packageName: String): String = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0),
        ).toString()
    }.getOrDefault(packageName)

    private fun buildPickerAppRow(packageName: String, count: Int): View {
        val row = LinearLayout(context).apply {
            tag = packageName
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
            isClickable = true
            setOnClickListener {
                pickerPackage = packageName
                pickerSearch?.setText("")
                renderPickerRows()
                (picker?.findViewWithTag<View>("picker-title") as? TextView)?.text =
                    pickerPackageLabel(packageName)
            }
        }
        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f)).apply { marginEnd = dp(14f) }
            runCatching {
                setImageDrawable(context.packageManager.getApplicationIcon(packageName))
            }
        }
        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = pickerPackageLabel(packageName)
                setTextColor(primaryTextColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            })
            addView(TextView(context).apply {
                text = "共 $count 个小组件"
                setTextColor(secondaryTextColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
        }
        row.addView(icon)
        row.addView(labels)
        row.addView(TextView(context).apply {
            text = "›"
            setTextColor(secondaryTextColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            gravity = Gravity.CENTER
        })
        return row
    }

    private fun filterPickerRows(query: String) {
        val normalized = query.trim().lowercase()
        if (pickerPackage == null) {
            pickerAppRows.forEach { row ->
                val packageName = row.tag?.toString().orEmpty()
                row.visibility = if (
                    normalized.isEmpty() ||
                        pickerPackageLabel(packageName).lowercase().contains(normalized) ||
                        packageName.lowercase().contains(normalized)
                ) VISIBLE else GONE
            }
            return
        }
        pickerRows.forEach { (info, row) ->
            row.visibility = if (matchesPickerQuery(info, normalized)) VISIBLE else GONE
        }
    }

    private fun matchesPickerQuery(info: AppWidgetProviderInfo, query: String): Boolean {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return true
        val label = runCatching { info.loadLabel(context.packageManager).toString() }
            .getOrDefault(info.provider?.packageName.orEmpty())
        return label.lowercase().contains(normalized) ||
            info.provider?.packageName.orEmpty().lowercase().contains(normalized)
    }

    private fun buildPickerRow(info: AppWidgetProviderInfo): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(12f), dp(18f), dp(12f))
            isClickable = true
            setOnClickListener {
                dismissPicker()
                addWidget(info)
            }
        }
        val heading = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36f), dp(36f)).apply { marginEnd = dp(14f) }
            runCatching {
                setImageDrawable(info.loadIcon(context, resources.displayMetrics.densityDpi))
            }
        }
        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = runCatching { info.loadLabel(context.packageManager).toString() }
                    .getOrDefault(info.provider?.packageName.orEmpty())
                setTextColor(primaryTextColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            })
            addView(TextView(context).apply {
                text = "${info.minWidth} × ${info.minHeight}"
                setTextColor(secondaryTextColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
        }
        heading.addView(icon)
        heading.addView(labels)
        heading.addView(TextView(context).apply {
            text = "›"
            setTextColor(secondaryTextColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            gravity = Gravity.CENTER
        })
        row.addView(heading)

        val preview = runCatching {
            info.loadPreviewImage(context, resources.displayMetrics.densityDpi)
        }.getOrNull()
        val previewHeight = if (preview != null && preview.intrinsicWidth > 0 &&
            preview.intrinsicHeight > 0
        ) {
            (resources.displayMetrics.widthPixels.toFloat() *
                preview.intrinsicHeight / preview.intrinsicWidth)
                .roundToInt().coerceIn(dp(120f), dp(220f))
        } else {
            dp(136f)
        }
        val previewCard = FrameLayout(context).apply {
            background = roundedBackground(
                if (isDarkMode()) Color.argb(34, 255, 255, 255)
                else Color.argb(23, 0, 0, 0),
                dp(18f).toFloat(),
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                previewHeight,
            ).apply { topMargin = dp(12f) }
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
        }
        if (preview != null) {
            previewCard.addView(ImageView(context).apply {
                setImageDrawable(preview)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "小组件预览"
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
        } else {
            previewCard.addView(TextView(context).apply {
                text = "此小组件未提供预览"
                setTextColor(secondaryTextColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
        }
        row.addView(previewCard)
        return row
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun widgetsReady(): Boolean =
        runCatching { userManager?.isUserUnlocked == true }.getOrDefault(false)

    internal fun acceptsPageGesture(): Boolean = widgetsReady()

    private fun safeAppWidgetInfo(appWidgetId: Int): AppWidgetProviderInfo? {
        if (!widgetsReady()) return null
        return runCatching { appWidgetManager.getAppWidgetInfo(appWidgetId) }.getOrNull()
    }

    private fun dp(value: Float): Int = (value * density).roundToInt().coerceAtLeast(1)

    private fun widgetMetrics(info: AppWidgetProviderInfo?): WidgetMetrics {
        if (info == null) return WidgetMetrics(dp(48f), dp(48f), 1, 1)
        val contentWidth = resources.displayMetrics.widthPixels - dp(32f)
        val gap = dp(8f)
        val cellWidth = ((contentWidth - gap * 3) / 4).coerceAtLeast(dp(48f))
        val cellHeight = cellWidth
        val spanX = widgetSpan(info.minWidth, 4)
        val spanY = widgetSpan(info.minHeight, 4)
        val width = cellWidth * spanX + gap * (spanX - 1)
        val height = cellHeight * spanY + gap * (spanY - 1)
        return WidgetMetrics(width, height, spanX, spanY)
    }

    /** Android launcher convention: one cell is 70dp with 30dp widget padding. */
    private fun widgetSpan(minSizePx: Int, maxSpan: Int): Int =
        kotlin.math.ceil((minSizePx + dp(30f)) / (dp(70f).toFloat())).toInt()
            .coerceIn(1, maxSpan)

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }

    private fun roundedOutline(radius: Float): ViewOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }

    private fun circle(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun showDeleteZone() {
        val zone = deleteZone ?: return
        zone.animate().cancel()
        zone.visibility = VISIBLE
        zone.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f)).start()
    }

    private fun hideDeleteZone(immediate: Boolean = false) {
        val zone = deleteZone ?: return
        deleteZoneDanger = false
        zone.animate().cancel()
        if (immediate) {
            zone.alpha = 0f
            zone.scaleX = 0.72f
            zone.scaleY = 0.72f
            zone.visibility = INVISIBLE
        } else {
            zone.animate().alpha(0f).scaleX(0.72f).scaleY(0.72f).setDuration(160L)
                .withEndAction { zone.visibility = INVISIBLE }.start()
        }
    }

    private fun isOverDeleteZone(rawX: Float, rawY: Float): Boolean {
        val zone = deleteZone ?: return false
        if (zone.visibility != VISIBLE || zone.alpha < 0.5f) return false
        val location = IntArray(2).also(zone::getLocationOnScreen)
        return rawX >= location[0] && rawX <= location[0] + zone.width &&
            rawY >= location[1] && rawY <= location[1] + zone.height
    }

    private fun updateDeleteZone(rawX: Float, rawY: Float) {
        val over = isOverDeleteZone(rawX, rawY)
        if (over == deleteZoneDanger) return
        deleteZoneDanger = over
        val zone = deleteZone ?: return
        val from = if (over) Color.argb(220, 90, 90, 96) else Color.rgb(90, 90, 96)
        val to = if (over) Color.rgb(220, 55, 58) else Color.argb(220, 90, 90, 96)
        val drawable = zone.background as? GradientDrawable ?: return
        ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = 180L
            addUpdateListener { drawable.setColor(it.animatedValue as Int) }
            start()
        }
        zone.animate().scaleX(if (over) 1.14f else 1f).scaleY(if (over) 1.14f else 1f)
            .setDuration(160L).start()
    }

    private inner class WidgetCell(val appWidgetId: Int) : FrameLayout(context) {
        lateinit var hostView: AppWidgetHostView
        val materialView = View(context)
        var spanX: Int = 1
        var spanY: Int = 1
        private var dragOriginX = 0f
        private var dragOriginY = 0f
        private var dragging = false
        var reorderPending = false
        private var movedAfterLongPress = false
        private var longPressTriggered = false
        private var gestureCancelled = false
        private var lastRawX = 0f
        private var lastRawY = 0f
        private var downX = 0f
        private var downY = 0f
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val materialBackground = WidgetMaterialBackground { isDarkMode() }
        private val longPressRunnable = Runnable {
            if (!isAttachedToWindow || longPressTriggered || horizontalGesture || gestureCancelled ||
                kotlin.math.hypot(lastRawX - downX, lastRawY - downY) > touchSlop
            ) return@Runnable
            longPressTriggered = true
            dragOriginX = downX
            dragOriginY = downY
            dragging = true
            movedAfterLongPress = false
            startDrag()
            requestDisallowInterceptTouchEvent(true)
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            cancelChildTouch()
        }

        init {
            addView(materialView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            applyCardBackground()
            isClickable = true
        }

        fun applyCardBackground() {
            background = null
            if (materialView.background !== materialBackground) {
                materialView.background = materialBackground
            }
            materialView.clipToOutline = true
            materialView.outlineProvider = roundedOutline(dp(22f).toFloat())
            clipToOutline = true
            outlineProvider = roundedOutline(dp(22f).toFloat())
            materialBackground.invalidateSelf()
        }

        fun updateFallbackBackground(hostView: AppWidgetHostView) {
            // A system-material widget owns its own translucent surface. Keeping the local
            // fallback underneath it makes HyperOS blur that fallback again and darkens it.
            val frame = hostView.findViewById<View>(android.R.id.widget_frame)
            val hasProviderSurface = frame?.background != null || hostView.background != null
            if (hasProviderSurface) materialView.visibility = INVISIBLE
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            applyCardBackground()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (longPressTriggered) return onTouchEvent(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    lastRawX = downX
                    lastRawY = downY
                    gestureCancelled = false
                    longPressTriggered = false
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    lastRawX = ev.rawX
                    lastRawY = ev.rawY
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (kotlin.math.abs(dx) > touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.15f) {
                        removeCallbacks(longPressRunnable)
                        gestureCancelled = true
                        return super.dispatchTouchEvent(ev)
                    }
                    if (kotlin.math.abs(dy) > touchSlop) {
                        removeCallbacks(longPressRunnable)
                        gestureCancelled = true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(longPressRunnable)
                    gestureCancelled = true
                }
            }
            return super.dispatchTouchEvent(ev)
        }

        private fun cancelChildTouch() {
            val now = SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            hostView.dispatchTouchEvent(cancel)
            cancel.recycle()
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragOriginX = ev.rawX
                    dragOriginY = ev.rawY
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!longPressTriggered) return true
                    if (!movedAfterLongPress &&
                        kotlin.math.hypot(ev.rawX - downX, ev.rawY - downY) < dp(6f)
                    ) return true
                    movedAfterLongPress = true
                    translationX = ev.rawX - dragOriginX
                    translationY = ev.rawY - dragOriginY
                    updateDeleteZone(ev.rawX, ev.rawY)
                    if (!deleteZoneDanger) reorderByDrag(this, ev.rawX, ev.rawY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(longPressRunnable)
                    if (dragging) {
                        val delete = ev.actionMasked == MotionEvent.ACTION_UP && deleteZoneDanger
                        finishDrag(ev.actionMasked == MotionEvent.ACTION_UP, delete)
                    }
                    longPressTriggered = false
                    dragging = false
                    movedAfterLongPress = false
                    requestDisallowInterceptTouchEvent(false)
                }
            }
            return true
        }

        private fun startDrag() {
            dragging = true
            widgetDragActive = true
            allowDragOutsideViewport(this)
            pendingOrder = cells.toMutableList()
            pendingDragIndex = cells.indexOf(this)
            showDeleteZone()
            translationZ = dp(16f).toFloat()
            animate().cancel()
            animate()
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(150L)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
                .start()
        }

        private fun finishDrag(commit: Boolean, delete: Boolean = false) {
            widgetDragActive = false
            if (delete) {
                hideDeleteZone()
                animate().cancel()
                animate().alpha(0f).scaleX(0.72f).scaleY(0.72f)
                    .setDuration(180L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.4f))
                    .withEndAction {
                        alpha = 1f
                        scaleX = 1f
                        scaleY = 1f
                        removeCell(this)
                    }
                    .start()
                pendingOrder = null
                pendingDragIndex = -1
                restoreDragClipping()
                return
            }
            hideDeleteZone()
            val order = pendingOrder
            val targetSlot = if (commit && order != null) computeGridSlots(order)[this] else null
            if (!commit) {
                pendingOrder = cells.toMutableList()
                animateAvoidance(this)
            }
            animate().cancel()
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX((targetSlot?.first ?: left) - left.toFloat())
                .translationY((targetSlot?.second ?: top) - top.toFloat())
                .setDuration(180L)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.4f))
                .withEndAction {
                    translationZ = 0f
                    if (commit && order != null) {
                        cells.clear()
                        cells.addAll(order)
                        rebuildGrid()
                        persistOrder()
                    }
                    cells.forEach { item ->
                        item.animate().cancel()
                        item.translationX = 0f
                        item.translationY = 0f
                    }
                    pendingOrder = null
                    pendingDragIndex = -1
                    restoreDragClipping()
                }
                .start()
        }
    }

    private class WidgetStore(context: Context) {
        private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun ids(): List<Int> = prefs.getString(KEY_IDS, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            .orEmpty()

        fun save(ids: List<Int>) {
            prefs.edit().putString(KEY_IDS, ids.joinToString(",")).apply()
        }

        fun remove(appWidgetId: Int) {
            save(ids().filterNot { it == appWidgetId })
        }
    }

    companion object {
        private const val GRID_COLUMNS = 4
        private const val HOST_ID = 0x4849534C
        private const val PREFS_NAME = "hyperisland_lockscreen_widgets"
        private const val KEY_IDS = "widget_ids"
        const val PAGE_TAG = "hyperisland_lockscreen_widget_page"
        private const val PAGE_TITLE = "小组件"
        private const val EMPTY_HINT = "还没有小组件\n点击右上角 + 添加"
        private const val PICKER_TITLE = "添加小组件"
        private const val PICKER_SEARCH_HINT = "搜索小组件"
        private const val PICKER_EMPTY = "没有可添加的小组件"
        private val DARK_PICKER_BACKGROUND = Color.argb(250, 12, 12, 16)
        private val LIGHT_PICKER_BACKGROUND = Color.argb(250, 248, 248, 250)
    }

    private data class WidgetMetrics(
        val width: Int,
        val height: Int,
        val spanX: Int,
        val spanY: Int,
    )
}

/** Best-effort bridge to HyperOS' widget blur and bloom APIs. */
private object WidgetMaterialEffect {
    private const val BLUR_RADIUS = 110
    private const val BLUR_MODE = 1
    private const val VIEW_BLUR_MODE = 1

    // Same 12-value GlassBloom profile used by Personal Assistant for its widget scene.
    private val bloomProfile = floatArrayOf(
        36f, 2.2f, -0.4f, 0.6f, -0.8f, 1.6f, 0.8f, 1.4f,
        0f, 1f, 0f, 0f,
    )

    private var methodsResolved = false
    private var setPassWindowBlurEnabled: Method? = null
    private var setMiBackgroundBlurMode: Method? = null
    private var setMiBackgroundBlurRadius: Method? = null
    private var setMiViewBlurMode: Method? = null
    private var setGlassBloom: Method? = null
    private var setMiViewMaterialType: Method? = null

    @Synchronized
    private fun resolveMethods() {
        if (methodsResolved) return
        methodsResolved = true
        val viewClass = View::class.java
        setPassWindowBlurEnabled = find(viewClass, "setPassWindowBlurEnabled", Boolean::class.javaPrimitiveType!!)
        setMiBackgroundBlurMode = find(viewClass, "setMiBackgroundBlurMode", Int::class.javaPrimitiveType!!)
        setMiBackgroundBlurRadius = find(viewClass, "setMiBackgroundBlurRadius", Int::class.javaPrimitiveType!!)
        setMiViewBlurMode = find(viewClass, "setMiViewBlurMode", Int::class.javaPrimitiveType!!)
        setGlassBloom = find(viewClass, "setGlassBloom", FloatArray::class.java)
        setMiViewMaterialType = find(viewClass, "setMiViewMaterialType", Int::class.javaPrimitiveType!!)
    }

    fun apply(view: View?) {
        if (view == null) return
        resolveMethods()
        invoke(setPassWindowBlurEnabled, view, true)
        invoke(setMiBackgroundBlurMode, view, BLUR_MODE)
        invoke(setMiBackgroundBlurRadius, view, BLUR_RADIUS)
        invoke(setMiViewBlurMode, view, VIEW_BLUR_MODE)
        invoke(setGlassBloom, view, bloomProfile)
    }

    fun clear(view: View?) {
        if (view == null) return
        resolveMethods()
        invoke(setGlassBloom, view, FloatArray(12))
        invoke(setMiViewBlurMode, view, 0)
        invoke(setMiBackgroundBlurMode, view, 0)
        invoke(setPassWindowBlurEnabled, view, false)
    }

    private fun find(clazz: Class<*>, name: String, vararg parameters: Class<*>): Method? =
        runCatching {
            clazz.getDeclaredMethod(name, *parameters).apply { isAccessible = true }
        }.getOrNull()

    private fun invoke(method: Method?, receiver: View, vararg args: Any?) {
        if (method == null) return
        runCatching { method.invoke(receiver, *args) }
            .onFailure { /* Unsupported vendor API: leave the other material layers active. */ }
    }
}

/** Opaque-enough fallback surface for providers that do not render HyperOS glass. */
private class WidgetMaterialBackground(
    private val darkMode: () -> Boolean,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        val radius = (bounds.height() * 0.12f).coerceAtLeast(18f)

        paint.shader = null
        paint.color = if (darkMode()) Color.rgb(26, 26, 26) else Color.rgb(245, 245, 245)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, radius, radius, paint)

        if (darkMode()) {
            paint.shader = LinearGradient(
                0f,
                bounds.top.toFloat(),
                0f,
                bounds.top + bounds.height() * 0.45f,
                Color.argb(26, 255, 255, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.shader = null
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
