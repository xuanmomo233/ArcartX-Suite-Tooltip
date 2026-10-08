package xuanmo.arcartx_suite_tooltips.client;

import java.nio.charset.StandardCharsets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;
import xuanmo.arcartx_suite_tooltips.ArcartXSuiteTooltips;

/**
 * {@code axstooltip:gunui} plugin message 通道。
 * <p>
 * 服务端 tooltip 模块在玩家客户端初始化完成时经 Bukkit
 * {@code sendPluginMessage} 推送枪械浏览 UI 模式（载荷 = "ax" / "mod" 原文），
 * 与 map 模块 {@code axsmap:*} 同机制。
 * <p>
 * 两条路径共存：
 * <ul>
 *   <li><b>通告</b>：Forge 事件通道注册会把通道名加进 {@code minecraft:register}
 *       通告——代理/服务端据此识别客户端支持该通道（保留此用途）</li>
 *   <li><b>接收</b>：实测 Forge 事件通道在本包分发不到本地实例（vanilla 报
 *       unknown packet id），实际接收由 {@code mixin.GunUiModePayloadMixin}
 *       直接拦 {@code handleCustomPayload} 完成；若某些环境 Forge 通道可用，
 *       {@link #onPayload} 也能正常工作（幂等）</li>
 * </ul>
 * 收不到推送（模块未装/老版本）时客户端默认 mod 模式。
 */
public final class GunUiModeChannel {

    public static final ResourceLocation CHANNEL = new ResourceLocation("axstooltip", "gunui");

    private static EventNetworkChannel channel;

    private GunUiModeChannel() {}

    /** 在 mod 构造器里调用（NetworkRegistry 通道表在 CommonSetup 阶段冻结）。 */
    public static void register() {
        if (channel != null) return;
        channel = NetworkRegistry.ChannelBuilder.named(CHANNEL)
            .networkProtocolVersion(() -> "1")
            .clientAcceptedVersions(version -> true)
            .serverAcceptedVersions(version -> true)
            .eventNetworkChannel();
        channel.addListener(GunUiModeChannel::onPayload);
        ArcartXSuiteTooltips.LOGGER.info("[AXS-Tooltips] 已注册插件消息通道 {}", CHANNEL);
    }

    private static void onPayload(NetworkEvent event) {
        if (!(event instanceof NetworkEvent.ClientCustomPayloadEvent payloadEvent)) return;
        FriendlyByteBuf buf = payloadEvent.getPayload();
        if (buf == null) return;
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        String mode = new String(data, StandardCharsets.UTF_8).trim();
        AxsTooltipAria.setAxGunUi("ax".equalsIgnoreCase(mode));
        ArcartXSuiteTooltips.LOGGER.info("[AXS-Tooltips] 收到枪械 UI 模式推送(Forge通道): {}", mode);
        payloadEvent.getSource().get().setPacketHandled(true);
    }
}
