package com.minimal.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity() {

    private enum class ScreenMode { HOME, DRAWER, PREFS }

    private lateinit var prefs: LauncherPrefs
    private lateinit var launcherApps: LauncherApps

    private var allApps: List<AppEntry> = emptyList()
    private var visibleApps: List<AppEntry> = emptyList()
    private var hiddenApps: List<AppEntry> = emptyList()

    private var currentScreen = ScreenMode.HOME
    private var prefsReturnScreen = ScreenMode.HOME
    private var searchQuery = ""
    private var activeIndexLetter = 'a'

    private var insetLeft = 0
    private var insetTop = 0
    private var insetRight = 0
    private var insetNavBottom = 0
    private var insetImeBottom = 0

    // Root & screen containers
    private lateinit var rootContainer: FrameLayout
    private lateinit var homeLayer: HomeTouchLayout
    private lateinit var drawerLayer: LinearLayout
    private lateinit var prefsLayer: LinearLayout
    private lateinit var sheetOverlay: FrameLayout
    private lateinit var sheetCard: LinearLayout

    // Home views
    private lateinit var clockContainer: LinearLayout
    private lateinit var clockTimeView: TextView
    private lateinit var clockDateView: TextView
    private lateinit var homePinnedContainer: LinearLayout

    // Drawer views
    private lateinit var drawerScrollView: DrawerScrollView
    private lateinit var drawerAppsContainer: LinearLayout
    private lateinit var drawerSidebar: LinearLayout
    private lateinit var drawerSearchContainer: LinearLayout
    private lateinit var drawerSearchIcon: SearchIconView
    private lateinit var drawerSearchInput: EditText
    private lateinit var drawerSearchClear: TextView
    private lateinit var drawerPrefsButton: FrameLayout
    private lateinit var drawerPrefsGearIcon: GearIconView

    // Preferences views
    private lateinit var prefsHeaderBar: LinearLayout
    private lateinit var prefsBackBtn: TextView
    private lateinit var prefsTitleView: TextView
    private lateinit var prefsScrollView: ScrollView
    private lateinit var prefsContent: LinearLayout

    private val sansRegular by lazy { Typeface.create("sans-serif", Typeface.NORMAL) }
    private val sansMedium by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val sansLight by lazy { Typeface.create("sans-serif-light", Typeface.NORMAL) }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateClockText()
        }
    }

    private val appsCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = postReload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = postReload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = postReload()
        override fun onPackagesAvailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean
        ) = postReload()

        override fun onPackagesUnavailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean
        ) = postReload()

        private fun postReload() {
            reloadAppsAsync()
        }
    }

    private val appExecutor = Executors.newSingleThreadExecutor()
    private var backInvokedCallback: Any? = null
    private var shouldReloadOnResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = LauncherPrefs(this)
        launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

        setupEdgeToEdgeWindow()
        buildViewHierarchy()
        setContentView(rootContainer)
        setupInsetsListener()
        setupBackHandler()

        try {
            launcherApps.registerCallback(appsCallback)
        } catch (_: Throwable) {
        }

        applyThemeColors()
        renderHome()
        renderDrawerList()
        updateClockText()
        updateSystemBarsAppearance()
        reloadAppsAsync()
    }

    override fun onResume() {
        super.onResume()
        updateClockText()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(timeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(timeReceiver, filter)
            }
        } catch (_: Throwable) {
        }
        if (shouldReloadOnResume) {
            reloadAppsAsync()
        }
        updateSystemBarsAppearance()
    }

    override fun onPause() {
        super.onPause()
        shouldReloadOnResume = true
        try {
            unregisterReceiver(timeReceiver)
        } catch (_: Throwable) {
            // Already unregistered
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            launcherApps.unregisterCallback(appsCallback)
        } catch (_: Throwable) {
        }
        appExecutor.shutdownNow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (backInvokedCallback as? OnBackInvokedCallback)?.let {
                try {
                    onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
                } catch (_: Throwable) {
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        hideKeyboard()
        closeOverlay()
        if (currentScreen != ScreenMode.HOME) {
            navigateToHome(animated = true)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        handleBackPress()
    }

    private fun handleBackPress() {
        if (sheetOverlay.visibility == View.VISIBLE) {
            closeOverlay()
            return
        }
        when (currentScreen) {
            ScreenMode.PREFS -> closePreferences()
            ScreenMode.DRAWER -> navigateToHome(animated = true)
            ScreenMode.HOME -> {
                // Intentionally ignore back on home screen so launcher stays active.
            }
        }
    }

    private fun setupBackHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val callback = OnBackInvokedCallback { handleBackPress() }
                backInvokedCallback = callback
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    callback
                )
            } catch (_: Throwable) {
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun setupEdgeToEdgeWindow() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
            } else {
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        } catch (_: Throwable) {
        }
    }

    @Suppress("DEPRECATION")
    private fun setupInsetsListener() {
        rootContainer.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sys = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                val ime = insets.getInsets(WindowInsets.Type.ime())
                insetLeft = sys.left
                insetTop = sys.top
                insetRight = sys.right
                insetNavBottom = sys.bottom
                insetImeBottom = ime.bottom
            } else {
                insetLeft = insets.systemWindowInsetLeft
                insetTop = insets.systemWindowInsetTop
                insetRight = insets.systemWindowInsetRight
                insetNavBottom = insets.systemWindowInsetBottom
                insetImeBottom = 0
            }
            applyLayoutInsets()
            insets
        }
    }

    private fun applyLayoutInsets() {
        val bottomWithIme = maxOf(insetNavBottom, insetImeBottom)
        homeLayer.setPadding(insetLeft, insetTop, insetRight, insetNavBottom)
        drawerLayer.setPadding(insetLeft, insetTop, insetRight, bottomWithIme)
        prefsLayer.setPadding(insetLeft, insetTop, insetRight, insetNavBottom)
        sheetCard.setPadding(dp(18), dp(12), dp(18), dp(16) + bottomWithIme)
    }

    private fun buildViewHierarchy() {
        rootContainer = FrameLayout(this)

        buildHomeLayer()
        buildDrawerLayer()
        buildPrefsLayer()
        buildSheetOverlay()

        rootContainer.addView(
            homeLayer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        rootContainer.addView(
            drawerLayer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        rootContainer.addView(
            prefsLayer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        rootContainer.addView(
            sheetOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun buildHomeLayer() {
        homeLayer = HomeTouchLayout(
            context = this,
            onSwipeUp = { if (prefs.swipeUpAllApps) openDrawer() },
            onSwipeDown = {
                if (prefs.swipeDownNotifications) SystemHelper.expandNotifications(this)
            },
            onLongPressEmpty = { openPreferences(fromDrawer = false) }
        )

        // Soft bottom scrim so lowercase text stays legible over bright wallpapers
        val scrimView = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00000000, 0x26000000, 0x6E000000)
            )
        }
        homeLayer.addView(
            scrimView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val contentCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        clockContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26), dp(28), dp(26), dp(8))
        }

        clockTimeView = TextView(this).apply {
            typeface = sansLight
            setTextColor(0xFFF4F4F5.toInt())
            setShadowLayer(6f, 0f, 2f, 0x80000000.toInt())
            includeFontPadding = false
            setOnClickListener { SystemHelper.openClock(this@MainActivity) }
        }

        clockDateView = TextView(this).apply {
            typeface = sansRegular
            textSize = 14f
            setTextColor(0xCCF4F4F5.toInt())
            setShadowLayer(4f, 0f, 1f, 0x80000000.toInt())
            setPadding(0, dp(4), 0, 0)
            setOnClickListener { SystemHelper.openCalendar(this@MainActivity) }
        }

        clockContainer.addView(clockTimeView)
        clockContainer.addView(clockDateView)

        val spacer = View(this)

        homePinnedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26), dp(12), dp(26), dp(28))
        }

        contentCol.addView(
            clockContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        contentCol.addView(
            spacer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        contentCol.addView(
            homePinnedContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        homeLayer.addView(
            contentCol,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildDrawerLayer() {
        drawerLayer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            isClickable = true
        }

        val listArea = FrameLayout(this)

        drawerScrollView = DrawerScrollView(this) {
            if (prefs.swipeDownHome) {
                navigateToHome(animated = true)
            }
        }.apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        drawerAppsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(42), dp(12))
        }
        drawerScrollView.addView(
            drawerAppsContainer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        drawerSidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(2), dp(12), dp(6), dp(12))
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        val height = view.height.coerceAtLeast(1)
                        val rel = ((event.y / height) * 26).toInt().coerceIn(0, 25)
                        val letter = ('a'.code + rel).toChar()
                        if (activeIndexLetter != letter || event.actionMasked == MotionEvent.ACTION_DOWN) {
                            activeIndexLetter = letter
                            updateSidebarSelection()
                            scrollToDrawerLetter(letter)
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        listArea.addView(
            drawerScrollView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        listArea.addView(
            drawerSidebar,
            FrameLayout.LayoutParams(
                dp(30),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END
            )
        )

        // Bottom bar: search pill + preferences gear button
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(14))
        }

        drawerSearchContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(4), dp(10), dp(4))
        }

        drawerSearchIcon = SearchIconView(this)
        drawerSearchContainer.addView(
            drawerSearchIcon,
            LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(8) }
        )

        drawerSearchInput = EditText(this).apply {
            hint = "search"
            background = null
            typeface = sansRegular
            textSize = 15f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(0, dp(6), 0, dp(6))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    searchQuery = s?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: ""
                    drawerSearchClear.visibility =
                        if (searchQuery.isEmpty()) View.GONE else View.VISIBLE
                    renderDrawerList()
                }
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val topMatch = getFilteredDrawerApps().firstOrNull()
                    if (topMatch != null) {
                        launchFromDrawer(topMatch)
                    }
                    true
                } else {
                    false
                }
            }
        }
        drawerSearchContainer.addView(
            drawerSearchInput,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        drawerSearchClear = TextView(this).apply {
            text = "×"
            textSize = 19f
            typeface = sansRegular
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(8), dp(2), dp(6), dp(2))
            setOnClickListener {
                drawerSearchInput.setText("")
            }
        }
        drawerSearchContainer.addView(drawerSearchClear)

        drawerPrefsButton = FrameLayout(this).apply {
            setOnClickListener { openPreferences(fromDrawer = true) }
        }
        drawerPrefsGearIcon = GearIconView(this)
        drawerPrefsButton.addView(
            drawerPrefsGearIcon,
            FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
        )

        bottomBar.addView(
            drawerSearchContainer,
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(10) }
        )
        bottomBar.addView(
            drawerPrefsButton,
            LinearLayout.LayoutParams(dp(42), dp(42))
        )

        drawerLayer.addView(
            listArea,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        drawerLayer.addView(
            bottomBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun buildPrefsLayer() {
        prefsLayer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            isClickable = true
        }

        prefsHeaderBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        prefsBackBtn = TextView(this).apply {
            text = "←"
            textSize = 20f
            typeface = sansRegular
            setPadding(dp(4), dp(4), dp(14), dp(4))
            setOnClickListener { closePreferences() }
        }

        prefsTitleView = TextView(this).apply {
            text = "preferences"
            textSize = 19f
            typeface = sansMedium
        }

        prefsHeaderBar.addView(prefsBackBtn)
        prefsHeaderBar.addView(prefsTitleView)

        prefsScrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }
        prefsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(28))
        }
        prefsScrollView.addView(
            prefsContent,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        prefsLayer.addView(
            prefsHeaderBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        prefsLayer.addView(
            prefsScrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    private fun buildSheetOverlay() {
        sheetOverlay = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(0x8A000000.toInt())
            isClickable = true
            setOnClickListener { closeOverlay() }
        }

        sheetCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
        }

        sheetOverlay.addView(
            sheetCard,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
    }

    private fun reloadAppsAsync() {
        if (appExecutor.isShutdown) return
        appExecutor.execute {
            val loaded = try {
                val list = SystemHelper.queryAllApps(this, prefs)
                prefs.seedDefaultPinsIfFirstRun(list)
                list
            } catch (_: Throwable) {
                emptyList()
            }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                allApps = loaded
                refreshVisibleAndHiddenLists()
                renderHome()
                renderDrawerList()
                if (currentScreen == ScreenMode.PREFS) renderPreferences()
            }
        }
    }

    private fun refreshLocalAppState() {
        allApps = allApps
            .map { it.copy(customLabel = prefs.getCustomLabel(it.idKey)) }
            .sortedBy { it.displayLabel }
        refreshVisibleAndHiddenLists()
    }

    private fun refreshVisibleAndHiddenLists() {
        val hiddenSet = prefs.getHiddenKeys()
        visibleApps = allApps.filter { !hiddenSet.contains(it.idKey) }
        hiddenApps = allApps.filter { hiddenSet.contains(it.idKey) }
    }

    private fun renderHome() {
        val alignment = prefs.alignment
        val sizeOpt = prefs.textSize

        clockContainer.visibility = if (prefs.showClock) View.VISIBLE else View.GONE
        clockContainer.gravity = alignment.gravity
        clockTimeView.gravity = alignment.gravity
        clockDateView.gravity = alignment.gravity
        clockTimeView.textSize = sizeOpt.clockSp

        homePinnedContainer.gravity = alignment.gravity
        homePinnedContainer.removeAllViews()

        val appByKey = visibleApps.associateBy { it.idKey }
        val pinnedEntries = prefs.getPinnedKeys().mapNotNull { appByKey[it] }

        for (entry in pinnedEntries) {
            val row = TextView(this).apply {
                text = entry.displayLabel
                typeface = sansRegular
                textSize = sizeOpt.homeAppSp
                gravity = alignment.gravity
                setTextColor(0xF5FFFFFF.toInt())
                setShadowLayer(6f, 0f, 2f, 0x88000000.toInt())
                setPadding(dp(4), dp(7), dp(4), dp(7))
                setOnClickListener { SystemHelper.launchApp(this@MainActivity, entry) }
                setOnLongClickListener {
                    showAppActionSheet(entry)
                    true
                }
            }
            homePinnedContainer.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun getFilteredDrawerApps(): List<AppEntry> {
        if (searchQuery.isEmpty()) return visibleApps
        return visibleApps.filter { it.displayLabel.contains(searchQuery) }
    }

    private fun renderDrawerList() {
        val theme = prefs.theme
        val sizeOpt = prefs.textSize
        val filtered = getFilteredDrawerApps()
        val pinnedSet = prefs.getPinnedKeys().toSet()

        drawerAppsContainer.removeAllViews()

        if (filtered.isEmpty()) {
            val empty = TextView(this).apply {
                text = "no matching apps"
                typeface = sansRegular
                textSize = sizeOpt.drawerAppSp
                setTextColor(theme.muted)
                setPadding(dp(8), dp(24), dp(8), dp(24))
            }
            drawerAppsContainer.addView(empty)
        } else {
            for (entry in filtered) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = createRippleDrawable(theme.bg, theme.surface, 10f)
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    setOnClickListener { launchFromDrawer(entry) }
                    setOnLongClickListener {
                        showAppActionSheet(entry)
                        true
                    }
                }
                row.tag = entry.displayLabel.firstOrNull()?.lowercaseChar()

                val labelView = TextView(this).apply {
                    text = entry.displayLabel
                    typeface = sansRegular
                    textSize = sizeOpt.drawerAppSp
                    setTextColor(theme.text)
                    isSingleLine = true
                }
                row.addView(
                    labelView,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )

                if (entry.isWorkProfile) {
                    val workTag = TextView(this).apply {
                        text = "work"
                        typeface = sansRegular
                        textSize = 11f
                        setTextColor(theme.muted)
                        setPadding(dp(6), 0, dp(6), 0)
                    }
                    row.addView(workTag)
                }

                if (pinnedSet.contains(entry.idKey)) {
                    val dot = View(this).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(theme.accent)
                        }
                    }
                    row.addView(
                        dot,
                        LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginStart = dp(8) }
                    )
                }

                drawerAppsContainer.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }

        renderSidebarLetters(filtered)
    }

    private fun renderSidebarLetters(filtered: List<AppEntry>) {
        val available = filtered.mapNotNull {
            it.displayLabel.firstOrNull()?.lowercaseChar()
        }.toSet()

        drawerSidebar.removeAllViews()
        for (i in 0 until 26) {
            val ch = ('a'.code + i).toChar()
            val tv = TextView(this).apply {
                text = ch.toString()
                textSize = 10f
                typeface = sansMedium
                gravity = Gravity.CENTER
                tag = ch
                includeFontPadding = false
            }
            drawerSidebar.addView(
                tv,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }
        updateSidebarSelection(available)
    }

    private fun updateSidebarSelection(
        availableOverride: Set<Char>? = null
    ) {
        val theme = prefs.theme
        val available = availableOverride ?: getFilteredDrawerApps().mapNotNull {
            it.displayLabel.firstOrNull()?.lowercaseChar()
        }.toSet()

        for (i in 0 until drawerSidebar.childCount) {
            val tv = drawerSidebar.getChildAt(i) as? TextView ?: continue
            val ch = tv.tag as? Char ?: continue
            val isActive = ch == activeIndexLetter
            val hasApps = available.contains(ch)
            tv.setTextColor(
                when {
                    isActive -> theme.accent
                    hasApps -> theme.text
                    else -> theme.muted
                }
            )
            tv.alpha = when {
                isActive -> 1f
                hasApps -> 0.8f
                else -> 0.28f
            }
        }
    }

    private fun scrollToDrawerLetter(letter: Char) {
        var target: View? = null
        var fallback: View? = null
        for (i in 0 until drawerAppsContainer.childCount) {
            val child = drawerAppsContainer.getChildAt(i)
            val rowChar = child.tag as? Char ?: continue
            if (rowChar == letter) {
                target = child
                break
            }
            if (fallback == null && rowChar > letter) {
                fallback = child
            }
        }
        val dest = target ?: fallback
        if (dest != null) {
            drawerScrollView.scrollTo(0, dest.top)
        } else if (drawerAppsContainer.childCount > 0) {
            val last = drawerAppsContainer.getChildAt(drawerAppsContainer.childCount - 1)
            drawerScrollView.scrollTo(0, last.top)
        }
    }

    private fun renderPreferences() {
        val theme = prefs.theme
        prefsLayer.setBackgroundColor(theme.bg)
        prefsBackBtn.setTextColor(theme.text)
        prefsTitleView.setTextColor(theme.text)

        prefsHeaderBar.background = GradientDrawable().apply {
            setColor(theme.bg)
        }

        prefsContent.removeAllViews()

        // Section: theme
        prefsContent.addView(sectionLabel("theme", theme))
        val themeGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val themes = LauncherTheme.entries
        for (rowIdx in themes.indices step 2) {
            val pairRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (col in 0..1) {
                val itemIdx = rowIdx + col
                if (itemIdx < themes.size) {
                    val item = themes[itemIdx]
                    val selected = item == theme
                    val pill = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = createRoundedCard(
                            fillColor = if (selected) theme.accent else theme.surface,
                            strokeColor = if (selected) theme.accent else theme.border,
                            radiusDp = 12f
                        )
                        setPadding(dp(12), dp(11), dp(12), dp(11))
                        setOnClickListener {
                            prefs.theme = item
                            applyThemeColors()
                            renderDrawerList()
                            renderPreferences()
                            updateSystemBarsAppearance()
                        }
                    }
                    val swatch = View(this).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(item.bg)
                            setStroke(dp(1), theme.border)
                        }
                    }
                    val label = TextView(this).apply {
                        text = item.label
                        typeface = sansMedium
                        textSize = 13.5f
                        setTextColor(if (selected) theme.pillText else theme.text)
                        isSingleLine = true
                    }
                    pill.addView(
                        swatch,
                        LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(8) }
                    )
                    pill.addView(label)
                    pairRow.addView(
                        pill,
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            if (col == 0) marginEnd = dp(8)
                        }
                    )
                } else {
                    pairRow.addView(
                        View(this),
                        LinearLayout.LayoutParams(0, 1, 1f)
                    )
                }
            }
            themeGrid.addView(
                pairRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            )
        }
        prefsContent.addView(themeGrid)

        // Section: home alignment
        prefsContent.addView(sectionLabel("home alignment", theme))
        prefsContent.addView(
            segmentedRow(
                options = HomeAlignment.entries.map { it.label to (it == prefs.alignment) },
                theme = theme,
                onSelect = { idx ->
                    prefs.alignment = HomeAlignment.entries[idx]
                    renderHome()
                    renderPreferences()
                }
            )
        )

        // Section: text size
        prefsContent.addView(sectionLabel("text size", theme))
        prefsContent.addView(
            segmentedRow(
                options = TextSizeOption.entries.map { it.label to (it == prefs.textSize) },
                theme = theme,
                onSelect = { idx ->
                    prefs.textSize = TextSizeOption.entries[idx]
                    renderHome()
                    renderDrawerList()
                    renderPreferences()
                }
            )
        )

        // Section: home & gestures
        prefsContent.addView(sectionLabel("home & gestures", theme))
        prefsContent.addView(
            toggleRow("show clock & date", prefs.showClock, theme) { checked ->
                prefs.showClock = checked
                renderHome()
                renderPreferences()
            }
        )
        prefsContent.addView(
            toggleRow("swipe up on home → all apps", prefs.swipeUpAllApps, theme) { checked ->
                prefs.swipeUpAllApps = checked
                renderPreferences()
            }
        )
        prefsContent.addView(
            toggleRow("swipe down on drawer → home", prefs.swipeDownHome, theme) { checked ->
                prefs.swipeDownHome = checked
                renderPreferences()
            }
        )
        prefsContent.addView(
            toggleRow(
                "swipe down on home: notifications",
                prefs.swipeDownNotifications,
                theme
            ) { checked ->
                prefs.swipeDownNotifications = checked
                renderPreferences()
            }
        )

        // Section: hidden apps
        prefsContent.addView(sectionLabel("hidden apps", theme))
        val hiddenCount = hiddenApps.size
        val hiddenRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRoundedCard(theme.surface, theme.border, 12f)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            setOnClickListener { showHiddenAppsSheet() }
        }
        val hiddenTitle = TextView(this).apply {
            text = "hidden apps"
            typeface = sansRegular
            textSize = 14.5f
            setTextColor(theme.text)
        }
        val hiddenValue = TextView(this).apply {
            text = "$hiddenCount hidden"
            typeface = sansRegular
            textSize = 13f
            setTextColor(theme.muted)
        }
        hiddenRow.addView(
            hiddenTitle,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        hiddenRow.addView(hiddenValue)
        prefsContent.addView(
            hiddenRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        )
    }

    private fun sectionLabel(text: String, theme: LauncherTheme): TextView {
        return TextView(this).apply {
            this.text = text
            typeface = sansMedium
            textSize = 12f
            setTextColor(theme.muted)
            setPadding(dp(2), dp(14), 0, dp(7))
        }
    }

    private fun segmentedRow(
        options: List<Pair<String, Boolean>>,
        theme: LauncherTheme,
        onSelect: (Int) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        options.forEachIndexed { index, (label, selected) ->
            val btn = TextView(this).apply {
                text = label
                typeface = sansMedium
                textSize = 13.5f
                gravity = Gravity.CENTER
                setTextColor(if (selected) theme.pillText else theme.text)
                background = createRoundedCard(
                    fillColor = if (selected) theme.accent else theme.surface,
                    strokeColor = if (selected) theme.accent else theme.border,
                    radiusDp = 10f
                )
                setPadding(dp(8), dp(10), dp(8), dp(10))
                setOnClickListener { onSelect(index) }
            }
            row.addView(
                btn,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index < options.lastIndex) marginEnd = dp(8)
                }
            )
        }
        return row
    }

    private fun toggleRow(
        label: String,
        checked: Boolean,
        theme: LauncherTheme,
        onToggle: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRoundedCard(theme.surface, theme.border, 12f)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            setOnClickListener { onToggle(!checked) }
        }
        val titleView = TextView(this).apply {
            text = label
            typeface = sansRegular
            textSize = 14f
            setTextColor(theme.text)
        }
        val switchView = ToggleSwitchView(this).apply {
            isChecked = checked
            updateColors(theme)
        }
        row.addView(
            titleView,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(12)
            }
        )
        row.addView(switchView, LinearLayout.LayoutParams(dp(42), dp(24)))
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        return row
    }

    private fun showAppActionSheet(entry: AppEntry) {
        val theme = prefs.theme
        hideKeyboard()
        prepareSheetCard(theme)

        val isPinned = prefs.isPinned(entry.idKey)

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(12))
        }
        val titleView = TextView(this).apply {
            text = entry.displayLabel
            typeface = sansMedium
            textSize = 18f
            setTextColor(theme.text)
        }
        val badgeView = TextView(this).apply {
            text = if (isPinned) "on home" else "drawer only"
            typeface = sansMedium
            textSize = 11.5f
            setTextColor(if (isPinned) theme.pillText else theme.muted)
            background = createRoundedCard(
                fillColor = if (isPinned) theme.accent else theme.bg,
                strokeColor = if (isPinned) theme.accent else theme.border,
                radiusDp = 99f
            )
            setPadding(dp(10), dp(3), dp(10), dp(3))
        }
        headerRow.addView(
            titleView,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        headerRow.addView(badgeView)
        sheetCard.addView(headerRow)

        sheetCard.addView(
            sheetActionRow(
                label = if (isPinned) "unpin from home" else "pin to home",
                color = theme.text,
                theme = theme
            ) {
                prefs.togglePin(entry.idKey)
                closeOverlay()
                renderHome()
                renderDrawerList()
            }
        )

        sheetCard.addView(
            sheetActionRow(
                label = "rename",
                color = theme.text,
                theme = theme
            ) {
                showRenameSheet(entry)
            }
        )

        if (entry.customLabel != null) {
            sheetCard.addView(
                sheetActionRow(
                    label = "reset custom name",
                    color = theme.text,
                    theme = theme
                ) {
                    prefs.setCustomLabel(entry.idKey, null)
                    closeOverlay()
                    refreshLocalAppState()
                    renderHome()
                    renderDrawerList()
                }
            )
        }

        sheetCard.addView(
            sheetActionRow(
                label = "hide app",
                color = theme.text,
                theme = theme
            ) {
                prefs.hideApp(entry.idKey)
                closeOverlay()
                refreshLocalAppState()
                renderHome()
                renderDrawerList()
                if (currentScreen == ScreenMode.PREFS) renderPreferences()
            }
        )

        sheetCard.addView(
            sheetActionRow(
                label = "app info",
                color = theme.text,
                theme = theme
            ) {
                closeOverlay()
                SystemHelper.openAppInfo(this, entry)
            }
        )

        sheetCard.addView(
            sheetActionRow(
                label = "uninstall",
                color = theme.danger,
                theme = theme
            ) {
                closeOverlay()
                SystemHelper.uninstallApp(this, entry)
            }
        )

        openOverlay()
    }

    private fun showRenameSheet(entry: AppEntry) {
        val theme = prefs.theme
        prepareSheetCard(theme)

        val titleView = TextView(this).apply {
            text = "rename"
            typeface = sansMedium
            textSize = 17f
            setTextColor(theme.text)
            setPadding(dp(4), dp(4), dp(4), dp(12))
        }
        sheetCard.addView(titleView)

        val input = EditText(this).apply {
            setText(entry.displayLabel)
            setSelection(text.length)
            typeface = sansRegular
            textSize = 15.5f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(theme.text)
            setHintTextColor(theme.muted)
            background = createRoundedCard(theme.bg, theme.border, 12f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        sheetCard.addView(
            input,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        )

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val cancelBtn = TextView(this).apply {
            text = "cancel"
            typeface = sansMedium
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(theme.text)
            background = createRoundedCard(theme.bg, theme.border, 10f)
            setPadding(dp(12), dp(11), dp(12), dp(11))
            setOnClickListener {
                hideKeyboard()
                closeOverlay()
            }
        }
        val saveBtn = TextView(this).apply {
            text = "save"
            typeface = sansMedium
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(theme.pillText)
            background = createRoundedCard(theme.accent, theme.accent, 10f)
            setPadding(dp(12), dp(11), dp(12), dp(11))
            setOnClickListener {
                val updated = input.text.toString().trim().lowercase(Locale.getDefault())
                prefs.setCustomLabel(entry.idKey, updated)
                hideKeyboard()
                closeOverlay()
                refreshLocalAppState()
                renderHome()
                renderDrawerList()
            }
        }
        btnRow.addView(
            cancelBtn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        )
        btnRow.addView(
            saveBtn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        sheetCard.addView(btnRow)

        openOverlay()
        input.requestFocus()
        input.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun showHiddenAppsSheet() {
        val theme = prefs.theme
        prepareSheetCard(theme)

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(10))
        }
        val titleView = TextView(this).apply {
            text = "hidden apps"
            typeface = sansMedium
            textSize = 17f
            setTextColor(theme.text)
        }
        headerRow.addView(
            titleView,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        if (hiddenApps.isNotEmpty()) {
            val restoreAllBtn = TextView(this).apply {
                text = "restore all"
                typeface = sansMedium
                textSize = 13f
                setTextColor(theme.accent)
                setPadding(dp(8), dp(4), dp(4), dp(4))
                setOnClickListener {
                    prefs.restoreAllHidden()
                    closeOverlay()
                    refreshLocalAppState()
                    renderDrawerList()
                    renderPreferences()
                }
            }
            headerRow.addView(restoreAllBtn)
        }
        sheetCard.addView(headerRow)

        if (hiddenApps.isEmpty()) {
            val empty = TextView(this).apply {
                text = "no hidden apps"
                typeface = sansRegular
                textSize = 14f
                setTextColor(theme.muted)
                setPadding(dp(4), dp(12), dp(4), dp(14))
            }
            sheetCard.addView(empty)
        } else {
            val scroll = ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
            }
            val listCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            for (entry in hiddenApps) {
                listCol.addView(
                    sheetActionRow(
                        label = "${entry.displayLabel}  ·  restore",
                        color = theme.text,
                        theme = theme
                    ) {
                        prefs.unhideApp(entry.idKey)
                        refreshLocalAppState()
                        renderDrawerList()
                        renderPreferences()
                        if (hiddenApps.isEmpty()) {
                            closeOverlay()
                        } else {
                            showHiddenAppsSheet()
                        }
                    }
                )
            }
            scroll.addView(listCol)
            val maxHeight = (resources.displayMetrics.heightPixels * 0.45f).toInt()
            sheetCard.addView(
                scroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    if (hiddenApps.size > 6) maxHeight else LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        openOverlay()
    }

    private fun prepareSheetCard(theme: LauncherTheme) {
        sheetCard.removeAllViews()
        sheetCard.background = GradientDrawable().apply {
            setColor(theme.surface)
            val r = dp(20).toFloat()
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setStroke(dp(1), theme.border)
        }
        val handle = View(this).apply {
            background = GradientDrawable().apply {
                setColor(theme.muted)
                cornerRadius = dp(99).toFloat()
            }
            alpha = 0.45f
        }
        sheetCard.addView(
            handle,
            LinearLayout.LayoutParams(dp(36), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(10)
            }
        )
    }

    private fun sheetActionRow(
        label: String,
        color: Int,
        theme: LauncherTheme,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            typeface = sansRegular
            textSize = 15f
            setTextColor(color)
            background = createRippleDrawable(theme.bg, theme.surface, 10f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(7) }
            setOnClickListener { onClick() }
        }
    }

    private fun openOverlay() {
        sheetOverlay.animate().cancel()
        if (sheetOverlay.visibility != View.VISIBLE) {
            sheetOverlay.alpha = 0f
            sheetOverlay.visibility = View.VISIBLE
            sheetOverlay.animate().alpha(1f).setDuration(150L).setListener(null).start()
        }
    }

    private fun closeOverlay() {
        if (sheetOverlay.visibility == View.VISIBLE) {
            hideKeyboard()
            sheetOverlay.animate()
                .alpha(0f)
                .setDuration(120L)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        sheetOverlay.visibility = View.GONE
                    }
                })
                .start()
        }
    }

    private fun openDrawer() {
        if (currentScreen == ScreenMode.DRAWER) return
        currentScreen = ScreenMode.DRAWER
        drawerScrollView.scrollTo(0, 0)
        drawerLayer.animate().cancel()
        drawerLayer.visibility = View.VISIBLE
        drawerLayer.alpha = 0f
        drawerLayer.translationY = dp(28).toFloat()
        drawerLayer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180L)
            .setInterpolator(DecelerateInterpolator())
            .setListener(null)
            .start()
        updateSystemBarsAppearance()
    }

    private fun navigateToHome(animated: Boolean) {
        hideKeyboard()
        closeOverlay()
        currentScreen = ScreenMode.HOME
        if (drawerSearchInput.text.isNotEmpty()) {
            drawerSearchInput.setText("")
        }
        prefsLayer.visibility = View.GONE
        if (!animated) {
            drawerLayer.visibility = View.GONE
            updateSystemBarsAppearance()
            return
        }
        if (drawerLayer.visibility == View.VISIBLE) {
            drawerLayer.animate()
                .alpha(0f)
                .translationY(dp(24).toFloat())
                .setDuration(150L)
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (currentScreen == ScreenMode.HOME) {
                            drawerLayer.visibility = View.GONE
                            drawerLayer.translationY = 0f
                            drawerLayer.alpha = 1f
                        }
                    }
                })
                .start()
        }
        updateSystemBarsAppearance()
    }

    private fun openPreferences(fromDrawer: Boolean) {
        hideKeyboard()
        closeOverlay()
        prefsReturnScreen = if (fromDrawer) ScreenMode.DRAWER else ScreenMode.HOME
        currentScreen = ScreenMode.PREFS
        renderPreferences()
        prefsLayer.animate().cancel()
        prefsLayer.visibility = View.VISIBLE
        prefsLayer.alpha = 0f
        prefsLayer.animate().alpha(1f).setDuration(150L).setListener(null).start()
        updateSystemBarsAppearance()
    }

    private fun closePreferences() {
        currentScreen = prefsReturnScreen
        prefsLayer.animate()
            .alpha(0f)
            .setDuration(130L)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (currentScreen != ScreenMode.PREFS) {
                        prefsLayer.visibility = View.GONE
                    }
                }
            })
            .start()
        updateSystemBarsAppearance()
    }

    private fun launchFromDrawer(entry: AppEntry) {
        hideKeyboard()
        SystemHelper.launchApp(this, entry)
        navigateToHome(animated = false)
    }

    private fun applyThemeColors() {
        val theme = prefs.theme
        drawerLayer.setBackgroundColor(theme.bg)
        drawerSearchContainer.background = createRoundedCard(theme.surface, theme.border, 999f)
        drawerSearchIcon.setColor(theme.muted)
        drawerSearchInput.setTextColor(theme.text)
        drawerSearchInput.setHintTextColor(theme.muted)
        drawerSearchClear.setTextColor(theme.muted)
        drawerPrefsButton.background = createRoundedCard(theme.surface, theme.border, 999f)
        drawerPrefsGearIcon.setColor(theme.text)
    }

    @Suppress("DEPRECATION")
    private fun updateSystemBarsAppearance() {
        val useLightBars = when (currentScreen) {
            ScreenMode.HOME -> isWallpaperBright()
            ScreenMode.DRAWER, ScreenMode.PREFS -> prefs.theme.isLight
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return
            val flags = if (useLightBars) {
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            } else {
                0
            }
            controller.setSystemBarsAppearance(
                flags,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            var vis = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            if (useLightBars) {
                vis = vis or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vis = vis or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
            window.decorView.systemUiVisibility = vis
        }
    }

    private fun isWallpaperBright(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            val wm = WallpaperManager.getInstance(this)
            val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return false
            (colors.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun updateClockText() {
        try {
            val now = Date()
            val timePattern = if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a"
            clockTimeView.text = SimpleDateFormat(timePattern, Locale.getDefault())
                .format(now)
                .lowercase(Locale.getDefault())
            clockDateView.text = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                .format(now)
                .lowercase(Locale.getDefault())
        } catch (_: Throwable) {
        }
    }

    private fun hideKeyboard() {
        drawerSearchInput.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(rootContainer.windowToken, 0)
    }

    private fun createRoundedCard(fillColor: Int, strokeColor: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
            cornerRadius = dp(radiusDp)
        }
    }

    private fun createRippleDrawable(fillColor: Int, pressedColor: Int, radiusDp: Float): RippleDrawable {
        val content = GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = dp(radiusDp)
        }
        val mask = GradientDrawable().apply {
            setColor(0xFFFFFFFF.toInt())
            cornerRadius = dp(radiusDp)
        }
        val rippleColor = if (fillColor == pressedColor) 0x33888888 else pressedColor
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )

    // --- Custom lightweight views ---

    private class HomeTouchLayout(
        context: Context,
        private val onSwipeUp: () -> Unit,
        private val onSwipeDown: () -> Unit,
        private val onLongPressEmpty: () -> Unit
    ) : FrameLayout(context) {

        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val swipeThreshold =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, context.resources.displayMetrics)

        private var startX = 0f
        private var startY = 0f
        private var intercepting = false

        init {
            isLongClickable = true
            setOnLongClickListener {
                onLongPressEmpty()
                true
            }
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.x
                    startY = ev.y
                    intercepting = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(ev.x - startX)
                    val dy = abs(ev.y - startY)
                    if (dy > touchSlop && dy > dx * 1.15f) {
                        intercepting = true
                        cancelLongPress()
                        return true
                    }
                }
            }
            return false
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    intercepting = false
                    super.onTouchEvent(event)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(event.x - startX)
                    val dy = abs(event.y - startY)
                    if (dx > touchSlop || dy > touchSlop) {
                        cancelLongPress()
                    }
                    return if (intercepting) true else super.onTouchEvent(event)
                }
                MotionEvent.ACTION_UP -> {
                    val dy = event.y - startY
                    val dx = abs(event.x - startX)
                    if (abs(dy) > swipeThreshold && abs(dy) > dx) {
                        cancelLongPress()
                        if (dy < 0) onSwipeUp() else onSwipeDown()
                        return true
                    }
                    return super.onTouchEvent(event)
                }
            }
            return super.onTouchEvent(event)
        }
    }

    private class DrawerScrollView(
        context: Context,
        private val onPullDownAtTop: () -> Unit
    ) : ScrollView(context) {

        private val swipeThreshold =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64f, context.resources.displayMetrics)
        private var downY = 0f
        private var startedAtTop = false
        private var triggered = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            trackEdgePull(ev)
            return super.onInterceptTouchEvent(ev)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (trackEdgePull(ev)) return true
            return super.onTouchEvent(ev)
        }

        private fun trackEdgePull(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = ev.y
                    startedAtTop = scrollY == 0
                    triggered = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (startedAtTop && scrollY == 0 && !triggered && (ev.y - downY) > swipeThreshold) {
                        triggered = true
                        onPullDownAtTop()
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    startedAtTop = false
                    triggered = false
                }
            }
            return false
        }
    }

    private class SearchIconView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1.8f, context.resources.displayMetrics
            )
        }

        fun setColor(color: Int) {
            paint.color = color
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val r = minOf(w, h) * 0.30f
            val cx = w * 0.42f
            val cy = h * 0.42f
            canvas.drawCircle(cx, cy, r, paint)
            canvas.drawLine(cx + r * 0.72f, cy + r * 0.72f, w * 0.86f, h * 0.86f, paint)
        }
    }

    private class GearIconView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1.7f, context.resources.displayMetrics
            )
        }

        fun setColor(color: Int) {
            paint.color = color
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val outer = minOf(width, height) * 0.34f
            val inner = outer * 0.44f
            canvas.drawCircle(cx, cy, inner, paint)
            canvas.drawCircle(cx, cy, outer * 0.82f, paint)
            for (i in 0 until 8) {
                val angle = Math.toRadians(i * 45.0)
                val cos = kotlin.math.cos(angle).toFloat()
                val sin = kotlin.math.sin(angle).toFloat()
                canvas.drawLine(
                    cx + cos * outer * 0.86f,
                    cy + sin * outer * 0.86f,
                    cx + cos * outer * 1.15f,
                    cy + sin * outer * 1.15f,
                    paint
                )
            }
        }
    }

    private class ToggleSwitchView(context: Context) : View(context) {
        var isChecked: Boolean = false
        private var trackOnColor = 0xFFFFFFFF.toInt()
        private var trackOffColor = 0xFF18181B.toInt()
        private var thumbOnColor = 0xFF000000.toInt()
        private var thumbOffColor = 0xFF71717A.toInt()

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val trackRect = RectF()

        fun updateColors(theme: LauncherTheme) {
            trackOnColor = theme.accent
            trackOffColor = theme.bg
            thumbOnColor = theme.pillText
            thumbOffColor = theme.muted
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val r = h / 2f
            trackRect.set(0f, 0f, w, h)
            paint.color = if (isChecked) trackOnColor else trackOffColor
            canvas.drawRoundRect(trackRect, r, r, paint)

            val pad = h * 0.16f
            val thumbR = (h - pad * 2f) / 2f
            val cx = if (isChecked) w - pad - thumbR else pad + thumbR
            paint.color = if (isChecked) thumbOnColor else thumbOffColor
            canvas.drawCircle(cx, h / 2f, thumbR, paint)
        }
    }
}
