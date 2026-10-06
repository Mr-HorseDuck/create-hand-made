package com.alben.createhandmade.compat.tlm;

import com.alben.createhandmade.CreateHandMade;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraftforge.fml.ModList;

@LittleMaidExtension
public class HandMadeMaidExtension implements ILittleMaid {

    @Override
    public void addMaidTask(TaskManager manager) {
        if (isBlockedByMod()) {
            CreateHandMade.LOGGER.info(
                    "[HandMade] Maid compatibility disabled: a blocked mod is loaded");
            return;
        }
        manager.add(new TaskHandMade());
    }

    private static boolean isBlockedByMod() {
        try {
            for (String modId : MaidConfig.INSTANCE.blockedMods.get()) {
                if (ModList.get().isLoaded(modId)) {
                    CreateHandMade.LOGGER.info(
                            "[HandMade] Blocked by loaded mod: {}", modId);
                    return true;
                }
            }
        } catch (Exception e) {
            // 配置未加载时静默跳过
        }
        return false;
    }
}