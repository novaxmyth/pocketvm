package com.pocketvm.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pocketvm.ui.theme.DarkBackground
import com.pocketvm.ui.theme.OnDarkSurfaceVariant

/**
 * Simulated Android 3-button navigation bar: Back, Home, Recents.
 */
@Composable
fun VirtualNavigationBar(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRecents: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground.copy(alpha = 0.9f))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button (triangle / arrow)
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Outlined.ArrowBackIosNew,
                contentDescription = "Back",
                modifier = Modifier.size(20.dp),
                tint = OnDarkSurfaceVariant
            )
        }

        // Home button (circle)
        IconButton(onClick = onHome, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Filled.Circle,
                contentDescription = "Home",
                modifier = Modifier.size(22.dp),
                tint = OnDarkSurfaceVariant
            )
        }

        // Recents button (square)
        IconButton(onClick = onRecents, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Filled.CropSquare,
                contentDescription = "Recents",
                modifier = Modifier.size(22.dp),
                tint = OnDarkSurfaceVariant
            )
        }
    }
}
