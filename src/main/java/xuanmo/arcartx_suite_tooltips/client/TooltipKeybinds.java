package xuanmo.arcartx_suite_tooltips.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
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

    /** MOD 总线：注册按键映射。 */
    @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = ArcartXSuiteTooltips.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_LORE);
        }
    }

    /** FORGE 总线：客户端 tick 中消费按键点击（UNIVERSAL 上下文，GUI 打开时也生效）。 */
    @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = ArcartXSuiteTooltips.MOD_ID)
    public static class ForgeBus {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            while (TOGGLE_LORE.consumeClick()) {
                AxsTooltipAria.toggleLore();
            }
        }
    }
}
