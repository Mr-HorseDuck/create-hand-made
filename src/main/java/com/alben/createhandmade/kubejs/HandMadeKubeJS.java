package com.alben.createhandmade.kubejs;

import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.ToolType;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.resources.ResourceLocation;

/**
 * KubeJS 集成：插件入口 + 事件组 + 脚本侧事件对象，三合一。
 *
 * <p>★ 插件类由 {@code resources/kubejs.plugins.txt} 按全限定名加载，改名要同步改那个文件。</p>
 *
 * <p>脚本用法：
 * <pre>
 * HandMadeEvents.toolFilter(event =&gt; {
 *     event.disable('hand_saw', 'minecraft:oak_planks')
 *     event.disableByMod('mortar', 'some_mod')
 * })
 * </pre>
 * </p>
 */
public class HandMadeKubeJS extends KubeJSPlugin {

    /** 事件组：脚本里写 {@code HandMadeEvents.toolFilter}。 */
    public interface Events {

        EventGroup GROUP = EventGroup.of("HandMadeEvents");

        EventHandler TOOL_FILTER = GROUP.server(
                "toolFilter",
                () -> ToolFilterEvent.class
        );
    }

    /** 每次服务器脚本重载时，清空旧过滤 + 派发事件让脚本重新声明。 */
    @Override
    public void onServerReload() {
        HandMadeRecipePool.clearKubeJSFilters();
        // ★ 删掉 hasListeners() 检查（它引用了 Rhino，会编译报错）
        Events.TOOL_FILTER.post(new ToolFilterEvent());
    }

    /** KubeJS 1.20.1 无参方法，直接调 EventGroup.register() */
    @Override
    public void registerEvents() {
        Events.GROUP.register();
    }

    /** 脚本侧拿到的过滤事件对象。 */
    public static class ToolFilterEvent extends EventJS {

        public ToolFilterEvent() {}

        /** 精确禁用某个工具下的一条配方 */
        public void disable(String toolId, String recipeId) {
            ToolType tool = parseTool(toolId);
            HandMadeRecipePool.addKubeJSFilter(tool, new ResourceLocation(recipeId));
        }

        /** 禁用某个工具下、某个 mod namespace 的全部配方 */
        public void disableByMod(String toolId, String modId) {
            ToolType tool = parseTool(toolId);
            HandMadeRecipePool.addKubeJSFilterByMod(tool, modId);
        }

        private static ToolType parseTool(String toolId) {
            ToolType tool = ToolType.byId(toolId);
            if (tool == null) {
                throw new IllegalArgumentException("Unknown tool id: " + toolId);
            }
            return tool;
        }
    }
}
