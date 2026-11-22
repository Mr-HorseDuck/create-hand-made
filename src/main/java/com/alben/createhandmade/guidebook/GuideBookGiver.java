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
import java.lang.reflect.Method;

/**
 * 帕秋莉手册的「首次进游戏送书」。
 *
 * <p>本类**不编译依赖 Patchouli**（模组的「零编译依赖」约定），拿书的顺序是<b>运行时动态</b>的三级兜底：</p>
 * <ol>
 *   <li><b>Patchouli 官方 API</b>：反射调 {@code vazkii.patchouli.api.PatchouliAPI.get()
 *       .getBookStack(ResourceLocation)} —— 这是「联动模组」最正规的取法，
 *       Patchouli 自己决定书物品长什么样（哪版换了物品、换了组件，我们都不用跟着改）；</li>
 *   <li>{@code patchouli:guide_book} + NBT {@code patchouli:book="create_hand_made:handbook"}
 *       —— 老版本 Patchouli 没有上面那个方法时的兜底；</li>
 *   <li>{@code create_hand_made:handbook} 这个独立物品 —— 万一以后真有模组注册了同名物品。</li>
 * </ol>
 *
 * <p>没装 Patchouli 的玩家完全无感（{@link ModList#isLoaded} 直接挡掉，连注册表都不查）。</p>
 */
@Mod.EventBusSubscriber(modid = CreateHandMade.MODID)
public final class GuideBookGiver {

    /** 手册的资源路径（同时也是 book.json 所在目录名）。 */
    public static final String BOOK_PATH = "handbook";
    /** 手册完整 ID，写进书物品的 NBT / 喂给 Patchouli API。 */
    public static final ResourceLocation BOOK_ID =
            new ResourceLocation(CreateHandMade.MODID, BOOK_PATH);
    /** Patchouli 用来标记"这本书是哪本"的 NBT 键。 */
    private static final String BOOK_NBT_KEY = "patchouli:book";
    /** 玩家数据里的"已经送过"标记。 */
    private static final String GIVEN_TAG = "create_hand_made:guidebook_given";

    /** 只为日志：这轮启动里第一次取书走的是哪条路。 */
    private static boolean loggedSource;
    /** 只为日志：「没装 Patchouli 所以不送」只提醒一次，免得每次登录刷屏。 */
    private static boolean loggedMissingPatchouli;

    private GuideBookGiver() {}

