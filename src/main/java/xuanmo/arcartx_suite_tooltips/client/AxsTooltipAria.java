package xuanmo.arcartx_suite_tooltips.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import priv.seventeen.artist.arcartx.common.game.world.itemstack.ItemStackParser;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.value.BooleanValue;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.ListValue;
import priv.seventeen.artist.aria.value.NumberValue;
import priv.seventeen.artist.aria.value.StringValue;
import xuanmo.arcartx_suite_tooltips.ArcartXSuiteTooltips;

/**
 * 向 Aria 脚本引擎注册 {@code axstooltip.*} 全局函数，供 ArcartX tip UI yml
 * 以 {@code axstooltip.getTaczLore()} 等方式自由拼接 TACZ/Apotheosis 附加信息。
 * <p>
 * 不再把附加文本行注入 {@code ItemTooltipEvent.getToolTip()}——注入会让
 * {@code Tip.getLore()} 的行数/宽度随动态值（弹药计数等）每帧抖动，
 * 导致 tip 面板宽高反复重建闪烁。附加数据改为由 Aria 函数直供，
 * tip yml 用独立控件区渲染，原版 lore 区保持稳定。
 * <p>
 * 注册的函数（均无参数，作用于当前悬停物品）：
 * <ul>
 *   <li>{@code axstooltip.getTaczLore()} → TACZ 物品文本行列表（含 § 颜色码）</li>
 *   <li>{@code axstooltip.getTaczGuns()} → TACZ 配件"支持枪械"的 itemJson 列表（供 Slot Icon + setItemIcon）</li>
 *   <li>{@code axstooltip.hasTaczGuns() / getTaczGunCount() / getTaczGunRows(列数) / getTaczGunNames()} → 枪支列表状态/数量/行数/名称列表</li>
 *   <li>{@code axstooltip.shiftDown() / showGunGrid() / showTaczStats()} → Shift 状态与网格/文本互斥显隐</li>
 *   <li>{@code axstooltip.getApothLore()} → Apotheosis 宝石描述文本行列表</li>
 *   <li>{@code axstooltip.getExtraLore()} → 两者合并列表（TACZ 在前）</li>
 *   <li>{@code axstooltip.hasTacz() / hasApoth() / hasExtra()} → 布尔值</li>
 *   <li>{@code axstooltip.loreHidden()} → 开关原始状态；{@code loreVisible()} → 有效显隐
 *       （开关开着且物品带 TACZ/神化内容才隐藏，普通物品 lore 恒显示）</li>
 *   <li>{@code axstooltip.toggleLore()} → 翻转显隐，返回新状态</li>
 *   <li>{@code axstooltip.loreKeyName()} → lore 切换键的本地化显示名（跟随按键设置改键）</li>
 * </ul>
 * 数据由 {@link TooltipEventListener#onItemTooltip} 每帧刷新缓存，
 * 函数调用本身只做读取，开销可忽略。
 */
public final class AxsTooltipAria {

    public static final String NAMESPACE = "axstooltip";

    private static volatile List<String> taczLines = List.of();
    private static volatile List<String> apothLines = List.of();
    private static volatile List<String> taczGunJsons = List.of();
    private static volatile List<String> taczGunNames = List.of();
    private static volatile boolean loreHidden;
    private static volatile boolean registered;

    /** 持有优先排序的支持枪械栈（含真实背包栈，保留 display.Name） */
    private static volatile List<ItemStack> taczGunStacks = List.of();
    /** taczGunStacks 前 N 项为已持有 */
    private static volatile int taczOwnedCount;
    /**
     * Shift 打开枪械浏览时的一次性快照。tooltip 缓存随悬停每帧刷新/清空，
     * 而"按 Shift → 服务端 → UI 打开"有网络往返——快照保证 UI 打开时数据仍在。
     */
    private static volatile List<ItemStack> pendingGuns = List.of();
    /** 快照时已序列化好的 itemJson（复用 onHover 的缓存，避免 UI 打开时再遍历序列化）。 */
    private static volatile List<String> pendingJsons = List.of();
    private static volatile int pendingOwned;
    private static volatile long pendingExpireAt;

