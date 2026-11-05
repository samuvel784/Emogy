package com.vurka.emogy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vurka.emogy.data.AlbumProgressCalculator
import com.vurka.emogy.data.EmojiCatalog
import com.vurka.emogy.data.login.LoginRepository
import com.vurka.emogy.data.tasks.TaskRepository
import com.vurka.emogy.ui.album.AlbumScreen
import com.vurka.emogy.ui.components.AppBottomBar
import com.vurka.emogy.ui.daily.DailyScreen
import com.vurka.emogy.ui.game.GameScreen
import com.vurka.emogy.ui.home.HomeScreen
import com.vurka.emogy.ui.legal.LegalScreen
import com.vurka.emogy.ui.levels.LevelSelectScreen
import com.vurka.emogy.ui.more.MoreScreen
import com.vurka.emogy.ui.navigation.Screen
import com.vurka.emogy.ui.navigation.TopLevelDestinations
import com.vurka.emogy.ui.settings.SettingsScreen
import com.vurka.emogy.ui.stats.StatsScreen
import com.vurka.emogy.ui.store.StoreScreen
import com.vurka.emogy.ui.tasks.TasksScreen
import com.vurka.emogy.ui.tasks.TasksViewModel
import com.vurka.emogy.ui.themes.ThemesScreen
import com.vurka.emogy.ui.theme.MemoryMatchTheme
import com.vurka.emogy.util.AndroidGlyphSupport
import com.vurka.emogy.util.MusicManager
import com.vurka.emogy.util.SoundManager
import com.vurka.emogy.util.WorldMusic
import com.vurka.emogy.viewmodel.GameViewModel
import com.vurka.emogy.viewmodel.GameViewModelFactory
import com.vurka.emogy.viewmodel.LevelSelectionViewModel
import kotlinx.coroutines.launch

