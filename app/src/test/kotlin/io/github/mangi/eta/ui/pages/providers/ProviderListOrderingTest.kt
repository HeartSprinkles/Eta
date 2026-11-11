package io.github.mangi.eta.ui.pages.providers

import io.github.mangi.eta.agent.auth.ProviderAccountSummary
import io.github.mangi.eta.data.model.OpenAiCompatibleProviderSetting
import io.github.mangi.eta.data.model.ProviderAuthModes
import io.github.mangi.eta.data.model.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderListOrderingTest {
    private fun provider(
        id: String,
        sortOrder: Int = 0,
        apiKey: String = "",
        authMode: String = ProviderAuthModes.API_KEY,
    ): ProviderSetting = OpenAiCompatibleProviderSetting(
        id = id,
        name = id,
        baseUrl = "https://example.com/v1",
        apiKey = apiKey,
        sortOrder = sortOrder,
        authMode = authMode,
    )

    private fun ProviderAccountSummary?.isUsable() = this?.isUsableCredential() == true

    private fun account(needsReauthentication: Boolean = false, planEnabled: Boolean = true) =
        ProviderAccountSummary(
            email = null,
            displayName = null,
            needsReauthentication = needsReauthentication,
            planEnabled = planEnabled,
        )

    @Test
    fun `已配置凭据的供应商排在未配置之前且组内保持原有顺序`() {
        val providers = listOf(
            provider("a"),
            provider("b", apiKey = "sk-b"),
            provider("c"),
            provider("d", apiKey = "sk-d"),
            provider("e"),
        )

        assertEquals(
            listOf("b", "d", "a", "c", "e"),
            providers.sortedConfiguredFirst().map(ProviderSetting::id),
        )
    }

    @Test
    fun `全部未配置时顺序完全不变`() {
        val providers = listOf(provider("a"), provider("b"), provider("c"))

        assertEquals(
            providers.map(ProviderSetting::id),
            providers.sortedConfiguredFirst().map(ProviderSetting::id),
        )
    }

    @Test
    fun `全部已配置时顺序完全不变`() {
        val providers = listOf(
            provider("a", apiKey = "sk-a"),
            provider("b", apiKey = "sk-b"),
            provider("c", apiKey = "sk-c"),
        )

        assertEquals(
            providers.map(ProviderSetting::id),
            providers.sortedConfiguredFirst().map(ProviderSetting::id),
        )
    }

    @Test
    fun `sortOrder 与入参顺序不一致时仍严格按入参分区`() {
        val providers = listOf(
            provider("late", sortOrder = 99),
            provider("early", sortOrder = -1, apiKey = "sk"),
        )

        assertEquals(
            listOf("early", "late"),
            providers.sortedConfiguredFirst().map(ProviderSetting::id),
        )
    }

    @Test
    fun `空白 apiKey 不算已配置`() {
        assertFalse(provider("a", apiKey = "").isConfiguredForUse(emptySet()))
        assertFalse(provider("b", apiKey = "   ").isConfiguredForUse(emptySet()))
        assertFalse(provider("c", apiKey = "\n\t ").isConfiguredForUse(emptySet()))
        assertTrue(provider("d", apiKey = "sk-d").isConfiguredForUse(emptySet()))
    }

    @Test
    fun `仅有 baseUrl 或默认配置不算已配置`() {
        val onlyBaseUrl = provider("a", apiKey = "")

        assertFalse(onlyBaseUrl.isConfiguredForUse(emptySet()))
        assertEquals(listOf("a"), listOf(onlyBaseUrl).sortedConfiguredFirst().map(ProviderSetting::id))
    }

    @Test
    fun `OAuth 供应商按登录状态而不是 apiKey 判定`() {
        val loggedOut = provider("openai", authMode = ProviderAuthModes.OPENAI_CHATGPT)
        val loggedIn = provider("xai", authMode = ProviderAuthModes.XAI_GROK)
        val providers = listOf(loggedOut, loggedIn)

        assertFalse(loggedOut.isConfiguredForUse(emptySet()))
        assertTrue(loggedIn.isConfiguredForUse(setOf("xai")))
        assertEquals(
            listOf("xai", "openai"),
            providers.sortedConfiguredFirst(setOf("xai")).map(ProviderSetting::id),
        )
    }

    @Test
    fun `OAuth 供应商即使残留 apiKey 也不按 apiKey 判定`() {
        val provider = provider(
            "openai",
            apiKey = "sk-stale",
            authMode = ProviderAuthModes.OPENAI_CHATGPT,
        )

        assertFalse(provider.isConfiguredForUse(emptySet()))
        assertTrue(provider.isConfiguredForUse(setOf("openai")))
    }

    @Test
    fun `未知认证方式保守视为未配置`() {
        assertFalse(provider("a", apiKey = "sk-a", authMode = "unknown_mode").isConfiguredForUse(emptySet()))
    }

    @Test
    fun `OAuth 账号需要重新登录或未授权时不算已配置`() {
        assertTrue(account().isUsable())
        assertFalse(account(needsReauthentication = true).isUsable())
        assertFalse(account(planEnabled = false).isUsable())
        assertFalse(null.isUsable())
    }

    @Test
    fun `解析已登录 OAuth 供应商 ID 时忽略 API Key 供应商`() {
        val providers = listOf(
            provider("api", apiKey = "sk-api"),
            provider("openai", authMode = ProviderAuthModes.OPENAI_CHATGPT),
            provider("xai", authMode = ProviderAuthModes.XAI_GROK),
        )

        assertEquals(
            setOf("openai", "xai"),
            providers.resolveConfiguredOAuthProviderIds { true },
        )
        assertEquals(
            setOf("xai"),
            providers.resolveConfiguredOAuthProviderIds { it.id == "xai" },
        )
        assertEquals(
            emptySet<String>(),
            providers.resolveConfiguredOAuthProviderIds { false },
        )
    }

    @Test
    fun `空列表与单元素列表保持稳定`() {
        assertEquals(emptyList<String>(), emptyList<ProviderSetting>().sortedConfiguredFirst().map(ProviderSetting::id))
        assertEquals(listOf("only"), listOf(provider("only")).sortedConfiguredFirst().map(ProviderSetting::id))
    }

    @Test
    fun `排序不增删元素也不改变元素内容`() {
        val providers = listOf(
            provider("a"),
            provider("b", apiKey = "sk-b"),
            provider("c", apiKey = ""),
        )

        val sorted = providers.sortedConfiguredFirst()

        assertEquals(providers.size, sorted.size)
        assertEquals(providers.toSet(), sorted.toSet())
        assertEquals(listOf("b", "a", "c"), sorted.map(ProviderSetting::id))
    }
}
