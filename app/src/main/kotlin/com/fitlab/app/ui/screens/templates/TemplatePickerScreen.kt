package com.fitlab.app.ui.screens.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitlab.app.ui.components.FitScaffold
import com.fitlab.core.templates.TemplateId
import com.fitlab.core.templates.TemplateRegistry

@Composable
fun TemplatePickerScreen(onBack: () -> Unit, onSelect: (TemplateId) -> Unit) {
    FitScaffold(title = "Wybierz szablon", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(TemplateRegistry.all, key = { it.id.name }) { t ->
                Card(Modifier.fillMaxWidth().clickable { onSelect(t.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(t.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(t.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
