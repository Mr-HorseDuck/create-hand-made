package com.alben.createhandmade.item;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * 研钵 / 碾钵「碗里的东西」。
 *
 * <p>除了原料本身，还带上<b>这次加工的时长</b>（<b>不含起手</b>，tick —— 起手只在一次长按的
 * 首次使用里计入总时长，见 {@code StickyUseSession#beginUse}）。原因：加工时长要按
 * {@code 配方 processing_time × 系数} 算，而配方查找需要 {@code level}、开销也不小，
 * 不能放进 {@code getUseDuration}（它被<b>每帧</b>调用 —— 渲染器每帧都会问
 * {@code player.getTicksUsingItem()}）。所以在 {@code use()} 时算好、存进这个组件；
 * 组件是持久化 + 网络同步的，客户端与服务端天然拿到同一个值（这点是硬要求：两端都用它倒数计）。</p>
 *
 * <p>组件格式变了，所以 {@link #CODEC} 做<b>双向兼容</b>：优先读新格式
 * {@code {stack, duration_ticks}}，失败则回退到 R8 之前的"裸 ItemStack"（此时 {@code durationTicks = 0}，
 * 由配置里的默认加工时长兜底）。写出时恒用新格式。</p>
 */
public record MortarContents(ItemStack stack, int durationTicks) {

    /** 新格式：{@code {"stack": <ItemStack>, "duration_ticks": <int>}}（duration 可缺省 = 0）。 */
    private static final Codec<MortarContents> NEW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("stack").forGetter(MortarContents::stack),
            Codec.INT.optionalFieldOf("duration_ticks", 0).forGetter(MortarContents::durationTicks)
    ).apply(instance, MortarContents::new));

    /**
     * 持久化码：新格式优先，旧格式（裸 ItemStack）回退。
     * <p>{@code Codec.either} 会先试左边；旧数据是一个 ItemStack（字符串或 {@code {id,count,...}}），
     * 缺少必填的 {@code stack} 字段 ⇒ 左边解码失败 ⇒ 自动走右边 ✓。</p>
     */
    public static final Codec<MortarContents> CODEC = Codec.either(NEW_CODEC, ItemStack.CODEC)
            .xmap(either -> either.map(contents -> contents, stack -> new MortarContents(stack, 0)),
                    Either::left);

    public static final StreamCodec<RegistryFriendlyByteBuf, MortarContents> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, MortarContents::stack,
            ByteBufCodecs.VAR_INT, MortarContents::durationTicks,
            MortarContents::new);
}
