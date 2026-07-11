package com.example.dreamescape_ai.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.dreamescape_ai.ui.theme.FilterComments
import com.example.dreamescape_ai.ui.theme.FilterFollowing
import com.example.dreamescape_ai.ui.theme.FilterLikes
import com.example.dreamescape_ai.ui.theme.FilterSaved
import com.example.dreamescape_ai.ui.theme.TagFemale
import com.example.dreamescape_ai.ui.theme.TagM
import com.example.dreamescape_ai.ui.theme.TagMale
import com.example.dreamescape_ai.ui.theme.TagMature

// ===========================================================================
//  Story / discovery items (Screen 1)
// ===========================================================================

/** What a story card's stat line is counting. Drives icon + label. */
enum class StoryMetricType(val label: String) {
    MESSAGES("messages"),
    LIKES("likes"),
    NEW("new")
}

data class StoryMetric(val type: StoryMetricType, val value: Int) {
    fun formatted(): String = when {
        value >= 1000 -> "${value / 1000}k"
        else -> value.toString()
    }
}

/** Top-left corner tag on a story card (18+, M, gender...). */
enum class ContentTag(val label: String, val color: Color) {
    MATURE("18+", TagMature),
    M("M", TagM),
    MALE("♂", TagMale),    // ♂
    FEMALE("♀", TagFemale) // ♀
}

/**
 * A story shown in the discovery feed. Conceptually maps to a backend
 * [org.openapitools.client.models.Scene] plus UI-only metadata (author handle,
 * tags, stats) that the API does not yet expose.
 */
@Immutable
data class StoryItem(
    val id: String,
    val title: String,
    val description: String,
    val authorHandle: String,
    val coverImageUrl: String?,
    val metric: StoryMetric? = null,
    val tags: List<ContentTag> = emptyList()
)

/** A titled horizontal carousel of stories, e.g. "Most Liked Today". */
@Immutable
data class FeedSection(val title: String, val stories: List<StoryItem>)

// ===========================================================================
//  History items (Screen 2)
// ===========================================================================

/** The filter/category a history entry belongs to. Also colors the filter button. */
enum class HistoryCategory(val label: String, val color: Color) {
    SAVED("Saved", FilterSaved),
    LIKES("Likes", FilterLikes),
    COMMENTS("Comments", FilterComments),
    FOLLOWING("Following", FilterFollowing)
}

@Immutable
data class HistoryItem(
    val id: String,
    val title: String,
    val chapterTag: String,
    val lastReadSnippet: String,
    val thumbnailUrl: String?,
    val timestamp: String,
    val category: HistoryCategory
)

// ===========================================================================
//  User profile (Screen 3)
// ===========================================================================

enum class CreditType(val title: String) {
    MANA("MANA CREDITS"),
    ELITE("ELITE CREDITS")
}

/** A credit balance card. Display-only for now (billing intentionally deferred). */
@Immutable
data class CreditBalance(val type: CreditType, val amount: Int, val subtitle: String)

@Immutable
data class ActiveCharacter(val name: String, val portraitUrl: String?)

@Immutable
data class UserProfile(
    val username: String,
    val handle: String,
    val bio: String,
    val avatarUrl: String?,
    val followers: Int,
    val storylines: Int,
    val characters: Int,
    val manaCredits: CreditBalance,
    val eliteCredits: CreditBalance,
    val activeCharacter: ActiveCharacter?
)
