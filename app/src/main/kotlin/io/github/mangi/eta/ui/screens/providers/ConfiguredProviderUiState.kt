package io.github.mangi.eta.ui.screens.providers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mangi.eta.data.model.ProviderAuthModes
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.repository.ProviderCredentialRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolve only OAuth provider IDs for which the encrypted account is usable.
 * Credentials stay in their storage layer and lookup is kept off the UI thread.
 */
@Composable
internal fun rememberConfiguredOAuthProviderIds(providers: List<ProviderSetting>): Set<String> {
    var configuredIds by remember { mutableStateOf(emptySet<String>()) }
    var credentialRevision by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) credentialRevision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(providers, credentialRevision) {
        val oauthProviders = providers.filter { ProviderAuthModes.isOAuth(it.authMode) }
        if (oauthProviders.isEmpty()) {
            configuredIds = emptySet()
            return@LaunchedEffect
        }
        configuredIds = withContext(Dispatchers.IO) {
            oauthProviders.resolveConfiguredOAuthProviderIds { provider ->
                try {
                    ProviderCredentialRepository.account(provider.id, provider.authMode)
                        ?.isUsableCredential() == true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Unreadable credentials degrade to unconfigured; cancellation must not be swallowed.
                    false
                }
            }
        }
    }

    return configuredIds
}
