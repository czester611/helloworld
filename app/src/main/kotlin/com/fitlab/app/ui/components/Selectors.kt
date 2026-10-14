package com.fitlab.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitlab.core.fit.FitClass
import com.fitlab.core.fit.Verdict

/** Etykieta klasy pasowania po polsku. */
fun FitClass.label(): String = when (this) {
    FitClass.PRESS -> "Wcisk"
    FitClass.SLIDE -> "Suwliwe"
    FitClass.LOOSE -> "Luźne"
}

/** Wybór klasy pasowania (3 chipy). */
@Composable
fun FitClassSelector(selected: FitClass, onSelect: (FitClass) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FitClass.entries.forEach { c ->
            FilterChip(selected = c == selected, onClick = { onSelect(c) }, label = { Text(c.label()) })
        }
    }
}

/** Etykieta werdyktu po polsku. */
fun Verdict.label(): String = when (this) {
    Verdict.TOO_TIGHT -> "Za ciasno"
    Verdict.OK -> "OK"
    Verdict.TOO_LOOSE -> "Za luźno"
    Verdict.MEASURED -> "Zmierzyłem"
}

/** Wybór werdyktu (4 chipy); [selected] = null oznacza „pominięto”. */
@Composable
fun VerdictSelector(selected: Verdict?, onSelect: (Verdict?) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Verdict.entries.forEach { v ->
            FilterChip(
                selected = v == selected,
                onClick = { onSelect(if (v == selected) null else v) },
                label = { Text(v.label()) },
            )
        }
    }
}
