package com.worstalarm.clock.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/**
 * A digit-only field flanked by -/+ stepper buttons, for editing small non-negative
 * quantities (e.g. minutes/seconds). Tapping into the text selects its whole current
 * value so typing replaces it outright — no hunting for backspace to clear a lone "0"
 * first, and no stray digit ending up glued in front of it.
 */
@Composable
fun NumberStepperField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 999,
    step: Int = 1
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value.toString())) }

    // Resync when `value` changes for a reason other than this field's own edits —
    // e.g. this slot now represents a different step after one was deleted. A no-op
    // for our own round trip: by the time this runs, `value` already matches what we
    // just typed or stepped to.
    LaunchedEffect(value) {
        if (fieldValue.text.toIntOrNull() != value) {
            val text = value.toString()
            fieldValue = TextFieldValue(text, selection = TextRange(text.length))
        }
    }

    fun commit(newValue: Int) {
        val clamped = newValue.coerceIn(min, max)
        val text = clamped.toString()
        fieldValue = TextFieldValue(text, selection = TextRange(text.length))
        onValueChange(clamped)
    }

    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { commit(value - step) },
                enabled = value > min
            ) { Icon(Icons.Default.Remove, contentDescription = "Decrease $label") }

            OutlinedTextField(
                value = fieldValue,
                onValueChange = { new ->
                    val digits = new.text.filter(Char::isDigit).take(3)
                    fieldValue = new.copy(text = digits)
                    onValueChange((digits.toIntOrNull() ?: min).coerceIn(min, max))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            fieldValue = fieldValue.copy(
                                selection = TextRange(0, fieldValue.text.length)
                            )
                        }
                    }
            )

            FilledTonalIconButton(
                onClick = { commit(value + step) },
                enabled = value < max
            ) { Icon(Icons.Default.Add, contentDescription = "Increase $label") }
        }
    }
}
