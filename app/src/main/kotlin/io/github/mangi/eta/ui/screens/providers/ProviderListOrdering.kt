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

/** Display rank: the selected provider leads, then providers with a usable credential. */
private fun ProviderSetting.displayRank(
    selectedProviderId: String?,
    configuredOAuthProviderIds: Set<String>,
): Int = when {
    id == selectedProviderId -> 0
    isConfiguredForUse(configuredOAuthProviderIds) -> 1
    else -> 2
}

/**
 * Order for the provider list: the selected provider first, configured providers next, the rest last.
 * Sorting is stable, so the incoming relative order inside every group is preserved.
 */
internal fun List<ProviderSetting>.sortedForDisplay(
    selectedProviderId: String? = null,
    configuredOAuthProviderIds: Set<String> = emptySet(),
): List<ProviderSetting> {
    if (size < 2) return this
    val ranks = associate { provider ->
        provider.id to provider.displayRank(selectedProviderId, configuredOAuthProviderIds)
    }
    return sortedWith(compareBy<ProviderSetting> { provider -> ranks.getValue(provider.id) })
}

/** Stable partition: preserve the incoming relative order for configured and unconfigured providers. */
internal fun List<ProviderSetting>.sortedConfiguredFirst(
    configuredOAuthProviderIds: Set<String> = emptySet(),
): List<ProviderSetting> = sortedForDisplay(null, configuredOAuthProviderIds)
