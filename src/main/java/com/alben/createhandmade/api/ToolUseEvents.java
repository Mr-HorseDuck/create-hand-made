package com.alben.createhandmade.api;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.recipe.ToolType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;

/**
 * {@link HandMadeToolUsedEvent} 的唯一发射点。
 *
 * <p>0.7.0 及以前，事件只在 {@link CreateHandMadeApi} 的写操作成功后发——也就是"只有第三方
 * 通过 API 触发的加工"能被监听到，玩家自己手搓、女仆用工具都不会发。0.7.1.1 起把发射点下沉到
 * 各工具真正成功的那一步（{@code PressHammerItem.tryPressBasin} 之类），于是三种来源都会发：</p>
 *
 * <ul>
 *   <li>玩家手搓；</li>
 *   <li>女仆行为（走的是同一批静态方法）；</li>
 *   <li>第三方通过 {@link CreateHandMadeApi} 的写操作。</li>
 * </ul>
 *
 * <p>预演（{@code simulate = true}）永远不发。想彻底静音由配置
 * {@code [api] fire_tool_used_events} 控制（默认 true）。</p>
 *
 * <p><b>只在服务端发射</b>：客户端调用直接 return，接入方不需要自己判 {@code isClientSide}。</p>
 *
 * @since 0.7.1.1
 */
public final class ToolUseEvents {

    private ToolUseEvents() {
    }

    /**
     * 加工成功后发事件。调用方必须是"已经真的改了世界"的那一步，预演不要调。
     *
     * @param tool      走的哪条工具路径
     * @param level     必须是服务端世界
     * @param pos       被加工的方块坐标（手锯 / 研钵这类没有方块坐标的，用使用者所在位置）
     * @param toolStack 手上的工具；没有就传 null（事件里会变成空栈）
     * @param target    被作用的物品/介质；没有就传 null
     */
    public static void post(ToolType tool, Level level, BlockPos pos,
                            @Nullable ItemStack toolStack, @Nullable ItemStack target) {
        if (level == null || level.isClientSide || pos == null) return;
        if (!Config.INSTANCE.apiFireToolUsedEvents.get()) return;

        MinecraftForge.EVENT_BUS.post(new HandMadeToolUsedEvent(
                tool, level, pos,
                toolStack != null ? toolStack : ItemStack.EMPTY,
                target));
    }
}