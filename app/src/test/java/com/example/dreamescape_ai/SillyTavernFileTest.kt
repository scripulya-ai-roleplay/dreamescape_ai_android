package com.example.dreamescape_ai

import org.junit.Assert.assertTrue
import org.junit.Test

class SillyTavernFileTest {

    @Test
    fun standaloneLorebookWithEntriesIsValid() {
        val bytes = """{"entries":{"0":{"comment":"x","content":"y","group":"character"}}}""".toByteArray()
        assertTrue(SillyTavernFile.classify(bytes) is SillyTavernFileResult.Valid)
    }

    @Test
    fun v2CharacterCardBySpecIsValid() {
        val bytes = """{"spec":"chara_card_v2","data":{"name":"Hero","description":"Brave"}}""".toByteArray()
        assertTrue(SillyTavernFile.classify(bytes) is SillyTavernFileResult.Valid)
    }

    @Test
    fun embeddedCharacterBookEntriesIsValid() {
        val bytes =
            """{"data":{"character_book":{"entries":{"0":{"comment":"x","content":"y"}}}}}""".toByteArray()
        assertTrue(SillyTavernFile.classify(bytes) is SillyTavernFileResult.Valid)
    }

    @Test
    fun unrelatedJsonIsNotSillyTavern() {
        val bytes = """{"hello":"world","list":[1,2,3]}""".toByteArray()
        assertTrue(SillyTavernFile.classify(bytes) is SillyTavernFileResult.NotSillyTavern)
    }

    @Test
    fun brokenJsonReportsNotJson() {
        val bytes = """{"entries": {"0": {""".toByteArray()
        val result = SillyTavernFile.classify(bytes)
        assertTrue("expected NotJson, got $result", result is SillyTavernFileResult.NotJson)
    }
}
