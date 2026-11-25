# Fork 维护协议变更记录

## 1.0.0

- 建立 `upstream-tracking` / `custom-main` / `feature/*` 三层分支职责；跟踪分支只快进，定制线性 rebase。
- 重建三个独立定制提交，隔离供应商排序、用量格式化与 Debug 安装标识；移除旧 merge 同步方案在新主线中的作用。
- 新增只读双变体验证、快进/rebase/冲突/精确 lease 安全测试与每周同步工作流。
- 提升定制前保留远端备份；旧分支、原 SHA 和备份 tag 留作回滚。
- 新增本地 Git hooks 与服务端 tracking 规则。应用版本继承上游，此记录不代表正式应用发版。
