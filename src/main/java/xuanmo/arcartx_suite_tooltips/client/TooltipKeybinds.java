package xuanmo.arcartx_suite_tooltips.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.EditBox;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import xuanmo.arcartx_suite_tooltips.ArcartXSuiteTooltips;

/**
 * 客户端按键：切换 tip 中 lore 介绍的显隐。
 * <p>
 * 默认绑定 {@code V} 键（可在 选项→按键绑定 中改键）。按下后翻转
 * {@link AxsTooltipAria} 的 {@code loreHidden} 标志，tip yml 中的
 * {@code axstooltip.loreVisible()} 等表达式每帧求值，立即生效，
 * 全程不经过网络往返。
 */
public final class TooltipKeybinds {

    public static final KeyMapping TOGGLE_LORE = new KeyMapping(
        "key.arcartx_suite_tooltips.toggle_lore",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        "key.categories.arcartx_suite_tooltips"
    );

    private TooltipKeybinds() {}

    /** 键盘按键码是否匹配 lore 切换键（仅键盘绑定；绑鼠标键时键码域不同，不匹配） */
    public static boolean isLoreKey(int keyCode) {
        return TOGGLE_LORE.getKey().getType() == InputConstants.Type.KEYSYM
            && TOGGLE_LORE.getKey().getValue() == keyCode;
    }

    /** MOD 总线：注册按键映射。 */
    @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = ArcartXSuiteTooltips.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_LORE);
        }
    }

    /** FORGE 总线：客户端 tick 中消费按键点击，并在界面事件中处理按键。 */
    @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = ArcartXSuiteTooltips.MOD_ID)
    public static class ForgeBus {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            while (TOGGLE_LORE.consumeClick()) {
                // 与屏内路径同一闸门：悬停物品无 TACZ/神化附加内容
                //（或根本没悬停物品，如在世界中按键）时不翻转全局
                // 开关——避免不可见的污染让下次悬停枪械时 lore 莫名消失。
                if (AxsTooltipAria.recentItemHasExtra()
                        && !TooltipEventListener.freshHoveredStack().isEmpty()) {
                    AxsTooltipAria.toggleLore();
                }
            }
        }

        /**
         * 界面打开时 vanilla {@link KeyMapping} 的 consumeClick 不触发，
         * lore 切换必须挂 ScreenEvent 才能在背包等界面生效。
         * 焦点在文本输入框（如 JEI 搜索）时跳过，避免吞掉输入的字符；
         * 普通物品（无 TACZ/神化附加内容）上按键不翻转开关。
         */
        @SubscribeEvent
        public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
            if (!isLoreKey(event.getKeyCode())
                    || event.getScreen().getFocused() instanceof EditBox
                    || !AxsTooltipAria.recentItemHasExtra()
                    || TooltipEventListener.freshHoveredStack().isEmpty()) return;
            AxsTooltipAria.toggleLore();
        }
    }
}
