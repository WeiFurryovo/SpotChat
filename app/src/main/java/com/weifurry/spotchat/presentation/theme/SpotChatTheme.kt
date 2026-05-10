package com.weifurry.spotchat.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

@Composable
internal fun SpotChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = dynamicColorScheme(LocalContext.current) ?: spotChatColorScheme,
        typography = Typography,
        content = content
    )
}
