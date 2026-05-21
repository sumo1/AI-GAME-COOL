# docs/task — 任务专项文档

每个具体任务在此建一个独立目录，结构按 `agents/task-designer/task-designer.md` §6 定义。

## 命名

```
docs/task/{YYMMDD}-{任务名}/
```

YYMMDD = 任务起始日期，方便字典序倒排即为时间倒序。

## 标准结构

```
{YYMMDD}-{name}/
├── progress.md              # 任务入口（必建）
├── memory/                  # 决策备忘录（必建，按天沉淀）
│   ├── README.md            # 格式约定
│   ├── YYYY-MM-DD-xxx.md    # 单条决策
│   ├── SUMMARY.md           # dreamer 整理后产出
│   └── archive/             # 已归档条目
├── plan/                    # 复杂任务的子任务设计（按需）
│   └── stepN-xxx.md         # 含【实现契约】+【验收契约】两段
├── background/              # 调研、竞品分析（按需）
└── task-code-reviewer/      # 任务专项审查规则（按需）
    └── code-review.md
```

## 生命周期

1. **task-designer** 创建目录 + `progress.md` + `memory/`
2. **coder / evaluator** 根据 `plan/*.md` 的契约施工与验收
3. 实施过程中按天往 `memory/` 写决策条目
4. 阶段收尾时 **dreamer** 蒸馏 `memory/` → `SUMMARY.md`，把跨任务原则上浮到 `docs/knowledge/`
5. **doc-refresher** 在每次提交时检查任务文档是否与代码同步
