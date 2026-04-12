# 将 OpenClaw 默认模型切换为 Copilot GPT-5 mini

变更（仅修改两处）：
1. agents.defaults.model.primary: 从 "volcengine-plan/ark-code-latest" -> "gpt-5-mini"
2. agents.defaults.models: 将键 "volcengine-plan/ark-code-latest" 改为 "gpt-5-mini" 并保留值（或用 {}）

替换片段（替换 agents.defaults 下的 model 与 models）：

"model": {
  "primary": "gpt-5-mini"
},
"models": {
  "gpt-5-mini": {}
}

应用步骤：
1. 打开 OpenClaw 配置文件（示例：~/.openclaw/config.json 或你当前使用的配置文件）。
2. 备份原文件并替换上述片段（只改这两处）。
3. 保存并重启 OpenClaw 服务/daemon，使新模型生效。

备注：
- 若系统要求完整模型标识（含提供商前缀），请把 "gpt-5-mini" 改为 "copilot/gpt-5-mini" 并在 models 中对应添加条目。
- 本文档仅说明最小修改，操作前请先备份配置。
