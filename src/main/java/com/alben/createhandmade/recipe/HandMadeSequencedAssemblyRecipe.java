package com.alben.createhandmade.recipe;

import com.alben.createhandmade.recipe.serializer.HandMadeSequencedAssemblyRecipeSerializer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.Create;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeSerializer;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.conditions.ICondition;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * 手搓专属序列组装（{@code create_hand_made:hand_sequenced_assembly}）。
 *
 * <h2>为什么不直接注册一个 type 复用原版配方</h2>
 * <p>Create 的 {@link SequencedAssemblyRecipe} 把 type 写死在 {@code getType()} 里
 * （返回 {@code AllRecipeTypes.SEQUENCED_ASSEMBLY}），它的三个静态查询入口内部也用
 * <b>Create 自己的 type</b> 去 {@code getAllRecipesFor(...)}。所以「只换注册的 RecipeType」
 * 是做不到的：配方管理器里同一个 {@code Recipe} 实例的 {@code getType()} 决定它挂在哪个桶里，
 * 而查询侧也写死了桶名。</p>
 *
 * <p>因此本类<b>继承</b>原版配方以复用其数据结构（{@code ingredient / sequence / loops /
 * transitionalItem / resultPool} 都是 {@code protected} / {@code public}）、
 * {@code addToTooltip} 的进度提示与 {@code toJson}/{@code toNetwork} 的字段布局，
 * 但把四处「私有逻辑」（步进 {@code getStep} / 命中判定 {@code appliesTo} / 前进 {@code advance}
 * + 结果抽取 {@code rollResult} / 下一步 {@code getNextRecipe}）在子类里重新实现 ——
 * 它们原本都是 {@code private}，无法复用。</p>
 *
 * <h2>过渡物品机制</h2>
 * <p><b>直接复用原版的 NBT 格式</b>：1.20.1 的进度存在物品 NBT 的
 * {@code SequencedAssembly:{id, Step, Progress}} 里（1.21 才改成 DataComponent）。
 * 沿用同一个格式带来两个好处：</p>
 * <ul>
 *   <li>Create 注册的 {@code SequencedAssemblyRecipe.addToTooltip} 会照常显示「第 N / M 步」，
 *       我们一行都不用改；</li>
 *   <li>原版机器<b>拿不到</b>它：机器的 {@code getRecipe(...)} 只遍历
 *       {@code create:sequenced_assembly} 桶，并按 NBT 里的 {@code id} 与「该桶里的配方 id」比较，
 *       我们的 id 不在那个桶里，构不成命中。</li>
 * </ul>
 *
 * <h2>步骤约束</h2>
 * <p>步骤必须是「{@code ProcessingRecipe} + {@code IAssemblyRecipe}」，
 * 本模组的 L3 家族类（{@link HandMadeAssemblyRecipe} 的实现）天然满足。
 * serializer 会额外校验步骤的 {@code type} 必须是本模组的 {@code tool_recipe}，
 * 把「写了 Create 的步骤」这种错误在加载期就报出来，而不是运行时卡住。</p>
 *
 * <p>★ 本文件移植自 1.21.1 分支（0.7.0-beta），按 1.20.1 的 API 改写：
 * {@code RecipeHolder} 不存在（直接用配方实例）、进度用 NBT 而非 DataComponent、
 * {@code ProcessingRecipe} 只有一个泛型参数、流体用 Create 的 {@code FluidIngredient}。</p>
 */
public class HandMadeSequencedAssemblyRecipe extends SequencedAssemblyRecipe {

    /** 进度所在的 NBT 键（与 Create 完全一致，见类注释）。 */
    public static final String NBT_ASSEMBLY = "SequencedAssembly";

    /** 本类自己的 serializer（父类那个字段的类型是 Create 的 serializer，不能放我们的）。 */
    private final HandMadeSequencedAssemblyRecipeSerializer handSerializer;

