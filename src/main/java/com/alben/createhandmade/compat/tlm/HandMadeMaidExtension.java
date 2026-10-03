package com.alben.createhandmade.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;

/**
 * 车万女仆扩展入口。
 * 标注 @LittleMaidExtension 后，TLM 会在初始化阶段自动发现并加载。
 * 如果 TLM 未安装，此类不会被加载，因此不会因缺少 TLM 类而崩溃。
 */
@LittleMaidExtension
public class HandMadeMaidExtension implements ILittleMaid {

    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new TaskHandMade());
    }
}