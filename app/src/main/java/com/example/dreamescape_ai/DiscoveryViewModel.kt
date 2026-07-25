package com.example.dreamescape_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamescape_ai.model.FeedSection
import com.example.dreamescape_ai.model.StoryItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.apis.MediaApi
import org.openapitools.client.apis.ScenesApi
import org.openapitools.client.apis.UsersApi
import org.openapitools.client.models.ApiResponsePageMediaAssetDTO
import org.openapitools.client.models.ApiResponsePageScene
import org.openapitools.client.models.ApiResponsePageUser
import org.openapitools.client.models.MediaEntityType
import org.openapitools.client.models.Scene
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class DiscoveryUiState(
    val sections: List<FeedSection> = emptyList(),
    val isLoading: Boolean = false,
    /** True while a pull-to-refresh is running; existing content stays visible. */
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    /** Offset of the next "Recently Released" page to fetch. */
    val recentOffset: Int = 0,
    /** Whether more scenes can still be loaded into Recently Released. */
    val recentHasMore: Boolean = false,
    /** True while a "Recently Released" page is being fetched. */
    val recentIsLoadingMore: Boolean = false
)

/**
 * Feeds the Home / Discover tabs from the backend. Scenes are fetched from
 * [ScenesApi], each owner is resolved to a `@username` via [UsersApi], and each
 * scene's cover image is looked up lazily via [MediaApi].
 *
 * The backend has no "most messages / most liked" ordering, so the fetched
 * scenes are rotated across the four feed sections — each tab still shows
 * distinct, real content (Home shows Recently Released + Feeling Lucky;
 * Discover shows Most Messages + Most Liked).
 */