    /**
     * 枪械浏览 UI 模式：true=服务端 AX UI（gun_list），false=本地 Forge 界面。
     * 由服务端通过 gun_channel HUD 的 gun_mode 包经 {@code setGunUiMode()} 下发；
     * 收不到推送（模块未装/老版本）时默认 false——本地界面。
     */
    private static volatile boolean axGunUi;

    /** 原版 16 色 RGB → § 颜色码字符。 */
    private static final int[] LEGACY_RGB = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA,
        0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
        0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };
    private static final char[] LEGACY_CODE = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    private AxsTooltipAria() {}

    /**
     * 每帧悬停回调：重新采集 TACZ/Apotheosis 附加行并转为带 § 颜色码的字符串。
     *
     * @param stack               当前悬停物品
     * @param apotheosisAvailable Apotheosis 是否可用（由调用方缓存检测结果）
     */
    public static void onHover(ItemStack stack, boolean apotheosisAvailable) {
        try {
            taczLines = toLegacyLines(TaczTooltipCollector.collectTooltipLines(stack));
        } catch (Throwable ignored) {
            taczLines = List.of();
        }
        try {
            TaczTooltipCollector.SortedGuns sorted = TaczTooltipCollector.collectSupportedGunsSorted(stack);
            taczGunStacks = sorted.stacks();
            taczOwnedCount = sorted.ownedCount();
            List<String> jsons = new ArrayList<>(sorted.stacks().size());
            List<String> names = new ArrayList<>(sorted.stacks().size());
            for (ItemStack gun : sorted.stacks()) {
                jsons.add(ItemStackParser.INSTANCE.toJson(
                    (priv.seventeen.artist.arcartx.common.game.world.itemstack.ItemStack) (Object) gun));
                names.add(gun.getHoverName().getString());
            }
            taczGunJsons = jsons;
            taczGunNames = names;
        } catch (Throwable ignored) {
            taczGunStacks = List.of();
            taczOwnedCount = 0;
            taczGunJsons = List.of();
            taczGunNames = List.of();
        }
        if (apotheosisAvailable) {
            try {
                apothLines = toLegacyLines(ApotheosisSocketFix.collectSocketLines(stack));
            } catch (Throwable ignored) {
                apothLines = List.of();
            }
        } else {
            apothLines = List.of();
        }
    }

    /** 悬停结束/断线时清空缓存，避免旧数据泄漏到下一次 tip 渲染。 */
    public static void clear() {
        taczLines = List.of();
        apothLines = List.of();
        taczGunJsons = List.of();
        taczGunNames = List.of();
    }

    /**
     * 翻转 lore 显隐（由 {@link TooltipKeybinds} 或 Aria {@code toggleLore()} 调用）。
     *
     * @return 翻转后的新状态（true = 隐藏）
     */
    public static boolean toggleLore() {
        loreHidden = !loreHidden;
        return loreHidden;
    }

    public static boolean isLoreHidden() {
        return loreHidden;
    }

    /** 当前悬停物品是否带 TACZ/神化附加内容（lore 隐藏开关只对它生效）。 */
    public static boolean currentItemHasExtra() {
        return !taczLines.isEmpty() || !apothLines.isEmpty();
    }

    /**
     * Shift 打开枪械浏览时拍摄快照（由 {@link GunListOpener} 调用）。
     * 之后 5 秒内 UI 的 {@code getTaczGuns()}/{@code openGunScreen()} 优先读快照。
     */
    public static void snapshotGunsForUi() {
        pendingGuns = taczGunStacks;
        pendingJsons = taczGunJsons;
        pendingOwned = taczOwnedCount;
        pendingExpireAt = System.currentTimeMillis() + 5000;
    }

    /** 快照/悬停缓存里已序列化好的 itemJson（过期回落到悬停缓存）。 */
    private static List<String> activeGunJsons() {
        return System.currentTimeMillis() <= pendingExpireAt ? pendingJsons : taczGunJsons;
    }

    /** 快照内的已排序枪栈（过期返回空）。 */
    private static List<ItemStack> activeGunStacks() {
        return System.currentTimeMillis() <= pendingExpireAt ? pendingGuns : taczGunStacks;
    }

    private static int activeOwnedCount() {
        return System.currentTimeMillis() <= pendingExpireAt ? pendingOwned : taczOwnedCount;
    }

    /** 枪械浏览 UI 是否走服务端 AX 界面（false=本地 Forge 界面，默认）。 */
    public static boolean useAxGunUi() {
        return axGunUi;
    }

    /** 由 {@link GunUiModeChannel} 在收到服务端模式推送/登出时设置。 */
    public static void setAxGunUi(boolean ax) {
        axGunUi = ax;
    }

    /**
     * 打开本地 Forge 枪械浏览界面（mod 模式）。
     * <p>
     * 由 {@link GunListOpener} 在 Shift 时直接调用；列表先拍快照再开，
     * 避免渲染期间悬停缓存被刷新。
     */
    public static boolean openLocalGunScreen() {
        snapshotGunsForUi();
        List<ItemStack> guns = activeGunStacks();
        if (guns.isEmpty()) return false;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(new GunListScreen(mc.screen, guns, activeOwnedCount()));
        return true;
    }

    /** 合并的附加行（供服务端 AXS_TOOLTIP_DATA 附带，保持聊天预览内容不变）。 */
    public static List<String> extraLines() {
        if (taczLines.isEmpty() && apothLines.isEmpty()) return List.of();
        List<String> merged = new ArrayList<>(taczLines.size() + apothLines.size());
        merged.addAll(taczLines);
        merged.addAll(apothLines);
        return merged;
    }

    /**
     * 注册 Aria 全局函数。ArcartX 不存在时静默跳过，
     * 避免 NoClassDefFoundError 拖垮整个 mod。
     */
    public static void register() {
        if (registered) return;
        try {
            Class.forName("priv.seventeen.artist.aria.callable.CallableManager");
        } catch (ClassNotFoundException e) {
            ArcartXSuiteTooltips.LOGGER.warn("[AXS-Tooltips] Aria 不可用，跳过 axstooltip 函数注册");
            return;
        }
        try {
            CallableManager manager = CallableManager.INSTANCE;
            manager.registerStaticFunction(NAMESPACE, "getTaczLore",
                data -> linesValue(taczLines));
            manager.registerStaticFunction(NAMESPACE, "getApothLore",
                data -> linesValue(apothLines));
            manager.registerStaticFunction(NAMESPACE, "getExtraLore",
                data -> linesValue(extraLines()));
            manager.registerStaticFunction(NAMESPACE, "hasTacz",
                data -> BooleanValue.of(!taczLines.isEmpty()));
            manager.registerStaticFunction(NAMESPACE, "hasApoth",
                data -> BooleanValue.of(!apothLines.isEmpty()));
            manager.registerStaticFunction(NAMESPACE, "hasExtra",
                data -> BooleanValue.of(!taczLines.isEmpty() || !apothLines.isEmpty()));
            manager.registerStaticFunction(NAMESPACE, "getTaczGuns",
                data -> linesValue(activeGunJsons()));
            manager.registerStaticFunction(NAMESPACE, "getTaczOwnedCount",
                data -> NumberValue.of(activeOwnedCount()));
            manager.registerStaticFunction(NAMESPACE, "hasTaczGuns",
                data -> BooleanValue.of(!taczGunJsons.isEmpty()));
            manager.registerStaticFunction(NAMESPACE, "getTaczGunRows",
                data -> {
                    int cols = data.argCount() > 0 ? Math.max(1, (int) data.get(0).numberValue()) : 1;
                    return NumberValue.of((taczGunJsons.size() + cols - 1) / cols);
                });
            manager.registerStaticFunction(NAMESPACE, "getTaczGunCount",
                data -> NumberValue.of(taczGunJsons.size()));
            manager.registerStaticFunction(NAMESPACE, "getTaczGunNames",
                data -> linesValue(taczGunNames));
            manager.registerStaticFunction(NAMESPACE, "shiftDown",
                data -> BooleanValue.of(net.minecraft.client.gui.screens.Screen.hasShiftDown()));
            manager.registerStaticFunction(NAMESPACE, "showGunGrid",
                data -> BooleanValue.of(!taczGunJsons.isEmpty()
                    && net.minecraft.client.gui.screens.Screen.hasShiftDown()));
            manager.registerStaticFunction(NAMESPACE, "showTaczStats",
                data -> BooleanValue.of(!taczLines.isEmpty()
                    && (taczGunJsons.isEmpty()
                        || !net.minecraft.client.gui.screens.Screen.hasShiftDown())));
            manager.registerStaticFunction(NAMESPACE, "loreHidden",
                data -> BooleanValue.of(loreHidden));
            manager.registerStaticFunction(NAMESPACE, "loreVisible",
                data -> BooleanValue.of(!loreHidden
                    || (taczLines.isEmpty() && apothLines.isEmpty())));
            manager.registerStaticFunction(NAMESPACE, "toggleLore",
                data -> BooleanValue.of(toggleLore()));
            manager.registerStaticFunction(NAMESPACE, "loreKeyName",
                data -> new StringValue(TooltipKeybinds.TOGGLE_LORE
                    .getTranslatedKeyMessage().getString()));
            registered = true;
            ArcartXSuiteTooltips.LOGGER.info("[AXS-Tooltips] Aria 函数 axstooltip.* 注册完成");
        } catch (Throwable t) {
            ArcartXSuiteTooltips.LOGGER.error("[AXS-Tooltips] 注册 Aria 函数失败", t);
        }
    }

    private static IValue<?> linesValue(List<String> lines) {
        List<IValue<?>> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(new StringValue(line));
        }
        return new ListValue(out);
    }

    /**
     * 把 styled {@link Component} 序列化为带 § 颜色码的字符串。
     * <p>
     * {@code toFlatList()} 已把父子样式合并到每个分段，逐段输出颜色/格式码后接
     * 纯文本；非原版 16 色的 RGB 颜色映射到最近的原版颜色（Text 控件不认 §x 十六进制）。
     */
    private static List<String> toLegacyLines(List<Component> components) {
        if (components == null || components.isEmpty()) return List.of();
        List<String> lines = new ArrayList<>(components.size());
        for (Component component : components) {
            StringBuilder sb = new StringBuilder();
            for (Component part : component.toFlatList()) {
                sb.append(legacyPrefix(part.getStyle()));
                sb.append(part.getString());
            }
            String line = sb.toString();
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String legacyPrefix(Style style) {
        StringBuilder sb = new StringBuilder();
        TextColor color = style.getColor();
        if (color != null) {
            sb.append('§').append(LEGACY_CODE[nearestLegacy(color.getValue())]);
        }
        if (style.isBold()) sb.append("§l");
        if (style.isItalic()) sb.append("§o");
        if (style.isUnderlined()) sb.append("§n");
        if (style.isStrikethrough()) sb.append("§m");
        if (style.isObfuscated()) sb.append("§k");
        return sb.toString();
    }

    /** RGB → 最近的原版 16 色索引（欧氏距离）。 */
    private static int nearestLegacy(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < LEGACY_RGB.length; i++) {
            int lr = (LEGACY_RGB[i] >> 16) & 0xFF, lg = (LEGACY_RGB[i] >> 8) & 0xFF, lb = LEGACY_RGB[i] & 0xFF;
            long d = (long) (r - lr) * (r - lr) + (long) (g - lg) * (g - lg) + (long) (b - lb) * (b - lb);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }
}
