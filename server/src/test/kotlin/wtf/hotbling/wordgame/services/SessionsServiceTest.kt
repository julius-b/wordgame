package wtf.hotbling.wordgame.services

import wtf.hotbling.wordgame.api.ApiCharStatus.Correct
import wtf.hotbling.wordgame.api.ApiCharStatus.Kinda
import wtf.hotbling.wordgame.api.ApiCharStatus.Wrong
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionsServiceTest {

    @Test
    fun testBuildCloud() {
        assertEquals(emptyList(), buildCloud("", ""))
        assertEquals(listOf(Correct), buildCloud("a", "a"))
        assertEquals(listOf(Wrong), buildCloud("a", "b"))
        assertEquals(
            listOf(Correct, Correct),
            buildCloud("aa", "aa")
        )
        assertEquals(
            listOf(Correct, Correct),
            buildCloud("ab", "ab")
        )
        assertEquals(
            listOf(Correct, Wrong),
            buildCloud("aa", "ab")
        )
        // case: not Kinda
        assertEquals(
            listOf(Correct, Wrong),
            buildCloud("ab", "aa")
        )
        assertEquals(
            listOf(Kinda, Kinda, Correct, Kinda, Kinda),
            buildCloud("hello", "olleh")
        )
        // case: first fake-Kinda is before *only* occurrence of that char in word in a correct place, therefore not Kinda but Wrong
        assertEquals(
            listOf(Wrong, Kinda, Wrong, Kinda, Wrong, Wrong, Correct, Wrong, Kinda, Wrong),
            buildCloud("compulsory", "submission")
        )
        // case: occurrence of fake-Kinda after only valid Kinda (since h only exists once in word)
        assertEquals(
            listOf(Kinda, Correct, Wrong, Wrong, Kinda, Wrong),
            buildCloud("teethe", "health")
        )
        // case: first fake_kinda is before tripe correct and occurrence that uses up all occurrences and invalidates the Kinda
        assertEquals(
            listOf(Wrong, Correct, Correct, Wrong, Wrong, Correct),
            buildCloud("teethe", "eeeeee")
        )
        // case: Kinda only on first e, second wrong (both dups in wrong position)
        assertEquals(
            listOf(Correct, Wrong, Kinda, Wrong, Wrong),
            buildCloud("close", "cheer")
        )
        // case: letter with the correct position comes first followed by the same letter in the incorrect position
        assertEquals(
            listOf(Correct, Kinda, Wrong, Wrong, Kinda),
            buildCloud("close", "cocks")
        )
        // case: letter with incorrect position first, followed by the same letter in the correct position
        assertEquals(
            listOf(Kinda, Wrong, Wrong, Wrong, Correct),
            buildCloud("close", "leave")
        )
    }

    @Test
    fun testBuildClouds() {
        assertEquals(emptyList(), buildClouds("", listOf()))
        assertEquals(
            listOf(
                listOf(Correct, Wrong, Kinda, Wrong, Wrong),
                listOf(Kinda, Wrong, Wrong, Wrong, Correct)
            ),
            buildClouds("close", listOf("cheer", "leave"))
        )
    }

    @Test
    fun testBuildKeyboard() {
        assertEquals(emptyMap(), buildKeyboard(emptyList()))
        assertEquals(
            mapOf(
                'c' to Correct,
                'h' to Wrong,
                'e' to Correct,
                'r' to Wrong,
                'l' to Kinda,
                'a' to Wrong,
                'v' to Wrong
            ),
            buildKeyboard(
                cloudsToRatings(
                    buildClouds("close", listOf("cheer", "leave")),
                    listOf("cheer", "leave")
                )
            )
        )
    }
}
