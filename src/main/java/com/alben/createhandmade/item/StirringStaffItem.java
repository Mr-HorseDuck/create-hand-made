package com.alben.createhandmade.item;

import com.alben.createhandmade.network.ModNetwork;
import com.alben.createhandmade.item.renderer.StirringStaffRenderer;
import com.alben.createhandmade.network.PressParticlesPacket;
import com.alben.createhandmade.network.PressParticlesPacket.ParticleStyle;
import com.alben.createhandmade.network.StirringStatePacket;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.type.HandMadeToolRecipe;
import com.alben.createhandmade.recipe.ToolType;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.foundation.item.CustomUseEffectsItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.simibubi.create.foundation.recipe.RecipeFinder;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.data.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Mod.EventBusSubscriber
public class StirringStaffItem extends Item implements CustomUseEffectsItem {

    private static final Object MIXING_RECIPE_KEY = new Object();
    private static final int STIR_DURATION = 60;
    private static final int WINDUP_TICKS = 8;

    /** 玩家 → 搅拌目标 Basin 位置。use 时缓存，onUseTick / finishUsingItem 复用。 */
    private static final Map<UUID, BlockPos> STIRRING_TARGET = new HashMap<>();

    public StirringStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if (enchantment == Enchantments.SHARPNESS) return true;
        return super.canApplyAtEnchantingTable(stack, enchantment);
    }

    // ================= 攻击扣耐久 =================

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof StirringStaffItem) {
            mainHand.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
            return;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof StirringStaffItem) {
            offHand.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.OFFHAND));
        }
    }

    // ================= 右键拦截 =================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!(player.getItemInHand(event.getHand()).getItem() instanceof StirringStaffItem)) return;

        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof BasinBlockEntity basin)) return;
        if (basin.isEmpty()) return;

        event.setUseBlock(Event.Result.DENY);
    }

    // ================= 右键 =================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND && !player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }

        ItemStack stack = player.getItemInHand(hand);

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.fail(stack);
        }

        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) {
            return InteractionResultHolder.fail(stack);
        }
        if (basin.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }

        if (findMatchingRecipe(level, basin) == null) {
            return InteractionResultHolder.fail(stack);
        }

        STIRRING_TARGET.put(player.getUUID(), pos.immutable());

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    // ★ 按住不松手，连续搅拌
    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    // ================= 每 tick：连续搅拌 + 音效 + 粒子 =================

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) return;

        int usedTicks = getUseDuration(stack) - remainingTicks;
        if (usedTicks < WINDUP_TICKS) return;

        BlockPos pos = STIRRING_TARGET.get(player.getUUID());
        if (pos == null) {
            if (!level.isClientSide) player.stopUsingItem();
            return;
        }

        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) {
            if (!level.isClientSide) player.stopUsingItem();
            return;
        }

        if (basin.isEmpty()) {
            if (!level.isClientSide) player.stopUsingItem();
            return;
        }

        // ---- 服务端：按周期应用配方 ----
        if (!level.isClientSide
                && usedTicks >= WINDUP_TICKS + STIR_DURATION
                && (usedTicks - WINDUP_TICKS) % STIR_DURATION == 0) {

            Recipe<?> recipe = findMatchingRecipe(level, basin);
            if (recipe == null) {
                player.stopUsingItem();
                return;
            }

            if (!BasinRecipe.apply(basin, recipe)) {
                player.stopUsingItem();
                return;
            }

            basin.notifyChangeOfContents();

            EquipmentSlot slot = entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(1, entity, e -> e.broadcastBreakEvent(slot));
        }

        // ---- 服务端：过程音效 + 粒子 ----
        if (!level.isClientSide && usedTicks % 4 == 0) {
            if (level instanceof ServerLevel serverLevel) {
                StirringStatePacket packet = new StirringStatePacket(pos);
                double rangeSq = 64.0 * 64.0;
                for (ServerPlayer p : serverLevel.players()) {
                    if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) {
                        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
                    }
                }

                AllSoundEvents.MIXING.playOnServer(level, pos, 3.0f, 2f);
                broadcastStirParticles(level, pos, basin);
            }
        }
    }

    private static void broadcastStirParticles(Level level, BlockPos pos, BasinBlockEntity basin) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < basin.inputInventory.getSlots(); i++) {
            ItemStack s = basin.inputInventory.getItem(i);
            if (!s.isEmpty()) stacks.add(s.copy());
        }
        if (stacks.isEmpty()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        PressParticlesPacket packet = new PressParticlesPacket(
                pos, stacks, ParticleStyle.STIR);
        double rangeSq = 64.0 * 64.0;

        for (ServerPlayer p : serverLevel.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
            }
        }
    }

    // ================= 松手：清理缓存 =================

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;
        STIRRING_TARGET.remove(player.getUUID());
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        STIRRING_TARGET.remove(player.getUUID());
    }

    // ================= 玩家下线清理 =================

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STIRRING_TARGET.remove(event.getEntity().getUUID());
    }

    // ================= 三层配方查询 =================

    @Nullable
    public static Recipe<?> findMatchingRecipe(Level level, BasinBlockEntity basin) {
        if (basin.isEmpty()) return null;

        for (Recipe<?> r : level.getRecipeManager()
                .getAllRecipesFor(HandMadeRecipeTypes.TOOL_RECIPE.get())) {
            if (!(r instanceof HandMadeToolRecipe htr)) continue;
            if (htr.getTool() != ToolType.STIRRING_STAFF) continue;
            if (BasinRecipe.match(basin, htr)) {
                return htr;
            }
        }

        try {
            for (Recipe<?> recipe : RecipeFinder.get(MIXING_RECIPE_KEY, level,
                    StirringStaffItem::matchStaticFilters)) {
                if (!BasinRecipe.match(basin, recipe)) continue;

                List<Recipe<?>> filtered = HandMadeRecipePool.applyFilter(
                        ToolType.STIRRING_STAFF, level, List.of(recipe));
                if (filtered.isEmpty()) continue;

                return recipe;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static boolean matchStaticFilters(Recipe<?> r) {
        boolean shapelessCrafting = r instanceof CraftingRecipe
                && !(r instanceof ShapedRecipe)
                && AllConfigs.server().recipes.allowShapelessInMixer.get()
                && r.getIngredients().size() > 1
                && !MechanicalPressBlockEntity.canCompress(r)
                && !AllRecipeTypes.shouldIgnoreInAutomation(r);
        boolean mixingRecipe = r.getType() == AllRecipeTypes.MIXING.getType();
        return shapelessCrafting || mixingRecipe;
    }

    // ================= 动画 / 音效屏蔽 =================

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.EMPTY;
    }

    @Override
    public TriState shouldTriggerUseEffects(ItemStack stack, LivingEntity entity) {
        return TriState.TRUE;
    }

    @Override
    public boolean triggerUseEffects(ItemStack stack, LivingEntity entity, int count, RandomSource random) {
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new StirringStaffRenderer()));
    }
}