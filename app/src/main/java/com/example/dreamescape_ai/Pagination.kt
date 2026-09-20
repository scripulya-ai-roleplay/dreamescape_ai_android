package com.example.dreamescape_ai

/**
 * One page of a paginated backend listing, reduced to what [fetchAllPages]
 * needs: the page's items and the backend-reported grand total of matching
 * items across all pages (the `count` field of every Page* DTO).
 */
data class ListingPage<out T>(
    val items: List<T>,
    val totalCount: Int
)

const val DEFAULT_PAGE_SIZE = 100

/**
 * Collects every item of a paginated backend endpoint, requesting pages of
 * [pageSize] until all [ListingPage.totalCount] items are loaded. The backend
 * paginates oldest-first, so a single request silently drops the newest items
 * once they exceed one page. An empty page also ends the loop, guarding
 * against a `count` that overstates the real number of items.
 */
fun <T> fetchAllPages(
    pageSize: Int = DEFAULT_PAGE_SIZE,
    fetch: (offset: Int, limit: Int) -> ListingPage<T>
): List<T> {
    val all = mutableListOf<T>()
    while (true) {
        val page = fetch(all.size, pageSize)
        all += page.items
        if (all.size >= page.totalCount || page.items.isEmpty()) break
    }
    return all
}
