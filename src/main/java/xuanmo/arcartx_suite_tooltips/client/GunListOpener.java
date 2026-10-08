package xuanmo.arcartx_suite_tooltips.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import priv.seventeen.artist.arcartx.common.api.ArcartXClient;
import xuanmo.arcartx_suite_tooltips.ArcartXSuiteTooltips;

import java.util.ArrayList;
import java.util.List;

/**
 * 悬停 TACZ 配件时<b>松开 Shift</b> 才打开"支持枪械"浏览界面：
 * Shift 按下仅登记"打开意图"（{@link #pendingAttachment}），意图有效期内
 * 任何鼠标点击都会作废——这样 Shift+右键快捷丢弃/删除等组合键完全不受影响；
 * 松开 Shift 且意图未作废才真正触发：
 * <ul>
 *   <li>{@code ax} — 向服务端发 {@code AXS_GUN_LIST} 开 AX UI（gun_list.yml）</li>
 *   <li>{@code mod} — 本地直接开 Forge {@link GunListScreen}</li>
 * </ul>
 * 枪数据不走网络包——axstooltip.* 是客户端全局 Aria 函数，UI 侧直接读取。
 * Shift 触发时先拍 {@link AxsTooltipAria#snapshotGunsForUi()} 快照，
 * 防止网络往返期间悬停缓存被刷新清空。
 * <p>
 * 不再取消 Shift 按下事件本身：取消会让 Shift+点击 组合键失效
 * （如管理员的 Shift+右键快捷删除）。按下不取消、释放不取消，纯意图登记。
 * <p>
 * GunListScreen 打开期间忽略 Shift——防止在界面内再按 Shift 叠开新界面
 * （ESC 每次只退一层，叠加后表现为"按很多次 ESC 才关掉"）。
 * <p>
 * 同一事件顺带处理 V 键 lore 显隐切换：vanilla {@link net.minecraft.client.KeyMapping}
 * 的 consumeClick 在 Screen 打开时不触发，必须挂 ScreenEvent 才能在背包里生效。
 * 焦点在文本输入框时跳过，避免吞掉输入的字符 'v'。
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = ArcartXSuiteTooltips.MOD_ID)
public class GunListOpener {

    public static final String PACKET_ID = "AXS_GUN_LIST";
    /** Shift 意图有效窗口：按住超过此时间视为"长按查看"，松手仍打开 */
    private static final long INTENT_WINDOW_MS = 5_000;

    /** Shift 按下瞬间登记的待开配件；松开或点击后判定/作废 */
    private static ItemStack pendingAttachment = ItemStack.EMPTY;
    private static long pendingSince = 0L;

    private static boolean isShift(int key) {
        return key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT;
    }

    /** 当前鼠标下的悬停物品：容器界面实时查槽位，其它界面用新鲜度缓存 */
    private static ItemStack hoveredStack(net.minecraft.client.gui.screens.Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container) {
            Slot slot = container.getSlotUnderMouse();
            return slot != null ? slot.getItem() : ItemStack.EMPTY;
        }
        return TooltipEventListener.freshHoveredStack();
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        int key = event.getKeyCode();

        // V 键 lore 显隐：界面打开时 vanilla 按键绑定不触发，须在此处理。
        // 焦点在输入框（如 JEI 搜索）时跳过；普通物品（无 TACZ/神化附加内容）
        // 上按 V 不翻转开关——避免"在这里按过 V，下一把枪 lore 突然没了"。
        if (key == TooltipKeybinds.TOGGLE_LORE.getKey().getValue()
                && !(event.getScreen().getFocused() instanceof EditBox)
                && AxsTooltipAria.currentItemHasExtra()
                && !TooltipEventListener.freshHoveredStack().isEmpty()) {
            AxsTooltipAria.toggleLore();
            return;
        }

        // GunListScreen 内不登记 Shift 意图：防止界面内按 Shift 叠开新层
        if (!isShift(key) || event.getScreen() instanceof GunListScreen) return;

        ItemStack hovered = hoveredStack(event.getScreen());
        if (hovered.isEmpty() || TaczTooltipCollector.collectSupportedGuns(hovered).isEmpty()) return;

        // 只登记意图，不取消事件——Shift+点击 组合键照常工作
        pendingAttachment = hovered.copy();
        pendingSince = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (!isShift(event.getKeyCode())) return;
        if (event.getScreen() instanceof GunListScreen) { pendingAttachment = ItemStack.EMPTY; return; }
        if (pendingAttachment.isEmpty()) return;

        ItemStack attachment = pendingAttachment;
        pendingAttachment = ItemStack.EMPTY;
        if (System.currentTimeMillis() - pendingSince > INTENT_WINDOW_MS) return;
        // 悬停可能已移走：重新确认配件有效（槽位内容仍支持枪械）
        if (TaczTooltipCollector.collectSupportedGuns(attachment).isEmpty()) return;

        if (AxsTooltipAria.useAxGunUi()) {
            // ax 模式：拍快照后请服务端打开 gun_list，UI 自取 axstooltip.* 数据
            AxsTooltipAria.snapshotGunsForUi();
            ArcartXClient.sendPacket(PACKET_ID, new ArrayList<>(List.of("open")));
        } else {
            // mod 模式（含未收到模式推送的默认情况）：本地直接开
            AxsTooltipAria.openLocalGunScreen();
        }
    }

    /** 按下期间任何鼠标点击都作废意图——Shift+右键等组合键不触发 UI */
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        pendingAttachment = ItemStack.EMPTY;
    }
}
