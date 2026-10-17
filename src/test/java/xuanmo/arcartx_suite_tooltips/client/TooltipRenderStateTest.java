package xuanmo.arcartx_suite_tooltips.client;

import static org.junit.Assert.assertSame;

import org.junit.Test;

public class TooltipRenderStateTest {
    private static final Object EMPTY = new Object();
    private static final long MAX_AGE = 350_000_000L;
    private final Object screen = new Object();
    private final Object gun = new Object();
    private final Object armor = new Object();
    private final TooltipRenderState<Object> state = new TooltipRenderState<>(EMPTY, MAX_AGE);

    private void show(Object item) {
        state.bind(screen, item, 0, 10, 20);
    }

    private Object current() {
        return state.current(screen, 1, 10, 20);
    }

    private Object recent() {
        return state.recent(screen, 1, 10, 20);
    }

    @Test
    public void collectionOutsideRenderFrameCannotPublishTooltip() {
        show(gun);
        assertSame(EMPTY, current());
        assertSame(EMPTY, recent());
    }

    @Test
    public void frameStartsEmptyUntilActualItemTooltipIsBound() {
        state.beginFrame(screen);
        assertSame(EMPTY, current());
        show(gun);
        assertSame(gun, current());
    }

    @Test
    public void textTooltipImmediatelyReplacesGunInSameFrame() {
        state.beginFrame(screen);
        show(gun);
        show(EMPTY);
        assertSame(EMPTY, current());
        assertSame(EMPTY, recent());
    }

    @Test
    public void textTooltipDoesNotPreventLaterItemTooltipInSameFrame() {
        state.beginFrame(screen);
        show(EMPTY);
        show(gun);
        assertSame(gun, current());
    }

    @Test
    public void switchingItemsUsesOnlyLatestRenderedItem() {
        state.beginFrame(screen);
        show(gun);
        show(armor);
        assertSame(armor, current());
        assertSame(armor, recent());
    }

    @Test
    public void ordinaryItemReplacesExtraDataWithoutKeepingPreviousFlags() {
        TooltipRenderState<TooltipData> dataState = new TooltipRenderState<>(TooltipData.EMPTY, MAX_AGE);
        dataState.beginFrame(screen);
        dataState.bind(screen, new TooltipData(java.util.List.of("gun"), java.util.List.of(), true), 0, 10, 20);
        dataState.bind(screen, TooltipData.EMPTY, 1, 10, 20);
        assertSame(TooltipData.EMPTY, dataState.current(screen, 2, 10, 20));
    }

    @Test
    public void nextFrameCannotReadLastFramesItemBeforeNewRender() {
        state.beginFrame(screen);
        show(gun);
        state.endFrame();
        state.beginFrame(screen);
        assertSame(EMPTY, current());
        assertSame(EMPTY, recent());
    }

    @Test
    public void frameEndHidesScriptDataButAllowsKeyForDisplayedItem() {
        state.beginFrame(screen);
        show(gun);
        state.endFrame();
        assertSame(EMPTY, current());
        assertSame(gun, recent());
    }

    @Test
    public void frameWithoutTooltipAlsoInvalidatesKeyTarget() {
        state.beginFrame(screen);
        show(gun);
        state.endFrame();
        state.beginFrame(screen);
        state.endFrame();
        assertSame(EMPTY, current());
        assertSame(EMPTY, recent());
    }

    @Test
    public void changingScreenInvalidatesDataAndCannotResurrectOnReturn() {
        state.beginFrame(screen);
        show(gun);
        assertSame(EMPTY, state.current(new Object(), 1, 10, 20));
        assertSame(EMPTY, current());
        assertSame(EMPTY, recent());
    }

    @Test
    public void wrongScreenCannotBindIntoExistingFrame() {
        state.beginFrame(screen);
        show(gun);
        state.bind(new Object(), armor, 1, 10, 20);
        assertSame(EMPTY, current());
        assertSame(EMPTY, recent());
    }

    @Test
    public void movingMouseImmediatelyInvalidatesKeyTarget() {
        state.beginFrame(screen);
        show(gun);
        state.endFrame();
        assertSame(EMPTY, state.recent(screen, 1, 11, 20));
        assertSame(EMPTY, recent());
    }

    @Test
    public void timeoutProtectsBothScriptReadsAndKeysIfFrameEndIsMissing() {
        state.beginFrame(screen);
        show(gun);
        assertSame(gun, state.current(screen, MAX_AGE, 10, 20));
        assertSame(EMPTY, state.current(screen, MAX_AGE + 1, 10, 20));
        assertSame(EMPTY, state.recent(screen, MAX_AGE + 1, 10, 20));
    }

    @Test
    public void clockRollbackCannotKeepAnOldTooltipAlive() {
        state.beginFrame(screen);
        state.bind(screen, gun, 100, 10, 20);
        assertSame(EMPTY, state.current(screen, 99, 10, 20));
    }

    @Test
    public void closeOrLogoutClearsBothReadAndInteractionContexts() {
        state.beginFrame(screen);
        show(gun);
        state.clear();
        assertSame(EMPTY, current());
        assertSame(EMPTY, recent());
        show(armor);
        assertSame(EMPTY, current());
        state.beginFrame(screen);
        show(armor);
        assertSame(armor, current());
    }

    @Test
    public void nullScreenIsAValidFrameOwnerForHudTooltips() {
        state.beginFrame(null);
        state.bind(null, gun, 0, 10, 20);
        assertSame(gun, state.current(null, 1, 10, 20));
    }
}
