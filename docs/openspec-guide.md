# OpenSpec 使用手册（本项目版）

> 面向本项目的 OpenSpec 学习与操作记录。
> 所有命令均在本机 OpenSpec **1.14.1** 上实测过，标注「已实测」的是我实际跑出来验证过的结论，不是照抄文档。

---

## 一、OpenSpec 到底解决什么问题

一句话：**在写代码之前，先让人和 AI 对"要做什么"达成一致。**

它针对的是 AI 编码最大的老毛病——你随口说一句需求，AI 立刻哐哐写代码，写了半天你发现方向就不对，返工。OpenSpec 的做法是在中间插一道**计划审批**关卡：

```
你描述想法  →  AI 生成计划（不碰代码！）  →  你审阅、修改  →  AI 才动手实现
```

这不是流程洁癖，是有强制约束的。`propose` 工作流的定义文件里明写了这条硬规则（原文）：

> **Planning boundary**: This workflow creates planning artifacts only... Do not edit project code.
> After the planning artifacts are complete, stop. Do not start implementation in the same response.

也就是说 —— 提需求的那一轮，AI **只准写文档，不准写代码**，必须等你下一轮明确说"开始实现"。

**它管什么、不管什么**：管「该不该做、做成什么样」（需求层），不管「怎么实现」（代码层）。

---

## 二、三个核心概念

这是理解 OpenSpec 的全部关键，其余都是细节。

### 1. `specs/` —— 基线（Source of Truth）

系统的**当前真实行为**，按 capability（能力/模块）组织：

```
openspec/specs/
├── user-roles/spec.md
├── product-catalog/spec.md
└── group-buying/spec.md
```

它是**累积**的、长期存在的。任何时候想知道"这个系统现在应该做什么"，看这里。

### 2. `changes/` —— 变更（提案）

**一次**对系统的改动，一个功能一个目录：

```
openspec/changes/<变更名>/
├── proposal.md     # 为什么做（Why）—— 动机、范围、明确不做什么
├── specs/          # 做什么（What）—— 增量，不是全量！
├── design.md       # 怎么做（How）—— 技术方案、取舍
├── tasks.md        # 步骤 —— 实现清单，做完打勾
└── .openspec.yaml  # 元数据（schema、创建日期）
```

### 3. delta —— 增量

`changes/<名字>/specs/` 里写的**不是完整规格**，而是**相对基线的差异**。

这是最容易搞混的一点，用本项目的真实例子说明：

```markdown
# changes/mvp-group-buying/specs/order-lifecycle/spec.md
## ADDED Requirements              ← 注意这个标记

### Requirement: 待成团可取消
订单处于"待成团"状态时，用户 SHALL 能主动取消订单...
```

`## ADDED Requirements` 表示「这是在基线上**新增**的要求」。归档时，OpenSpec 会把它合并进 `specs/order-lifecycle/spec.md`。

变更标记有三种：`ADDED`（新增）、`MODIFIED`（修改既有）、`REMOVED`（删除）。因为 MVP 是全新项目，你的 7 个 spec 全是 `ADDED`。

**为什么这么设计？** 因为一个系统跑三年会有几十个变更。如果每个变更都写全量规格，根本没法看也没有 diff 可言。delta 让每次改动都是**可审阅的小块**。

---

## 三、工作流：四拍循环

每个新功能都走这一遍，周而复始：

```
① /opsx:propose <变更名>   →  生成 changes/<变更名>/ 全套计划
                 ↓
② 你审阅（可能来回改几轮）   →  纯看文档，不改代码
                 ↓
③ /opsx:apply              →  照着 tasks.md 实现，逐条打勾
                 ↓
④ /opsx:archive            →  折进 specs/，变更目录挪进 archive/
```

### ① Propose 阶段生成什么

四个文件，回答四个不同的问题：

| 文件 | 回答的问题 |
| --- | --- |
| `proposal.md` | **为什么**要做？做到什么程度？**明确不做什么**？ |
| `specs/*/spec.md` | **做什么**？（写成 Requirement + Scenario，可验证） |
| `design.md` | **怎么做**？技术选型、关键取舍 |
| `tasks.md` | **分几步**？每步的验证标准是什么 |

写 Requirement 的句式是固定的：

```markdown
### Requirement: 待成团可取消
订单处于"待成团"状态时，用户 SHALL 能主动取消订单。

#### Scenario: 取消待成团订单
- **WHEN** 用户取消一笔待成团订单
- **THEN** 订单变为"已取消"，库存回补
```

`SHALL`（必须）+ `WHEN/THEN`（触发条件/预期结果）。**好处是每条 Scenario 都能直接翻译成一条测试用例**——我上次给你做的那份 `verification-report.md` 就是这么来的：把 Scenario 逐条变成断言去跑。

