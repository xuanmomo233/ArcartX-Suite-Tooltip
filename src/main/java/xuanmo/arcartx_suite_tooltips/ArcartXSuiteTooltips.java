package xuanmo.arcartx_suite_tooltips;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xuanmo.arcartx_suite_tooltips.client.AxsTooltipAria;
import xuanmo.arcartx_suite_tooltips.client.GunUiModeChannel;

/**
 * ArcartX Suite 客户端辅助 mod 主类。
 * <p>
 * 为 ArcartX 自定义 tip UI 提供 TACZ 枪械和 Apotheosis 神化词条的数据桥接。
 * 附加信息不再注入原版 tooltip，改由 {@code axstooltip.*} Aria 全局函数
 * （如 {@code axstooltip.getTaczLore()}）直接提供给 tip yml 自由拼接渲染，
 * 避免动态文本注入导致的 tip 面板宽高重构与闪烁。
 * <p>
 * 事件监听器 {@code TooltipEventListener} 通过 {@code @Mod.EventBusSubscriber}
 * 注解自动注册到 Forge EVENT_BUS，无需在此手动注册。
 */
@Mod(ArcartXSuiteTooltips.MOD_ID)
public class ArcartXSuiteTooltips {
    public static final String MOD_ID = "arcartx_suite_tooltips";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public ArcartXSuiteTooltips() {
        AxsTooltipAria.register();
        // 通道必须在构造期注册：NetworkRegistry 在 CommonSetup 阶段冻结通道表，
        // enqueueWork 晚于冻结点 → 注册了也收不到包（vanilla 报 unknown packet id）
        GunUiModeChannel.register();
        LOGGER.info("ArcartX Suite Tooltips 已加载，tooltip 桥接已就绪。");
    }
}
