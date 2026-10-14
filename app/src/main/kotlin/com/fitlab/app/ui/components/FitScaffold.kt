package com.fitlab.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

/** Wspólny szkielet ekranu: pasek z tytułem i przyciskiem „Wstecz” (bez ikon). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("Wstecz") } },
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        bottomBar = bottomBar,
        content = content,
    )
}
