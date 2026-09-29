# Random Capes

解决了玩家披风过多选择困难症的问题。

每次登录随机切换披风，支持按权重抽选。Minecraft 26.3。

- 仓库主页：https://github.com/UltraURS/Random_Capes/tree/26.3
- 问题反馈：https://github.com/UltraURS/Random_Capes/issues

## 目录结构

```
RandomCapes/26.3/
├── common/     共享源码：抽选逻辑、配置、两个界面。不认识任何加载器
├── fabric/     Fabric 版入口 + fabric.mod.json
└── neoforge/   NeoForge 版入口 + neoforge.mods.toml
```

构建要求是 JDK 25（`gradle.properties` 里的 `java_version`）。如果默认 JAVA_HOME
不是 25，把它填到 `gradle.properties` 末尾已经预留的那行：

## 两种加载器各自的入口

| | Fabric | NeoForge |
|---|---|---|
| 主类 | `RandomCapesFabric`（`ClientModInitializer`） | `RandomCapesNeoForge`（`@Mod(dist = CLIENT)`） |
| 触发时机 | `ClientLifecycleEvents.CLIENT_STARTED` | `ClientTickEvent.Post`（内部去重一次） |
| 配置界面入口 | Mod Menu | `IConfigScreenFactory`（模组列表的 Config 按钮） |

两边都调用同一个 `RandomCapes.shuffleOnce(...)`，所以用了一句
`AtomicBoolean` 去重 —— NeoForge 挂的是 tick 事件，每秒会触发二十次。

## 抽选规则

- **真随机**：每次从候选里随机挑一个。
- **伪随机**：一轮之内不重复，全轮完才开新一轮（记在 `drawnCapes` 里）。
- **抽选跟随权重**：按每条披风的权重（0~1）加权抽选。权重为 0 的既不会被抽到，
  也不会占用伪随机的轮次名额。
