package com.worstalarm.clock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Day-of-week picker (v0.4 redesign): seven circular single-letter bubbles
 * running Sunday-first — S M T W T F S. Each bubble takes an equal weight of
 * the row and keeps a 1:1 aspect ratio, so the selector always fits the exact
 * width of any screen with perfectly round bubbles; no wrapping, no overflow.
 *
 * The Sunday-first DISPLAY order is mapped back to the ISO daysMask STORAGE
 * order (bit 0 = Monday … bit 6 = Sunday) by [WeekdayOrder], which is
 * unit-tested precisely so this visual change can never shift which days an
 * existing alarm rings on.
 */
@Composable
fun DayOfWeekSelector(
    daysMask: Int,
    onMaskChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(WeekdayOrder.DAY_COUNT) { index ->
            val selected = WeekdayOrder.isSelected(daysMask, index)
            val dayName = WeekdayOrder.FULL_NAMES[index]
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .then(
                        if (selected) Modifier
                        else Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            CircleShape
                        )
                    )
                    .clickable { onMaskChange(WeekdayOrder.toggle(daysMask, index)) }
                    .semantics { contentDescription = dayName },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = WeekdayOrder.LETTERS[index],
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
