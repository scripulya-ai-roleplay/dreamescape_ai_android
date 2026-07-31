package com.example.dreamescape_ai

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dreamescape_ai.model.HistoryItem
import com.example.dreamescape_ai.model.StoryItem
import com.example.dreamescape_ai.model.UserProfile
import com.example.dreamescape_ai.ui.components.ScripulyaBottomNav
import com.example.dreamescape_ai.ui.components.ScripulyaTab
import com.example.dreamescape_ai.ui.components.ScripulyaTopBar
import com.example.dreamescape_ai.ui.screens.CreditUsageScreen
import com.example.dreamescape_ai.ui.screens.DiscoveryScreen
import com.example.dreamescape_ai.ui.screens.HistoryScreen
import com.example.dreamescape_ai.ui.screens.ProfileScreen
import com.example.dreamescape_ai.ui.theme.BlueVoid
import com.example.dreamescape_ai.ui.theme.NightVoid
import com.example.dreamescape_ai.ui.theme.nightSkyGradient
import org.openapitools.client.models.Chat

/**
 * Root app shell: a persistent [ScripulyaTopBar] and a floating [ScripulyaBottomNav]
 * wrapped around a single in-composition screen that swaps by selected tab. Hosting
 * the screens in one activity (instead of spawning more activities) keeps the
 * navigation bar alive across tabs and avoids Intent boilerplate.
 *
 * The History screen is surfaced as a full-screen overlay reachable from Home and
 * Profile, so it does not need its own bottom-nav slot (the five nav icons are fixed).
 */
@Composable
fun ScripulyaApp(
    profile: UserProfile,
    history: List<HistoryItem>,
    onChangeCharacter: () -> Unit,
    onChatClick: (Chat) -> Unit,
    onStoryClick: (StoryItem) -> Unit = {},
    onPlay: (HistoryItem) -> Unit = {},
    onCreateCharacter: () -> Unit = {},
    onCreateScene: () -> Unit = {},
    onMyCharacters: () -> Unit = {},
    onMyScenes: () -> Unit = {},
    onImportSillyTavern: () -> Unit = {},
    discoveryViewModel: DiscoveryViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableStateOf(ScripulyaTab.HOME) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val openHistory = { showHistory = true }
    val context = LocalContext.current

    val discoveryState by discoveryViewModel.uiState.collectAsState()

    // Returning to the Home tab reuses the cached feed if fresh (< CACHE_TTL_MS)
    // or silently refetches; pull-to-refresh forces a refresh regardless. Tracked
    // against the previous tab so it only fires on an actual non-Home -> Home move
    // (never on first composition, which would double-fetch against the VM's init).
    val previousTab = remember { mutableStateOf<ScripulyaTab?>(null) }
    LaunchedEffect(selectedTab) {
        if (selectedTab == ScripulyaTab.HOME &&
            previousTab.value != null && previousTab.value != ScripulyaTab.HOME
        ) {
            discoveryViewModel.refreshIfStale()
        }
        previousTab.value = selectedTab
    }

    Box(modifier = Modifier.fillMaxSize().nightSkyGradient()) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                ScripulyaTopBar(
                    manaCredits = profile.manaCredits.amount.toString(),
                    arcaneCredits = profile.eliteCredits.amount.toString(),
                    onNotifications = {}
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (selectedTab) {
                    ScripulyaTab.HOME, ScripulyaTab.DISCOVER -> {
                        val allowed = if (selectedTab == ScripulyaTab.HOME) {
                            DiscoveryViewModel.homeSections
                        } else {
                            DiscoveryViewModel.discoverSections
                        }
                        DiscoveryScreen(
                            sections = discoveryState.sections.filter { it.title in allowed },
                            isLoading = discoveryState.isLoading,
                            isRefreshing = discoveryState.isRefreshing,
                            errorMessage = discoveryState.errorMessage,
                            onRetry = discoveryViewModel::loadDiscovery,
                            onRefresh = discoveryViewModel::refresh,
                            onStoryClick = onStoryClick,
                            onOpenHistory = openHistory,
                            recentHasMore = discoveryState.recentHasMore,
                            recentIsLoadingMore = discoveryState.recentIsLoadingMore,
                            onLoadMoreRecent = discoveryViewModel::loadMoreRecent
                        )
                    }

                    ScripulyaTab.CREDITS -> CreditUsageScreen(
                        mana = profile.manaCredits,
                        elite = profile.eliteCredits,
                        onCreateCharacter = onCreateCharacter,
                        onCreateScene = onCreateScene,
                        onMyCharacters = onMyCharacters,
                        onMyScenes = onMyScenes,
                        onImportSillyTavern = onImportSillyTavern
                    )

                    // The Feedback/Chat tab shows the user's own conversations inline.
                    ScripulyaTab.CHAT -> ChatListScreen(
                        modifier = Modifier.padding(bottom = 96.dp), // clear the floating nav
                        onChatClick = onChatClick
                    )

                    ScripulyaTab.PROFILE -> ProfileScreen(
                        profile = profile,
                        onChangeCharacter = onChangeCharacter,
                        onOpenHistory = openHistory,
                        onSettings = {
                            // Profile's settings gear → Advanced settings (backend address, etc.).
                            context.startActivity(
                                Intent(context, AdvancedSettingsActivity::class.java)
                            )
                        },
                        onEditProfile = {}
                    )
                }

                if (showHistory) {
                    HistoryScreen(
                        items = history,
                        onPlay = onPlay,
                        onBack = { showHistory = false },
                        modifier = Modifier.fillMaxSize().background(BlueVoid)
                    )
                }
            }
        }

        if (!showHistory) {
            ScripulyaBottomNav(
                selected = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
    }
}