    public HandMadeSequencedAssemblyRecipe(ResourceLocation id,
                                           HandMadeSequencedAssemblyRecipeSerializer handSerializer) {
        // 父类构造要求一个 Create 的 serializer 实例；我们重写 getSerializer()，
        // 这个值只用于满足父类字段，不会被当作真正的 serializer 使用。
        super(id, sequencedAssemblySerializerRef());
        this.handSerializer = handSerializer;
    }

    private static SequencedAssemblyRecipeSerializer sequencedAssemblySerializerRef() {
        return AllRecipeTypes.SEQUENCED_ASSEMBLY.getSerializer();
    }

    /** 本模组自己的配方类型（泛型版，方便 {@code getAllRecipesFor}）。 */
    @SuppressWarnings("unchecked")
    public static RecipeType<HandMadeSequencedAssemblyRecipe> recipeType() {
        return (RecipeType<HandMadeSequencedAssemblyRecipe>) (RecipeType<?>) HandMadeRecipeTypes
                .HAND_SEQUENCED_ASSEMBLY.get();
    }

    @Override
    public RecipeType<?> getType() {
        return recipeType();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return handSerializer;
    }

    // ==================== 解析（由 serializer 调用，必须写在子类里才能写 protected 字段） ====================

    /**
     * 从 JSON 组装配方。
     *
     * <p>字段布局与 Create 的 {@code SequencedAssemblyRecipeSerializer#readFromJson} 完全一致；
     * 唯一的差别是<b>步骤解析带空守卫</b>：Create 直接 {@code getIngredients().set(0, ...)}，
     * 纯流体步骤（没有物品输入）会抛 {@code IndexOutOfBoundsException} 让整个 RecipeManager 加载失败。</p>
     */
    public static HandMadeSequencedAssemblyRecipe fromJson(ResourceLocation id, JsonObject json,
                                                    HandMadeSequencedAssemblyRecipeSerializer serializer) {
        HandMadeSequencedAssemblyRecipe recipe = new HandMadeSequencedAssemblyRecipe(id, serializer);

        recipe.ingredient = Ingredient.fromJson(json.get("ingredient"));
        recipe.transitionalItem =
                ProcessingOutput.deserialize(GsonHelper.getAsJsonObject(json, "transitionalItem"));

        JsonArray steps = GsonHelper.getAsJsonArray(json, "sequence");
        for (int i = 0; i < steps.size(); i++) {
            recipe.getSequence().add(parseStep(id, steps.get(i).getAsJsonObject(),
                    recipe, i));
        }

        for (JsonElement je : GsonHelper.getAsJsonArray(json, "results")) {
            recipe.resultPool.add(ProcessingOutput.deserialize(je));
        }
        if (GsonHelper.isValidNode(json, "loops")) {
            recipe.loops = GsonHelper.getAsInt(json, "loops");
        }
        return recipe;
    }

    /**
     * 解析一步，并完成「步骤继承序列组装上下文」。
     *
     * <p>与 {@code SequencedRecipe#fromJson} 等价，区别只有两点：
     * ① 显式要求 {@code type} 是本模组的 {@code tool_recipe}（否则报错信息会说清楚）；
     * ② {@code ingredients[0]} 的改写带空守卫。</p>
     */
    private static SequencedRecipe<?> parseStep(ResourceLocation parentId, JsonObject stepJson,
                                                HandMadeSequencedAssemblyRecipe parent, int index) {
        ResourceLocation stepId =
                new ResourceLocation(parentId.getNamespace(), parentId.getPath() + "_step_" + index);

        String type = GsonHelper.getAsString(stepJson, "type", "");
        if (!type.equals(HandMadeRecipeTypes.TOOL_RECIPE_ID)) {
            throw new JsonParseException("Step " + index + " of " + parentId
                    + " must be a " + HandMadeRecipeTypes.TOOL_RECIPE_ID
                    + " (a hand tool recipe), got: " + type);
        }

        Recipe<?> parsed = RecipeManager.fromJson(stepId, stepJson, ICondition.IContext.EMPTY);
        if (!(parsed instanceof ProcessingRecipe<?> recipe) || !(parsed instanceof IAssemblyRecipe assembly)) {
            throw new JsonParseException("Step " + index + " of " + parentId
                    + " is not a ProcessingRecipe with assembly support");
        }
        if (!assembly.supportsAssembly()) {
            throw new JsonParseException("Step " + index + " of " + parentId
                    + " does not support sequenced assembly");
        }

        // ★ 空守卫：纯流体步骤没有第 0 个物品槽，set(0, ...) 会抛 IndexOutOfBounds
        if (!recipe.getIngredients().isEmpty()) {
            Ingredient transit = Ingredient.of(parent.getTransitionalItem());
            recipe.getIngredients().set(0, index == 0
                    ? Ingredient.merge(List.of(transit, parent.getIngredient()))
                    : transit);
        }
        return new SequencedRecipe<>(recipe);
    }

