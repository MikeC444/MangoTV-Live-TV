package com.mangotv.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.components.MangoLogo
import com.mangotv.app.ui.theme.MangoBackground

/**
 * The very first thing rendered on every launch — resolves to Home or
 * AuthStart almost immediately (a local-only check, see
 * AuthGateViewModel) and is gone from the back stack the moment it
 * decides, so it's never something a user can navigate back into. Kept
 * deliberately minimal (just the logo — no spinner, no copy) since it's
 * expected to be on screen for a very short time.
 */
@Composable
fun AuthGateScreen(
    onNavigate: (GateDestination) -> Unit,
    viewModel: AuthGateViewModel = viewModel()
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()

    LaunchedEffect(destination) {
        destination?.let(onNavigate)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MangoBackground),
        contentAlignment = Alignment.Center
    ) {
        MangoLogo(fontSize = 32.sp)
    }
}
