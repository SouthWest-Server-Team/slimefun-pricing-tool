# SfPricingTool — Slimefun 成本推导工具

XiNanTown V2 阶段 1 任务 A：Slimefun 物品 base 价成本推导脚本（需求 v1.4 §Slimefun 物品定价）。

## 功能

扫描服务器上**全部已启用**的 Slimefun 物品配方，按公式递归计算每个物品的 base 价：

```
base 价 = Σ(配方材料数量 × 材料单价) × 加工系数(来源附属)
```

- **材料单价解析**：
  - 材料是 Slimefun 物品（PDC 标记 `slimefun_item`）→ 递归计算其 base 价
  - 材料是原版物品 → 查原版材料价表（内置 `material-prices.yml`，629 种，三锚点推导；启动时复制到数据目录，管理员可编辑调价）
  - 原版材料价表未收录 → 记入 `missing-material-prices`（该格按 0 计入，报告标出）
- **配方输出格排除**：Slimefun 9 格配方 index 8 = 输出（`getRecipeOutput()`），不参与材料成本
- **加工系数分档**（按物品来源附属插件名）：

| 附属 | 系数 | 档位 |
|------|:---:|------|
| Slimefun（本体）| 1.2 | 基础 |
| FoxyMachines（神秘科技）| 1.5 | 中级 |
| InfinityExpansion（无尽科技）| 2.0 | 高级 |
| Networks / SlimeGlue 等基础设施附属 | 1.2 | 基础（2026-08-13 已确认）|

- **无配方物品**（RecipeType 为 mob_drop / barter_drop / geo_miner / null / interact）→ 归入 `no-recipe-manual-whitelist`（需求 v1.4 例外条款：白名单手动定价）
- **配方循环依赖**检测（A 需要 B、B 需要 A）→ 记入 `recipe-cycles`，不卡死

## 使用方法

1. 将 `SfPricingTool-1.0.0.jar` 放入服务器 `plugins/` 目录，重启服务器（或 `/reload confirm`）
2. 确认 Slimefun 已加载（本插件软依赖 Slimefun，Slimefun 未装时插件正常启动但扫描不可用）
3. 控制台或游戏内执行（需要 `sfprice.admin` 权限，op 默认有）：

```
/sfprice scan          # 全量扫描所有启用的 Slimefun 物品
/sfprice scan <id>     # 只扫描指定物品（调试用）
```

4. 扫描完成后，结果写入 `plugins/SfPricingTool/slimefun-prices.yml`，控制台会显示统计摘要
5. 首次启动会把内置 `material-prices.yml` 复制到 `plugins/SfPricingTool/`——**想调材料价直接编辑该文件**，改完重扫即可（无需重编译）

## 输出文件（slimefun-prices.yml）

```yaml
generated-by: SfPricingTool 1.0.0
formula: base = Σ(材料数量×单价) × 加工系数(addon)
prices:
  AUTO_DRILL: 45.6          # 物品 id → base 价（银）
  # ... 其余已定价物品
no-recipe-manual-whitelist: # 无配方物品，需人工白名单定价
  - DROPPED_ITEM
missing-material-prices:    # 原版材料价表缺口（材料 → 出现次数）
  BEDROCK: 3
recipe-cycles:              # 配方循环依赖（A↔B）
  - "A (配方循环)"
```

## 构建

### 前置依赖（先装好再编译）

| 依赖 | 坐标 | 安装方式 |
|------|------|---------|
| JDK 21 | — | 团队环境路径（搭档：`D:\Java_Home\jdk-21`；本机：`C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot`）|
| Leaf/Paper API 1.21.11 | `cn.dreeam.leaf:leaf-api:1.21.11-R0.1-SNAPSHOT`（provided）| 从服务器 `XiNanTown/libraries/` 取 jar，`mvnw install:install-file` 装入本地 m2（或 pom 指向本地仓库）|
| Slimefun4 | `io.github.thebusybiscuit:Slimefun4:2026.07-release`（provided）| 从服务器 `plugins/` 取 **slimefun4 包名** 的 `[S][粘液科技]Slimefun-2026.07-release.jar`，install 到本地 m2 |
| Maven wrapper | — | 项目自带 `.mvn/` + `mvnw.cmd`（apache-maven-3.9.6 已缓存）|

**Slimefun4 不在任何公共 Maven 仓库**（官方走 SpigotMC 下载），必须手动 install 到本地 m2。命令（**必须加 `-DgeneratePom=true`**，否则 Maven 会尝试解析 fat jar 内部的 dough 依赖 `dough:1.3.1-SNAPSHOT`，公共仓库解析失败——本插件代码只用 SlimefunItem/RecipeType/Slimefun 类，不 import dough）：

```powershell
.\mvnw.cmd install:install-file "-Dfile=<服务器plugins里的Slimefun-2026.07-release.jar>" `
  "-DgroupId=io.github.thebusybiscuit" "-DartifactId=Slimefun4" `
  "-Dversion=2026.07-release" "-Dpackaging=jar" "-DgeneratePom=true"
```

> ⚠️ `<服务器plugins里的Slimefun-2026.07-release.jar>` 换成实际路径（本机为 `D:\SST\server\XiNanTown\plugins\[S][粘液科技]Slimefun-2026.07-release.jar`，注意方括号路径要用引号包裹）。

### 编译打包

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot"
.\mvnw.cmd test       # 运行单测（9 例，纯 JVM 无需服务器）
.\mvnw.cmd package    # 打包 target/SfPricingTool-1.0.0.jar
```

## 已知限制

- `prices` 只导出 base 价 > 0 的物品；值为 0 的物品（循环依赖或缺材料）不出现
- Networks / SlimeGlue 加工系数按基础档 1.2（2026-08-13 已确认：基础设施附属归基础档，非高级科技）
- 缺价材料按 0 计入成本，最终价可能偏低——请按 `missing-material-prices` 清单补齐 `material-prices.yml` 后再重扫
