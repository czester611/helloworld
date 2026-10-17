package com.fitlab.core.advice

import com.fitlab.core.fit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UsageAdvisorTest {
    @Test
    fun primaryMapping() {
        val expected = mapOf(
            UsageType.INDOOR_LIGHT to Material.PLA,
            UsageType.PROTOTYPE to Material.PLA,
            UsageType.AESTHETIC to Material.PLA,
            UsageType.PRECISION_FIT to Material.PETG,
            UsageType.MOVING_WEAR to Material.PETG,
            UsageType.LOAD_BEARING to Material.PETG,
            UsageType.HEAT_SUN to Material.ASA,
            UsageType.OUTDOOR_UV to Material.ASA,
            UsageType.SEAL_DAMPER to Material.TPU,
        )
        assertEquals(UsageType.entries.toSet(), expected.keys)
        expected.forEach { (u, m) -> assertEquals(m, UsageAdvisor.advise(u).primary.material, u.name) }
    }

    @Test
    fun nylonIsAlternativeForMovingAndLoaded() {
        listOf(UsageType.MOVING_WEAR, UsageType.LOAD_BEARING).forEach {
            assertTrue(UsageAdvisor.advise(it).alternatives.any { a -> a.nameHint == "nylon" })
        }
    }

    @Test
    fun everyAdviceHasTextsAndWarnings() {
        UsageType.entries.forEach { u ->
            val a = UsageAdvisor.advise(u)
            assertTrue(a.primary.reason.isNotBlank() && a.primary.notes.isNotEmpty())
            assertTrue(a.orientationTip.isNotBlank())
            assertTrue(a.warnings.any { it.contains("krytycznych dla bezpieczeństwa") })
            assertTrue(a.warnings.any { it.contains("sprawdź w instrukcji drukarki") })
        }
    }

    @Test
    fun noClaimsAboutChamberOrNozzleLimit() {
        UsageType.entries.forEach { u ->
            val all = UsageAdvisor.advise(u).let { it.alternatives + it.primary }
            assertTrue(all.none { o -> o.notes.any { n -> Regex("\\d{3} ?°C").containsMatchIn(n) && n.contains("dysz") } })
        }
    }

    @Test
    fun profileMatching() {
        val profiles = listOf(Material.PLA to "PLA (domyślny)", Material.PETG to "PETG", Material.OTHER to "Nylon PA12 CF")
        assertEquals(1, UsageAdvisor.matchProfile(UsageAdvisor.PETG, profiles))
        assertEquals(2, UsageAdvisor.matchProfile(UsageAdvisor.NYLON, profiles))
        assertNull(UsageAdvisor.matchProfile(UsageAdvisor.ASA, profiles))
        assertNull(UsageAdvisor.matchProfile(UsageAdvisor.NYLON, profiles.take(2)))
        assertEquals(0, UsageAdvisor.matchProfile(UsageAdvisor.NYLON, listOf(Material.OTHER to "PA6")))
    }

    @Test
    fun enclosureAdvice() {
        val indoor = UsageAdvisor.forEnclosure(false)
        assertEquals(Material.PLA, indoor.primary.material)
        assertEquals(UsageType.INDOOR_LIGHT, indoor.usage)
        val outdoor = UsageAdvisor.forEnclosure(true)
        assertEquals(Material.ASA, outdoor.primary.material)
        assertTrue(outdoor.alternatives.any { it.material == Material.PETG })
        assertTrue(outdoor.orientationTip.contains("Pokrywę"))
        assertTrue(outdoor.warnings.any { it.contains("krytycznych dla bezpieczeństwa") })
    }
}
