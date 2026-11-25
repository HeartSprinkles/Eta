# Fork 独有功能保留清单

下列行为是稳定契约；上游同步时保留最新上游结构，以小钩子接入独立实现。不恢复已被上游删除的旧 UI 或未采用的实验功能。

| 功能 | 独立实现 / 最小接入 | 回归验证 |
| --- | --- | --- |
| 已配置 API Key 或可用 OAuth 账号的供应商前置；组内顺序稳定、不改选中项 | `ui/screens/providers/ProviderListOrdering.kt`、`ConfiguredProviderUiState.kt`；`ModelProviderListScreen.kt` 仅接入状态与排序 | `ProviderListOrderingTest`；IO 凭据读取、恢复页面时刷新、取消传播、不把令牌放进 UI 状态 |
| 用量总数以 K/M/B 紧凑显示、明细保持精确 | `ui/components/chat/TokenAmountFormat.kt`；统计卡片仅替换顶部标题/格式化调用 | `TokenAmountFormatTest`：阈值、四舍五入进位、负数、区域、Int 上限与精确明细 |
| `.debug` 测试包与正式包可共存安装 | `core/ForkPackageIdentity.kt`、`gradle/fork-debug.gradle`；清单权限、authority 与安装包标识同步 | `ForkComponentIdentityTest`、`AssistantBindingTest`、`EntrySurfaceGuardTest`；实际 APK 权限、authority、组件类名验证 |

Kotlin 文件路径以 `app/src/main/kotlin/io/github/mangi/eta/` 为前缀；对应测试在 `app/src/test/kotlin/io/github/mangi/eta/`。

## 配置与 namespace 边界

- 翻译资源放在各语言目录的 `fork_strings.xml`，不修改上游默认 `strings.xml`，也不定义重复同名资源期待文件顺序覆盖。
- Gradle 定制放在独立脚本；正常 Debug/Release 不传后缀时仍保持上游 application ID 和版本。测试传 `-Peta.applicationIdSuffix=.debug`。
- 安装 application ID 可以是 `io.github.mangi.eta.debug`；实现类仍属于 `io.github.mangi.eta.*`。因此 Debug 助理组件必须是 `io.github.mangi.eta.debug/io.github.mangi.eta.agent.voice.EtaVoiceInteractionService`，不能误用相对 `.agent...`。
- 可共存安装不意味着可同时激活两个 LSPosed 模块；系统入口与无障碍保护的双模块运行未在本次迁移中验证。
- 本次只整理既有功能及维护工具，不创建正式应用版本或发布标签；应用继承上游 SemVer，非发布构建保留提交 SHA 元数据。维护协议版本独立为 `1.0.0`。
