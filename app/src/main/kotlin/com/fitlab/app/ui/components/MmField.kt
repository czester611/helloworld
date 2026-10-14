package com.fitlab.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions

/**
 * Pole liczbowe w mm: klawiatura dziesiętna, akceptuje „,” i „.”. [allowNegative] przełącza na zwykłą klawiaturę,
 * bo klawiatura dziesiętna często nie ma znaku minus.
 */
@Composable
fun MmField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
    modifier: Modifier = Modifier,
    hint: String? = null,
    unit: String = "mm",
    allowNegative: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it) },
        label = { Text(label) },
        suffix = { Text(unit) },
        isError = error != null,
        supportingText = {
            val t = error ?: hint
            if (t != null) Text(t)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (allowNegative) KeyboardType.Text else KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}
