package com.example.nasbackup.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionIndicator(isConnected: Boolean) {
    val color = if (isConnected) Color.Green else Color.Red
    Text(text = if (isConnected) "Connected" else "Not Connected")
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(color, shape = CircleShape)
    )
}
