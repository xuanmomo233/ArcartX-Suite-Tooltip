package xuanmo.arcartx_suite_tooltips.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientBlockIndex;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.item.GunTooltipPart;
import com.tacz.guns.item.ModernKineticGunItem;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import com.tacz.guns.util.AttachmentDataUtils;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * TACZ 枪属性 tooltip 文本采集器。
 * <p>
 * 精确复刻 {@code ClientGunTooltip.getText()} 的计算逻辑，将枪属性转为
 * {@code Component} 文本行，供 {@code ItemTooltipEvent} 注入。
 * <p>
 * 直接调用 TACZ public API（{@code IGun}、{@code TimelessAPI}、
 * {@code AttachmentDataUtils}），不使用反射。
 */
public final class TaczTooltipCollector {

    private static final DecimalFormat FORMAT = new DecimalFormat("#.##%");
    private static final DecimalFormat FORMAT_P_D1 = new DecimalFormat("#.#%");
    private static final DecimalFormat DAMAGE_FORMAT = new DecimalFormat("#.##");
    private static final DecimalFormat CURRENT_AMMO_FORMAT_PERCENT = new DecimalFormat("0%");

    private TaczTooltipCollector() {}

    /**
     * 采集 TACZ 枪属性或配件的 tooltip 文本行。
     *
     * @param stack 物品栈
     * @return 文本行列表，不是 TACZ 物品则返回空列表
     */
    public static List<Component> collectTooltipLines(ItemStack stack) {
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun == null) {
            // 非枪械：依次尝试 TACZ 配件 / 弹药箱 / 方块物品
            List<Component> lines = collectAttachmentLines(stack);
            if (!lines.isEmpty()) return lines;
            lines = collectAmmoBoxLines(stack);
            if (!lines.isEmpty()) return lines;
            return collectBlockLines(stack);
        }

        ResourceLocation gunId = iGun.getGunId(stack);
        Optional<CommonGunIndex> optIndex = TimelessAPI.getCommonGunIndex(gunId);
        if (optIndex.isEmpty()) {
            return List.of();
        }

        CommonGunIndex gunIndex = optIndex.get();
        GunData gunData = gunIndex.getGunData();
        BulletData bulletData = gunIndex.getBulletData();
        int hideFlags = GunTooltipPart.getHideFlags(stack);

        List<Component> lines = new ArrayList<>();

        // ── 弹药信息 ──
        if (shouldShow(GunTooltipPart.AMMO_INFO, hideFlags)) {
            ResourceLocation ammoId = gunData.getAmmoId();
            if (ammoId != null) {
                ItemStack ammo = AmmoItemBuilder.create().setId(ammoId).build();
                lines.add(ammo.getHoverName().copy().withStyle(ChatFormatting.GOLD));

                int barrelBullet = (iGun.hasBulletInBarrel(stack)
                    && gunData.getBolt() != Bolt.OPEN_BOLT) ? 1 : 0;
                int maxAmmo = AttachmentDataUtils.getAmmoCountWithAttachment(stack, gunData) + barrelBullet;
                int currentAmmo = iGun.getCurrentAmmoCount(stack) + barrelBullet;

                MutableComponent ammoCountText;
                if (iGun.useInventoryAmmo(stack)) {
                    ammoCountText = Component.translatable("tooltip.tacz.gun.inventory_mode")
                        .withStyle(ChatFormatting.YELLOW);
                } else if (!iGun.useDummyAmmo(stack)) {
                    ammoCountText = Component.literal("%d/%d".formatted(currentAmmo, maxAmmo))
                        .withStyle(ChatFormatting.GRAY);
                } else {
                    int dummyAmmo = iGun.getDummyAmmoAmount(stack);
                    ammoCountText = Component.literal("%d/%d (%d)".formatted(currentAmmo, maxAmmo, dummyAmmo))
                        .withStyle(ChatFormatting.GRAY);
                }
                lines.add(ammoCountText);
            }
        }

