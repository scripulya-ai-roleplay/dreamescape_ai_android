package com.example.dreamescape_ai.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Returns a [NestedScrollConnection] that forwards vertical scroll drags which
 * start on the attached node to [scrollState].
 *
 * A multiline [androidx.compose.material3.OutlinedTextField] swallows vertical
 * drag gestures with its own (empty) scroll container, which stops a surrounding
 * scrolling column from ever receiving them. Attaching this connection to the
 * field makes a drag over it scroll the page instead: each delta is pre-consumed
 * and applied to [scrollState]. The text fields on the Create screens grow to
 * fit their content, so they never need to scroll internally and surrendering the
 * gesture is always correct.
 *
 * Sign note: [NestedScrollConnection.onPreScroll] reports a negative `available.y`
 * when scrolling toward the end, while [ScrollState.dispatchRawDelta] scrolls
 * toward the end for a positive argument — hence the negation when forwarding.
 */
@Composable
fun rememberScrollForwarder(scrollState: ScrollState): NestedScrollConnection =
    remember(scrollState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val consumed = scrollState.dispatchRawDelta(-available.y)
                return Offset(0f, -consumed)
            }
        }
    }
