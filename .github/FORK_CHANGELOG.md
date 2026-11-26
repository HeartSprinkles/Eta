# Fork 维护协议变更记录

## 1.1.0

- 新增 fork 安装身份配置：Release 为 `io.github.mangi.etafork`，Debug 默认 `io.github.mangi.etafork.debug`；外置 overlay 在上游 DSL 完成后覆盖，namespace 与完整实现类名保持原值。
- 保持显式 `.debug` 后缀兼容，增加显式空后缀的 CI 专用正式包名 Debug 夹具；校验默认 Debug 和真正的无签名 Release APK，候选不接触发布签名 Secrets。
- 增强实际 APK 所有组件、语音 XML、签名权限和 authority 隔离验证，并增加验证器回归测试。
- 自身画布路由、ColorOS 后台设置使用实际安装包名；保留稳定 action / extra / IPC 协议字符串，补充两种 fork 安装身份的组件回归用例。
- 改包名是独立安装，不自动继承旧包数据或系统授权；本次不创建应用发版 / 标签，应用仍继承上游 SemVer `4.0.0`，维护协议按兼容新增配置能力递增次版本。

## 1.0.0

- 建立 `upstream-tracking` / `custom-main` / `feature/*` 三层分支职责；跟踪分支只快进，定制线性 rebase。
- 重建三个独立定制提交，隔离供应商排序、用量格式化与 Debug 安装标识；移除旧 merge 同步方案在新主线中的作用。
- 新增只读双变体验证、快进/rebase/冲突/精确 lease 安全测试与每周同步工作流。
- 提升定制前保留远端备份；旧分支、原 SHA 和备份 tag 留作回滚。
- 新增本地 Git hooks 与服务端 tracking 规则。应用版本继承上游，此记录不代表正式应用发版。
