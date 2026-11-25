import com.android.build.api.dsl.ApplicationExtension

// Fork-owned opt-in override: never change the upstream namespace or release identity.
val forkDebugSuffix = providers.gradleProperty("eta.applicationIdSuffix").orNull
    ?.takeIf { it.isNotBlank() }
require(forkDebugSuffix == null || forkDebugSuffix.matches(Regex("\\.[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*"))) {
    "eta.applicationIdSuffix must be a dot-prefixed Android package suffix"
}
if (forkDebugSuffix != null) {
    extensions.configure<ApplicationExtension> {
        buildTypes.named("debug") {
            applicationIdSuffix = forkDebugSuffix
        }
    }
}
