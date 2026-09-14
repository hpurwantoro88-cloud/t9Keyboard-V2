package com.opent9.keyboard.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishOrthographyTest {

    @Test
    fun testUnambiguousContractionsDisplayForm() {
        assertEquals("don't", EnglishOrthography.displayForm("dont", "EN"))
        assertEquals("doesn't", EnglishOrthography.displayForm("doesnt", "EN"))
        assertEquals("didn't", EnglishOrthography.displayForm("didnt", "EN"))
        assertEquals("won't", EnglishOrthography.displayForm("wont", "EN"))
        assertEquals("can't", EnglishOrthography.displayForm("cant", "EN"))
        assertEquals("isn't", EnglishOrthography.displayForm("isnt", "EN"))
        assertEquals("aren't", EnglishOrthography.displayForm("arent", "EN"))
        assertEquals("wasn't", EnglishOrthography.displayForm("wasnt", "EN"))
        assertEquals("weren't", EnglishOrthography.displayForm("werent", "EN"))
        assertEquals("wouldn't", EnglishOrthography.displayForm("wouldnt", "EN"))
        assertEquals("couldn't", EnglishOrthography.displayForm("couldnt", "EN"))
        assertEquals("shouldn't", EnglishOrthography.displayForm("shouldnt", "EN"))
        assertEquals("I'm", EnglishOrthography.displayForm("im", "EN"))
        assertEquals("I've", EnglishOrthography.displayForm("ive", "EN"))
        assertEquals("I", EnglishOrthography.displayForm("i", "EN"))
        assertEquals("you're", EnglishOrthography.displayForm("youre", "EN"))
        assertEquals("they're", EnglishOrthography.displayForm("theyre", "EN"))
        assertEquals("he's", EnglishOrthography.displayForm("hes", "EN"))
        assertEquals("she's", EnglishOrthography.displayForm("shes", "EN"))
        assertEquals("that's", EnglishOrthography.displayForm("thats", "EN"))
        assertEquals("what's", EnglishOrthography.displayForm("whats", "EN"))
        assertEquals("where's", EnglishOrthography.displayForm("wheres", "EN"))
        assertEquals("there's", EnglishOrthography.displayForm("theres", "EN"))
        assertEquals("who's", EnglishOrthography.displayForm("whos", "EN"))
        assertEquals("how's", EnglishOrthography.displayForm("hows", "EN"))
        assertEquals("o'clock", EnglishOrthography.displayForm("oclock", "EN"))
        assertEquals("ma'am", EnglishOrthography.displayForm("maam", "EN"))
    }

    @Test
    fun testAmbiguousContractionsDisplayFormRemainUnchanged() {
        assertEquals("well", EnglishOrthography.displayForm("well", "EN"))
        assertEquals("its", EnglishOrthography.displayForm("its", "EN"))
        assertEquals("were", EnglishOrthography.displayForm("were", "EN"))
        assertEquals("ill", EnglishOrthography.displayForm("ill", "EN"))
        assertEquals("id", EnglishOrthography.displayForm("id", "EN"))
        assertEquals("lets", EnglishOrthography.displayForm("lets", "EN"))
    }

    @Test
    fun testIndonesianIsolation() {
        assertEquals("dont", EnglishOrthography.displayForm("dont", "ID"))
        assertEquals("i", EnglishOrthography.displayForm("i", "ID"))
        assertEquals("im", EnglishOrthography.displayForm("im", "ID"))
        assertEquals("cant", EnglishOrthography.displayForm("cant", "ID"))

        val idCandidates = listOf("dont", "bisa", "selamat")
        val processed = EnglishOrthography.processCandidates(idCandidates, "ID")
        assertEquals(idCandidates, processed)
    }

    @Test
    fun testRegularWordsPassThrough() {
        assertEquals("hello", EnglishOrthography.displayForm("hello", "EN"))
        assertEquals("keyboard", EnglishOrthography.displayForm("keyboard", "EN"))
    }

    @Test
    fun testToCanonical() {
        assertEquals("dont", EnglishOrthography.toCanonical("don't"))
        assertEquals("dont", EnglishOrthography.toCanonical("Don't"))
        assertEquals("im", EnglishOrthography.toCanonical("I'm"))
        assertEquals("cant", EnglishOrthography.toCanonical("can't"))
        assertEquals("its", EnglishOrthography.toCanonical("it's"))
        assertEquals("i", EnglishOrthography.toCanonical("I"))
        assertEquals("hello", EnglishOrthography.toCanonical("hello"))
    }

    @Test
    fun testProcessCandidatesPromotionAndRestoration() {
        // 3668: foot vs dont -> don't is restored and promoted over foot
        val cands3668 = listOf("foot", "dont", "font", "doot")
        val processed3668 = EnglishOrthography.processCandidates(cands3668, "EN")
        assertEquals("don't", processed3668[0])
        assertEquals("foot", processed3668[1])
        assertEquals("font", processed3668[2])

        // 27368: brent vs arent -> aren't promoted over brent
        val cands27368 = listOf("brent", "arent")
        val processed27368 = EnglishOrthography.processCandidates(cands27368, "EN")
        assertEquals("aren't", processed27368[0])
        assertEquals("brent", processed27368[1])

        // 96873: youse vs youre -> you're promoted over youse
        val cands96873 = listOf("youse", "youre")
        val processed96873 = EnglishOrthography.processCandidates(cands96873, "EN")
        assertEquals("you're", processed96873[0])
        assertEquals("youse", processed96873[1])

        // 2268: cant restored to can't
        val cands2268 = listOf("cant", "abou", "banu")
        val processed2268 = EnglishOrthography.processCandidates(cands2268, "EN")
        assertEquals("can't", processed2268[0])

        // 46: im restored to I'm
        val cands46 = listOf("in", "go", "im")
        val processed46 = EnglishOrthography.processCandidates(cands46, "EN")
        assertEquals("in", processed46[0])
        assertEquals("go", processed46[1])
        assertEquals("I'm", processed46[2])

        // 4: i restored to I
        val cands4 = listOf("i")
        val processed4 = EnglishOrthography.processCandidates(cands4, "EN")
        assertEquals("I", processed4[0])
    }

    @Test
    fun testProcessCandidatesAmbiguousExpansion() {
        // 487 (its): should offer both it's and its
        val cands487 = listOf("its", "gus", "hup")
        val processed487 = EnglishOrthography.processCandidates(cands487, "EN")
        assertTrue(processed487.contains("it's"))
        assertTrue(processed487.contains("its"))
        assertEquals("it's", processed487[0])
        assertEquals("its", processed487[1])

        // 9373 (were): should offer both were and we're
        val cands9373 = listOf("were", "zere")
        val processed9373 = EnglishOrthography.processCandidates(cands9373, "EN")
        assertTrue(processed9373.contains("were"))
        assertTrue(processed9373.contains("we're"))
        assertEquals("were", processed9373[0])
        assertEquals("we're", processed9373[1])

        // 9355 (well): should offer both well and we'll
        val cands9355 = listOf("well")
        val processed9355 = EnglishOrthography.processCandidates(cands9355, "EN")
        assertTrue(processed9355.contains("well"))
        assertTrue(processed9355.contains("we'll"))
        assertEquals("well", processed9355[0])
        assertEquals("we'll", processed9355[1])

        // 455 (ill): should offer both I'll and ill
        val cands455 = listOf("ill", "ilk")
        val processed455 = EnglishOrthography.processCandidates(cands455, "EN")
        assertTrue(processed455.contains("I'll"))
        assertTrue(processed455.contains("ill"))
        assertEquals("I'll", processed455[0])
        assertEquals("ill", processed455[1])

        // 5387 (lets): should offer both let's and lets
        val cands5387 = listOf("lets", "jets")
        val processed5387 = EnglishOrthography.processCandidates(cands5387, "EN")
        assertTrue(processed5387.contains("let's"))
        assertTrue(processed5387.contains("lets"))
        assertEquals("let's", processed5387[0])
        assertEquals("lets", processed5387[1])
    }
}