    // ==================== 步进逻辑（父类里都是 private，这里重新实现） ====================

    /** 当前进度（0 起）。没有 NBT 即第 0 步。 */
    public int getStepFor(ItemStack input) {
        if (!input.hasTag() || !input.getTag().contains(NBT_ASSEMBLY)) {
            return 0;
        }
        return input.getTag().getCompound(NBT_ASSEMBLY).getInt("Step");
    }

    /** 这个物品是否属于本食谱（已在流程中 → 看 id；否则看起始原料）。 */
    public boolean appliesTo(ResourceLocation id, ItemStack input) {
        if (input.hasTag() && input.getTag().contains(NBT_ASSEMBLY)) {
            CompoundTag assembly = input.getTag().getCompound(NBT_ASSEMBLY);
            return getTransitionalItem().getItem() == input.getItem()
                    && assembly.getString("id").equals(id.toString());
        }
        return getIngredient().test(input);
    }

    /** 下一个要执行的步骤；序列为空时返回 {@code null}（调用方负责判空）。 */
    @Nullable
    public SequencedRecipe<?> getNextStep(ItemStack input) {
        List<SequencedRecipe<?>> sequence = getSequence();
        if (sequence.isEmpty()) {
            return null;
        }
        return sequence.get(getStepFor(input) % sequence.size());
    }

    /**
     * 前进一格：返回「带着新进度的过渡物品」；走完最后一圈则从结果池里抽产物。
     *
     * <p>与 Create 的 {@code advance} 等价（含 {@code (step + 1) / size >= loops} 的收尾判定），
     * NBT 键名也完全一致 —— 这样原版的 tooltip 处理器才能读出进度。</p>
     */
    public ItemStack advance(ItemStack input) {
        int step = getStepFor(input);
        int size = getSequence().size();
        int loops = getLoops();
        if (size <= 0) {
            return input.copy();
        }
        if ((step + 1) / size >= loops) {
            return rollResult();
        }

        ItemStack advanced = getTransitionalItem().copy();
        advanced.setCount(1);
        CompoundTag tag = advanced.getOrCreateTag();
        CompoundTag assembly = new CompoundTag();
        assembly.putString("id", getId().toString());
        assembly.putInt("Step", step + 1);
        assembly.putFloat("Progress", (step + 1f) / (size * loops));
        tag.put(NBT_ASSEMBLY, assembly);
        advanced.setTag(tag);
        return advanced;
    }

    /** 结果池的加权随机（与 Create 的私有 {@code rollResult} 一致）。 */
    private ItemStack rollResult() {
        float totalWeight = 0;
        for (ProcessingOutput entry : resultPool) {
            totalWeight += entry.getChance();
        }
        float number = Create.RANDOM.nextFloat() * totalWeight;
        for (ProcessingOutput entry : resultPool) {
            number -= entry.getChance();
            if (number < 0) {
                return entry.getStack().copy();
            }
        }
        return ItemStack.EMPTY;
    }

