# Fork 独有功能保留清单

下列行为是稳定契约；上游同步时保留最新上游结构，以小钩子接入独立实现。不恢复已被上游删除的旧 UI 或未采用的实验功能。

| 功能 | 独立实现 / 最小接入 | 回归验证 |
| --- | --- | --- |
| 当前选中的供应商置顶，其余已配置 API Key 或可用 OAuth 账号的供应商紧随其后；组内顺序稳定、排序不改选中项 | `ui/screens/providers/ProviderListOrdering.kt`、`ConfiguredProviderUiState.kt`；`ModelProviderListScreen.kt` 仅接入状态与排序 | `ProviderListOrderingTest`：选中项置顶、选中项缺失时等价于仅按凭据分区、IO 凭据读取、恢复页面时刷新、取消传播、不把令牌放进 UI 状态 |
| 用量总数以 K/M/B 紧凑显示、明细保持精确 | `ui/components/chat/TokenAmountFormat.kt`；统计卡片仅替换顶部标题/格式化调用 | `TokenAmountFormatTest`：阈值、四舍五入进位、负数、区域、Int 上限与精确明细 |
| 正式 / Debug 安装身份均独立于上游，三者可共存安装 | `core/ForkPackageIdentity.kt`、`gradle/fork-debug.gradle`；清单权限、authority 与安装包标识同步；自身画布路由与 ColorOS 后台设置使用实际包名 | `ForkComponentIdentityTest`、`AssistantBindingTest`、`CanvasRoutingPolicyTest`、`EntrySurfaceGuardTest`；实际 Debug / Release APK 权限、authority、组件类名验证 |
| 后台命令完成通知提供「继续处理」按钮，回到原会话并按普通发送流程续跑 | `agent/terminal/TerminalJobWakePrompt.kt`、`BackgroundCommandNotification.kt`、`ui/MainActivityLaunchRequests.kt`、`ui/app/AgentAppRoot.kt`；任务记录新增 `wakeConversationKey`，仅 App 内会话记录 | `TerminalJobWakePromptTest`：指令内容、状态措辞、日志尾部截断、缺失日志；只在用户点按时续跑，不在后台自动发起模型调用 |
| tok/s 只按可信的流式解码窗口计算；一次性返回的响应退回整轮耗时，仍不可信则不展示速度 | `agent/model/DecodeTiming.kt`；`AgentLoop` 用量累加器只替换窗口选择，其余统计口径不变 | `DecodeTimingTest`：正常窗口保留、缓冲式返回回退、200ms 与 1000 tok/s 边界、缺失窗口、零 token |

Kotlin 文件路径以 `app/src/main/kotlin/io/github/mangi/eta/` 为前缀；对应测试在 `app/src/test/kotlin/io/github/mangi/eta/`。

## 配置与 namespace 边界

- 翻译资源放在各语言目录的 `fork_strings.xml`，不修改上游默认 `strings.xml`，也不定义重复同名资源期待文件顺序覆盖。
- 安装身份由外置 `gradle/fork-debug.gradle` 的 `finalizeDsl` 覆盖，晚于上游 `defaultConfig`；Release 为 `io.github.mangi.etafork`，默认 Debug 为 `io.github.mangi.etafork.debug`。不修改上游 `namespace` 或应用版本。
- 现有 `-Peta.applicationIdSuffix=.debug` 保持兼容。省略此属性默认 `.debug`；显式空值 `-Peta.applicationIdSuffix=` 仅用于 CI 的正式包名 Debug 测试夹具，不代表可分发的正式 Release。非空值必须是合法点前缀包名后缀，空白或非法值直接失败；此属性不影响 Release。
- CI 的 `standard` 变体使用空后缀验证正式安装身份的 Debug 单测 / Lint / APK，同时构建并校验真正的无签名 Release APK；`isolated` 不传属性验证默认 Debug，并检查显式 `.debug` 兼容性。标准测试产物不能冒充签名正式版；候选验证不读取签名 Secrets。
- Kotlin namespace 和实现类保持 `io.github.mangi.eta.*`。正式助理组件为 `io.github.mangi.etafork/io.github.mangi.eta.agent.voice.EtaVoiceInteractionService`，Debug 为 `io.github.mangi.etafork.debug/io.github.mangi.eta.agent.voice.EtaVoiceInteractionService`，两者均不能使用相对 `.agent...`。
- 清单中的相对实现类由 AGP 以原 namespace 解析；CI 从实际 APK 检查所有本应用组件及语音 XML 的完整类名、包名派生 authority 和签名权限。固定 action / extra / IPC 方法名属于稳定协议，不随安装包名替换。
- 改包名会创建独立应用，不自动继承旧包的数据、权限、默认助理或 LSPosed 启用状态。请先用应用已有备份 / 导出能力保留所需数据，再在新安装中导入和重新授权；本改动不自动安装、卸载或迁移设备数据。
- 可共存安装不意味着可同时激活多个 LSPosed 模块；系统入口与无障碍保护的多模块运行未在本次迁移中验证。
- 此改动不创建正式应用版本或发布标签；应用继承上游 SemVer（当前 `4.0.0`），非发布构建保留提交 SHA 元数据。新安装身份另开数据空间，不伪称兼容覆盖旧包；维护配置 / 验证能力按 SemVer 增至独立的 `1.1.0`，不以改包名为由擅自创建应用 `5.0.0` 发布。
