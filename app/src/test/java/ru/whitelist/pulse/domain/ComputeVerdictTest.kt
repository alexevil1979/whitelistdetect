package ru.whitelist.pulse.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.whitelist.pulse.domain.model.GroupStats
import ru.whitelist.pulse.domain.model.SiteGroup
import ru.whitelist.pulse.domain.model.VerdictKind
import ru.whitelist.pulse.domain.usecase.ComputeVerdict

class ComputeVerdictTest {

    private val engine = ComputeVerdict()

    private fun stats(group: SiteGroup, success: Int, total: Int) =
        GroupStats(group, total, success, 200)

    @Test
    fun normalInternetWhenAAndBOkCDown() {
        val verdict = engine.compute(
            a = stats(SiteGroup.WHITELIST, 14, 15),
            b = stats(SiteGroup.REGULAR, 8, 10),
            c = stats(SiteGroup.RESTRICTED, 1, 10),
            vpnActive = false,
        )
        assertEquals(VerdictKind.NORMAL, verdict.kind)
        assertTrue(verdict.confidence > 0.5f)
    }

    @Test
    fun whitelistModeWhenOnlyAOk() {
        val verdict = engine.compute(
            a = stats(SiteGroup.WHITELIST, 13, 15),
            b = stats(SiteGroup.REGULAR, 1, 10),
            c = stats(SiteGroup.RESTRICTED, 0, 10),
            vpnActive = false,
        )
        assertEquals(VerdictKind.WHITELIST_MODE, verdict.kind)
    }

    @Test
    fun noInternetWhenAllDown() {
        val verdict = engine.compute(
            a = stats(SiteGroup.WHITELIST, 0, 15),
            b = stats(SiteGroup.REGULAR, 0, 10),
            c = stats(SiteGroup.RESTRICTED, 0, 10),
            vpnActive = false,
        )
        assertEquals(VerdictKind.NO_INTERNET, verdict.kind)
    }

    @Test
    fun abroadWhenCMostlyOk() {
        val verdict = engine.compute(
            a = stats(SiteGroup.WHITELIST, 12, 15),
            b = stats(SiteGroup.REGULAR, 4, 10),
            c = stats(SiteGroup.RESTRICTED, 8, 10),
            vpnActive = false,
        )
        assertEquals(VerdictKind.ABROAD_OR_BYPASS, verdict.kind)
    }

    @Test
    fun abroadWhenAPartialAndCOk() {
        val verdict = engine.compute(
            a = stats(SiteGroup.WHITELIST, 6, 15),
            b = stats(SiteGroup.REGULAR, 2, 10),
            c = stats(SiteGroup.RESTRICTED, 9, 10),
            vpnActive = false,
        )
        assertEquals(VerdictKind.ABROAD_OR_BYPASS, verdict.kind)
    }

    @Test
    fun partialWhenMixed() {
        val verdict = engine.compute(
            a = stats(SiteGroup.WHITELIST, 8, 15),
            b = stats(SiteGroup.REGULAR, 5, 10),
            c = stats(SiteGroup.RESTRICTED, 4, 10),
            vpnActive = false,
        )
        assertEquals(VerdictKind.PARTIAL, verdict.kind)
    }

    @Test
    fun vpnOverlaysNonInternetVerdict() {
        val verdict = engine.compute(
            a = stats(SiteGroup.WHITELIST, 14, 15),
            b = stats(SiteGroup.REGULAR, 8, 10),
            c = stats(SiteGroup.RESTRICTED, 1, 10),
            vpnActive = true,
        )
        assertEquals(VerdictKind.VPN_ACTIVE, verdict.kind)
        assertEquals(VerdictKind.NORMAL, verdict.underlyingKind)
        assertTrue(verdict.vpnDistorts)
    }

    @Test
    fun vpnDoesNotOverrideNoInternet() {
        val verdict = engine.compute(
            a = stats(SiteGroup.WHITELIST, 0, 15),
            b = stats(SiteGroup.REGULAR, 0, 10),
            c = stats(SiteGroup.RESTRICTED, 0, 10),
            vpnActive = true,
        )
        assertEquals(VerdictKind.NO_INTERNET, verdict.kind)
    }

    @Test
    fun allGroupsOkIsAbroadOrBypass() {
        val verdict = engine.compute(
            a = stats(SiteGroup.WHITELIST, 15, 15),
            b = stats(SiteGroup.REGULAR, 10, 10),
            c = stats(SiteGroup.RESTRICTED, 10, 10),
            vpnActive = false,
        )
        assertEquals(VerdictKind.ABROAD_OR_BYPASS, verdict.kind)
    }

    @Test
    fun aDownBOkIsPartial() {
        val verdict = engine.compute(
            a = stats(SiteGroup.WHITELIST, 0, 15),
            b = stats(SiteGroup.REGULAR, 8, 10),
            c = stats(SiteGroup.RESTRICTED, 1, 10),
            vpnActive = false,
        )
        assertEquals(VerdictKind.PARTIAL, verdict.kind)
    }

    @Test
    fun cOpenWithoutAllowlistIsPartial() {
        val verdict = engine.compute(
            a = stats(SiteGroup.WHITELIST, 0, 15),
            b = stats(SiteGroup.REGULAR, 1, 10),
            c = stats(SiteGroup.RESTRICTED, 9, 10),
            vpnActive = false,
        )
        assertEquals(VerdictKind.PARTIAL, verdict.kind)
    }

    @Test
    fun cBorderlineKeepsNormalWhenAAndBOk() {
        val verdict = engine.compute(
            a = stats(SiteGroup.WHITELIST, 12, 15),
            b = stats(SiteGroup.REGULAR, 7, 10),
            c = stats(SiteGroup.RESTRICTED, 2, 10),
            vpnActive = false,
        )
        assertEquals(VerdictKind.NORMAL, verdict.kind)
    }

    @Test
    fun whitelistOnlyIgnoresOtherGroupsWhenFlagsOff() {
        val verdict = engine.compute(
            a = stats(SiteGroup.WHITELIST, 14, 15),
            b = stats(SiteGroup.REGULAR, 0, 10),
            c = stats(SiteGroup.RESTRICTED, 0, 10),
            vpnActive = false,
            includeRegular = false,
            includeRestricted = false,
        )
        assertEquals(VerdictKind.WHITELIST_MODE, verdict.kind)
    }
}
