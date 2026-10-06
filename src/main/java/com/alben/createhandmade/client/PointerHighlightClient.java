package com.alben.createhandmade.client;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.item.PointerItem;
import com.simibubi.create.AllSpecialTextures;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = CreateHandMade.MODID, value = Dist.CLIENT)
public class PointerHighlightClient {

    private static final Map<Integer, BlockPos> HIGHLIGHTS = new HashMap<>();

    public static void setHighlight(int color, BlockPos pos) {
        HIGHLIGHTS.put(color, pos.immutable());
    }

    public static void clearAll() {
        HIGHLIGHTS.clear();
    }

    @Nullable
    public static BlockPos getHighlight(int color) {
        return HIGHLIGHTS.get(color);
    }

    public static boolean hasHighlight(int color) {
        return HIGHLIGHTS.containsKey(color);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // ★ 主手不是指杆时清除所有高亮
        if (!(player.getMainHandItem().getItem() instanceof PointerItem)) {
            HIGHLIGHTS.clear();
            return;
        }

        if (HIGHLIGHTS.isEmpty()) return;

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        for (Map.Entry<Integer, BlockPos> entry : HIGHLIGHTS.entrySet()) {
            BlockPos pos = entry.getValue();
            if (!level.isLoaded(pos)) continue;

            String key = "pointer_highlight_" + entry.getKey();
            Outliner.getInstance()
                    .showAABB(key, Shapes.block().bounds().move(pos), 2)
                    .lineWidth(1 / 32f)
                    .colored(0xFF000000 | entry.getKey())
                    .withFaceTexture(AllSpecialTextures.SELECTION);
        }
    }
}