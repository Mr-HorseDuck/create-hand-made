package com.alben.createhandmade.guidebook;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.CreateHandMade;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * 帕秋莉手册的"首次进游戏送书"。
 *
 * <p>这个类**不编译依赖 Patchouli**：只通过注册表查物品、用字符串写 NBT，
 * 所以没装 Patchouli 的玩家完全无感（{@link ModList#isLoaded} 直接挡掉）。</p>
 */
@Mod.EventBusSubscriber(modid = CreateHandMade.MODID)
public final class GuideBookGiver {

    /** 手册的资源路径（同时也是 book.json 所在目录名）。 */
    public static final String BOOK_PATH = "handbook";
    /** 手册完整 ID，写进书物品的 NBT。 */
    public static final ResourceLocation BOOK_ID =
            new ResourceLocation(CreateHandMade.MODID, BOOK_PATH);
    /** Patchouli 用来标记"这本书是哪本"的 NBT 键。 */
    private static final String BOOK_NBT_KEY = "patchouli:book";
    /** 玩家数据里的"已经送过"标记。 */
    private static final String GIVEN_TAG = "create_hand_made:guidebook_given";

    private GuideBookGiver() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ModList.get().isLoaded("patchouli")) return;

        // 配置未加载时（极端早期）按"不送"处理，别让登录事件炸掉
        if (!Config.SPEC.isLoaded()) return;
        if (!Config.INSTANCE.giveGuideBookOnFirstJoin.get()) return;

        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(GIVEN_TAG)) return;

        ItemStack book = createBook();
        if (book.isEmpty()) {
            // Patchouli 装了但物品没找到：只记一次日志，不反复刷
            CreateHandMade.LOGGER.warn(
                    "[HandMade] Patchouli 已加载，但找不到手册物品，跳过赠送（book={}）", BOOK_ID);
            return;
        }

        // 先打标记再给：万一给的过程中抛异常，也不会变成每次登录都刷一本
        data.putBoolean(GIVEN_TAG, true);

        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        player.containerMenu.broadcastChanges();
        CreateHandMade.debug("gave guide book {} to {}", BOOK_ID, player.getGameProfile().getName());
    }

    /**
     * 造一本手册物品。
     *
     * <p>★ 对 Patchouli 的物品注册方式做两种兼容，按顺序尝试：</p>
     * <ol>
     *   <li>{@code create_hand_made:handbook}——如果 Patchouli 按书 ID 注册了独立物品；</li>
     *   <li>{@code patchouli:guide_book} + NBT {@code patchouli:book="create_hand_made:handbook"}
     *       ——Patchouli 的通用书物品，靠 NBT 区分是哪本。</li>
     * </ol>
     *
     * <p>两种都在就优先第一种（不需要 NBT，也不会被别的模组覆盖标签）。</p>
     */
    public static ItemStack createBook() {
        Item dedicated = ForgeRegistries.ITEMS.getValue(BOOK_ID);
        if (dedicated != null) {
            return new ItemStack(dedicated);
        }

        Item generic = ForgeRegistries.ITEMS.getValue(new ResourceLocation("patchouli", "guide_book"));
        if (generic != null) {
            ItemStack stack = new ItemStack(generic);
            stack.getOrCreateTag().putString(BOOK_NBT_KEY, BOOK_ID.toString());
            return stack;
        }

        return ItemStack.EMPTY;
    }

    /** 这本书是否已经发给过该玩家（调试用）。 */
    public static boolean hasBeenGiven(ServerPlayer player) {
        return player.getPersistentData().getBoolean(GIVEN_TAG);
    }

    /** 清掉"已赠送"标记，下次登录会再送一本（调试用）。 */
    public static void resetGivenFlag(@Nullable ServerPlayer player) {
        if (player == null) return;
        player.getPersistentData().remove(GIVEN_TAG);
    }
}