        // ── 基础信息 ──
        if (shouldShow(GunTooltipPart.BASE_INFO, hideFlags)) {
            // 等级
            int level = iGun.getLevel(stack);
            int maxLevel = iGun.getMaxLevel();
            if (level >= 0) {
                MutableComponent levelLine;
                if (level >= maxLevel) {
                    levelLine = Component.translatable("tooltip.tacz.gun.level")
                        .append(Component.literal("%d (MAX)".formatted(level))
                            .withStyle(ChatFormatting.DARK_PURPLE));
                } else {
                    int expCurrent = iGun.getExpCurrentLevel(stack);
                    int expToNext = iGun.getExpToNextLevel(stack);
                    String levelText = "%d (%.1f%%)".formatted(level,
                        expCurrent / (expToNext + expCurrent + 0.0) * 100f);
                    levelLine = Component.translatable("tooltip.tacz.gun.level")
                        .append(Component.literal(levelText).withStyle(ChatFormatting.YELLOW));
                }
                lines.add(levelLine);
            }

            // 枪械类型
            String typeKey = "tacz.type." + gunIndex.getType() + ".name";
            lines.add(Component.translatable("tooltip.tacz.gun.type")
                .append(Component.translatable(typeKey).withStyle(ChatFormatting.AQUA)));

            // 伤害
            double damage = AttachmentDataUtils.getDamageWithAttachment(stack, gunData);
            int bulletAmount = hasSlugInstalled(iGun, stack) ? 1 : bulletData.getBulletAmount();
            MutableComponent damageValue;
            if (bulletAmount > 1) {
                damageValue = Component.literal(
                    DAMAGE_FORMAT.format(damage / bulletAmount) + "x" + bulletAmount)
                    .withStyle(ChatFormatting.AQUA);
            } else {
                damageValue = Component.literal(DAMAGE_FORMAT.format(damage))
                    .withStyle(ChatFormatting.AQUA);
            }
            // 爆炸伤害
            if (bulletData.getExplosionData() != null
                && (AttachmentDataUtils.isExplodeEnabled(stack, gunData)
                    || bulletData.getExplosionData().isExplode())) {
                double explosionDmg = bulletData.getExplosionData().getDamage()
                    * SyncConfig.DAMAGE_BASE_MULTIPLIER.get();
                damageValue.append(" + ").append(DAMAGE_FORMAT.format(explosionDmg))
                    .append(Component.translatable("tooltip.tacz.gun.explosion"));
            }
            lines.add(Component.translatable("tooltip.tacz.gun.damage").append(damageValue));
        }

        // ── 额外伤害信息 ──
        if (shouldShow(GunTooltipPart.EXTRA_DAMAGE_INFO, hideFlags)) {
            ExtraDamage extraDamage = bulletData.getExtraDamage();
            double armorIgnore;
            double headshotMultiplier;
            if (extraDamage != null) {
                armorIgnore = AttachmentDataUtils.getArmorIgnoreWithAttachment(stack, gunData);
                headshotMultiplier = AttachmentDataUtils.getHeadshotMultiplier(stack, gunData);
            } else {
                armorIgnore = 0;
                headshotMultiplier = 1;
            }
            armorIgnore = Mth.clamp(armorIgnore, 0.0F, 1.0F);

            lines.add(Component.translatable("tooltip.tacz.gun.armor_ignore",
                FORMAT.format(armorIgnore)).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("tooltip.tacz.gun.head_shot_multiplier",
                FORMAT.format(headshotMultiplier)).withStyle(ChatFormatting.GOLD));

            // 移速惩罚
            double weightFactor = SyncConfig.WEIGHT_SPEED_MULTIPLIER.get();
            double weight = AttachmentDataUtils.getWightWithAttachment(stack, gunData);
            lines.add(Component.translatable("tooltip.tacz.gun.movement_speed",
                FORMAT_P_D1.format(-weightFactor * weight)).withStyle(ChatFormatting.RED));
        }

