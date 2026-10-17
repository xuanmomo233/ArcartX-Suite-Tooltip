package xuanmo.arcartx_suite_tooltips.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.value.BooleanValue;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.ListValue;
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
    private static volatile boolean apothContent;
    private static volatile boolean loreHidden;
    private static volatile boolean registered;

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
        if (apotheosisAvailable) {
            try {
                apothContent = ApotheosisSocketFix.hasContent(stack);
            } catch (Throwable ignored) {
                apothContent = false;
            }
            try {
                apothLines = toLegacyLines(ApotheosisSocketFix.collectSocketLines(stack));
            } catch (Throwable ignored) {
                apothLines = List.of();
            }
        } else {
            apothContent = false;
            apothLines = List.of();
        }
    }

    /** 悬停结束/断线时清空缓存，避免旧数据泄漏到下一次 tip 渲染。 */
    public static void clear() {
        taczLines = List.of();
        apothLines = List.of();
        apothContent = false;
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
        return !taczLines.isEmpty() || hasApothContent();
    }

    private static boolean hasApothContent() {
        return apothContent || !apothLines.isEmpty();
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
                data -> BooleanValue.of(hasApothContent()));
            manager.registerStaticFunction(NAMESPACE, "hasExtra",
                data -> BooleanValue.of(currentItemHasExtra()));
            manager.registerStaticFunction(NAMESPACE, "loreHidden",
                data -> BooleanValue.of(loreHidden));
            manager.registerStaticFunction(NAMESPACE, "loreVisible",
                data -> BooleanValue.of(!loreHidden || !currentItemHasExtra()));
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
