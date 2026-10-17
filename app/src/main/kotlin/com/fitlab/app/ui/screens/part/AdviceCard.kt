package com.fitlab.app.ui.screens.part

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitlab.app.data.db.FilamentProfileEntity
import com.fitlab.core.advice.FilamentOption
import com.fitlab.core.advice.UsageAdvisor
import com.fitlab.core.advice.UsageType
import com.fitlab.core.fit.Material

/**
 * Karta „Zalecenia”: po wyborze typu zastosowania podpowiada filament i orientację druku oraz wstępnie wybiera
 * pasujący profil filamentu (bez zmiany uczenia kompensacji).
 */
@Composable
fun AdviceCard(
    filaments: List<FilamentProfileEntity>,
    activeFilamentName: String,
    onUseFilament: (Long) -> Unit,
) {
    var usage by remember { mutableStateOf<UsageType?>(null) }
    val profiles = filaments.map { f ->
        (runCatching { Material.valueOf(f.material) }.getOrDefault(Material.OTHER)) to f.name
    }

    fun profileFor(o: FilamentOption): FilamentProfileEntity? =
        UsageAdvisor.matchProfile(o, profiles)?.let { filaments[it] }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Zalecenia", style = MaterialTheme.typography.titleSmall)
            Text("Do czego posłuży część?")
            UsageType.entries.forEach { u ->
                FilterChip(
                    selected = usage == u,
                    onClick = {
                        usage = u
                        // Wstępny wybór filamentu: pasujący profil głównej rekomendacji.
                        profileFor(UsageAdvisor.advise(u).primary)?.let { onUseFilament(it.id) }
                    },
                    label = { Text(u.label) },
                )
            }
            usage?.let { u ->
                val a = UsageAdvisor.advise(u)
                OptionBlock("Zalecany: ", a.primary, profileFor(a.primary), activeFilamentName, onUseFilament)
                a.alternatives.forEach { alt ->
                    OptionBlock("Alternatywa: ", alt, profileFor(alt), activeFilamentName, onUseFilament)
                }
                Text("Orientacja: ${a.orientationTip}")
                a.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun OptionBlock(
    prefix: String,
    o: FilamentOption,
    profile: FilamentProfileEntity?,
    activeName: String,
    onUse: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(prefix + o.label, style = MaterialTheme.typography.titleSmall)
        Text(o.reason)
        o.notes.forEach { Text("• $it") }
        if (profile != null) {
            OutlinedButton(onClick = { onUse(profile.id) }, enabled = !activeName.startsWith(profile.name)) {
                Text("Użyj profilu: ${profile.name}")
            }
        } else {
            Text("Brak profilu filamentu dla tego materiału; dodaj go w Profilach.")
        }
    }
}

