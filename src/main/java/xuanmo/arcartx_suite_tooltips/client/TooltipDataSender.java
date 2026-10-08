package xuanmo.arcartx_suite_tooltips.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import priv.seventeen.artist.arcartx.common.api.ArcartXClient;

/**
 * 将采集到的 tooltip 数据通过 ArcartX 自定义网络包发送给服务端。
 * <p>
 * 使用 {@code ArcartXClient.sendPacket(packetId, data...)} 发送，
 * 服务端通过 {@code ClientPacketHandler} 接收。
 * <p>
 * 数据包格式：
 * <pre>
 * packetId: "AXS_TOOLTIP_DATA"
 * data[0]: 物品指纹（物品注册名，如 "tacz:ak47" 或 "minecraft:diamond_sword"）
 * data[1]: 物品类型 ID（同 data[0]，保留用于扩展）
 * data[2]: tooltip 文本行（JSON 数组字符串，每行一条）
 * data[3]: 结构化数据（JSON 对象字符串，包含数值属性）
 * </pre>
 * <p>
 * 指纹格式为 {@code ForgeRegistries.ITEMS.getKey(item).toString()}（即 {@code namespace:path}），
 * 与服务端 {@code ItemStack.getType().getKey().toString()} 一致，确保缓存能正确匹配。
 * <p>
 * 内置 2 秒冷却：同一物品指纹在 2 秒内不会重复发送，避免 ItemTooltipEvent
 * 每帧触发导致每秒 60 次发包。
 */
public final class TooltipDataSender {

    /** 数据包 ID，与服务端 ClientPacketHandler 约定 */
    public static final String PACKET_ID = "AXS_TOOLTIP_DATA";

    /** 同一物品指纹的发送冷却（毫秒），避免每帧发包 */
    private static final long COOLDOWN_MILLIS = 2000L;

    /** 上次发送的物品指纹 */
    private static String lastFingerprint = null;
    /** 上次发送时间戳 */
    private static long lastSendTime = 0L;

    private TooltipDataSender() {}

    /**
     * 发送 tooltip 数据给服务端。
     *
     * @param stack 物品栈
     * @param textLines tooltip 文本行列表
     * @param structuredData 结构化数据 JSON 字符串
     */
    public static boolean send(ItemStack stack, List<String> textLines, String structuredData) {
        if (stack == null || stack.isEmpty()) return false;
        if (textLines == null || textLines.isEmpty()) return false;

        try {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key == null) return false;
            String itemId = key.toString();
            String fingerprint = itemId;

            long now = System.currentTimeMillis();
            if (fingerprint.equals(lastFingerprint) && now - lastSendTime < COOLDOWN_MILLIS) {
                return false;
            }
            lastFingerprint = fingerprint;
            lastSendTime = now;

            // 构建文本行 JSON 数组
            StringBuilder linesJson = new StringBuilder("[");
            for (int i = 0; i < textLines.size(); i++) {
                if (i > 0) linesJson.append(",");
                linesJson.append("\"").append(escapeJson(textLines.get(i))).append("\"");
            }
            linesJson.append("]");

            // 构建数据包
            List<String> data = new ArrayList<>(4);
            data.add(fingerprint);
            data.add(itemId);
            data.add(linesJson.toString());
            data.add(structuredData != null ? structuredData : "{}");

            ArcartXClient.sendPacket(PACKET_ID, new ArrayList<>(data));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
