package com.example.dreamescape_ai.model

/**
 * Hand-curated sample content so the three screens render a full, previewable UI.
 * Replace these with real data from the OpenAPI client (ScenesApi, MediaApi,
 * users, chats) once the backend exposes author handles, tags and stats.
 */
object SampleData {

    private fun cover(seed: String) = "https://picsum.photos/seed/$seed/600/800"

    val mostMessagesToday = listOf(
        StoryItem(
            id = "mm1",
            title = "The Velvet Hour",
            description = "A noir romance where every whisper changes the city's fate.",
            authorHandle = "@clover",
            coverImageUrl = cover("velvet"),
            metric = StoryMetric(StoryMetricType.MESSAGES, 4820),
            tags = listOf(ContentTag.MATURE, ContentTag.FEMALE)
        ),
        StoryItem(
            id = "mm2",
            title = "Starfall Protocol",
            description = "Lead a renegade crew across a dying galaxy.",
            authorHandle = "@nyx",
            coverImageUrl = cover("starfall"),
            metric = StoryMetric(StoryMetricType.MESSAGES, 3915),
            tags = listOf(ContentTag.M, ContentTag.MALE)
        ),
        StoryItem(
            id = "mm3",
            title = "Hollow Crown",
            description = "Court intrigue, betrayal, and a throne that whispers back.",
            authorHandle = "@raven",
            coverImageUrl = cover("crown"),
            metric = StoryMetric(StoryMetricType.MESSAGES, 2740),
            tags = listOf(ContentTag.MATURE)
        )
    )

    val mostLikedToday = listOf(
        StoryItem(
            id = "ml1",
            title = "Neon Lullaby",
            description = "A synthwave love letter told one message at a time.",
            authorHandle = "@echo",
            coverImageUrl = cover("neon"),
            metric = StoryMetric(StoryMetricType.LIKES, 18200),
            tags = listOf(ContentTag.FEMALE)
        ),
        StoryItem(
            id = "ml2",
            title = "Frostbound",
            description = "Survive a winter that learns your secrets.",
            authorHandle = "@lumen",
            coverImageUrl = cover("frost"),
            metric = StoryMetric(StoryMetricType.LIKES, 14750),
            tags = listOf(ContentTag.M, ContentTag.MALE)
        ),
        StoryItem(
            id = "ml3",
            title = "Ember & Ash",
            description = "Two rival mages, one collapsing world.",
            authorHandle = "@sable",
            coverImageUrl = cover("ember"),
            metric = StoryMetric(StoryMetricType.LIKES, 11330),
            tags = listOf(ContentTag.MATURE, ContentTag.FEMALE)
        )
    )

    val recentlyReleased = listOf(
        StoryItem(
            id = "rr1",
            title = "Tideborn",
            description = "An ocean god offers you a single, terrible bargain.",
            authorHandle = "@marlow",
            coverImageUrl = cover("tide"),
            metric = StoryMetric(StoryMetricType.NEW, 1),
            tags = listOf(ContentTag.FEMALE)
        ),
        StoryItem(
            id = "rr2",
            title = "Glasshouse",
            description = "Every flower in this greenhouse remembers you.",
            authorHandle = "@ivory",
            coverImageUrl = cover("glass"),
            metric = StoryMetric(StoryMetricType.NEW, 2),
            tags = listOf(ContentTag.M)
        ),
        StoryItem(
            id = "rr3",
            title = "Last Train West",
            description = "A ghost story on rails, destination unknown.",
            authorHandle = "@dell",
            coverImageUrl = cover("train"),
            metric = StoryMetric(StoryMetricType.NEW, 3),
            tags = listOf(ContentTag.MATURE, ContentTag.MALE)
        )
    )

    val feelingLucky = listOf(
        StoryItem(
            id = "lk1",
            title = "Mothlight",
            description = "Chase the thing that glows at the edge of the woods.",
            authorHandle = "@petra",
            coverImageUrl = cover("moth"),
            metric = StoryMetric(StoryMetricType.LIKES, 980),
            tags = listOf(ContentTag.FEMALE)
        ),
        StoryItem(
            id = "lk2",
            title = "The Undertow",
            description = "A small town where the sea keeps its promises.",
            authorHandle = "@finch",
            coverImageUrl = cover("undertow"),
            metric = StoryMetric(StoryMetricType.MESSAGES, 640),
            tags = listOf(ContentTag.M, ContentTag.MALE)
        )
    )

    /** All four discovery sections, ready to render config-driven. */
    val feedSections: List<FeedSection> = listOf(
        FeedSection("Most Messages Today", mostMessagesToday),
        FeedSection("Most Liked Today", mostLikedToday),
        FeedSection("Recently Released", recentlyReleased),
        FeedSection("I'm Feeling Lucky", feelingLucky)
    )

    private fun thumb(seed: String) = "https://picsum.photos/seed/$seed/300/300"

    val history: List<HistoryItem> = listOf(
        HistoryItem(
            id = "h1",
            title = "The Velvet Hour",
            chapterTag = "Chapter 7 · The Rooftop",
            lastReadSnippet = "\"You weren't supposed to find me here,\" she said, but she didn't step back.",
            thumbnailUrl = thumb("velvet"),
            timestamp = "2h ago",
            category = HistoryCategory.SAVED
        ),
        HistoryItem(
            id = "h2",
            title = "Neon Lullaby",
            chapterTag = "Module 3 · Static",
            lastReadSnippet = "The city blinked awake in shades of magenta and regret.",
            thumbnailUrl = thumb("neon"),
            timestamp = "Yesterday",
            category = HistoryCategory.LIKES
        ),
        HistoryItem(
            id = "h3",
            title = "Hollow Crown",
            chapterTag = "Chapter 2 · The Whispering Throne",
            lastReadSnippet = "There were eleven knives hidden in the great hall. She knew them all.",
            thumbnailUrl = thumb("crown"),
            timestamp = "3d ago",
            category = HistoryCategory.COMMENTS
        ),
        HistoryItem(
            id = "h4",
            title = "Frostbound",
            chapterTag = "Chapter 5 · The Long Dark",
            lastReadSnippet = "He built the fire small, the way you do when you're being hunted.",
            thumbnailUrl = thumb("frost"),
            timestamp = "5d ago",
            category = HistoryCategory.FOLLOWING
        ),
        HistoryItem(
            id = "h5",
            title = "Ember & Ash",
            chapterTag = "Chapter 1 · Tinder",
            lastReadSnippet = "The first spell she ever cast was an accident. The second was not.",
            thumbnailUrl = thumb("ember"),
            timestamp = "1w ago",
            category = HistoryCategory.SAVED
        )
    )

    val profile = UserProfile(
        username = "Alex Vesper",
        handle = "@vesper",
        bio = "an example bio — collector of midnight stories and bad decisions.",
        avatarUrl = "https://picsum.photos/seed/vesper-avatar/400/400",
        followers = 1284,
        storylines = 37,
        characters = 12,
        manaCredits = CreditBalance(CreditType.MANA, 2450, "Renews in 12 days"),
        eliteCredits = CreditBalance(CreditType.ELITE, 320, "Premium currency"),
        activeCharacter = ActiveCharacter(
            name = "Lyra Nightshade",
            portraitUrl = "https://picsum.photos/seed/lyra/300/300"
        )
    )
}
