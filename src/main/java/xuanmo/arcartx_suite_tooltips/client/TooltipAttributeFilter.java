package xuanmo.arcartx_suite_tooltips.client;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** 只按组件翻译键清理自动生成的属性，避免误删服务器写入的文字 Lore。 */
final class TooltipAttributeFilter {

    private TooltipAttributeFilter() {}

    static void filter(List<Component> tooltip, boolean hasExtra) {
        if (hasExtra) return;

        // 第一行是物品名称；从后往前删除，保持其余 Lore 和组件顺序。
        for (int i = tooltip.size() - 1; i > 0; i--) {
            Component line = tooltip.get(i);
            boolean heading = containsKey(line, key -> key.startsWith("item.modifiers."));
            if (!heading && !containsKey(line, key -> key.startsWith("attribute.modifier.")
                    || key.startsWith("attributeslib.modifier."))) continue;

            tooltip.remove(i);
            // 装备栏标题之前由原版/AttributesLib 插入的空行一并清理。
            if (heading && i > 1 && tooltip.get(i - 1).getString().isBlank()) {
                tooltip.remove(--i);
            }
        }
    }

    private static boolean containsKey(Component component, Predicate<String> matches) {
        if (component.getContents() instanceof TranslatableContents contents) {
            if (matches.test(contents.getKey())) return true;
            for (Object argument : contents.getArgs()) {
                if (argument instanceof Component child && containsKey(child, matches)) return true;
            }
        }
        // AttributesLib 的基础属性和 Shift 展开行可能包在空白/列表前缀组件内。
        for (Component sibling : component.getSiblings()) {
            if (containsKey(sibling, matches)) return true;
        }
        return false;
    }
}
