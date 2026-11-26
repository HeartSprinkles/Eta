package io.github.mangi.eta.hook.system

import io.github.mangi.eta.BuildConfig
import io.github.mangi.eta.agent.runtime.ipc.AgentRuntimeWire
import io.github.mangi.eta.core.ForkPackageIdentity
import io.github.mangi.eta.core.ModuleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForkComponentIdentityTest {
    private val voiceClass = "io.github.mangi.eta.agent.voice.EtaVoiceInteractionService"

    @Test
    fun upstreamComponentCanUseRelativeClassButLegacyDebugCannot() {
        val production = "io.github.mangi.eta/$voiceClass"
        val debug = "io.github.mangi.eta.debug/$voiceClass"
        assertEquals("io.github.mangi.eta/.agent.voice.EtaVoiceInteractionService", flattenToShortComponentName(production))
        assertEquals(debug, flattenToShortComponentName(debug))
        assertTrue(isSameComponentName(production, "io.github.mangi.eta/.agent.voice.EtaVoiceInteractionService"))
        assertFalse(isSameComponentName(debug, "io.github.mangi.eta.debug/.agent.voice.EtaVoiceInteractionService"))
    }

    @Test
    fun forkProductionAndDebugBothRequireOriginalFullyQualifiedClass() {
        listOf("io.github.mangi.etafork", "io.github.mangi.etafork.debug").forEach { packageName ->
            val component = "$packageName/$voiceClass"
            val wrongRelativeComponent = "$packageName/.agent.voice.EtaVoiceInteractionService"
            assertEquals(component, flattenToShortComponentName(component))
            assertTrue(isSameComponentName(component, component))
            assertFalse(isSameComponentName(component, wrongRelativeComponent))
        }
    }

    @Test
    fun runtimeBindingUsesActualPackageAndUnchangedServiceClass() {
        val component = requireNotNull(AgentRuntimeWire.serviceIntent().component)
        assertEquals(BuildConfig.APPLICATION_ID, component.packageName)
        assertEquals("io.github.mangi.eta.agent.runtime.AgentRuntimeService", component.className)
        assertEquals(BuildConfig.APPLICATION_ID, ForkPackageIdentity.packageName)
        assertEquals("${BuildConfig.APPLICATION_ID}/$voiceClass", ModuleConfig.ETA_VOICE_INTERACTION_COMPONENT)
        assertEquals("${BuildConfig.APPLICATION_ID}.permission.CONTROL_ACCESSIBILITY_PROTECTION", ForkPackageIdentity.accessibilityPermission)
        assertEquals("${BuildConfig.APPLICATION_ID}.accessibility.health", ForkPackageIdentity.accessibilityHealthAuthority)
    }
}
