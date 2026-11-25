# Fork-specific feature register

The automated sync PR checks these behaviors explicitly. Keep the behavior and its tests when resolving overlap with upstream; refactor the implementation when useful, but do not drop the user-visible contract without an intentional decision.

| Behavior to preserve | Implementation | Regression coverage |
|---|---|---|
| Configured providers (API key present or usable OAuth account) appear before unconfigured providers. Ordering is stable within each group and does not change the selected provider. OAuth credential lookup stays off the UI thread; tokens are not put into UI state, and returning from sign-in refreshes the order. | `app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/ProviderListOrdering.kt`, `ModelProviderListScreen.kt` | `app/src/test/kotlin/io/github/mangi/eta/ui/pages/providers/ProviderListOrderingTest.kt` |
| The chat usage-card headline abbreviates large totals as K/M/B with one decimal and correct carry; detailed token rows remain exact. | `app/src/main/kotlin/io/github/mangi/eta/ui/components/ChatStatsPopover.kt` | `app/src/test/kotlin/io/github/mangi/eta/ui/components/TokenAmountFormatTest.kt` |
| Runtime package checks derive from the actual application ID so a `.debug` test APK can coexist with the production app. Keep manifest authorities/permissions and runtime identity aligned with Gradle's application ID. | `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/io/github/mangi/eta/core/ModuleConfig.kt`, runtime package checks | `app/src/test/kotlin/io/github/mangi/eta/agent/runtime/EntrySurfaceGuardTest.kt`; `.github/workflows/test-build.yml` |

The first upstream integration keeps the customized provider ordering, compact usage summary, and application-ID-aware test package alongside newer upstream changes. Do not reintroduce unrelated experiments that are not listed here unless they are deliberately adopted and documented.