/** Tab taps are siblings, not a push, so roots cross-fade instead of sliding sideways. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTopLevelSwitch(): Boolean =
    initialState.destination.route in TopLevelDestinations.routes &&
        targetState.destination.route in TopLevelDestinations.routes

class MainActivity : ComponentActivity() {
    /**
     * Login and task rollover are owned by the activity, not by a screen, so the streak advances
     * even when the player goes straight from Home into a level.
     */
    private val loginRepository by lazy { LoginRepository(applicationContext) }
    private val taskRepository by lazy { TaskRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Remove emoji the device font cannot draw before any board or menu is composed.
        EmojiCatalog.installGlyphFilter { emoji -> AndroidGlyphSupport.supports(emoji) }
        // Renders the deterministic sound set once, off the main thread.
        SoundManager.init(this)
        MusicManager.init(this)
        setContent {
            val levelSelectionViewModel: LevelSelectionViewModel = viewModel()
            val themeMode by levelSelectionViewModel.themeMode.collectAsState()
            val audioSettings by levelSelectionViewModel.audioSettings.collectAsState()
            val lastPlayedLevel by levelSelectionViewModel.lastPlayedLevel.collectAsState()

            // Push the persisted sliders into both engines only after DataStore has answered.
            // Until then the engines keep their defaults but nothing starts, so a muted player
            // never hears a startup note.
            LaunchedEffect(audioSettings) {
                audioSettings?.let { settings ->
                    SoundManager.setVolumes(settings.master, settings.effects)
                    MusicManager.setVolumes(settings.master, settings.music)
                }
            }

            // Home and most menus inherit the ambience of the level you last played.
            LaunchedEffect(lastPlayedLevel, audioSettings) {
                if (lastPlayedLevel > 0 && audioSettings != null) {
                    MusicManager.setGroup(WorldMusic.groupForLevel(lastPlayedLevel))
                }
            }

            MemoryMatchTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val navigateToTab: (String) -> Unit = { route ->
                    if (route == Screen.Home.route) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                val tasksViewModel: TasksViewModel = viewModel()
                val tasksUiState by tasksViewModel.ui.collectAsState()
                val hasTaskClaims = tasksUiState.hasClaimable
                val collectedEmojis by levelSelectionViewModel.collectedEmojis.collectAsState()
                val claimedAlbumEmojis by levelSelectionViewModel.claimedAlbumEmojis.collectAsState()
                val hasAlbumClaims = remember(collectedEmojis, claimedAlbumEmojis) {
                    AlbumProgressCalculator.calculate(
                        catalog = EmojiCatalog.items,
                        collectedIds = collectedEmojis,
                        claimedIds = claimedAlbumEmojis,
                    ).readyToClaimCount > 0
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (TopLevelDestinations.showsBottomBar(currentRoute)) {
                            AppBottomBar(
                                currentRoute = currentRoute,
                                hasTaskClaims = hasTaskClaims,
                                hasAlbumClaims = hasAlbumClaims,
                                onSelect = navigateToTab
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                        navController = navController,
                        startDestination = Screen.Home.route,
                        enterTransition = {
                            if (isTopLevelSwitch()) {
                                fadeIn(animationSpec = tween(160))
                            } else {
                                slideInHorizontally(
                                    animationSpec = tween(320),
                                    initialOffsetX = { it / 6 }
                                ) + fadeIn(animationSpec = tween(320))
                            }
                        },
                        exitTransition = {
                            if (isTopLevelSwitch()) {
                                fadeOut(animationSpec = tween(120))
                            } else {
                                slideOutHorizontally(
                                    animationSpec = tween(320),
                                    targetOffsetX = { -it / 6 }
                                ) + fadeOut(animationSpec = tween(240))
                            }
                        },
                        popEnterTransition = {
                            if (isTopLevelSwitch()) {
                                fadeIn(animationSpec = tween(160))
                            } else {
                                slideInHorizontally(
                                    animationSpec = tween(320),
                                    initialOffsetX = { -it / 6 }
                                ) + fadeIn(animationSpec = tween(320))
                            }
                        },
                        popExitTransition = {
                            if (isTopLevelSwitch()) {
                                fadeOut(animationSpec = tween(120))
                            } else {
                                slideOutHorizontally(
                                    animationSpec = tween(320),
                                    targetOffsetX = { it / 6 }
                                ) + fadeOut(animationSpec = tween(240))
                            }
                        }
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                viewModel = levelSelectionViewModel,
                                onPlayClicked = { levelNum ->
                                    navController.navigate(Screen.Game.createRoute(levelNum))
                                },
                                onWorldMapClicked = {
                                    navigateToTab(Screen.WorldMap.route)
                                },
                                onStatsClicked = {
                                    navController.navigate(Screen.Stats.route)
                                },
                                onStoreClicked = {
                                    navController.navigate(Screen.Store.route)
                                },
                                onSettingsClicked = {
                                    navController.navigate(Screen.Settings.route)
                                },
                                onDailyClicked = {
                                    navigateToTab(Screen.Daily.route)
                                }
                            )
                        }

                        composable(Screen.Album.route) {
                            AlbumScreen(
                                viewModel = levelSelectionViewModel,
                            )
                        }

                        composable(Screen.Daily.route) {
                            val tasksViewModel: TasksViewModel = viewModel()
                            DailyScreen(
                                viewModel = levelSelectionViewModel,
                                tasksViewModel = tasksViewModel,
                                onPlay = { day ->
                                    navController.navigate(Screen.DailyGame.createRoute(day))
                                }
                            )
                        }

                        composable(
                            route = Screen.DailyGame.route,
                            arguments = listOf(navArgument("day") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val day = backStackEntry.arguments?.getLong("day") ?: 0L
                            val dailyViewModel: GameViewModel = viewModel(
                                key = "daily-$day",
                                factory = GameViewModelFactory(
                                    application = application,
                                    levelNumber = 1,
                                    isDaily = true,
                                    dailyEpochDay = day,
                                )
                            )

                            GameScreen(
                                viewModel = dailyViewModel,
                                onBackPressed = { navController.popBackStack() },
                                onNextLevel = {},
                                onWorldMap = {
                                    navController.navigate(Screen.WorldMap.route) {
                                        popUpTo(Screen.Home.route) { inclusive = false }
                                    }
                                },
                                onHome = {
                                    if (!navController.popBackStack(Screen.Home.route, inclusive = false)) {
                                        navController.navigate(Screen.Home.route)
                                    }
                                }
                            )
                        }

                        composable(Screen.Themes.route) {
                            ThemesScreen(
                                viewModel = levelSelectionViewModel,
                                onBackPressed = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(Screen.WorldMap.route) {
                            LevelSelectScreen(
                                viewModel = levelSelectionViewModel,
                                onLevelSelected = { levelNum ->
                                    navController.navigate(Screen.Game.createRoute(levelNum))
                                },
                            )
                        }

                        composable(Screen.Stats.route) {
                            StatsScreen(
                                viewModel = levelSelectionViewModel,
                                onBackPressed = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(Screen.Tasks.route) {
                            val tasksViewModel: TasksViewModel = viewModel()
                            TasksScreen(
                                viewModel = tasksViewModel,
                                onBackPressed = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(Screen.Store.route) {
                            StoreScreen(
                                viewModel = levelSelectionViewModel,
                                onBackPressed = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(
                            route = Screen.Game.route,
                            arguments = listOf(navArgument("levelNumber") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val levelNum = backStackEntry.arguments?.getInt("levelNumber") ?: 1
                            val gameViewModel: GameViewModel = viewModel(
                                factory = GameViewModelFactory(application, levelNum)
                            )

                            GameScreen(
                                viewModel = gameViewModel,
                                onBackPressed = { navController.popBackStack() },
                                onNextLevel = { next ->
                                    // Replace the finished run so "back" never reopens a solved board.
                                    navController.navigate(Screen.Game.createRoute(next)) {
                                        popUpTo(Screen.Game.route) { inclusive = true }
                                    }
                                },
                                onWorldMap = {
                                    // Reuse the existing map entry when there is one, so its scroll
                                    // position and selection survive the trip through the level.
                                    val popped = navController.popBackStack(Screen.WorldMap.route, inclusive = false)
                                    if (!popped) {
                                        navController.navigate(Screen.WorldMap.route) {
                                            popUpTo(Screen.Home.route) { inclusive = false }
                                        }
                                    }
                                },
                                onHome = {
                                    if (!navController.popBackStack(Screen.Home.route, inclusive = false)) {
                                        navController.navigate(Screen.Home.route)
                                    }
                                }
                            )
                        }

                        composable(Screen.More.route) {
                            MoreScreen(
                                viewModel = levelSelectionViewModel,
                                onRewards = { navController.navigate(Screen.Tasks.route) },
                                onStats = { navController.navigate(Screen.Stats.route) },
                                onThemes = { navController.navigate(Screen.Themes.route) },
                                onStore = { navController.navigate(Screen.Store.route) },
                                onSettings = { navController.navigate(Screen.Settings.route) },
                                onAbout = { navController.navigate(Screen.Legal.route) }
                            )
                        }

                        composable(Screen.Legal.route) {
                            LegalScreen(onBackPressed = { navController.popBackStack() })
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = levelSelectionViewModel,
                                onBackPressed = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private var noisyReceiverRegistered = false
    private val audioNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                MusicManager.onAudioBecomingNoisy()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SoundManager.setAppForeground(true)
        MusicManager.onEnterForeground()
        // One idempotent check-in per local date; a second foreground pass changes nothing.
        lifecycleScope.launch {
            loginRepository.checkIn()
            taskRepository.refresh()
        }
        if (!noisyReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                audioNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyReceiverRegistered = true
        }
    }

    override fun onStop() {
        if (noisyReceiverRegistered) {
            try {
                unregisterReceiver(audioNoisyReceiver)
            } catch (_: IllegalArgumentException) {
            }
            noisyReceiverRegistered = false
        }
        // No cue or loop may leak into the background.
        SoundManager.setAppForeground(false)
        MusicManager.onLeaveForeground()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            MusicManager.release()
        } catch (_: Exception) {
        }
        try {
            SoundManager.release()
        } catch (_: Exception) {
        }
    }
}
