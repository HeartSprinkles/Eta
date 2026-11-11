package io.github.mangi.eta.ui.pages.providers

import io.github.mangi.eta.agent.auth.ProviderAccountSummary
import io.github.mangi.eta.data.model.ProviderAuthModes
import io.github.mangi.eta.data.model.ProviderSetting

/**
 * 供应商是否已填好可以直接发起请求的凭据。
 *
 * - API Key 模式：只看配置内的 `apiKey` 是否非空白；baseUrl、模型列表、默认模板都不参与判定。
 * - OAuth 模式：凭据加密存放在 ProviderCredentialStorage 中，配置内 `apiKey` 恒为空，
 *   因此由调用方在 I/O 线程预先解析出已登录的 Provider ID 集合传入。
 * - 其余未知模式：保守视为未配置。
 */
internal fun ProviderSetting.isConfiguredForUse(configuredOAuthProviderIds: Set<String>): Boolean = when {
    ProviderAuthModes.isOAuth(authMode) -> id in configuredOAuthProviderIds
    authMode == ProviderAuthModes.API_KEY -> apiKey.isNotBlank()
    else -> false
}

/**
 * 从供应商配置中挑出 OAuth 模式下已成功登录的 ID 集合。
 *
 * OAuth 凭据不在配置里，[hasUsableAccount] 由调用方在 I/O 线程提供（读取加密存储），
 * 这里只做纯逻辑筛选，便于单测覆盖。
 */
internal fun List<ProviderSetting>.resolveConfiguredOAuthProviderIds(
    hasUsableAccount: (ProviderSetting) -> Boolean,
): Set<String> = asSequence()
    .filter { ProviderAuthModes.isOAuth(it.authMode) && hasUsableAccount(it) }
    .mapTo(mutableSetOf(), ProviderSetting::id)

/**
 * OAuth 账号是否真的可以发起请求：需要已登录且账号具备模型调用权限。
 *
 * 与 [io.github.mangi.eta.agent.auth.ProviderCredentialSessions] 在取令牌时的校验保持一致，
 * 只提示“请重新登录”或“账号未授权”的账号不算已配置。
 */
internal fun ProviderAccountSummary.isUsableCredential(): Boolean = !needsReauthentication && planEnabled

/**
 * 供应商列表展示顺序：已配置凭据的排在前面，未配置的排在后面。
 *
 * 采用稳定的分区（partition）而非整体排序，两个分组内部都完整保留调用方传入的相对顺序，
 * 因此不会因为 sortOrder 重复或缺失而打乱原有列表，也不会改动当前选中的供应商。
 */
internal fun List<ProviderSetting>.sortedConfiguredFirst(
    configuredOAuthProviderIds: Set<String> = emptySet(),
): List<ProviderSetting> {
    val (configured, unconfigured) = partition { it.isConfiguredForUse(configuredOAuthProviderIds) }
    return if (unconfigured.isEmpty() || configured.isEmpty()) this else configured + unconfigured
}
