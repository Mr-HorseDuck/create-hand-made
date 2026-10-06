package com.alben.createhandmade.item;

import com.alben.createhandmade.network.ModNetwork;
import com.alben.createhandmade.network.PressParticlesPacket;
import com.alben.createhandmade.network.PressParticlesPacket.ParticleStyle;
import com.alben.createhandmade.network.StirringStatePacket;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Mod.EventBusSubscriber
public class StirringStaffItem extends Item implements CustomUseEffectsItem {

    private static final Object MIXING_RECIPE_KEY = new Object();
    private static final int STIR_DURATION = 60;
    private static final int WINDUP_TICKS = 8;

    public StirringStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
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

    // ================= 右键拦截：把"工作盆的空手交互"让给搅拌杖 =================

    /**
     * 原版 {@code Minecraft#startUseItem} 在准星命中方块时，只要**当前手**的 useItemOn
     * 返回 SUCCESS/CONSUME（或 FAIL）就直接 return，副手根本不会被尝试。
     * 而 Create 的工作盆在**空手**时 useItemOn 无条件返回 SUCCESS（把盆里的东西掏回背包），
     * 于是"主手空着 + 搅拌杖在副手"永远走不到搅拌杖的 use()，只有潜行（跳过方块交互）才用得出。
     *
     * <p>这里按 {@link PlayerInteractEvent.RightClickBlock} 的既有做法（BellowsItem /
     * InfusionGunItem / PressHammerItem 同款）显式关掉方块交互，让 useItemOn 返回 PASS，
     * 手部循环才能轮到副手的 useItem。判定必须用 {@code event.getHand()}（正在使用的那只手），
     * 不能用 {@code getMainHandItem()}，否则副手场景不生效。</p>
     */
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
        // ★ 副手使用需要潜行，避免和主手冲突（主手的 useItemOn 通常已经拦过一层）
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

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    // ================= 使用时长 =================

    @Override
    public int getUseDuration(ItemStack stack) {
        return STIR_DURATION;
    }

    // ================= 完成 =================

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;
        if (level.isClientSide) return stack;

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) return stack;

        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) return stack;

        Recipe<?> recipe = findMatchingRecipe(level, basin);
        if (recipe != null) {
            if (BasinRecipe.apply(basin, recipe)) {
                basin.notifyChangeOfContents();
                EquipmentSlot slot = entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                        ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                stack.hurtAndBreak(1, entity, e -> e.broadcastBreakEvent(slot));
            }
        }
        return stack;
    }

    // ================= 每 tick：音效 + 粒子 =================

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) return;

        int usedTicks = getUseDuration(stack) - remainingTicks;
        if (usedTicks < WINDUP_TICKS) return;

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) return;
        if (basin.isEmpty()) return;

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

    // ================= 三层配方查询：MIXING + 无序合成 =================

    /**
     * ★ 三层模型：
     *   L3 独占配方（遍历所有 L3 配方，用 BasinRecipe.match 匹配）
     *   L1 原有 Create 配方（MIXING + shapeless crafting）
     *   L2 数据包过滤（对每个匹配的 L1 配方应用过滤，被过滤的跳过）
     *
     *   ⚠️ 自动酿造暂时禁用
     */
    @Nullable
    public static Recipe<?> findMatchingRecipe(Level level, BasinBlockEntity basin) {
        if (basin.isEmpty()) return null;

        // ★ L3：优先查独占配方
        List<Recipe<?>> custom = HandMadeRecipePool.getAllCustomRecipes(ToolType.STIRRING_STAFF);
        for (Recipe<?> r : custom) {
            if (BasinRecipe.match(basin, r)) {
                return r;
            }
        }

        // ★ L1：原有逻辑 + ★ L2 过滤
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
        return UseAnim.EAT;
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