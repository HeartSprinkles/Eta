package io.github.mangi.eta.ui.screens.providers

import io.github.mangi.eta.agent.auth.ProviderAccountSummary
import io.github.mangi.eta.data.model.ProviderAuthModes
import io.github.mangi.eta.data.model.ProviderSetting

/**
 * Providers are ordered by whether a usable credential is available, without disturbing their
 * existing order within either group.
 */
internal fun ProviderSetting.isConfiguredForUse(configuredOAuthProviderIds: Set<String>): Boolean = when {
    ProviderAuthModes.isOAuth(authMode) -> id in configuredOAuthProviderIds
    authMode == ProviderAuthModes.API_KEY -> apiKey.isNotBlank()
    else -> false
}

/** Select OAuth provider IDs whose credential lookup completed successfully and found a usable account. */
internal fun List<ProviderSetting>.resolveConfiguredOAuthProviderIds(
    hasUsableAccount: (ProviderSetting) -> Boolean,
): Set<String> = asSequence()
    .filter { ProviderAuthModes.isOAuth(it.authMode) && hasUsableAccount(it) }
    .mapTo(mutableSetOf(), ProviderSetting::id)

/** Match the credential session's entitlement and reauthentication checks without exposing tokens. */
internal fun ProviderAccountSummary.isUsableCredential(): Boolean = !needsReauthentication && planEnabled

/** Stable partition: preserve the incoming relative order for configured and unconfigured providers. */
internal fun List<ProviderSetting>.sortedConfiguredFirst(
    configuredOAuthProviderIds: Set<String> = emptySet(),
): List<ProviderSetting> {
    val (configured, unconfigured) = partition { it.isConfiguredForUse(configuredOAuthProviderIds) }
    return if (unconfigured.isEmpty() || configured.isEmpty()) this else configured + unconfigured
}
