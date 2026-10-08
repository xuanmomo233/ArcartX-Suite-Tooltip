package xuanmo.arcartx_suite_tooltips.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * TACZ 配件"支持枪械"本地浏览界面（mod 模式）。
 * <p>
 * 网格分页展示配件支持的全部枪械（默认 10×6）；背包已持有的排前并以
 * <b>绿色描边</b>标记。悬停格渲染该枪的完整原版 tooltip（含服务器自定义名）。
 * 滚轮/←→键/底部文字按钮翻页，ESC 关闭并返回打开前的界面。
 * <p>
 * 尺寸自适应 GUI 缩放：格距随屏幕宽度收缩（最大 46px），行数随屏幕高度收缩
 * （最少 3 行，每页数量动态变化），保证 GUI 缩放 2-4 下均不超出屏幕。
 */
public class GunListScreen extends Screen {

    private static final int COLS = 10;
    private static final int MAX_CELL = 46;   // 大缩放下的标准格距
    private static final int MIN_CELL = 20;   // 极限下限：GUI 缩放 5（~216px 高）时仍要放满 6 行
    private static final int ROWS = 6;
    private static final int MARGIN_X = 40;   // 面板左右内边距
    private static final int MARGIN_TOP = 56; // 标题 + 统计
    private static final int MARGIN_BOT = 34; // 翻页行

    private final Screen previous;
    private final List<ItemStack> guns;
    /** 列表前 ownedCount 项为玩家背包已持有（绿框标记） */
    private final int ownedCount;
    private int page = 0;

    public GunListScreen(Screen previous, List<ItemStack> guns, int ownedCount) {
        super(Component.translatable("screen.arcartx_suite_tooltips.supported_guns"));
        this.previous = previous;
        this.guns = guns;
        this.ownedCount = ownedCount;
    }

    /**
     * 格距自适应：屏幕宽/高哪个瓶颈按哪个缩，行数始终 6 行不减枪数。
     * GUI 缩放 4 时格距缩到 ~26px（槽 20px 仍包得住 16px 图标）。
     */
    private int cell() {
        int byWidth = (width - 80) / COLS;
        int byHeight = (height - 110) / ROWS;
        return Math.max(MIN_CELL, Math.min(MAX_CELL, Math.min(byWidth, byHeight)));
    }

    private int rows() { return ROWS; }

    private int pageSize() { return COLS * ROWS; }

    private int pageCount() {
        return Math.max(1, (guns.size() + pageSize() - 1) / pageSize());
    }

    private int slot() { return cell() - 6; }

    private int panelW() { return COLS * cell() + MARGIN_X; }
    private int panelH() { return rows() * cell() + MARGIN_TOP + MARGIN_BOT; }
    private int panelX() { return (width - panelW()) / 2; }
    private int panelY() { return (height - panelH()) / 2; }

    private int gridX() { return panelX() + MARGIN_X / 2; }
    private int gridY() { return panelY() + MARGIN_TOP - 4; }

    @Override
    protected void init() {
        // 窗口尺寸变化（如切 GUI 缩放）时行数变化 → 校正页码防越界
        page = Math.min(page, pageCount() - 1);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
        g.fill(px, py, px + pw, py + ph, 0xE014141A);
        g.fill(px, py, px + pw, py + 1, 0xFF555566);
        g.fill(px, py + ph - 1, px + pw, py + ph, 0xFF555566);
        g.fill(px, py, px + 1, py + ph, 0xFF555566);
        g.fill(px + pw - 1, py, px + pw, py + ph, 0xFF555566);

        g.drawCenteredString(font, title, px + pw / 2, py + 10, 0xFFFFFF);
        g.drawCenteredString(font,
            Component.literal("已持有 " + ownedCount + " / 共 " + guns.size() + " 把"),
            px + pw / 2, py + 26, 0xAAAAAA);

        int cell = cell(), slot = slot();
        int start = page * pageSize();
        int end = Math.min(start + pageSize(), guns.size());
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = start; i < end; i++) {
                int gi = i - start;
            int cx = gridX() + (gi % COLS) * cell;
            int cy = gridY() + (gi / COLS) * cell;
            ItemStack gun = guns.get(i);

            g.fill(cx, cy, cx + slot, cy + slot, 0x80303040);
            if (i < ownedCount) {
                // 已持有：绿色描边（不是绿底）
                int c = 0xFF3CBE5F;
                g.fill(cx, cy, cx + slot, cy + 1, c);
                g.fill(cx, cy + slot - 1, cx + slot, cy + slot, c);
                g.fill(cx, cy, cx + 1, cy + slot, c);
                g.fill(cx + slot - 1, cy, cx + slot, cy + slot, c);
            }
            g.renderItem(gun, cx + slot / 2 - 8, cy + slot / 2 - 8); // 16px 图标居中
            if (mouseX >= cx && mouseX < cx + slot && mouseY >= cy && mouseY < cy + slot) {
                hovered = gun;
            }
        }

        // 底部：文字式翻页按钮 + 页码
        int by = py + ph - 22;
        boolean canPrev = page > 0;
        boolean canNext = page < pageCount() - 1;
        String prevText = "< 上一页";
        String nextText = "下一页 >";
        int prevX = px + 24;
        int nextX = px + pw - 24 - font.width(nextText);
        g.drawString(font, prevText, prevX, by, canPrev ? 0xFFFFFF : 0x777777);
        g.drawString(font, nextText, nextX, by, canNext ? 0xFFFFFF : 0x777777);
        g.drawCenteredString(font, Component.literal((page + 1) + " / " + pageCount()),
            px + pw / 2, by, 0xCCCCCC);

        // 右上角 ✕ 关闭按钮
        int cx2 = px + pw - 16, cy2 = py + 8;
        boolean xHovered = mouseX >= cx2 - 3 && mouseX <= cx2 + 9 && mouseY >= cy2 - 3 && mouseY <= cy2 + 9;
        g.drawString(font, "✕", cx2, cy2, xHovered ? 0xFFFF5555 : 0xFFAAAAAA);

        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ✕ 关闭按钮命中区（面板右上角 12px 热区）
        int cx2 = panelX() + panelW() - 16, cy2 = panelY() + 8;
        if (button == 0 && mouseX >= cx2 - 3 && mouseX <= cx2 + 9 && mouseY >= cy2 - 3 && mouseY <= cy2 + 9) {
            onClose();
            return true;
        }
        int by = panelY() + panelH() - 22;
        if (button == 0 && mouseY >= by - 2 && mouseY <= by + 10) {
            int prevX = panelX() + 24;
            int nextX = panelX() + panelW() - 24 - font.width("下一页 >");
            if (mouseX >= prevX && mouseX <= prevX + font.width("< 上一页") && page > 0) {
                page--;
                return true;
            }
            if (mouseX >= nextX && mouseX <= nextX + font.width("下一页 >") && page < pageCount() - 1) {
                page++;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0 && page > 0) { page--; return true; }
        if (delta < 0 && page < pageCount() - 1) { page++; return true; }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263 && page > 0) { page--; return true; }          // GLFW LEFT
        if (keyCode == 262 && page < pageCount() - 1) { page++; return true; } // GLFW RIGHT
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previous);
    }
}
