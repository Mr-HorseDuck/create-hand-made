package com.alben.createhandmade.api;

import com.alben.createhandmade.recipe.ToolType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;

/**
 * 手搓工具成功完成一次加工后触发（{@link Event}，走 Forge 事件总线，**服务端**）。
 *
 * <p>用途：附属模组 / KubeJS 想做"每次加工都记一笔"（统计、进度、奖励、触发连锁）时，
 * 不必再包一层写操作。事件在加工**已经生效之后**发出，属于通知性质，**不可取消**——
 * 想拦截请在调用写操作之前用查询方法（或 {@code simulate} 重载）自己判断。</p>
 *
 * <p>发出点：{@code CreateHandMadeApi} 的写操作成功时。本模组自己的工具逻辑（玩家手搓）目前
 * 不经过 API，所以**不会**发这个事件；它只覆盖"第三方通过 API 触发的加工"。</p>
 */
public class HandMadeToolUsedEvent extends Event {

    private final ToolType tool;
    private final Level level;
    private final BlockPos pos;
    private final ItemStack toolStack;
    @Nullable
    private final ItemStack target;

    public HandMadeToolUsedEvent(ToolType tool, Level level, BlockPos pos,
                                 ItemStack toolStack, @Nullable ItemStack target) {
        this.tool = tool;
        this.level = level;
        this.pos = pos.immutable();
        this.toolStack = toolStack;
        this.target = target;
    }

    /** 哪个手搓工具干的。 */
    public ToolType tool() {
        return tool;
    }

    public Level level() {
        return level;
    }

    /** 加工发生的方块位置（工作盆 / 置物台 / 传送带等）。 */
    public BlockPos pos() {
        return pos;
    }

    /** 发起加工的工具栈（可能为空栈，例如女仆场景拿不到）。 */
    public ItemStack toolStack() {
        return toolStack;
    }

    /** 被加工的目标物品；有些操作（工作盆、纯流体转移）拿不到单一目标，此时为 null。 */
    @Nullable
    public ItemStack target() {
        return target;
    }

    @Override
    public boolean isCancelable() {
        return false;
    }
}
