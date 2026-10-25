package com.alben.createhandmade;

import com.alben.createhandmade.bellows.BellowsMediaEvents;
import com.alben.createhandmade.item.BellowsItem;
import com.alben.createhandmade.item.HandSawItem;
import com.alben.createhandmade.item.InfusionGunItem;
import com.alben.createhandmade.item.ModCreativeModeTabs;
import com.alben.createhandmade.item.ModItems;
import com.alben.createhandmade.item.PointerItem;
import com.alben.createhandmade.item.PressHammerItem;
import com.alben.createhandmade.item.StirringStaffItem;
import com.alben.createhandmade.network.ModNetwork;
import com.alben.createhandmade.particle.ModParticleTypes;
import com.alben.createhandmade.recipe.HandMadeRecipeFilters;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组入口（Fabric）。
 *
 * <p>Create Fly 的注册全部走 {@code BuiltInRegistries} + {@code Registry.register}，
 * 因此这里按依赖顺序显式调用各注册类，并把 NeoForge 的事件换成 Fabric 对应回调。</p>
 */
public class CreateHandMade implements ModInitializer {

    public static final String MODID = "create_hand_made";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        ServerConfig.load();

        ModDataComponents.register();
        ModParticleTypes.register();
        ModItems.register();
        ModCreativeModeTabs.register();

        // L3 独占层：自定义 RecipeType / RecipeSerializer
        HandMadeRecipeTypes.register();
        HandMadeRecipeFilters.register();

        // L2：风箱介质表
        BellowsMediaEvents.register();

        ModNetwork.register();
        registerEvents();

        LOGGER.info("Create: Hand Made (Fabric port) initialised");
    }

    /**
     * 事件接线。
     *
     * <p>原 NeoForge 的 {@code PlayerInteractEvent.RightClickBlock} 只能"取消方块交互"，
     * 物品自身的 {@code use()} 仍会被原版继续调用。Fabric 的 {@code UseBlockCallback}
     * 一旦返回非 PASS 就会把整个 {@code useItemOn} 短路，因此命中时必须在回调里
     * 手动调用物品的 {@code use()} / {@code startUsingItem()}。</p>
     */
    private static void registerEvents() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            InteractionResult result = PressHammerItem.interceptRightClick(player, level, hand, hit);
            if (result.consumesAction()) {
                return result;
            }

            result = PointerItem.interceptRightClick(player, level, hand, hit);
            if (result.consumesAction()) {
                return result;
            }

            result = StirringStaffItem.interceptRightClick(player, level, hand, hit);
            if (result.consumesAction()) {
                return result;
            }

            result = BellowsItem.interceptRightClick(player, level, hand, hit);
            if (result.consumesAction()) {
                return result;
            }

            return InfusionGunItem.interceptRightClick(player, level, hand, hit);
        });

        // 手锯：左键记录待砍目标
        AttackBlockCallback.EVENT.register(HandSawItem::onAttackBlock);

        // 手锯：方块被挖碎后触发整树砍伐（服务端）
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) ->
                HandSawItem.onBlockBreak(level, player, pos, state));

        // 手锯：砍树循环音效
        ServerTickEvents.END_SERVER_TICK.register(HandSawItem::onServerTick);

        // 灌注枪 / 手锯：玩家下线清理静态状态
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            InfusionGunItem.clearPlayer(handler.getPlayer().getUUID());
            HandSawItem.clearPlayer(handler.getPlayer().getUUID());
        });
    }
}
