# docs/knowledge — 跨任务知识库

跨任务沉淀的业务知识与工程原则。由 `dreamer` agent 从 task memory 上浮维护。

## 目录

| 子目录 | 内容 |
|--------|------|
| `background/` | 项目背景、生态定位、用户场景 |
| `principles/` | 工程元原则（怎么判断、怎么决策） |
| `pitfalls/` | 反模式、踩坑清单 |

## 上浮规则

详见 `agents/dreamer/dreamer.md` 的 §2 上浮判断硬标准。三条同时满足才上浮：

1. 去掉任务专名后结论仍然成立且仍有信息量
2. 现有 knowledge / engineering 尚未覆盖
3. 来源有具体证据（被推翻的假设、踩过的坑、讨论记录）

## 维护原则

- 只增不删（除非主会话明确批准）
- 任何条目末尾必须注明来源 task memory
- 与 `docs/engineering/conventions.md` 的关系：knowledge 是经验，conventions 是规则。规则稳定后从 knowledge 升级进 conventions
