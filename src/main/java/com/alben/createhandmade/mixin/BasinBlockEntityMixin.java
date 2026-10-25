package com.alben.createhandmade.mixin;

import com.alben.createhandmade.client.ClientStirringState;
import com.zurrtum.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.zurrtum.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让手动搅拌杖在客户端也能让工作盆里的流体"动起来"。
 *
 * <p>Create 的 {@code BasinBlockEntity#lazyTick} 只在动力搅拌器运行时才把
 * {@code areFluidsMoving} 置真。手动搅拌时没有搅拌器，所以这里拦截那次调用，
 * 在「本盆正在被搅拌杖搅拌」时强制为真。</p>
 */
@Mixin(value = BasinBlockEntity.class, remap = false)
public class BasinBlockEntityMixin {

    @Redirect(
            method = "lazyTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/zurrtum/create/content/processing/basin/BasinBlockEntity;setAreFluidsMoving(Z)Z"
            ),
            remap = false
    )
    private boolean createHandMade$overrideSetMoving(BasinBlockEntity basin, boolean original) {
        Level level = basin.getLevel();
        if (level == null || !level.isClientSide()) {
            return basin.setAreFluidsMoving(original);
        }

        BlockPos pos = basin.getBlockPos();

        // 手动搅拌杖正在作用 → 永远优先
        if (ClientStirringState.isBeingStirred(pos)) {
            // 上方有 mixer 且正在跑 → 让原版控制（它会传 true）
            // 上方无 mixer / mixer 停了 → 强制 true
            BlockEntity above = level.getBlockEntity(pos.above(2));
            if (above instanceof MechanicalMixerBlockEntity mixer && mixer.running) {
                return basin.setAreFluidsMoving(original);
            }
            return basin.setAreFluidsMoving(true);
        }

        // 没有手动搅拌 → 完全走原版
        return basin.setAreFluidsMoving(original);
    }
}
