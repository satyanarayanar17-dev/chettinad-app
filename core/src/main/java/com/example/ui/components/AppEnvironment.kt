package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.BuildConfig

@Composable fun AppEnvironment(content: @Composable () -> Unit) {
    if (BuildConfig.FLAVOR == "production") { content(); return }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Test environment · synthetic records only", textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF594B12), modifier = Modifier.fillMaxWidth()
                .background(Color(0xFFFFF2C5)).padding(horizontal = 16.dp, vertical = 8.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}
