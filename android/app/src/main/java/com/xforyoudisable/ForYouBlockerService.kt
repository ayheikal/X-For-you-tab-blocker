package com.xforyoudisable

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches the X app. When the Home "For you" tab is selected it taps
 * "Following", and it keeps an overlay over the "For you" tab so it can't be
 * tapped or seen.
 */
class ForYouBlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val scanRunnable = Runnable { scan() }
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    private var scanPending = false
    private var overlay: View? = null
    private val overlayBounds = Rect()
    private var lastClickAt = 0L

    private var lastDiagnosticsAt = 0L

    override fun onServiceConnected() {
        Log.d(TAG, "service connected")
        Diagnostics.write(this, Diagnostics.KEY_CONNECTED, Diagnostics.timestamp())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        if (pkg != X_PACKAGE) {
            // Another app (or the launcher) came to the foreground.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) removeOverlay()
            return
        }

        // Throttle rather than debounce: content events fire continuously
        // while scrolling, and the overlay must keep following the tab bar.
        if (!scanPending) {
            scanPending = true
            handler.postDelayed(scanRunnable, SCAN_DELAY_MS)
        }
    }

    private fun scan() {
        scanPending = false

        val root = findXRoot()
        if (root == null) {
            removeOverlay()
            return
        }

        val tabs = findHomeTabs(root)
        recordDiagnostics(root, tabs)
        if (tabs == null) {
            removeOverlay()
            return
        }
        val (forYou, following) = tabs

        val now = SystemClock.uptimeMillis()
        if (isSelected(forYou) && !isSelected(following) && now - lastClickAt > CLICK_COOLDOWN_MS) {
            lastClickAt = now
            val clicked = clickTarget(following).performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "For you was selected, clicked Following: $clicked")
        }

        val bounds = Rect()
        clickTarget(forYou).getBoundsInScreen(bounds)
        if (bounds.isEmpty) removeOverlay() else showOverlay(bounds)
    }

    /**
     * The active window isn't always X's main window (keyboard, popups, split
     * screen), so fall back to searching every window on screen.
     */
    private fun findXRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { if (it.packageName?.toString() == X_PACKAGE) return it }
        return try {
            windows.firstNotNullOfOrNull { window ->
                window.root?.takeIf { it.packageName?.toString() == X_PACKAGE }
            }
        } catch (e: RuntimeException) {
            null
        }
    }

    /**
     * Saves what the service sees at the top of X's screen, so it can be read
     * from the app without adb. Throttled because it walks the whole tree.
     */
    private fun recordDiagnostics(
        root: AccessibilityNodeInfo,
        tabs: Pair<AccessibilityNodeInfo, AccessibilityNodeInfo>?,
    ) {
        val now = SystemClock.uptimeMillis()
        if (now - lastDiagnosticsAt < DIAGNOSTICS_INTERVAL_MS) return
        lastDiagnosticsAt = now

        val topLimit = resources.displayMetrics.heightPixels / 3
        val lines = mutableListOf<String>()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > MAX_DIAGNOSTICS_DEPTH || lines.size >= MAX_DIAGNOSTICS_LINES) return
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            if ((text.isNotEmpty() || desc.isNotEmpty()) && !bounds.isEmpty && bounds.top < topLimit) {
                lines += "text=\"$text\" desc=\"$desc\" sel=${node.isSelected} click=${node.isClickable} " +
                    "id=${node.viewIdResourceName?.substringAfter('/')} cls=${node.className?.toString()?.substringAfterLast('.')} " +
                    "y=${bounds.top}-${bounds.bottom} x=${bounds.left}-${bounds.right}"
            }
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)

        val summary = if (tabs == null) {
            "tabs NOT found (For you nodes=${findLabelled(root, FOR_YOU_LABELS).size}, " +
                "Following nodes=${findLabelled(root, FOLLOWING_LABELS).size})"
        } else {
            "tabs found, For you selected=${isSelected(tabs.first)}, Following selected=${isSelected(tabs.second)}"
        }
        Diagnostics.write(
            this,
            Diagnostics.KEY_REPORT,
            "${Diagnostics.timestamp()} $summary\n\n" + lines.joinToString("\n"),
        )
    }

    /**
     * Finds the "For you" and "Following" tab nodes. Both labels must sit on
     * the same row, which rules out posts or buttons that happen to contain
     * the same words (and Explore's own "For You" tab).
     */
    private fun findHomeTabs(root: AccessibilityNodeInfo): Pair<AccessibilityNodeInfo, AccessibilityNodeInfo>? {
        val forYouNodes = findLabelled(root, FOR_YOU_LABELS)
        if (forYouNodes.isEmpty()) return null
        val followingNodes = findLabelled(root, FOLLOWING_LABELS)

        for (forYou in forYouNodes) {
            val a = Rect().also { forYou.getBoundsInScreen(it) }
            for (following in followingNodes) {
                val b = Rect().also { following.getBoundsInScreen(it) }
                if (!a.isEmpty && !b.isEmpty && a.top < b.bottom && b.top < a.bottom) {
                    return forYou to following
                }
            }
        }
        return null
    }

    private fun findLabelled(root: AccessibilityNodeInfo, labels: List<String>): List<AccessibilityNodeInfo> =
        labels.flatMap { label ->
            root.findAccessibilityNodeInfosByText(label).filter { node ->
                node.text?.toString()?.trim().equals(label, ignoreCase = true) ||
                    node.contentDescription?.toString()?.trim()?.startsWith(label, ignoreCase = true) == true
            }
        }

    /**
     * The label is usually a TextView inside the tab view that holds the
     * selected state, but some X versions set it on a sibling or child instead.
     */
    private fun isSelected(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(SELECTED_ANCESTOR_DEPTH) {
            if (current?.isSelected == true) return true
            current = current?.parent
        }
        return hasSelectedDescendant(clickTarget(node), 0)
    }

    private fun hasSelectedDescendant(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > SELECTED_DESCENDANT_DEPTH) return false
        if (node.isSelected) return true
        return (0 until node.childCount).any { hasSelectedDescendant(node.getChild(it), depth + 1) }
    }

    private fun clickTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo? = node
        repeat(CLICKABLE_ANCESTOR_DEPTH) {
            val c = current ?: return node
            if (c.isClickable) return c
            current = c.parent
        }
        return node
    }

    private fun showOverlay(bounds: Rect) {
        val existing = overlay
        if (existing != null && bounds == overlayBounds) return

        val params = (existing?.layoutParams as? WindowManager.LayoutParams) ?: WindowManager.LayoutParams(
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width()
        params.height = bounds.height()

        try {
            if (existing == null) {
                // A clickable view swallows taps so they never reach the tab underneath.
                val view = View(this).apply {
                    setBackgroundColor(overlayColor())
                    isClickable = true
                }
                windowManager.addView(view, params)
                overlay = view
            } else {
                windowManager.updateViewLayout(existing, params)
            }
            overlayBounds.set(bounds)
        } catch (e: RuntimeException) {
            Log.w(TAG, "overlay update failed", e)
            overlay = null
        }
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        overlay = null
        overlayBounds.setEmpty()
        try {
            windowManager.removeViewImmediate(view)
        } catch (e: RuntimeException) {
            Log.w(TAG, "overlay removal failed", e)
        }
    }

    /**
     * X follows the system theme by default. If you use X's "Dim" dark theme
     * instead of "Lights out", change the dark color to 0xFF15202B.
     */
    private fun overlayColor(): Int {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (night == Configuration.UI_MODE_NIGHT_YES) Color.BLACK else Color.WHITE
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ForYouBlocker"
        private const val X_PACKAGE = "com.twitter.android"

        // Add your X UI language's tab labels here if it isn't English.
        private val FOR_YOU_LABELS = listOf("For you")
        private val FOLLOWING_LABELS = listOf("Following")

        private const val SCAN_DELAY_MS = 120L
        private const val CLICK_COOLDOWN_MS = 800L
        private const val SELECTED_ANCESTOR_DEPTH = 3
        private const val SELECTED_DESCENDANT_DEPTH = 3
        private const val CLICKABLE_ANCESTOR_DEPTH = 4

        private const val DIAGNOSTICS_INTERVAL_MS = 2000L
        private const val MAX_DIAGNOSTICS_DEPTH = 40
        private const val MAX_DIAGNOSTICS_LINES = 60
    }
}