class DiscoveryViewModel(
    private val searchScenes: (offset: Int?, limit: Int?) -> ApiResponsePageScene = { offset, limit ->
        ScenesApi().searchSceneApiV1ScenesGet(offset = offset, limit = limit)
    },
    private val searchUsers: (userIds: List<UUID>) -> ApiResponsePageUser = { ids ->
        UsersApi().searchUsersUsersSearchGet(userIds = ids, limit = ids.size)
    },
    private val sceneCover: (sceneId: UUID) -> String? = { id ->
        MediaApi().searchMediaApiV1MediaGet(
            entityType = MediaEntityType.scene,
            entityId = id,
            limit = 1
        ).result.items.firstOrNull()?.url
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState(isLoading = true))
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    /** Epoch-ms of the last successful feed fetch; gates the Home tab TTL cache. */
    @Volatile
    private var lastFetchedAt: Long = 0L
    /** Epoch-ms the pull-to-refresh indicator was turned on; gates its min hold. */
    @Volatile
    private var indicatorShownAt: Long = 0L
    /** Owner id -> @username, reused across refreshes to skip [UsersApi] calls. */
    private val handleCache = ConcurrentHashMap<UUID, String>()
    /** Story id -> cover url, reused across refreshes to skip [MediaApi] lookups. */
    private val coverCache = ConcurrentHashMap<String, String>()
    /** Guards against overlapping fetches (init / refresh / tab-return). */
    private val isFetching = AtomicBoolean(false)

    init {
        loadDiscovery()
    }

    /** Initial / retry load (shows the blank spinner while there's no data). */
    fun loadDiscovery() = fetch(blankOnEmpty = true)

    /**
     * Pull-to-refresh: always refetch, ignoring the TTL cache, and update it. The
     * indicator is turned on unconditionally (and stamped at [indicatorShownAt]) so
     * it is guaranteed to turn off again — see [fetch]'s minimum-visible hold.
     */
    fun refresh() {
        indicatorShownAt = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        fetch(blankOnEmpty = false)
    }

    /**
     * Called when the user switches back to the Home tab. Reuses the cached feed
     * if it was fetched within [CACHE_TTL_MS]; otherwise silently refetches in the
     * background (existing content stays on screen).
     */
    fun refreshIfStale() {
        if (System.currentTimeMillis() - lastFetchedAt >= CACHE_TTL_MS) {
            fetch(blankOnEmpty = false)
        }
    }

    private fun fetch(blankOnEmpty: Boolean) {
        if (!isFetching.compareAndSet(false, true)) return
        if (blankOnEmpty) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        }
        // A pull (refresh) sets isRefreshing = true first; a silent refresh leaves
        // the existing content and flags untouched.
        viewModelScope.launch(ioDispatcher) {
            try {
                val scenes = searchScenes(0, INITIAL_LIMIT).result.items
                val handleByOwner = resolveHandles(scenes.map { it.ownerId }.distinct())
                val stories = scenes.map { it.toStoryItem(handleByOwner) }
                // Only the first page of scenes is shown in Recently Released up
                // front; the rest stream in on scroll via loadMoreRecent().
                val recentShown = minOf(RECENT_PAGE_SIZE, scenes.size)
                // Update the data immediately; the indicator (if any) is cleared
                // after the minimum-visible hold below.
                _uiState.value = _uiState.value.copy(
                    sections = buildSections(stories),
                    isLoading = false,
                    errorMessage = null,
                    recentOffset = recentShown,
                    recentHasMore = scenes.size > recentShown || scenes.size >= INITIAL_LIMIT,
                    recentIsLoadingMore = false
                )
                lastFetchedAt = System.currentTimeMillis()
                if (_uiState.value.isRefreshing) {
                    // Workaround for https://issuetracker.google.com/issues/359949836:
                    // PullToRefreshBox's indicator sticks if isRefreshing flips
                    // true -> false too quickly. Hold it for a minimum beat.
                    holdRefreshIndicator(indicatorShownAt)
                }
                _uiState.value = _uiState.value.copy(isRefreshing = false)
                resolveCovers(scenes)
            } catch (e: Exception) {
                if (_uiState.value.isRefreshing) holdRefreshIndicator(indicatorShownAt)
                _uiState.value = if (blankOnEmpty) {
                    _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: "Failed to load stories"
                    )
                } else {
                    // Keep showing the cached feed; surface the failure only when
                    // there's nothing else to show.
                    _uiState.value.copy(isLoading = false, isRefreshing = false)
                }
            } finally {
                isFetching.set(false)
            }
        }
    }

    /**
     * Delays just long enough that the pull-to-refresh indicator has been visible
     * for at least [REFRESH_MIN_VISIBLE_MS] since it was shown, so its dismissal
     * animation runs cleanly (see the bug linked in [fetch]).
     */
    private suspend fun holdRefreshIndicator(shownAt: Long) {
        val remaining = REFRESH_MIN_VISIBLE_MS - (System.currentTimeMillis() - shownAt)
        if (remaining > 0) delay(remaining)
    }

    /**
     * Fetches the next page of scenes for the "Recently Released" waterfall and
     * appends them in creation order (the backend's natural listing order). The
     * Scene DTO exposes no timestamp, so the API's default ordering is the only
     * source of "chronological" available.
     */
    fun loadMoreRecent() {
        val current = _uiState.value
        if (!current.recentHasMore || current.recentIsLoadingMore) return
        _uiState.value = current.copy(recentIsLoadingMore = true)
        viewModelScope.launch(ioDispatcher) {
            try {
                val offset = current.recentOffset
                val scenes = searchScenes(offset, RECENT_PAGE_SIZE).result.items
                val handleByOwner = resolveHandles(scenes.map { it.ownerId }.distinct())
                val more = scenes.map { it.toStoryItem(handleByOwner) }
                val updated = _uiState.value.sections.map { section ->
                    if (section.title == SECTION_RECENT) section.copy(stories = section.stories + more)
                    else section
                }
                _uiState.value = _uiState.value.copy(
                    sections = updated,
                    recentOffset = offset + scenes.size,
                    recentHasMore = scenes.size >= RECENT_PAGE_SIZE,
                    recentIsLoadingMore = false
                )
                resolveCovers(scenes)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(recentIsLoadingMore = false)
            }
        }
    }

    private fun resolveHandles(owners: List<UUID>): Map<UUID, String> {
        val distinct = owners.distinct()
        val cached = distinct.mapNotNull { id -> handleCache[id]?.let { id to it } }.toMap()
        val missing = distinct.filter { !handleCache.containsKey(it) }
        if (missing.isEmpty()) return cached
        return try {
            val fetched = searchUsers(missing).result.items
                .mapNotNull { user -> user.id?.let { id -> id to (user.username ?: "unknown") } }
                .toMap()
            fetched.forEach { (id, name) -> handleCache[id] = name }
            cached + fetched
        } catch (_: Exception) {
            cached
        }
    }

    private fun Scene.toStoryItem(handles: Map<UUID, String>): StoryItem = StoryItem(
        id = (id ?: ownerId).toString(),
        title = title,
        description = description?.takeIf { it.isNotBlank() } ?: backgroundPrompt,
        authorHandle = "@${handles[ownerId] ?: "unknown"}",
        coverImageUrl = coverCache[(id ?: ownerId).toString()], // reuse cached cover; else resolved lazily
        chatsCount = chatsCount,
        messagesCount = messagesCount,
        tags = emptyList()    // backend exposes no content tags yet
    )

    /** Rotates stories across the carousel sections so each shows distinct
     *  content. Recently Released is left out of the rotation — it is a lazy,
     *  paginated feed that starts with only the first [RECENT_PAGE_SIZE] scenes
     *  (enough to fill the screen) and streams the rest in via [loadMoreRecent]. */
    private fun buildSections(stories: List<StoryItem>): List<FeedSection> {
        val buckets = Array(4) { mutableListOf<StoryItem>() }
        stories.forEachIndexed { i, story -> buckets[i % 4].add(story) }
        return listOf(
            FeedSection(SECTION_MESSAGES, buckets[0].toList()),
            FeedSection(SECTION_LIKES, buckets[1].toList()),
            FeedSection(SECTION_LUCKY, buckets[3].toList()),
            FeedSection(SECTION_RECENT, stories.take(RECENT_PAGE_SIZE))
        )
    }

    /** Updates each story's cover as its media lookup completes (cached by id). */
    private fun resolveCovers(scenes: List<Scene>) {
        viewModelScope.launch(ioDispatcher) {
            for (scene in scenes) {
                val sceneId = scene.id ?: continue
                val storyId = sceneId.toString()
                // Already populated on the stories via toStoryItem(); skip the refetch.
                if (coverCache[storyId] != null) continue
                val url = try {
                    sceneCover(sceneId)
                } catch (_: Exception) {
                    null
                }
                if (url != null) {
                    coverCache[storyId] = url
                    applyCover(storyId, url)
                }
            }
        }
    }

    private fun applyCover(storyId: String, url: String) {
        val current = _uiState.value
        val updated = current.sections.map { section ->
            section.copy(
                stories = section.stories.map { story ->
                    if (story.id == storyId) story.copy(coverImageUrl = url) else story
                }
            )
        }
        _uiState.value = current.copy(sections = updated)
    }

    companion object {
        const val SECTION_MESSAGES = "Most Messages Today"
        const val SECTION_LIKES = "Most Liked Today"
        const val SECTION_RECENT = "Recently Released"
        const val SECTION_LUCKY = "I'm Feeling Lucky"

        /** How many scenes the initial load fetches (also the carousel pool). */
        const val INITIAL_LIMIT = 50

        /** Page size for incremental "Recently Released" loads. */
        const val RECENT_PAGE_SIZE = 20

        /** Home tab returns reuse the cached feed if it was fetched within this window. */
        const val CACHE_TTL_MS = 60_000L

        /**
         * Minimum time the pull-to-refresh indicator stays visible, so its dismiss
         * animation runs cleanly (works around issuetracker.google.com/359949836).
         */
        const val REFRESH_MIN_VISIBLE_MS = 1_000L

        /** Sections shown on the Home tab. */
        val homeSections = setOf(SECTION_RECENT, SECTION_LUCKY)

        /** Sections shown on the Discover tab. */
        val discoverSections = setOf(SECTION_MESSAGES, SECTION_LIKES)
    }
}
