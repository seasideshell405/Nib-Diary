package com.diary.app.ui

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.diary.app.DiaryApplication
import com.diary.app.R
import com.diary.app.data.DiaryDates
import com.diary.app.data.UiPrefsStore
import com.diary.app.ui.applock.BiometricAuth
import com.diary.app.ui.applock.UnlockScreen
import com.diary.app.ui.calendar.CalendarScreen
import com.diary.app.ui.calendar.CalendarViewModel
import com.diary.app.ui.detail.ReadingOverlay
import com.diary.app.ui.diary.DiaryListScreen
import com.diary.app.ui.diary.DiaryListViewModel
import com.diary.app.ui.diary.EditorScreen
import com.diary.app.ui.mine.MineScreen
import com.diary.app.ui.mine.MineViewModel
import com.diary.app.ui.random.RandomScreen
import com.diary.app.ui.search.SearchScreen
import com.diary.app.ui.settings.SettingsScreen
import com.diary.app.ui.ImageDecoder
import com.diary.app.ui.detail.BlockStyles
import com.diary.app.ui.detail.LocalBlockStyles
import com.diary.app.ui.theme.LocalBackgroundBrightness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

enum class DiaryTab(
    val labelRes: Int,
    val route: String,
) {
    DIARY(R.string.tab_diary, "diary"),
    CALENDAR(R.string.tab_calendar, "calendar"),
    MINE(R.string.tab_mine, "mine"),
}

private object Routes {
    const val SETTINGS = "settings"
    const val BLOCK_STYLES = "blockStyles"
    const val EDITOR = "editor?entryId={entryId}&date={date}"
    const val SEARCH = "search"
    const val RANDOM = "random"
    const val MEDIA_LIBRARY = "mediaLibrary"

    fun editor(entryId: String?, date: java.time.LocalDate? = null): String = buildString {
        append("editor?entryId=$entryId")
        if (date != null) append("&date=$date")
    }
}

/**
 * Tab-to-tab navigation slides horizontally: moving right in the tab order
 * slides in from the right, moving left slides in from the left. Returns
 * null when either side is not a tab (plain fade/scale then).
 */
private fun slideBetween(initial: String?, target: String?): Boolean? {
    val from = DiaryTab.entries.indexOfFirst { it.route == initial }.takeIf { it >= 0 } ?: return null
    val to = DiaryTab.entries.indexOfFirst { it.route == target }.takeIf { it >= 0 } ?: return null
    return to > from
}

