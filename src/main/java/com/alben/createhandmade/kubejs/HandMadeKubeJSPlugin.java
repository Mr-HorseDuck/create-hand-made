package com.alben.createhandmade.kubejs;

import com.alben.createhandmade.recipe.HandMadeRecipePool;
import dev.latvian.mods.kubejs.KubeJSPlugin;

public class HandMadeKubeJSPlugin extends KubeJSPlugin {

    public HandMadeKubeJSPlugin() {}

    /** KubeJS 1.20.1 无参方法，直接调 EventGroup.register() */
    @Override
    public void registerEvents() {
        HandMadeEvents.GROUP.register();
    }

    /** 每次服务器脚本重载时，清空旧过滤 + 派发事件让脚本重新声明 */
    @Override
    public void onServerReload() {
        HandMadeRecipePool.clearKubeJSFilters();
        // ★ 删掉 hasListeners() 检查（它引用了 Rhino，会编译报错）
        HandMadeEvents.TOOL_FILTER.post(new HandMadeToolFilterKubeEvent());
    }
}