### ④ Archive 阶段做什么

`openspec archive <变更名>` 会：
1. 校验 tasks 是否全部完成
2. 把 `changes/<变更名>/specs/` 里所有 delta **合并进** `openspec/specs/`
3. 把整个变更目录移到 `openspec/changes/archive/<变更名>/`

**归档才是"这个功能真正进入了系统基线"的时刻。** 没归档的变更，在 OpenSpec 眼里还是"提案中"。

---

## 四、`.claude/` 还是 `.codebuddy/`？—— 你问的那个问题

**结论：两个是同一套东西的两份拷贝，分别给两个不同的 AI 工具用。你需要哪个就装哪个。**

### 为什么会有两份

OpenSpec 是 AI 工具无关的，同一种工作流它会翻译成各个 IDE/Agent 各自的格式：

| 目录 | 服务的工具 | 谁装的 |
| --- | --- | --- |
| `.codebuddy/` | CodeBuddy | 上一个 agent 装好了 |
| `.claude/` | Claude Code | **你还没装** |

### 各自装了什么（已实测）

两边内容**完全对应**，各 12 个文件：

```
.claude/commands/opsx/{propose,apply,archive,explore,sync,update}.md   ← 6 个斜杠命令
.claude/skills/openspec-{propose,apply,archive,explore,sync,update}-*/SKILL.md  ← 6 个技能
```

- `commands/` 让你在对话框里打 `/opsx:propose` 就能触发
- `skills/` 是给 AI 读的能力说明（Claude Code 会自动发现）

### 怎么给 Claude Code 也装一份

```bash
cd E:\FreshCommunity
npx openspec init --tools claude
```

**安全性已实测**：我复制了你整个 `openspec/` 目录到临时目录跑了一遍这个命令，结果是——

- `openspec/config.yaml` md5 **一字未变**（它会打印 `Config: openspec/config.yaml (exists)` 表示沿用现有的）
- `changes/mvp-group-buying/` 下所有文件 md5 **一字未变**
- 只新增了 `.claude/` 目录

**不会碰你已有的变更**，放心跑。

想一次装多个工具就逗号分隔：`--tools claude,codebuddy,cursor`。

### 还有 6 个工作流没装

装完会提示（原文）：

> Note: 6 more workflows are available (new, continue, ff, bulk-archive, verify, onboard).
> Add them with `openspec config profile`.

这 6 个是进阶工作流（`verify` 校验、`bulk-archive` 批量归档等），默认给的 6 个够用了，想要再 `npx openspec config profile` 打开。

### 不装行不行？

行。不装的话在 Claude Code 里你就没有 `/opsx:*` 斜杠命令，但可以**直接跟 AI 说**"用 openspec 提一个 change：加评价功能"，AI 会用底层 CLI（`openspec` 命令本身）驱动，效果一样。斜杠命令只是省了这句开场白。

**底层是同一套东西**——不管你用哪个工具、走不走斜杠命令，真正干活的都是 `npx openspec` 这个 CLI 和 `openspec/` 目录里那几个 markdown 文件。

---

## 五、命令速查

```bash
# 查看
npx openspec list                    # 列出所有活跃变更（--specs 列出基线规格）
npx openspec view                    # 交互式仪表盘，最直观
npx openspec show <名字>             # 查看某个变更/规格详情
npx openspec status                  # 各产物的完成状态
npx openspec context                 # 打印当前 OpenSpec 工作上下文

# 校验
npx openspec validate --changes      # 校验所有变更
npx openspec validate --specs        # 校验所有基线规格
npx openspec validate --all
npx openspec validate --archived     # 校验已归档变更的 tasks 是否全勾（可做 pre-commit 钩子）
npx openspec validate --strict       # 严格模式

# 生命周期
npx openspec archive <变更名>        # 归档：折进 specs/ + 移入 archive/
npx openspec archive <名字> --skip-specs   # 只归档不更新 specs（适合纯文档/基建改动）
npx openspec archive <名字> -y             # 跳过确认提示

# 环境
npx openspec init --tools claude     # 装/重装 Claude Code 支持
npx openspec update                  # 刷新指令文件（工具升级后用）
npx openspec doctor                  # 检查 OpenSpec 目录健康状况
```

**你项目当前的实测状态**：

```
$ npx openspec list
Changes:
  mvp-group-buying     ✓ Complete    1h ago

$ npx openspec validate --changes
✓ change/mvp-group-buying
Totals: 1 passed, 0 failed
```

---

## 六、MVP 已归档（2026-10-11）

