package io.github.mangi.eta.core

import io.github.mangi.eta.BuildConfig

/** Installation identity is variable; namespace and implementation class names are not. */
internal object ForkPackageIdentity {
    val packageName: String = BuildConfig.APPLICATION_ID
    val accessibilityPermission: String = "$packageName.permission.CONTROL_ACCESSIBILITY_PROTECTION"
    val accessibilityHealthAuthority: String = "$packageName.accessibility.health"
}
