package xuanmo.arcartx_suite_tooltips.client;

import java.util.ArrayList;
import java.util.List;

/** 单次采集的完整快照；采集本身不改变正在显示的 tip。 */
record TooltipData(List<String> taczLines, List<String> apothLines, boolean apothContent) {
    static final TooltipData EMPTY = new TooltipData(List.of(), List.of(), false);

    TooltipData {
        taczLines = List.copyOf(taczLines);
        apothLines = List.copyOf(apothLines);
    }

    boolean hasTacz() {
        return !taczLines.isEmpty();
    }

    boolean hasApoth() {
        return apothContent || !apothLines.isEmpty();
    }

    boolean hasExtra() {
        return hasTacz() || hasApoth();
    }

    List<String> extraLines() {
        if (taczLines.isEmpty()) return apothLines;
        if (apothLines.isEmpty()) return taczLines;
        List<String> merged = new ArrayList<>(taczLines.size() + apothLines.size());
        merged.addAll(taczLines);
        merged.addAll(apothLines);
        return List.copyOf(merged);
    }
}
