@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.oks328.m3music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.oks328.m3music.R
import io.github.oks328.m3music.account.AccountState

@Composable
internal fun SignInScreen(state: AccountState, onSignIn: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 440.dp).fillMaxWidth(), shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(painterResource(R.drawable.ic_music_note), null, Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                    if (state.initializing || state.connecting) {
                        Text("Checking your YouTube Music account…")
                        LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        Text(if (state.expired) "Reconnect your Google account to continue."
                            else "Sign in with Google to open your YouTube Music library.")
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(onClick = onSignIn) {
                            Text(if (state.expired) "Reconnect Google account" else "Sign in with Google")
                        }
                    }
                }
            }
        }
    }
}