    // ==================== 查询入口（供手搓工具使用） ====================

    /**
     * 找出「这个物品当前该做的那一步」，并把「前进后的产物」强制设为第 0 个产物。
     *
     * <p>做法与 Create 的
     * {@code SequencedAssemblyRecipe#getRecipe(Level, ItemStack, RecipeType, Class)} 完全对称，
     * 区别只是遍历 {@link #recipeType()} 这个桶（原版遍历 Create 的桶）。</p>
     *
     * @param recipeClass 期望的步骤配方类（本模组某个 L3 家族类）
     * @return 命中时返回该步骤配方实例（其 {@code rollResults()} 会产出前进后的过渡物品）
     */
    public static <R extends ProcessingRecipe<?>> Optional<R> getStepRecipe(
            Level level, ItemStack item, Class<R> recipeClass) {
        List<HandMadeSequencedAssemblyRecipe> all =
                level.getRecipeManager().getAllRecipesFor(recipeType());

        for (HandMadeSequencedAssemblyRecipe recipe : all) {
            if (!recipe.appliesTo(recipe.getId(), item)) {
                continue;
            }
            SequencedRecipe<?> next = recipe.getNextStep(item);
            if (next == null) {
                continue;
            }
            ProcessingRecipe<?> step = next.getRecipe();
            if (!recipeClass.isInstance(step)) {
                continue;
            }
            step.enforceNextResult(() -> recipe.advance(item));
            return Optional.of(recipeClass.cast(step));
        }
        return Optional.empty();
    }

    /** 该物品是否正处在我们的手搓流水线上（用于 tooltip / 调试）。 */
    public static boolean isHandMadeAssemblyItem(Level level, ItemStack item) {
        if (!item.hasTag() || !item.getTag().contains(NBT_ASSEMBLY)) {
            return false;
        }
        String rawId = item.getTag().getCompound(NBT_ASSEMBLY).getString("id");
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return false;
        }
        return level.getRecipeManager()
                .byKey(id)
                .map(holder -> holder instanceof HandMadeSequencedAssemblyRecipe)
                .orElse(false);
    }

    // ==================== 网络同步（供 serializer 直接搬字段） ====================

    /** 写入序列的公共字段（顺序与 Create 的 {@code writeToBuffer} 一致）。 */
    public void writeFieldsTo(net.minecraft.network.FriendlyByteBuf buffer) {
        getIngredient().toNetwork(buffer);
        buffer.writeVarInt(getSequence().size());
        getSequence().forEach(step -> step.writeToBuffer(buffer));
        buffer.writeVarInt(resultPool.size());
        resultPool.forEach(output -> output.write(buffer));
        transitionalItem.write(buffer);
        buffer.writeInt(getLoops());
    }

    /** 从网络包读回（顺序与 {@link #writeFieldsTo} 一致；同样必须写在子类里才能写 protected 字段）。 */
    public static HandMadeSequencedAssemblyRecipe fromNetwork(ResourceLocation id,
                                                       net.minecraft.network.FriendlyByteBuf buffer,
                                                       HandMadeSequencedAssemblyRecipeSerializer serializer) {
        HandMadeSequencedAssemblyRecipe recipe = new HandMadeSequencedAssemblyRecipe(id, serializer);
        recipe.ingredient = Ingredient.fromNetwork(buffer);

        int size = buffer.readVarInt();
        for (int i = 0; i < size; i++) {
            recipe.getSequence().add(SequencedRecipe.readFromBuffer(buffer));
        }
        size = buffer.readVarInt();
        for (int i = 0; i < size; i++) {
            recipe.resultPool.add(ProcessingOutput.read(buffer));
        }
        recipe.transitionalItem = ProcessingOutput.read(buffer);
        recipe.loops = buffer.readInt();
        return recipe;
    }
}