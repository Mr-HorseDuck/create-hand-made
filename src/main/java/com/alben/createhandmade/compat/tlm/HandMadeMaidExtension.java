package com.alben.createhandmade.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;

@LittleMaidExtension
public class HandMadeMaidExtension implements ILittleMaid {

    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new TaskHandMade());
    }
}