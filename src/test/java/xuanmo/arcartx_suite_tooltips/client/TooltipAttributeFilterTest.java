package xuanmo.arcartx_suite_tooltips.client;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.Test;

public class TooltipAttributeFilterTest {

    @Test
    public void removesGeneratedAttributesButPreservesNameLoreAndDebugLines() {
        Component name = Component.literal("普通武器");
        Component lore = Component.literal("§9+20% 生命偷取：这是一行自定义说明");
        Component itemId = Component.literal("minecraft:diamond_sword");
        List<Component> tooltip = new ArrayList<>(List.of(name, lore, Component.empty(),
            Component.translatable("item.modifiers.mainhand"),
            Component.translatable("attributeslib.modifier.plus",
                Component.literal("20%"), Component.literal("生命偷取"))
                .append(Component.literal(" [+0.2]")),
            Component.translatable("attribute.modifier.equals.0", "7", Component.literal("攻击伤害")),
            itemId));

        TooltipAttributeFilter.filter(tooltip, false);

        assertEquals(List.of(name, lore, itemId), tooltip);
    }

    @Test
    public void preservesEntireTooltipForTaczOrApotheosisContent() {
        List<Component> original = List.of(Component.literal("神化装备"), Component.empty(),
            Component.translatable("item.modifiers.mainhand"),
            Component.translatable("attributeslib.modifier.plus", "15%", Component.literal("攻击速度")));
        List<Component> tooltip = new ArrayList<>(original);

        TooltipAttributeFilter.filter(tooltip, true);

        assertEquals(original, tooltip);
    }

    @Test
    public void removesWrappedAndShiftExpandedAttributeRowsWithoutHeadings() {
        Component name = Component.literal("普通装备");
        Component lore = Component.literal("其他说明");
        List<Component> tooltip = new ArrayList<>(List.of(name,
            Component.literal(" ").append(Component.translatable("attribute.modifier.equals.0", "7", "攻击伤害")),
            Component.literal(" ┇ ").append(Component.translatable("attributeslib.modifier.take", "10%", "攻击速度")),
            Component.translatable("%s", Component.translatable("attribute.modifier.plus.0", "2", "护甲")),
            lore));

        TooltipAttributeFilter.filter(tooltip, false);

        assertEquals(List.of(name, lore), tooltip);
    }

    @Test
    public void removesMultipleEquipmentSectionsWithoutEatingAdjacentLore() {
        Component name = Component.literal("普通装备");
        Component lore = Component.literal("保留这段介绍");
        Component separator = Component.empty();
        Component footer = Component.literal("介绍尾部");
        List<Component> tooltip = new ArrayList<>(List.of(name, lore, Component.empty(),
            Component.translatable("item.modifiers.both_hands"),
            Component.translatable("attributeslib.modifier.plus", "10%", "攻击速度"),
            Component.empty(), Component.translatable("item.modifiers.chest"),
            Component.translatable("attribute.modifier.plus.0", "2", "护甲"), separator, footer));

        TooltipAttributeFilter.filter(tooltip, false);

        assertEquals(List.of(name, lore, separator, footer), tooltip);
    }

    @Test
    public void preservesAttributeLikeNamesAndLiteralLore() {
        Component name = Component.translatable("attribute.modifier.plus.0", "2", "攻击伤害");
        Component lore = Component.literal("主手：+2.4 攻击伤害和+0.15 攻击速度");
        List<Component> tooltip = new ArrayList<>(List.of(name, lore));

        TooltipAttributeFilter.filter(tooltip, false);

        assertEquals(List.of(name, lore), tooltip);
    }

    @Test
    public void acceptsEmptyTooltip() {
        List<Component> tooltip = new ArrayList<>();

        TooltipAttributeFilter.filter(tooltip, false);

        assertEquals(List.of(), tooltip);
    }
}