> 本节原先记录的是"你项目卡在哪：MVP 还没归档"。该问题已解决，结论与经验保留如下。

当前状态：

```
openspec/
├── changes/
│   └── archive/
│       └── 2026-10-11-mvp-group-buying/   ← MVP 已归档
└── specs/                                 ★ 基线已建立
    ├── user-roles/spec.md
    ├── product-catalog/spec.md
    ├── group-buying/spec.md
    ├── inventory/spec.md
    ├── order-lifecycle/spec.md
    ├── pickup-verification/spec.md
    └── admin-dashboard/spec.md
```

7 个 capability、共 23 条 requirement 已成为正式基线，`openspec validate --specs` 通过。此后每个新功能都是**对这条基线的增量**，delta 才有东西可比对。

> **后续更新（2026-10-17）**：第三个变更 `ai-shopping-assistant`（AI 导购：需求订阅 + 评价洞察）已完成端到端实测并归档。归档时 `group-buying` 走了一次 `MODIFIED` delta（开团可指定拼团价），这是本项目第一次使用修改型 delta。

> **后续更新（2026-10-23）**：第四个变更 `admin-dashboard-and-leader-ui`（管理看板扩展 + 团长经营概览 + 核销台）已归档，`admin-dashboard` 与 `pickup-verification` 走 `ADDED`、`group-buying` 再次走 `MODIFIED`。当前 `openspec/specs/` 为 **11 个 capability / 49 条 requirement / 98 个场景**，`openspec validate --all` 11/11 通过。

### 归档前先修规格与实现的偏差（本次按此执行）

原计划归档时，`specs/user-roles/spec.md` 写着「角色注册与登录」，而代码里没有注册功能（见 `docs/verification-report.md`）。当时二选一：

| 选项 | 做法 |
| --- | --- |
| A. 实现注册 | 后端加 `/api/auth/register` + 前端注册页 |
| B. 改规格 | 把注册移进「明确不做」清单 |

最终选了 **A**，并顺带修掉验证报告里的两个真实缺陷（取消订单不回退人数、管理员退款无状态守卫）。

教训：**归档会把规格原样焊进基线，所以归档前必须让规格与实现对齐** —— 否则以后所有变更都建立在假前提上。

### 归档命令

```bash
openspec archive mvp-group-buying   # CLI 自带：归档 + 更新主规格
```

本次实际走的是 agent 驱动的 sync 工作流：先按 delta 生成 7 个主规格并校验，再把变更目录移入 `archive/`。

---

## 七、什么时候用，什么时候别用

别把每个改动都套一遍流程，会累死自己。分界线：

### ✅ 值得走 OpenSpec

**新增一个 capability，或改动既有行为边界**：

- 新模块：评价系统、优惠券、消息通知、配送
- 规则变更：改取消规则、改退款策略、加限购/秒杀
- 凡是涉及 **多表 + 多接口 + 状态机** 的——因为"要什么"必须先对齐

### ❌ 直接改就行

- 改样式、调文案、加个字段
- **修 bug**——修实现缺陷 ≠ 改需求。比如我发现的取消不回退人数、退款无守卫，直接修，不用走流程
- 小 UI 打磨

**一句话**：需求层面想不清楚的才值得写规格；需求清楚、只是动手的活，直接干。

---

## 八、常见误区

| 误区 | 实际 |
| --- | --- |
| 变更目录建好就算做完了 | 得**归档**才进入基线，否则一直是"提案中" |
| `changes/*/specs/` 是完整规格 | 是 **delta**（增量），只有 `ADDED`/`MODIFIED`/`REMOVED` 的差异部分 |
| 提需求时 AI 应该顺手把代码写了 | 设计上**禁止**——propose 只出文档，等你下一轮明确说才开始写代码 |
| `tasks.md` 打勾 = 功能验证通过 | **本项目已证伪**。22 个任务全打勾，实测 3 项不达标，另漏掉 5 个 bug。打勾只代表"代码写了" |
| 得在 CodeBuddy 和 Claude Code 各写一遍 | 不用。`openspec/` 目录只有一份，两边共用；只有 `.claude/`、`.codebuddy/` 的工具适配文件是各自的 |
| OpenSpec 是一套要学的语法 | 核心就 3 个概念 + 4 个动作，半小时能上手 |

---

## 九、延伸阅读

- 官方仓库：<https://github.com/Fission-AI/OpenSpec>
- 本项目的规格实例：`openspec/changes/mvp-group-buying/`（读一遍 `proposal.md` → `design.md` → 某个 `spec.md`，比看文档快）
- 本项目的实测报告：`docs/verification-report.md`
- 命令行自带帮助：`npx openspec --help`、`npx openspec <子命令> --help`
