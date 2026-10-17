package xuanmo.arcartx_suite_tooltips.client;

import java.util.Objects;

/**
 * 由渲染线程持有的 tip 生命周期。脚本只能读取当前帧已确认的 tip；
 * 按键可读取刚显示过的 tip，但必须仍在同一界面、同一鼠标位置且没有过期。
 * 此类不依赖 Minecraft，便于验证连续帧、同帧切换和关闭界面的行为。
 */
final class TooltipRenderState<T> {
    private final T empty;
    private final long maxAgeNanos;
    private boolean frameOpen;
    private Object screen;
    private T rendered;
    private long renderedAt;
    private double mouseX;
    private double mouseY;

    TooltipRenderState(T empty, long maxAgeNanos) {
        this.empty = Objects.requireNonNull(empty);
        if (maxAgeNanos <= 0) throw new IllegalArgumentException("maxAgeNanos must be positive");
        this.maxAgeNanos = maxAgeNanos;
        rendered = empty;
    }

    void beginFrame(Object currentScreen) {
        clear();
        screen = currentScreen;
        frameOpen = true;
    }

    void bind(Object currentScreen, T value, long now, double x, double y) {
        if (!frameOpen || screen != currentScreen) {
            clear();
            return;
        }
        rendered = Objects.requireNonNull(value);
        renderedAt = now;
        mouseX = x;
        mouseY = y;
    }

    T current(Object currentScreen, long now, double x, double y) {
        return frameOpen ? recent(currentScreen, now, x, y) : empty;
    }

    T recent(Object currentScreen, long now, double x, double y) {
        if (rendered == empty) return empty;
        long age = now - renderedAt;
        if (screen != currentScreen || age < 0 || age > maxAgeNanos
                || Double.compare(mouseX, x) != 0 || Double.compare(mouseY, y) != 0) {
            clear();
            return empty;
        }
        return rendered;
    }

    void endFrame() {
        frameOpen = false;
    }

    void clear() {
        frameOpen = false;
        screen = null;
        rendered = empty;
        renderedAt = 0;
        mouseX = 0;
        mouseY = 0;
    }
}