    // ==================== 事件 ====================

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        tryGive(player, false);
    }

    /**
     * 死亡重生 / 换维度会 Clone 一次玩家对象。
     *
     * <p>正常情况「已赠送」标记会跟着玩家数据一起复制过来，这里是兜底：
     * 万一某个整合包把玩家数据洗了，也不会因为丢标记就再也不给。</p>
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            tryGive(player, false);
        }
    }

    // ==================== 赠送 ====================

    /**
     * 送一本（或按标记跳过）。
     *
     * @param force true = 无视「已赠送」标记（调试命令用）
     * @return 真的发出去了一本
     */
    public static boolean tryGive(ServerPlayer player, boolean force) {
        if (!ModList.get().isLoaded("patchouli")) {
            // 静默失败最难查：开发环境（runClient）默认没装 Patchouli，
            // 表现就是「开场没送书」——这里提示一次，日志里能直接看到原因。
            if (!loggedMissingPatchouli) {
                loggedMissingPatchouli = true;
                CreateHandMade.LOGGER.info(
                        "[HandMade] 未检测到 Patchouli，手册相关功能（赠送 / 砸书 / /chm book）都不会生效");
            }
            return false;
        }
        // 配置未加载时（极端早期）按"不送"处理，别让登录事件炸掉
        if (!Config.SPEC.isLoaded()) {
            return false;
        }
        if (!force && !Config.INSTANCE.giveGuideBookOnFirstJoin.get()) {
            return false;
        }

        CompoundTag data = player.getPersistentData();
        if (!force && data.getBoolean(GIVEN_TAG)) {
            return false;
        }

        ItemStack book = createBook();
        if (book.isEmpty()) {
            // Patchouli 装了但造不出书物品：记一次 warn，方便排查"为什么没送"
            CreateHandMade.LOGGER.warn(
                    "[HandMade] Patchouli 已加载，但拿不到手册物品（book={}），跳过赠送", BOOK_ID);
            return false;
        }

        // 先打标记再给：万一给的过程中抛异常，也不会变成每次登录都刷一本
        data.putBoolean(GIVEN_TAG, true);

        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        player.containerMenu.broadcastChanges();
        CreateHandMade.LOGGER.info("[HandMade] 已向 {} 赠送手册《{}》",
                player.getGameProfile().getName(), BOOK_ID);
        return true;
    }

    // ==================== 造书 ====================

    /**
     * 造一本手册物品，<b>按 Patchouli API → 通用书 + NBT → 独立物品</b> 的顺序动态取。
     *
     * <p>顺序是有讲究的：优先问 Patchouli 自己要，「书长什么样」由它决定（这是真正的模组联动）。
     * 它没有那个 API（老版本）时才退回我们硬编码的 NBT 写法。</p>
     */
    public static ItemStack createBook() {
        ItemStack fromApi = bookFromPatchouliApi();
        if (!fromApi.isEmpty()) {
            logSource("PatchouliAPI.getBookStack");
            return fromApi;
        }

        Item dedicated = ForgeRegistries.ITEMS.getValue(BOOK_ID);
        if (dedicated != null) {
            logSource("独立物品 " + BOOK_ID);
            return new ItemStack(dedicated);
        }

        Item generic = ForgeRegistries.ITEMS.getValue(new ResourceLocation("patchouli", "guide_book"));
        if (generic != null) {
            ItemStack stack = new ItemStack(generic);
            stack.getOrCreateTag().putString(BOOK_NBT_KEY, BOOK_ID.toString());
            logSource("patchouli:guide_book + NBT");
            return stack;
        }

        return ItemStack.EMPTY;
    }

    /**
     * 反射问 Patchouli 要书。
     *
     * <p>为什么反射：本模组不引 Patchouli 编译依赖。</p>
     *
     * <p>★ 方法归属要小心（1.20.1-84 实测核对过）：{@code PatchouliAPI.get()} 是<b>静态</b>入口，
     * 但它返回的是接口 {@code PatchouliAPI$IPatchouliAPI}，{@code getBookStack(ResourceLocation)}
     * 声明在<b>那个接口</b>上，不在 {@code PatchouliAPI} 类上 —— 一开始我在
     * {@code apiClass.getMethod(...)} 上找，永远找不到，于是静默走了兜底。</p>
     */
    @Nullable
    private static ItemStack bookFromPatchouliApi() {
        try {
            Class<?> apiClass = Class.forName("vazkii.patchouli.api.PatchouliAPI");
            Object api = apiClass.getMethod("get").invoke(null);
            if (api == null) {
                return null;
            }
            Class<?> apiInterface = Class.forName("vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI");

            // API 还没初始化完时 get() 返回 stub，问它没有意义
            if (Boolean.TRUE.equals(apiInterface.getMethod("isStub").invoke(api))) {
                return null;
            }

            Method getBookStack = apiInterface.getMethod("getBookStack", ResourceLocation.class);
            Object result = getBookStack.invoke(api, BOOK_ID);
            if (result instanceof ItemStack stack && !stack.isEmpty()) {
                return stack;
            }
        } catch (Throwable t) {
            CreateHandMade.LOGGER.debug(
                    "[HandMade] PatchouliAPI.getBookStack 不可用，改用 NBT 兜底", t);
        }
        return null;
    }

    private static void logSource(String source) {
        if (loggedSource) {
            return;
        }
        loggedSource = true;
        CreateHandMade.LOGGER.info("[HandMade] 手册物品取自：{}", source);
    }

    // ==================== 调试 ====================

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
