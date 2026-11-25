# Fork Git 维护流程（1.0.0）

维护对象：`HeartSprinkles/Eta`；上游：`Mangi-11/Eta`。默认用 rebase，禁止把上游直接 merge/pull 到混合定制分支。

## 分支职责

- `upstream-tracking`：纯净跟踪上游实际默认分支，仅允许快进；禁止在此提交、删分支或改写历史。
- `custom-main`：个人定制集成与 fork 默认分支；定制提交在线性 patch stack 上，所有稳定功能汇集于此。
- `feature/*`：从 `custom-main` 开始，每分支一个独立功能；完成后同步上游、rebase，再快进集成。
- 旧 `main`、`feat/configured-providers-first`、`maintenance/upstream-sync` 和备份 tag 保留用于回滚，不作为新维护入口。旧 merge 同步 PR/工作流不再启用。

## 人工同步标准步骤

```bash
git status --short --branch               # 必须无未提交变更
git remote -v                            # origin=fork；upstream 只能 fetch
git fetch origin --prune
git fetch upstream --prune
# 用 gh repo view Mangi-11/Eta --json defaultBranchRef 确认实际默认分支
git switch upstream-tracking
git merge --ff-only upstream/main         # 默认分支改名时用实际名称
git push origin upstream-tracking         # 普通快进推送，绝不 force
old_custom=$(git rev-parse origin/custom-main)
git switch custom-main
git tag "backup/custom-main-${old_custom:0:12}" "$old_custom"  # 已存在则核对，不覆盖
git rebase upstream-tracking
# 解决冲突后 git add <逐个核对的文件> && git rebase --continue
# 所有测试 / 构建在 GitHub Actions；重写后先推唯一候选分支验证
# 确認 candidate 精确 SHA 检查全绿，并复核远端未变化后：
git push --force-with-lease="refs/heads/custom-main:$old_custom" origin custom-main
```

首次创建/线性快进可普通 push；只有重写个人专用 `custom-main` 才允许显式旧 SHA 的 `--force-with-lease`。禁止普通 `--force`，也不能对其他分支使用 force-with-lease。发现跟踪分支被污染、上游改写历史、定制栈包含额外 merge 或冲突范围过大时停止，先提出整理方案。

## 自动同步与验证

- `Fork Upstream Rebase` 每周一 07:17 UTC 运行，也可在上游发版后从 Actions 手动启动；仅在此 fork 的 `custom-main` 上执行。
- 准备 job 先校验分支职责和快进关系，更新 `upstream-tracking`；随后把定制重放到该基线，生成唯一 `sync-candidate/upstream-<run>-<attempt>`。
- 独立只读 job 对候选精确 SHA 跑维护安全测试，以及正常 / `.debug` 两组 Lint、单元测试、Debug 构建与实际 APK 标识校验。候选代码不接触写令牌或签名 Secrets。
- 全部通过后，独立提升 job 核对远端 `custom-main`、tracking、candidate 都没变化，保存 `backup/custom-main-<run>-<attempt>`，仅用精确旧 SHA lease 更新 `custom-main`。任何冲突、失败或竞态都阻止提升，不绕过 GitHub 分支规则。
- `GITHUB_TOKEN` 推送可能不触发其他工作流，所以验证是同一同步运行的前置 job，不依赖 push 连锁触发。无需 PAT，也不需要允许 Actions 创建 PR。
- `Fork Check` 覆盖 feature/*、custom-main 推送和指向 custom-main 的 PR。保留上游原有 CI 文件不进行大面积改写；本 fork 的主动维护检查由独立 fork-* 工作流承担。

冲突失败时，tracking 可能已快进，但 custom-main 保持原值；这不是完成同步。下一步由维护 Agent 检查、人工 rebase 并验证，不能把网页 “Sync fork” 或仅 tracking 更新当成 custom-main 已同步。测试失败的候选分支保留供检查；不要盲目重复提升。

## 本地护栏与回滚

运行 `bash scripts/fork/install-hooks.sh`：启用全局 rerere，禁用 upstream push URL，并安装本仓库专用 pre-commit / pre-push 护栏。安装器发现现有自定义 hooks 会停止，不覆盖。hooks 放在 Git 元数据目录，切到纯上游分支时仍生效；这些是本地辅助，不替代服务端规则。

GitHub 的 tracking 规则禁止非快进和删除；custom-main 仍为个人 rebase 分支，不因线性历史需要而添加不可满足的独立评审要求。工作流受信代码更改需维护者审阅，CODEOWNERS 标记维护文件。

回滚前先记录失败 SHA；从明确备份恢复定制内容，优先新增修复/revert 提交。若确需恢复个人 custom-main 旧历史，也先确认远端新工作，再用显式 lease；永不重置或回滚 `upstream-tracking`。旧分支和历史不会在迁移中自动删除。

每次维护报告必须包含：上游/定制起止 SHA、命令、冲突路径与处理、对应 Actions 结果和推送状态。安全测试验证规则，不代表实际应用行为已通过；应用通过两组 Android 检查后才能宣布完成。
