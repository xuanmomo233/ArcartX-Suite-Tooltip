# ArcartX-Suite-Tooltip

ArcartX Suite 客户端辅助 mod（Forge 1.20.1），为 ArcartX 自定义 tip UI 提供
TACZ 枪械和 Apotheosis 神化词条的数据桥接，并实现配件"支持枪械"浏览界面。

> 本仓库为 ArcartX-Suite-Tooltip 的开源代码仓库，供二次修改使用。

## 功能

### Aria 全局函数（作用于当前悬停物品）

| 函数 | 返回 | 说明 |
|---|---|---|
| `axstooltip.getTaczLore()` | `List<String>` | TACZ 物品文本行（枪械属性/配件增益/弹药箱/工作台描述，带 `§` 颜色码） |
| `axstooltip.getTaczGuns()` | `List<String>` | TACZ 配件"支持枪械"的 itemJson 列表（持有优先排序，供 `Slot(~Icon)` + `setItemIcon`） |
| `axstooltip.getTaczOwnedCount()` | `number` | `getTaczGuns()` 前 N 项为背包已持有 |
| `axstooltip.getTaczGunNames()` | `List<String>` | 支持枪械显示名列表（与 getTaczGuns 同序） |
| `axstooltip.hasTaczGuns()` | `boolean` | 当前配件是否有支持枪械列表 |
| `axstooltip.getTaczGunCount()` | `number` | 支持枪械总数 |
| `axstooltip.getTaczGunRows(列数)` | `number` | 按列数算行数 `ceil(n/cols)` |
| `axstooltip.shiftDown()` | `boolean` | 玩家是否按住 Shift |
| `axstooltip.showGunGrid()` / `showTaczStats()` | `boolean` | 网格/文本互斥显隐 |
| `axstooltip.getApothLore()` | `List<String>` | Apotheosis 宝石槽描述行 |
| `axstooltip.getExtraLore()` | `List<String>` | 两者合并（TACZ 在前） |
| `axstooltip.hasTacz()` / `hasApoth()` / `hasExtra()` | `boolean` | 附加信息存在性 |
| `axstooltip.loreHidden()` | `boolean` | lore 隐藏开关原始状态 |
| `axstooltip.loreVisible()` | `boolean` | **有效显隐**：开关开着且物品带 TACZ/神化内容才隐藏，普通物品 lore 恒显示 |
| `axstooltip.toggleLore()` | `boolean` | 翻转显隐，返回新状态 |
| `axstooltip.loreKeyName()` | `String` | lore 切换键的本地化显示名（跟随按键设置改键，如 `V`/`Left Shift`/`Mouse Button 4`） |

### 按键隐藏 lore（默认 `V` 键）

mod 注册了客户端按键 **切换物品 Lore 介绍显示**（选项 → 按键绑定中可改键）。
`loreVisible()` 是有效显隐而非裸开关：**只在悬停带 TACZ/神化附加内容的物品
时 V 才翻转开关、才隐藏 lore**——普通物品按 V 无任何效果。

GUI 界面打开时 vanilla 按键绑定不触发，mod 在 `ScreenEvent.KeyPressed.Pre`
中处理按键（输入框焦点时跳过，不影响 JEI 搜索等输入）。

tip yml 中绑定 `axstooltip.loreVisible()` 即可，右下角小提示可用
`loreKeyName()` 动态显示键名：

```yaml
lore:
  type: Text
  val: lore
  attribute:
    texts: var.lore列表
    visible: axstooltip.loreVisible()
按键提示:
  type: Text
  attribute:
    point: ~bottom_right
    texts: "'§8按 ' + axstooltip.loreKeyName() + (axstooltip.loreVisible() ? ' 隐藏说明' : ' 显示说明')"
```

### TACZ 配件"支持枪械"浏览界面（双模式）

悬停配件按 **Shift** 打开支持枪械浏览界面。**模式由服务端决定**——tooltip
模块进服时通过 `axstooltip:gunui` plugin message 推送 `gun-list.mode` 配置：

```
mode: ax  → 服务端 openUi(AXS:gun_list)，AX 自定义 UI（960×720 面板，
            10×6 网格、72px 格子、绿描边标记持有、滚轮/←→/按钮翻页、
            ESC 关闭；数据走 axstooltip.* 全局函数，不走包）
mode: mod → 客户端本地 Forge Screen（同尺寸自适应面板、原版 tooltip、
            ESC 返回原界面、GUI 缩放 1-5 自适应收格距保底 10×6）
模块未装/无推送 → 默认 mod 本地界面（向后安全）
```

服务端诊断：`/axs tooltip status` 显示当前模式与通道；
`/axs tooltip push <玩家>` 手动重推模式。

### 保留行为

- `APOTH_REMOVE_MARKER` 标记行仍被移除（防止泄漏进 `Tip.getLore()`）
- `GradientColor` 动态渐变色冻结为基色（防止 lore 每帧抖动）
- `AXS_TOOLTIP_DATA` 服务端上报不变，附加行随包附带
- 枪包缺 `gun/slot/*_slot.png` 贴图时 fallback 物品图标

### 悬停残留防护

Shift 触发检查走 `getSlotUnderMouse()`（容器界面实时查询）+
tooltip 缓存 350ms 新鲜度窗口（JEI 等非容器界面兜底）——鼠标离开物品后
不会误开浏览界面。

## 网络通道

| 通道 | 方向 | 用途 |
|---|---|---|
| `AXS_TOOLTIP_DATA` | C→S（ArcartX 通道） | tooltip 文本+结构化数据上报 |
| `AXS_GUN_LIST` | C→S（ArcartX 通道） | ax 模式下 Shift 请求打开浏览 UI |
| `axstooltip:gunui` | S→C（Bukkit plugin message） | 服务端推送 UI 模式（`ax`/`mod`） |

`axstooltip:gunui` 由 Mixin `GunUiModePayloadMixin` 拦截
`ClientPacketListener.handleCustomPayload` 接收（Forge `ChannelBuilder`
事件通道实测不生效，mixin 与 ArcartX-Suite-Map 同构）。

## 依赖

| 依赖 | 类型 | 版本 |
|---|---|---|
| Minecraft | 必需 | 1.20.1 |
| Forge | 必需 | 47.3.0+ |
| ArcartX | 必需 | 2.7.92 |
| TACZ | 可选 | 1.1.5+（1.1.7+ 的独头弹改装检测自动启用） |
| Apotheosis | 可选 | 7.4.8+ |
| Apotheosis Modern Ragnarok | 可选 | 7.0.0+ |

## 构建

```bash
gradlew build
```

需要 JDK 17。产物为 `build/libs/ArcartX-Suite-Tooltip-<版本>.jar`。
编译依赖 jar 在 `libs/` 目录，clone 后可直接构建。

## 发布

推送 `v*` 格式的 tag 即可触发自动构建和 Release 发布：

```bash
git tag v1.4.4
git push origin v1.4.4
```

## 声明

- 本项目为**私有项目**，保留所有权利（All Rights Reserved）。未经许可不得
  复制、修改、再分发或用于商业用途。
- `libs/` 目录下的 ArcartX/TACZ/Apotheosis JAR 版权归各自作者所有，仅作为
  编译依赖随本私有仓库附带。
- 本 Mod 与 Mojang / Microsoft 无任何隶属关系。
