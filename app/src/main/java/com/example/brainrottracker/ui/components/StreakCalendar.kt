package com.example.brainrottracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.brainrottracker.theme.DangerRed
import com.example.brainrottracker.theme.SuccessGreen
import com.example.brainrottracker.theme.TextTertiary
import java.time.LocalDate

@Composable
fun StreakCalendar(
    streakData: Map<String, Boolean>,
    modifier: Modifier = Modifier,
    weeks: Int = 12
) {
    val today = LocalDate.now()
    val totalDays = weeks * 7
    val startDate = today.minusDays(totalDays.toLong() - 1)
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    Column(modifier = modifier) {
        Row {
            // Day labels
            Column(
                modifier = Modifier.padding(end = 4.dp)
            ) {
                dayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        modifier = Modifier.height(14.dp)
                    )
                }
            }

            // Grid
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height((7 * 14).dp)
            ) {
                val cellSize = size.width / weeks
                val cellPadding = 2f
                val actualCellSize = cellSize - cellPadding * 2

                for (dayOffset in 0 until totalDays) {
                    val date = startDate.plusDays(dayOffset.toLong())
                    val dayOfWeek = (date.dayOfWeek.value - 1) // 0=Mon, 6=Sun
                    val weekIndex = dayOffset / 7

                    val dateStr = date.toString()
                    val underLimit = streakData[dateStr]

                    val color = when (underLimit) {
                        true -> SuccessGreen.copy(alpha = 0.8f)
                        false -> DangerRed.copy(alpha = 0.7f)
                        null -> Color.White.copy(alpha = 0.05f)
                    }

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(
                            x = weekIndex * cellSize + cellPadding,
                            y = dayOfWeek * (size.height / 7) + cellPadding
                        ),
                        size = Size(actualCellSize, (size.height / 7) - cellPadding * 2),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Legend
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Under limit", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Spacer(Modifier.width(4.dp))
            Canvas(Modifier.width(12.dp).height(12.dp)) {
                drawRoundRect(SuccessGreen.copy(alpha = 0.8f), cornerRadius = CornerRadius(2f))
            }
            Spacer(Modifier.width(16.dp))
            Text("Over limit", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Spacer(Modifier.width(4.dp))
            Canvas(Modifier.width(12.dp).height(12.dp)) {
                drawRoundRect(DangerRed.copy(alpha = 0.7f), cornerRadius = CornerRadius(2f))
            }
        }
    }
}
