# Random Capes

解决了玩家披风过多选择困难症的问题。
每次登录随机切换披风，支持按权重抽选。

- 仓库主页：https://github.com/UltraURS/Random_Capes/tree/26.3
- 问题反馈：https://github.com/UltraURS/Random_Capes/issues

## 抽选规则

- **真随机**：每次从候选里随机挑一个。
- **伪随机**：一轮之内不重复，全轮完才开新一轮（记在 `drawnCapes` 里）。
- **抽选跟随权重**：按每条披风的权重（0~1）加权抽选。权重为 0 的既不会被抽到，
  也不会占用伪随机的轮次名额。