@Composable
fun DiaryApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current
    val container = (context.applicationContext as DiaryApplication).container
    val lockStore = container.lockStore
    val appearanceStore = container.appearanceStore

    // App lock: shown at startup if enabled (cold start always locks), and
    // — when "lock on background" is on (default) — every time the app
    // leaves the foreground. The flag is set on ON_STOP while the activity
    // is still in the background, so the first frame on return is already
    // the lock screen: no flash of app content, and the task switcher
    // preview shows the lock too. With the option off, backgrounding does
    // not lock; only a fresh app start (initial locked state) does.
    var locked by remember { mutableStateOf(lockStore.isEnabled()) }

    // Status bar icons pick dark/light against what the status bar area
    // ACTUALLY shows:
    //  - tab pages: the TopStrip (surface blended with the background at
    //    the surface alpha);
    //  - pages with their own backing surface (editor, random, block
    //    styles, lock screen): that surface color;
    //  - bare pages (settings, search, media library): the background
    //    image itself.
    // The app is light-only for now (no dark mode adaptation).
    val surfaceAlpha by container.uiPrefs.surfaceAlpha.collectAsStateWithLifecycle()
    val view = LocalView.current
    val backgroundBrightness = LocalBackgroundBrightness.current
    val onTabPage = DiaryTab.entries.any { it.route == currentRoute }
    val onOwnSurface = locked ||
        currentRoute == Routes.EDITOR ||
        currentRoute == Routes.RANDOM ||
        currentRoute == Routes.BLOCK_STYLES
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val surface = 0.98f
        val effective = when {
            onTabPage -> surface * surfaceAlpha + backgroundBrightness * (1f - surfaceAlpha)
            onOwnSurface -> surface
            else -> backgroundBrightness
        }
        WindowCompat.getInsetsController(window, view)
            .isAppearanceLightStatusBars = effective > 0.55f
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP &&
                lockStore.isEnabled() && lockStore.lockOnBackground()
            ) {
                locked = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val biometricAvailable = remember { BiometricAuth.isAvailable(context) }

    // Background image, re-decoded on appearance change. The default
    // drawable is decoded OFF the main thread too: decoding a full-screen
    // JPEG during the first composition stalls the launch animation.
    val appearanceVersion by appearanceStore.version.collectAsStateWithLifecycle()
    val backgroundBitmap by produceState<ImageBitmap?>(null, appearanceVersion) {
        value = withContext(Dispatchers.IO) {
            if (appearanceStore.hasCustomBackground()) {
                ImageDecoder.decodeSampled(appearanceStore.backgroundFile, 2000)?.asImageBitmap()
            } else {
                runCatching {
                    context.resources.openRawResource(R.drawable.diary_bg)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }

    // Entry reading opens as an in-app overlay (not a system Dialog), so the
    // entry card can share-element into it.
    var readingEntryId by remember { mutableStateOf<String?>(null) }

    val showTabBar = currentRoute in DiaryTab.entries.map { it.route }

    // Height reserved at the top of tab pages: measured from the actual
    // floating tab bar so content is never covered by it.
    var tabBarHeight by remember { mutableStateOf(0.dp) }

    // Remember the last tab so the highlighted segment stays while a
    // pushed page (editor/search/...) covers the tabs.
    var lastTab by remember { mutableStateOf(DiaryTab.DIARY) }
    LaunchedEffect(currentRoute) {
        DiaryTab.entries.firstOrNull { it.route == currentRoute }?.let { lastTab = it }
    }
    val selectedTab = DiaryTab.entries.firstOrNull { it.route == currentRoute } ?: lastTab

    // The new-entry FAB lives on every tab, hidden on pushed pages.
    val fabScope = rememberCoroutineScope()
    // Tab view models live at activity scope: they survive tab switches so
    // stats/lists are not recomputed on every visit.
    val listViewModel: DiaryListViewModel = viewModel(factory = DiaryListViewModel.Factory)
    val calendarViewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory)
    val mineViewModel: MineViewModel = viewModel(factory = MineViewModel.Factory)

    // Returning to a tab from the editor refreshes the lists: Room flow
    // invalidation can lag behind, leaving a stale card visible.
    LaunchedEffect(currentRoute) {
        if (currentRoute in DiaryTab.entries.map { it.route }) {
            listViewModel.refresh()
            calendarViewModel.refresh()
        }
    }

    fun selectTab(tab: DiaryTab) {
        // Read the back stack top SYNCHRONOUSLY. currentBackStackEntryAsState()
        // updates a few frames after a navigation, so during/right after the
        // tab transition the composable route can lag behind — tapping the
        // tab that is already animating in would then navigate again,
        // popUpTo() pops it, and the new entry replays the whole transition.
        // currentBackStackEntry reflects the real stack immediately.
        val topRoute = navController.currentBackStackEntry?.destination?.route
        if (topRoute == tab.route) return
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // While locked the app renders ONLY the unlock screen: no background
    // decode, no DB-driven lists, no nav graph behind the PIN. The whole
    // main UI is composed on first unlock instead, so the PIN appears
    // instantly even on a cold start.
    if (!locked) {
        val blockStylesJson by appearanceStore.blockStylesJson.collectAsStateWithLifecycle()
        val blockStyles = remember(blockStylesJson) {
            blockStylesJson?.let { BlockStyles.parse(it) } ?: BlockStyles.defaults
        }
        CompositionLocalProvider(LocalBlockStyles provides blockStyles) {
        // Sky background under everything. A translucent mask (settings
        // toggle) dims the image so foreground content reads better.
        val uiPrefs = remember { container.uiPrefs }
        val bgMaskEnabled by uiPrefs.backgroundMask.collectAsStateWithLifecycle()
        val maskStrength by uiPrefs.maskStrength.collectAsStateWithLifecycle()
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            DiaryBackground(backgroundBitmap)
            if (bgMaskEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = maskStrength)),
                )
            }
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                // The new-entry FAB fades/shrinks out when a pushed page
                // (editor/search/random) covers the tabs, instead of
                // vanishing abruptly.
                AnimatedVisibility(
                    visible = showTabBar,
                    enter = scaleIn(initialScale = 0.5f, animationSpec = tween(220)) +
                        fadeIn(animationSpec = tween(220)),
                    exit = scaleOut(targetScale = 0.5f, animationSpec = tween(180)) +
                        fadeOut(animationSpec = tween(180)),
                ) {
                    // Press feedback for the new-entry FAB: vibration + scale.
                    val fabInteraction = remember { MutableInteractionSource() }
                    val fabPressed by fabInteraction.collectIsPressedAsState()
                    val fabScale by animateFloatAsState(
                        targetValue = if (fabPressed) 0.9f else 1f,
                        label = "fabPress",
                    )
                    val fabUiPrefs = remember { UiPrefsStore(context) }
                    Box(Modifier.graphicsLayer { scaleX = fabScale; scaleY = fabScale }) {
                        FloatingActionButton(
                            onClick = {
                                if (fabUiPrefs.hapticEnabled) vibrateTick(context)
                                // One diary per day: on the calendar tab the FAB
                                // targets the selected date; elsewhere, today.
                                fabScope.launch {
                                    val date = if (currentRoute == DiaryTab.CALENDAR.route) {
                                        calendarViewModel.selectedDate.value
                                    } else {
                                        DiaryDates.defaultDiaryDate()
                                    }
                                    val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    val existing = listViewModel.entryForDate(start, end)
                                    if (existing != null) {
                                        navController.navigate(Routes.editor(existing.id))
                                    } else {
                                        navController.navigate(Routes.editor(null, date))
                                    }
                                }
                            },
                            interactionSource = fabInteraction,
                            modifier = Modifier.padding(bottom = 24.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "新建日记")
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = DiaryTab.DIARY.route,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                enterTransition = {
                    when (slideBetween(initialState.destination.route, targetState.destination.route)) {
                        // Push animation between tabs: the new page slides
                        // in from the side it is heading towards.
                        true -> slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
                        false -> slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300))
                        null -> slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) +
                            fadeIn(animationSpec = tween(300))
                    }
                },
                exitTransition = {
                    when (slideBetween(initialState.destination.route, targetState.destination.route)) {
                        true -> slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
                        false -> slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                        null -> scaleOut(targetScale = 0.92f, animationSpec = tween(300)) +
                            fadeOut(animationSpec = tween(300))
                    }
                },
                popEnterTransition = {
                    when (slideBetween(initialState.destination.route, targetState.destination.route)) {
                        true -> slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
                        false -> slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300))
                        null -> scaleIn(initialScale = 0.92f, animationSpec = tween(300)) +
                            fadeIn(animationSpec = tween(300))
                    }
                },
                popExitTransition = {
                    when (slideBetween(initialState.destination.route, targetState.destination.route)) {
                        true -> slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
                        false -> slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                        null -> slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) +
                            fadeOut(animationSpec = tween(300))
                    }
                },
            ) {
                    composable(DiaryTab.DIARY.route) {
                        Box(Modifier.fillMaxSize().padding(top = tabBarHeight)) {
                            DiaryListScreen(
                                onOpenEntry = { id -> readingEntryId = id },
                                onSearch = { navController.navigate(Routes.SEARCH) },
                                onRandom = { navController.navigate(Routes.RANDOM) },
                                viewModel = listViewModel,
                            )
                        }
                    }
                    composable(DiaryTab.CALENDAR.route) {
                        Box(Modifier.fillMaxSize().padding(top = tabBarHeight)) {
                            CalendarScreen(
                                onOpenEntry = { id -> readingEntryId = id },
                                viewModel = calendarViewModel,
                            )
                        }
                    }
                    composable(DiaryTab.MINE.route) {
                        Box(Modifier.fillMaxSize().padding(top = tabBarHeight)) {
                            MineScreen(
                                onOpenMediaLibrary = { navController.navigate(Routes.MEDIA_LIBRARY) },
                                viewModel = mineViewModel,
                            )
                        }
                    }
                    composable(Routes.MEDIA_LIBRARY) {
                        com.diary.app.ui.media.MediaLibraryScreen(
                            onOpenEntry = { id -> readingEntryId = id },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onClose = { navController.popBackStack() },
                            onOpenBlockStyles = { navController.navigate(Routes.BLOCK_STYLES) },
                        )
                    }
                    composable(Routes.BLOCK_STYLES) {
                        com.diary.app.ui.settings.BlockStylesScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = Routes.EDITOR,
                        arguments = listOf(
                            navArgument("entryId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument("date") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        EditorScreen(
                            entryId = entry.arguments?.getString("entryId"),
                            initialDate = entry.arguments?.getString("date"),
                            onClose = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.SEARCH) {
                        SearchScreen(
                            dao = container.database.entryDao(),
                            onOpenEntry = { id -> readingEntryId = id },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.RANDOM) {
                        RandomScreen(
                            dao = container.database.entryDao(),
                            onWriteNew = { navController.navigate(Routes.editor(null)) },
                            onClose = { navController.popBackStack() },
                        )
                    }
                }
            }

            // Tab bar floats above the pages: it slides away without
            // affecting the page layout underneath.
            AnimatedVisibility(
                visible = showTabBar,
                enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(200)) +
                    fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(200)) +
                    fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                val density = LocalDensity.current
                Box(
                    modifier = Modifier.onSizeChanged { size ->
                        if (size.height > 0) {
                            tabBarHeight = with(density) { size.height.toDp() }
                        }
                    },
                ) {
                    TabBar(
                        selectedTab = selectedTab,
                        onSelect = ::selectTab,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
            }

            // Reading overlay: scrim fades, the sheet expands open like a card.
            val showReading = readingEntryId != null
            AnimatedVisibility(
                visible = showReading,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { readingEntryId = null },
                )
            }
            AnimatedVisibility(
                visible = showReading,
                enter = scaleIn(initialScale = 0.7f, animationSpec = tween(300)) +
                    fadeIn(animationSpec = tween(150)),
                exit = scaleOut(targetScale = 0.85f, animationSpec = tween(220)) +
                    fadeOut(animationSpec = tween(200)),
            ) {
                // Capture the id at open time: during the exit animation the
                // readingEntryId is already null, so reading it directly would
                // render an empty sheet and the close animation would not play.
                val entryId = remember { readingEntryId }
                if (entryId != null) {
                    ReadingOverlay(
                        entryId = entryId,
                        onDismiss = { readingEntryId = null },
                        onEdit = {
                            readingEntryId = null
                            navController.navigate(Routes.editor(entryId))
                        },
                        onDeleted = {
                            readingEntryId = null
                            // Hide the card in the UI immediately; the DB
                            // row is already tombstoned, the flow catches
                            // up on its own.
                            listViewModel.hide(entryId)
                            calendarViewModel.hide(entryId)
                            listViewModel.refresh()
                            calendarViewModel.refresh()
                        },
                    )
                }
            }
        }
        }
    }

    // Lock overlay: full-screen, above everything, opaque. Rendered alone
    // while locked (see the guard above): nothing loads behind the PIN.
    AnimatedVisibility(
        visible = locked,
        enter = fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(
            targetOffsetY = { -it / 4 },
            animationSpec = tween(250),
        ) + scaleOut(targetScale = 0.85f, animationSpec = tween(250)) +
            fadeOut(animationSpec = tween(250)),
    ) {
        UnlockScreen(
            lockStore = lockStore,
            biometricAvailable = biometricAvailable,
            onUnlocked = { locked = false },
        )
    }
}

/** Full-screen background: custom image if set, else the default drawable. */
@Composable
private fun DiaryBackground(bitmap: ImageBitmap?) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
    // Before the async decode finishes the theme's windowBackground shows
    // through; no synchronous drawable decode on the main thread.
}

/** Top strip container shared by every bar. */
@Composable
private fun TopStrip(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val uiPrefs = remember(context) { UiPrefsStore(context) }
    val surfaceAlpha by uiPrefs.surfaceAlpha.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha)),
    ) {
        content()
    }
}

/** Tab bar: segmented buttons, settings on the left, empty slot on the right. */
@Composable
private fun TabBar(
    selectedTab: DiaryTab?,
    onSelect: (DiaryTab) -> Unit,
    onOpenSettings: () -> Unit,
) {
    TopStrip {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(52.dp)) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(50),
                    )
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DiaryTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                            )
                            .clickable { onSelect(tab) }
                            .padding(vertical = 6.dp),
                    )
                }
            }
            // Right placeholder: keeps the pill centered; reserved for a
            // future action button.
            Spacer(modifier = Modifier.size(52.dp))
        }
    }
}
