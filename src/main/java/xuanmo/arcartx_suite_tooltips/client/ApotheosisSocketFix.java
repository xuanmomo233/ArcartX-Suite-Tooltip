package xuanmo.arcartx_suite_tooltips.client;

import dev.shadowsoffire.apotheosis.adventure.client.SocketTooltipRenderer;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketedGems;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemInstance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 从 ItemStack 提取 Apotheosis 宝石槽描述文本行。
 * <p>
 * 原方案在 {@code RenderTooltipEvent.GatherComponents} 中将 Apotheosis 的
 * {@code SocketComponent}（图像类型 TooltipComponent）替换为文本行，但
 * GatherComponents 中的 {@code remove+add} 操作不安全——当
 * {@code SocketTooltipRenderer.getSocketDesc} 抛异常或 {@code SocketedGems.gems()}
 * 返回空列表时，{@code remove} 已执行但 {@code add} 未执行/执行 0 次，
 * SocketComponent 丢失且无文本行补回，外层 {@code catch (Throwable)} 吞掉异常，
 * 导致该帧 elements 列表行数减少；下一帧 Apotheosis 重新插入 SocketComponent
 * 又正常替换，帧间行数跳变使 ArcartX 自定义 UI 的 {@code val.lore.height}
 * 抖动，表现为整块 UI 重绘闪烁。
 * <p>
 * 现改为在 {@code ItemTooltipEvent}（LOW 优先级，在 Apotheosis 的
 * AddAttributeTooltipsEvent/affixTooltips 之后）中直接从 ItemStack 提取
 * 宝石描述并追加为文本行，同时在 {@code TooltipEventListener} 中移除 Apotheosis
 * 插入的 {@code APOTH_REMOVE_MARKER} 标记行，使 Apotheosis 的 GatherComponents
 * 监听器（{@code AdventureModuleClient.comps}）找不到标记而不插入 SocketComponent。
 * <p>
 * 此类仅在 Apotheosis 存在时才会被类加载，调用方用 try-catch 包裹。
 */
public final class ApotheosisSocketFix {

    private ApotheosisSocketFix() {}

    /**
     * 从 ItemStack 提取 Apotheosis 宝石槽描述文本行。
     * <p>
     * 使用 Apotheosis public API（{@link SocketHelper}、{@link SocketedGems}、
     * {@link SocketTooltipRenderer}），不使用反射。
     *
     * @param stack 物品栈
     * @return 宝石描述文本行列表；无宝石槽或无宝石时返回空列表
     */
    public static List<Component> collectSocketLines(ItemStack stack) {
        int sockets = SocketHelper.getSockets(stack);
        if (sockets <= 0) return List.of();

        SocketedGems gems = SocketHelper.getGems(stack);
        if (gems == null || gems.isEmpty()) return List.of();

        List<Component> lines = new ArrayList<>();
        for (GemInstance inst : gems.gems()) {
            lines.add(SocketTooltipRenderer.getSocketDesc(inst));
        }
        return lines;
    }
}
