package com.example.dreamescape_ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ChatRenderPipelineTest {

    @Test
    fun `phase is derived from which facts have landed`() {
        assertEquals(ChatRenderPhase.IDLE, ChatRenderFacts().phase)
        assertEquals(ChatRenderPhase.LOADING, ChatRenderFacts(contextLoaded = true).phase)
        assertEquals(ChatRenderPhase.LOADING, ChatRenderFacts(messagesLoaded = true).phase)
        assertEquals(
            ChatRenderPhase.READY,
            ChatRenderFacts(contextLoaded = true, messagesLoaded = true).phase
        )
    }

    @Test
    fun `greeting is needed only for a scene chat with no greeting chosen`() {
        val base = ChatRenderFacts(contextLoaded = true, hasScene = true)

        assertTrue(deriveRenderDecision(base).needsInitialMessage)
        assertFalse(deriveRenderDecision(base.copy(initialMessageId = UUID.randomUUID())).needsInitialMessage)
        assertFalse(deriveRenderDecision(base.copy(greetingCommitted = true)).needsInitialMessage)
        assertFalse(deriveRenderDecision(base.copy(hasScene = false)).needsInitialMessage)
        assertFalse(deriveRenderDecision(base.copy(contextLoaded = false)).needsInitialMessage)
    }

    @Test
    fun `background decision passes the scene image through`() {
        assertNull(deriveRenderDecision(ChatRenderFacts()).backgroundImageUrl)
        assertEquals(
            "http://img",
            deriveRenderDecision(ChatRenderFacts(sceneImageUrl = "http://img")).backgroundImageUrl
        )
    }

    @Test
    fun `concurrent updates apply whole and in order`() = runBlocking {
        var lastPublished = 0
        val pipeline = ChatRenderPipeline { facts, _ ->
            lastPublished = facts.personaName?.length ?: 0
        }

        val jobs = (1..100).map {
            launch(Dispatchers.Default) {
                pipeline.update { f -> f.copy(personaName = (f.personaName ?: "") + "x") }
            }
        }
        jobs.forEach { it.join() }

        assertEquals(100, pipeline.currentFacts.personaName?.length)
        assertEquals(100, lastPublished)
    }
}
