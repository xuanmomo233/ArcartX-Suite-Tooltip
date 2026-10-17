package xuanmo.arcartx_suite_tooltips.client;

import net.minecraft.world.item.ItemStack;
import priv.seventeen.artist.arcartx.common.api.events.ArcartXEventManager;
import priv.seventeen.artist.arcartx.common.api.events.EventHandler;
import priv.seventeen.artist.arcartx.common.api.events.Listener;
import priv.seventeen.artist.arcartx.common.api.events.Priority;
import priv.seventeen.artist.arcartx.common.api.events.minecraft.screen.TooltipRenderEvent;
import xuanmo.arcartx_suite_tooltips.ArcartXSuiteTooltips;

/** 在 AX 自身的 tip 处理器（Medium）之前绑定实际渲染对象，包括 AX 物品槽的覆盖栈。 */
public final class ArcartXTooltipListener implements Listener {
    private static boolean registered;
    private boolean failureLogged;

    static void register() {
        if (registered) return;
        ArcartXEventManager.registerListener(new ArcartXTooltipListener());
        registered = true;
    }

    @EventHandler(priority = Priority.Highest, ignoreCancelled = false)
    public void onTooltipRender(TooltipRenderEvent event) {
        try {
            // AX 的 Forge 适配层直接传递实现了 AX ItemStack 接口的原生物品栈。
            // 文本提示或无法识别的栈必须绑定为空，不能沿用上一次物品。
            Object nativeStack = event.itemStack();
            ItemStack stack = nativeStack instanceof ItemStack item ? item : ItemStack.EMPTY;
            AxsTooltipAria.bindRenderedTooltip(stack);
        } catch (Throwable failure) {
            AxsTooltipAria.clear();
            if (!failureLogged) {
                failureLogged = true;
                ArcartXSuiteTooltips.LOGGER.warn("[AXS-Tooltips] tip 渲染上下文绑定失败，已清空附加词条", failure);
            }
        }
    }
}
