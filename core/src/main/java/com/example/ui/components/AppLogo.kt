package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.core.R

@Composable fun AppLogo(modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.secondary) {
    Image(painterResource(R.drawable.official_chettinad_logo), contentDescription = "Chettinad Health City",
        modifier = modifier, contentScale = ContentScale.Fit)
}
