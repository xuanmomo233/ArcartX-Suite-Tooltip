package xuanmo.arcartx_suite_tooltips.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class TooltipDataTest {
    @Test
    public void snapshotOwnsItsLinesEvenIfCollectorListsChange() {
        List<String> tacz = new ArrayList<>(List.of("gun A"));
        List<String> apoth = new ArrayList<>(List.of("gem A"));
        TooltipData data = new TooltipData(tacz, apoth, true);
        tacz.clear();
        apoth.set(0, "gem B");
        assertEquals(List.of("gun A", "gem A"), data.extraLines());
    }

    @Test
    public void affixesOrEmptySocketsCountAsApotheosisWithoutGemText() {
        TooltipData data = new TooltipData(List.of(), List.of(), true);
        assertTrue(data.hasApoth());
        assertTrue(data.hasExtra());
        assertFalse(data.hasTacz());
        assertEquals(List.of(), data.extraLines());
    }

    @Test
    public void gemTextStillWorksIfContentDetectionFails() {
        TooltipData data = new TooltipData(List.of(), List.of("gem"), false);
        assertTrue(data.hasApoth());
        assertTrue(data.hasExtra());
    }

    @Test
    public void independentServerSnapshotSurvivesRenderContextInvalidation() {
        TooltipData data = new TooltipData(List.of("gun"), List.of("gem"), true);
        TooltipRenderState<TooltipData> state = new TooltipRenderState<>(TooltipData.EMPTY, 350);
        Object screen = new Object();
        state.beginFrame(screen);
        state.bind(screen, data, 0, 10, 20);
        state.bind(screen, TooltipData.EMPTY, 1, 10, 20);
        state.clear();
        assertEquals(List.of("gun", "gem"), data.extraLines());
        assertFalse(TooltipData.EMPTY.hasExtra());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void mergedLinesCannotBeModifiedByAConsumer() {
        TooltipData data = new TooltipData(List.of("gun"), List.of("gem"), true);
        data.extraLines().clear();
    }
}
