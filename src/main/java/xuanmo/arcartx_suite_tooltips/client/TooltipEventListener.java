package xuanmo.arcartx_suite_tooltips.client;

import java.util.ArrayList;
import java.util.List;
import com.mojang.datafixers.util.Either;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import xuanmo.arcartx_suite_tooltips.ArcartXSuiteTooltips;

/**
 * ItemTooltipEvent 监听器：采集物品的独立数据快照，
 * 清理 tooltip 中的 Apotheosis 标记行与动态渐变颜色，并将 tooltip 数据
 * 发送给服务端 tooltip 模块。
 * <p>
 * 使用 LOW 优先级确保在 Apotheosis 的 HIGH 优先级监听器（affixTooltips）和
 * AttributesLib 的 AddAttributeTooltipsEvent 之后执行，这样 Apotheosis 已经
 * 插入了 affix 描述行和 {@code APOTH_REMOVE_MARKER} 标记行。
 * <p>
 * 不再向 {@code event.getToolTip()} 注入 TACZ/Apotheosis 文本行——注入会让
 * {@code Tip.getLore()} 的行数/宽度随动态值每帧抖动，导致 tip 面板宽高重建
 * 闪烁。附加信息改由 {@code axstooltip.*} Aria 函数提供给 tip yml 独立渲染。
 * 标记行仍移除（防止 {@code APOTH_REMOVE_MARKER} 文本泄漏进 AX tip 的 lore）。
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = ArcartXSuiteTooltips.MOD_ID)
public class TooltipEventListener {

    private static final boolean DEBUG = Boolean.getBoolean("axs.tooltip.debug");

    /** DEBUG 模式下限流：每 N 帧才输出一次完整日志，避免日志爆炸 */
    private static final long DEBUG_LOG_INTERVAL_MS = 500L;
    private static long lastDebugLogTime = 0L;

    /**
     * 仅返回刚实际显示过的物品；界面、鼠标位置、渲染帧或超时失效时返回 EMPTY。
     */
    public static ItemStack freshHoveredStack() {
        return AxsTooltipAria.recentRenderedStack();
    }

    static TooltipData collectTooltipData(ItemStack stack) {
        ensureApotheosisChecked();
        return AxsTooltipAria.collect(stack, apotheosisAvailable);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        List<Component> tooltip = event.getToolTip();
        long now = System.currentTimeMillis();
        boolean shouldLog = DEBUG && (now - lastDebugLogTime >= DEBUG_LOG_INTERVAL_MS);

        // 1. 仅采集本物品的快照；JEI/聊天等后台取 tooltip 不改变当前渲染上下文。
        TooltipData data = collectTooltipData(stack);

        if (shouldLog) {
            lastDebugLogTime = now;
            System.out.println("[AXS-DEBUG] ===== onItemTooltip(LOW) =====");
            System.out.println("[AXS-DEBUG]   item=" + stack.getDescriptionId()
                + " tooltipLines=" + tooltip.size()
                + " frame=" + getGameTick());
            dumpTooltip("BEFORE", tooltip);
        }

        // 2. Apotheosis 宝石槽：仅移除标记行（防止泄漏进 AX tip 的 lore 文本）
        if (apotheosisAvailable) {
            boolean removed = tooltip.removeIf(c -> "APOTH_REMOVE_MARKER".equals(c.getString()));
            if (shouldLog) {
                System.out.println("[AXS-DEBUG]   Apotheosis: markerRemoved=" + removed);
            }
        }

        // 3. 固定化动态颜色（防止渐变颜色每帧变化导致 lore 文本抖动）
        freezeDynamicColors(tooltip, shouldLog);

        if (shouldLog) {
            dumpTooltip("AFTER", tooltip);
        }

        // 4. 发送给服务端（附加行随包附带，不进 vanilla tooltip）
        sendTooltipDataToServer(stack, tooltip, data, shouldLog);

        // 5. 普通物品不展示原版/AttributesLib 自动生成的属性行。
        // 放在上报之后，保持服务端聊天预览所需的完整数据。
        TooltipAttributeFilter.filter(tooltip, data.hasExtra());
    }

    /** 完整 dump tooltip 列表（含颜色类型和 RGB 值，递归检查子组件） */
    private static void dumpTooltip(String tag, List<Component> tooltip) {
        for (int i = 0; i < tooltip.size(); i++) {
            dumpComponentRecursive(tag, i, tooltip.get(i), "");
        }
    }

    private static void dumpComponentRecursive(String tag, int idx, Component c, String subPath) {
        boolean isMutable = c instanceof MutableComponent;
        Style style = isMutable ? ((MutableComponent) c).getStyle() : c.getStyle();
        TextColor color = style.getColor();
        String colorClass = color == null ? "null" : color.getClass().getSimpleName();
        int colorValue = color == null ? -1 : color.getValue();
        System.out.println("[AXS-DEBUG]   [" + tag + " " + idx + subPath + "] "
            + "text='" + c.getString() + "' "
            + "cType=" + c.getClass().getSimpleName() + " "
            + "mutable=" + isMutable + " "
            + "color=" + colorClass + "(0x" + Integer.toHexString(colorValue) + ") "
            + "contents=" + c.getContents().getClass().getSimpleName()
            + " siblings=" + c.getSiblings().size());
        // 递归打印子组件
        List<Component> siblings = c.getSiblings();
        for (int j = 0; j < siblings.size(); j++) {
            dumpComponentRecursive(tag, idx, siblings.get(j), subPath + ".s[" + j + "]");
        }
    }

    /**
     * LOWEST 优先级调试监听器：在所有其他 mod 的监听器执行完后，
     * 打印最终 tooltip 列表（含颜色信息），确认最终状态。
     * <p>
     * 使用 500ms 限流，避免每帧日志爆炸。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltipDebug(ItemTooltipEvent event) {
        if (!DEBUG) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastLowestDebugLogTime < DEBUG_LOG_INTERVAL_MS) return;
        lastLowestDebugLogTime = now;
        List<Component> tooltip = event.getToolTip();
        System.out.println("[AXS-DEBUG] ===== onItemTooltip(LOWEST final) =====");
        System.out.println("[AXS-DEBUG]   item=" + stack.getDescriptionId() + " lines=" + tooltip.size());
        dumpTooltip("FINAL", tooltip);
    }

    private static long lastLowestDebugLogTime = 0L;

    /**
     * Apotheosis 超等级附魔渐变色（LIGHT_BLUE_FLASH）的基色。
     * <p>
     * GradientColor 的 88 个值中 70 个为此色（0x00b3ff），占 80% 时间。
     * 固定为此色可消除闪烁，同时保留 Apotheosis 超等级附魔的标志性深蓝色。
     */
    private static final int APOTH_GRADIENT_BASE_COLOR = 0x00b3ff;

    /**
     * 遍历 tooltip 列表，将动态颜色（GradientColor 等基于 tick 变化的 TextColor）
     * 替换为固定颜色，避免 ArcartX UI 的 Tip.getLore() 每帧读到变化的颜色码导致闪烁。
     * <p>
     * 检测方式：通过类名包含 "Gradient" 判断是否为动态颜色（兼容 placebo 的
     * GradientColor 和 Minecraft 原版的 TextColor.GradientColor）。
     * 替换为 {@link #APOTH_GRADIENT_BASE_COLOR}（0x00b3ff 深蓝色）。
     * <p>
     * 仅替换 MutableComponent 行（可修改 style 的行），只读行跳过。
     */
    private static void freezeDynamicColors(List<Component> tooltip, boolean shouldLog) {
        int replacedCount = 0;
        for (int i = 0; i < tooltip.size(); i++) {
            Component c = tooltip.get(i);
            int[] result = freezeComponentRecursive(c, shouldLog, "[" + i + "]");
            replacedCount += result[0];
        }
        if (shouldLog && replacedCount > 0) {
            System.out.println("[AXS-DEBUG]   freezeColor: replaced " + replacedCount + " dynamic colors");
        }
    }

    /**
     * 递归冻结 Component 及其所有子组件（siblings）中的 GradientColor。
     * 返回 int[1]，其中 [0] 为替换次数。
     */
    private static int[] freezeComponentRecursive(Component c, boolean shouldLog, String path) {
        int[] result = new int[]{0};
        boolean isMutable = c instanceof MutableComponent;
        Style style = isMutable ? ((MutableComponent) c).getStyle() : c.getStyle();
        TextColor color = style.getColor();
        String colorClass = color == null ? "null" : color.getClass().getName();
        int colorValue = color == null ? -1 : color.getValue();

        if (shouldLog) {
            System.out.println("[AXS-DEBUG]   freezeColor " + path + " "
                + "text='" + c.getString() + "' "
                + "mutable=" + isMutable + " "
                + "color=" + colorClass + "(0x" + Integer.toHexString(colorValue) + ")"
                + " siblings=" + c.getSiblings().size());
        }

        if (isMutable && color != null) {
            String className = color.getClass().getSimpleName();
            if (className.contains("Gradient")) {
                int frozenValue = getGradientBaseColor(color);
                if (shouldLog) {
                    System.out.println("[AXS-DEBUG]   freezeColor " + path + " "
                        + ">>> FREEZING " + className + "(0x" + Integer.toHexString(colorValue)
                        + ") → TextColor(0x" + Integer.toHexString(frozenValue) + ") [BASE]");
                }
                ((MutableComponent) c).setStyle(style.withColor(TextColor.fromRgb(frozenValue)));
                result[0]++;
            }
        }

        // 递归处理子组件
        if (isMutable) {
            List<Component> siblings = c.getSiblings();
            for (int j = 0; j < siblings.size(); j++) {
                int[] sub = freezeComponentRecursive(siblings.get(j), shouldLog, path + ".s[" + j + "]");
                result[0] += sub[0];
            }
        }
        return result;
    }

    /** 反射缓存：GradientColor.gradient 字段 */
    private static volatile java.lang.reflect.Field gradientField = null;
    private static volatile boolean gradientFieldResolved = false;

    /**
     * 获取 GradientColor 的基础颜色值（gradient[0]）。
     * <p>
     * GradientColor 构造函数调用 super(gradient[0], id)，所以 TextColor.value
     * 字段存储的就是 gradient[0]。但 GradientColor 重写了 getValue() 使其动态变化，
     * 所以不能直接调用 getValue()。这里反射读取 gradient 数组取第一个元素。
     *
     * @param color GradientColor 实例
     * @return 基础颜色值（gradient[0]），反射失败时回退到当前动态值
     */
    private static int getGradientBaseColor(TextColor color) {
        if (!gradientFieldResolved) {
            try {
                java.lang.reflect.Field f = color.getClass().getDeclaredField("gradient");
                f.setAccessible(true);
                gradientField = f;
            } catch (Exception ignored) {
            }
            gradientFieldResolved = true;
        }
        if (gradientField != null) {
            try {
                int[] arr = (int[]) gradientField.get(color);
                if (arr != null && arr.length > 0) return arr[0];
            } catch (Exception ignored) {
            }
        }
        // 回退：使用当前动态值（不理想但不会崩溃）
        return color.getValue();
    }

    /** Apotheosis 是否存在，首次调用时检测，避免重复触发 NoClassDefFoundError */
    private static volatile boolean apotheosisChecked = false;
    private static volatile boolean apotheosisAvailable = false;

    /**
     * 首次调用时检测 Apotheosis 是否存在，结果缓存到 {@link #apotheosisAvailable}。
     * 避免每帧重复触发 {@code NoClassDefFoundError}。
     */
    private static void ensureApotheosisChecked() {
        if (apotheosisChecked) return;
        try {
            Class.forName("dev.shadowsoffire.apotheosis.adventure.client.SocketTooltipRenderer");
            apotheosisAvailable = true;
        } catch (ClassNotFoundException ignored) {
            apotheosisAvailable = false;
        }
        apotheosisChecked = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderFrameStart(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) AxsTooltipAria.beginFrame();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderFrameEnd(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) AxsTooltipAria.endFrame();
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        AxsTooltipAria.clear();
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        AxsTooltipAria.clear();
    }

    /** 登出重置：清空悬停与附加 lore 缓存，避免跨服务器残留。 */
    @SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        AxsTooltipAria.resetSession();
    }

    /**
     * LOWEST 优先级调试监听器：打印 GatherComponents 事件最终 elements 列表，
     * 用于确认 Apotheosis 的 SocketComponent 是否已被绕过，以及 elements 行数是否稳定。
     * <p>
     * GatherComponents 是 ArcartX onTooltipRender 的数据源（ClientTooltipComponent 列表），
     * 如果这里行数每帧变化，说明闪烁根因在此阶段。
     * <p>
     * 仅在 {@code -Daxs.tooltip.debug=true} 时输出，500ms 限流。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        if (!DEBUG) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastGatherDebugLogTime < DEBUG_LOG_INTERVAL_MS) return;
        lastGatherDebugLogTime = now;

        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        int totalWidth = 0;
        int totalHeight = elements.size() * 10; // 每行约 10px
        System.out.println("[AXS-DEBUG] ===== GatherComponents(LOWEST) =====");
        System.out.println("[AXS-DEBUG]   item=" + stack.getDescriptionId()
            + " elements=" + elements.size()
            + " tick=" + getGameTick());
        for (int i = 0; i < elements.size(); i++) {
            Either<FormattedText, TooltipComponent> e = elements.get(i);
            final net.minecraft.client.gui.Font f = font;
            String info = e.map(
                ft -> {
                    String colorInfo = "";
                    int width = f.width(ft);
                    if (ft instanceof Component) {
                        Component c = (Component) ft;
                        Style s = c.getStyle();
                        TextColor col = s.getColor();
                        String colClass = col == null ? "null" : col.getClass().getSimpleName();
                        int colVal = col == null ? -1 : col.getValue();
                        colorInfo = " color=" + colClass + "(0x" + Integer.toHexString(colVal) + ")";
                    }
                    return "TEXT: '" + ft.getString() + "' ftType=" + ft.getClass().getSimpleName() + " width=" + width + colorInfo;
                },
                tc -> "IMAGE/COMPONENT type=" + tc.getClass().getSimpleName()
            );
            // 累加最大宽度
            int w = e.map(ft -> font.width(ft), tc -> 0);
            if (w > totalWidth) totalWidth = w;
            System.out.println("[AXS-DEBUG]   [GC " + i + "] " + info);
        }
        System.out.println("[AXS-DEBUG]   [GC SUMMARY] totalWidth=" + totalWidth + " totalHeight=" + totalHeight);
    }

    private static long lastGatherDebugLogTime = 0L;

    /** 获取当前 game tick（用于关联 GradientColor 的 tick 循环） */
    private static long getGameTick() {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null) {
                return mc.level.getGameTime();
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    /**
     * 采集 tooltip 文本行和结构化数据，发送给服务端。
     * 客户端内置 2 秒冷却（同一物品指纹），避免每帧发包。
     */
    private static void sendTooltipDataToServer(ItemStack stack, List<Component> tooltip, TooltipData data, boolean shouldLog) {
        try {
            List<String> textLines = new ArrayList<>(tooltip.size());
            for (Component component : tooltip) {
                String text = component.getString();
                if (text != null && !text.isBlank()) {
                    textLines.add(text);
                }
            }
            // 附加行（TACZ/Apotheosis）随包附带，保持服务端聊天预览内容不变
            textLines.addAll(data.extraLines());

            String structuredData = TaczTooltipCollector.collectStructuredData(stack);
            if (structuredData == null) {
                structuredData = "{}";
            }

            boolean sent = TooltipDataSender.send(stack, textLines, structuredData);
            if (shouldLog) {
                System.out.println("[AXS-DEBUG]   sendToServer: sent=" + sent
                    + " textLines=" + textLines.size()
                    + " structuredDataLen=" + structuredData.length());
            }
        } catch (Throwable t) {
            if (shouldLog) {
                System.out.println("[AXS-DEBUG]   sendToServer: EXCEPTION " + t);
            }
        }
    }
}