        return lines;
    }

    /**
     * 采集 TACZ 配件（瞄具/枪口等）的 tooltip 文本行。
     * <p>
     * 精确复刻 {@code ClientAttachmentItemTooltip} 的 {@code addText} 逻辑：
     * 配件属性行在 TACZ 里通过 {@code TooltipComponent} 自绘（非文本行），
     * ArcartX 只读 {@code ClientTextTooltip} 会整条丢失，这里转回文本行供
     * {@code axstooltip.getTaczLore()} 使用。
     */
    private static List<Component> collectAttachmentLines(ItemStack stack) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        if (attachment == null) return List.of();

        ResourceLocation attachmentId = attachment.getAttachmentId(stack);
        Optional<ClientAttachmentIndex> optIndex = TimelessAPI.getClientAttachmentIndex(attachmentId);
        if (optIndex.isEmpty()) return List.of();

        ClientAttachmentIndex index = optIndex.get();
        AttachmentData data = index.getData();
        AttachmentType type = attachment.getType(stack);

        List<Component> lines = new ArrayList<>();

        // 配件描述（tooltipKey → 多行灰色文本）
        String tooltipKey = index.getTooltipKey();
        if (tooltipKey != null) {
            for (String s : I18n.get(tooltipKey).split("\n")) {
                lines.add(Component.literal(s).withStyle(ChatFormatting.GRAY));
            }
        }

        // 激光颜色
        Integer laserColor = null;
        if (attachment.hasCustomLaserColor(stack)) {
            laserColor = attachment.getLaserColor(stack);
        } else if (index.getLaserConfig() != null) {
            laserColor = index.getLaserConfig().getDefaultColor();
        }
        if (laserColor != null) {
            lines.add(Component.translatable("tooltip.tacz.attachment.laser.color",
                String.format("#%06X", laserColor & 0xFFFFFF))
                .setStyle(Style.EMPTY.withColor(laserColor)));
        }

        // 瞄具倍率
        if (type == AttachmentType.SCOPE) {
            float[] zoom = index.getZoom();
            if (zoom != null) {
                String[] parts = new String[zoom.length];
                for (int i = 0; i < zoom.length; i++) {
                    parts[i] = "x" + zoom[i];
                }
                lines.add(Component.translatable("tooltip.tacz.attachment.zoom",
                    String.join(",", parts)).withStyle(ChatFormatting.GOLD));
            }
        }

        // 扩容弹匣等级
        if (type == AttachmentType.EXTENDED_MAG) {
            int level = data.getExtendedMagLevel();
            if (level == 1) {
                lines.add(Component.translatable("tooltip.tacz.attachment.extended_mag_level_1")
                    .withStyle(ChatFormatting.GRAY));
            } else if (level == 2) {
                lines.add(Component.translatable("tooltip.tacz.attachment.extended_mag_level_2")
                    .withStyle(ChatFormatting.BLUE));
            } else if (level == 3) {
                lines.add(Component.translatable("tooltip.tacz.attachment.extended_mag_level_3")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }

        // 属性修饰行（重量/开镜时间/后坐力等，即原版配件 tooltip 的主要增益行）
        data.getModifier().forEach((name, prop) -> lines.addAll(prop.getComponents()));

        // 提示行（"按下Shift查看支持枪械"——AX tip 中按 Shift 会切换到图标网格）
        lines.add(Component.translatable("tooltip.tacz.attachment.yaw.shift"));
        // 枪包名（"支持枪械"标签行省略——Shift 网格态由图标区上方的名称行承担）
        PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(attachmentId);
        if (packInfo != null) {
            lines.add(Component.literal(packInfo.getName())
                .withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        }

        return lines;
    }

    /**
     * 采集 TACZ 弹药箱的 tooltip 文本行（弹药名 + 数量）。
     * 复刻 {@code ClientAmmoBoxTooltip} 构造逻辑。
     */
    private static List<Component> collectAmmoBoxLines(ItemStack stack) {
        if (!(stack.getItem() instanceof IAmmoBox ammoBox)) return List.of();
        ResourceLocation ammoId = ammoBox.getAmmoId(stack);
        if (ammoId == null) return List.of();

        ItemStack ammo = AmmoItemBuilder.create().setId(ammoId).build();
        List<Component> lines = new ArrayList<>(2);
        lines.add(ammo.getHoverName());
        if (ammoBox.isCreative(stack)) {
            lines.add(Component.literal("∞"));
        } else {
            lines.add(Component.translatable("tooltip.tacz.ammo_box.count",
                ammoBox.getAmmoCount(stack)));
        }
        return lines;
    }

    /**
     * 采集 TACZ 方块物品（枪械工作台等）的 tooltip 文本行。
     * 复刻 {@code ClientBlockItemTooltip.addText} 逻辑。
     */
    private static List<Component> collectBlockLines(ItemStack stack) {
        if (!(stack.getItem() instanceof IBlock iBlock)) return List.of();
        ResourceLocation blockId = iBlock.getBlockId(stack);
        Optional<ClientBlockIndex> optIndex = TimelessAPI.getClientBlockIndex(blockId);
        if (optIndex.isEmpty()) return List.of();

        ClientBlockIndex index = optIndex.get();
        List<Component> lines = new ArrayList<>();

        String tooltipKey = index.getTooltipKey();
        if (tooltipKey != null) {
            for (String s : I18n.get(tooltipKey).split("\n")) {
                lines.add(Component.literal(s).withStyle(ChatFormatting.GRAY));
            }
        }

        PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(blockId);
        if (packInfo != null) {
            lines.add(Component.literal(packInfo.getName())
                .withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        }
        return lines;
    }

    /**
     * 采集 TACZ 配件的"支持枪械"物品栈列表。
     * 复刻 {@code ClientAttachmentItemTooltip.getAllAllowGuns}：遍历所有枪械索引，
     * 用 {@code IGun.allowAttachment} 过滤，结果按配件 id 缓存（原版也用缓存）。
     */
    private static final Map<ResourceLocation, List<ItemStack>> GUNS_CACHE = new ConcurrentHashMap<>();

    public static List<ItemStack> collectSupportedGuns(ItemStack stack) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        if (attachment == null) return List.of();
        ResourceLocation attachmentId = attachment.getAttachmentId(stack);
        return GUNS_CACHE.computeIfAbsent(attachmentId, id -> {
            List<ItemStack> guns = new ArrayList<>();
            for (Map.Entry<ResourceLocation, ?> entry : TimelessAPI.getAllCommonGunIndex()) {
                ItemStack gun = GunItemBuilder.create().setId(entry.getKey()).build();
                if (!(gun.getItem() instanceof IGun iGun)) continue;
                if (iGun.allowAttachment(gun, stack)) {
                    guns.add(gun);
                }
            }
            return guns;
        });
    }

    /**
     * 采集"支持枪械"显示名列表（与 {@link #collectSupportedGuns} 同序）。
     * <p>
     * 服务器可能用 {@code display.Name} NBT 改了枪名——优先取玩家背包中实际
     * 持有的同 id 枪的悬停名；未持有的枪回落到索引/语言名。
     */
    public static List<String> collectSupportedGunNames(ItemStack stack) {
        List<ItemStack> guns = collectSupportedGuns(stack);
        if (guns.isEmpty()) return List.of();

        java.util.Map<ResourceLocation, String> owned = new java.util.HashMap<>();
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            for (ItemStack s : mc.player.getInventory().items) putOwnedName(owned, s);
            for (ItemStack s : mc.player.getInventory().armor) putOwnedName(owned, s);
            for (ItemStack s : mc.player.getInventory().offhand) putOwnedName(owned, s);
        }

        List<String> names = new ArrayList<>(guns.size());
        for (ItemStack gun : guns) {
            IGun iGun = (IGun) gun.getItem();
            ResourceLocation gunId = iGun.getGunId(gun);
            String name = owned.get(gunId);
            names.add(name != null ? name : gun.getHoverName().getString());
        }
        return names;
    }

    private static void putOwnedName(java.util.Map<ResourceLocation, String> map, ItemStack s) {
        if (!(s.getItem() instanceof IGun iGun)) return;
        map.putIfAbsent(iGun.getGunId(s), s.getHoverName().getString());
    }

    /**
     * 收集玩家背包（主栏+护甲+副手）中已持有的 TACZ 枪械，gunId → 真实物品栈。
     * <p>
     * 用真实背包栈而非 {@code GunItemBuilder} 生成栈，可以保留服务器
     * {@code display.Name} 自定义名与完整 NBT，tooltip/名称显示更准确。
     */
    public static Map<ResourceLocation, ItemStack> collectOwnedGunStacks() {
        Map<ResourceLocation, ItemStack> guns = new java.util.LinkedHashMap<>();
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            for (ItemStack s : mc.player.getInventory().items) putOwnedGun(guns, s);
            for (ItemStack s : mc.player.getInventory().armor) putOwnedGun(guns, s);
            for (ItemStack s : mc.player.getInventory().offhand) putOwnedGun(guns, s);
        }
        return guns;
    }

    private static void putOwnedGun(Map<ResourceLocation, ItemStack> map, ItemStack s) {
        IGun iGun = IGun.getIGunOrNull(s);
        if (iGun == null) return;
        ResourceLocation id = iGun.getGunId(s);
        if (id != null) map.putIfAbsent(id, s);
    }

    /**
     * 持有优先排序的支持枪械列表。
     * <p>
     * 持有的枪用背包真实栈（保留服务器自定义名），未持有的用生成栈兜底。
     * 返回列表前 {@code ownedCount} 项为已持有。
     */
    public record SortedGuns(java.util.List<ItemStack> stacks, int ownedCount) {}

    public static SortedGuns collectSupportedGunsSorted(ItemStack stack) {
        List<ItemStack> guns = collectSupportedGuns(stack);
        if (guns.isEmpty()) return new SortedGuns(List.of(), 0);

        Map<ResourceLocation, ItemStack> invGuns = collectOwnedGunStacks();
        List<ItemStack> owned = new ArrayList<>();
        List<ItemStack> others = new ArrayList<>();
        for (ItemStack gun : guns) {
            IGun iGun = IGun.getIGunOrNull(gun);
            ResourceLocation id = iGun != null ? iGun.getGunId(gun) : null;
            ItemStack real = id != null ? invGuns.get(id) : null;
            (real != null ? owned : others).add(real != null ? real : gun);
        }
        List<ItemStack> sorted = new ArrayList<>(owned);
        sorted.addAll(others);
        return new SortedGuns(sorted, owned.size());
    }

    private static boolean shouldShow(GunTooltipPart part, int hideFlags) {
        return (hideFlags & part.getMask()) == 0;
    }

    /**
     * 采集 TACZ 枪属性的结构化数据（供后续网络包发送用）。
     *
     * @param stack 物品栈
     * @return 结构化数据的 JSON 字符串，如果不是 TACZ 枪则返回 null
     */
    public static String collectStructuredData(ItemStack stack) {
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun == null) return null;

        ResourceLocation gunId = iGun.getGunId(stack);
        Optional<CommonGunIndex> optIndex = TimelessAPI.getCommonGunIndex(gunId);
        if (optIndex.isEmpty()) return null;

        CommonGunIndex gunIndex = optIndex.get();
        GunData gunData = gunIndex.getGunData();
        BulletData bulletData = gunIndex.getBulletData();

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"gunId\":\"").append(gunId).append("\",");

        double damage = AttachmentDataUtils.getDamageWithAttachment(stack, gunData);
        sb.append("\"damage\":").append(damage).append(",");

        int bulletAmount = bulletData.getBulletAmount();
        sb.append("\"bulletAmount\":").append(bulletAmount).append(",");

        ExtraDamage extraDamage = bulletData.getExtraDamage();
        double armorIgnore = extraDamage != null
            ? AttachmentDataUtils.getArmorIgnoreWithAttachment(stack, gunData) : 0;
        armorIgnore = Mth.clamp(armorIgnore, 0.0F, 1.0F);
        sb.append("\"armorIgnore\":").append(armorIgnore).append(",");

        double headshot = extraDamage != null
            ? AttachmentDataUtils.getHeadshotMultiplier(stack, gunData) : 1;
        sb.append("\"headshotMultiplier\":").append(headshot).append(",");

        double weight = AttachmentDataUtils.getWightWithAttachment(stack, gunData);
        sb.append("\"weight\":").append(weight).append(",");

        double weightFactor = SyncConfig.WEIGHT_SPEED_MULTIPLIER.get();
        sb.append("\"movementSpeedPenalty\":").append(-weightFactor * weight).append(",");

        int barrelBullet = (iGun.hasBulletInBarrel(stack)
            && gunData.getBolt() != Bolt.OPEN_BOLT) ? 1 : 0;
        int maxAmmo = AttachmentDataUtils.getAmmoCountWithAttachment(stack, gunData) + barrelBullet;
        sb.append("\"maxAmmo\":").append(maxAmmo).append(",");

        int currentAmmo = iGun.getCurrentAmmoCount(stack) + barrelBullet;
        sb.append("\"currentAmmo\":").append(currentAmmo).append(",");

        int level = iGun.getLevel(stack);
        sb.append("\"level\":").append(level).append(",");

        sb.append("\"type\":\"").append(gunIndex.getType()).append("\"");

        sb.append("}");
        return sb.toString();
    }

    /** 独头弹配件标签：TACZ 1.1.7+ 的 DefaultPropertyModification.SLUGS 值 */
    private static final ResourceLocation SLUGS_TAG = new ResourceLocation("tacz", "intrinsic/slug");

    /**
     * 1.1.7+ 的 {@code AllowAttachmentTagMatcher.matchTag}（1.1.5 无此方法——
     * 那时独头弹改装特性尚未实现）。用 MethodHandle 一次性解析：存在则真检测，
     * 不存在（1.1.5）恒为 false，语义同样正确。
     */
    private static final MethodHandle SLUG_TAG_MATCHER = resolveSlugTagMatcher();

    private static MethodHandle resolveSlugTagMatcher() {
        try {
            return MethodHandles.publicLookup().findStatic(AllowAttachmentTagMatcher.class,
                "matchTag", MethodType.methodType(boolean.class,
                    ResourceLocation.class, ResourceLocation.class));
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 扩容弹匣是否装了独头弹配件（把多弹头缩成单发）。
     * TACZ 1.1.5 无此特性恒 false；1.1.7+ 走 matchTag 检测。
     */
    private static boolean hasSlugInstalled(IGun iGun, ItemStack stack) {
        if (SLUG_TAG_MATCHER == null) return false;
        try {
            return (boolean) SLUG_TAG_MATCHER.invokeExact(SLUGS_TAG,
                iGun.getAttachmentId(stack, AttachmentType.EXTENDED_MAG));
        } catch (Throwable t) {
            return false;
        }
    }
}
