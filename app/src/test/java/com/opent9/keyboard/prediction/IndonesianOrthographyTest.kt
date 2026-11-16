package com.opent9.keyboard.prediction

import org.junit.Assert.*
import org.junit.Test

class IndonesianOrthographyTest {

    @Test
    fun testPassThroughPreservesNativeOrdering() {
        val raw = listOf("capek", "abrek", "carek")
        val processed = IndonesianOrthography.processCandidates(raw)
        assertEquals(raw, processed)
    }

    @Test
    fun testSuffixSynthesisKemandiriannya() {
        // Digits for "kemandiriannya":
        // k(5) e(3) m(6) a(2) n(6) d(3) i(4) r(7) i(4) a(2) n(6) + n(6) y(9) a(2)
        val kemandiriannyaDigits = listOf(5, 3, 6, 2, 6, 3, 4, 7, 4, 2, 6, 6, 9, 2)
        val raw = listOf("kemandirian", "kemandirianku")

        val processed = IndonesianOrthography.processCandidates(raw, kemandiriannyaDigits)
        assertEquals("kemandiriannya must be synthesized as top candidate", "kemandiriannya", processed[0])
        assertTrue("Original candidates must still be present", processed.contains("kemandirian"))
    }

    @Test
    fun testSuffixSynthesisTemankuAndBukumu() {
        // "teman" + "ku" (k=5, u=8)
        val temankuDigits = listOf(8, 3, 6, 2, 6, 5, 8)
        val rawTeman = listOf("teman")
        val processedTeman = IndonesianOrthography.processCandidates(rawTeman, temankuDigits)
        assertEquals("temanku", processedTeman[0])

        // "buku" + "mu" (m=6, u=8)
        val bukumuDigits = listOf(2, 8, 5, 8, 6, 8)
        val rawBuku = listOf("buku")
        val processedBuku = IndonesianOrthography.processCandidates(rawBuku, bukumuDigits)
        assertEquals("bukumu", processedBuku[0])
    }

    @Test
    fun testNoDuplicateIfCompoundedWordAlreadyPresent() {
        val bukunyaDigits = listOf(2, 8, 5, 8, 6, 9, 2)
        val raw = listOf("bukunya", "buku")
        val processed = IndonesianOrthography.processCandidates(raw, bukunyaDigits)
        assertEquals(1, processed.count { it == "bukunya" })
    }
}
