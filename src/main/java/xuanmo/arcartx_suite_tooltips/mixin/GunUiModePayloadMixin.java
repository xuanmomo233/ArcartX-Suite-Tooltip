package xuanmo.arcartx_suite_tooltips.mixin;

import java.nio.charset.StandardCharsets;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xuanmo.arcartx_suite_tooltips.ArcartXSuiteTooltips;
import xuanmo.arcartx_suite_tooltips.client.AxsTooltipAria;

/**
 * 拦截 {@code axstooltip:gunui} plugin message：服务端 tooltip 模块在
 * 客户端初始化完成时推送枪械浏览 UI 模式（载荷 = "ax" / "mod" 原文）。
 * <p>
 * 与 Map 模块 axsmap:* 同一路径（mixin 直接读包），绕开 Forge 事件通道——
 * 实测 Forge ChannelBuilder 事件通道在本包（Forge 47.4.x + 代理）下分发不到
 * 本地实例，vanilla 直接报 unknown packet id。
 */
@Mixin(ClientPacketListener.class)
public abstract class GunUiModePayloadMixin {

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void axst$receiveGunUiMode(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (!"axstooltip:gunui".equals(packet.getIdentifier().toString())) return;
        FriendlyByteBuf buf = packet.getData();
        if (buf == null) return;
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        String mode = new String(data, StandardCharsets.UTF_8).trim();
        AxsTooltipAria.setAxGunUi("ax".equalsIgnoreCase(mode));
        ArcartXSuiteTooltips.LOGGER.info("[AXS-Tooltips] 收到枪械 UI 模式推送: {}", mode);
        ci.cancel();
    }
}
