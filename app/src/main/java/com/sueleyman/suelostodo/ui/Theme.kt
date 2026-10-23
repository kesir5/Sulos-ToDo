package com.sueleyman.suelostodo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.sueleyman.suelostodo.data.ListColors

val Brand = Color(0xFF2564CF)

fun listColor(index: Int) = Color(ListColors.argb(index))

object SmartColors {
    val myDay = Color(0xFF3B6EA5)
    val important = Color(0xFFC4314B)
    val planned = Color(0xFF038387)
    val due = Color(0xFFCA5010)
    val overdue = Color(0xFFC4314B)
}

@Composable
fun SuelosToDoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF8AB4F8),
        surface = Color(0xFF1F1F1F),
        background = Color(0xFF121212),
        surfaceVariant = Color(0xFF2B2B2B),
    ) else lightColorScheme(
        primary = Brand,
        background = Color(0xFFFAF9F8),
        surface = Color.White,
        surfaceVariant = Color(0xFFF3F2F1),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
