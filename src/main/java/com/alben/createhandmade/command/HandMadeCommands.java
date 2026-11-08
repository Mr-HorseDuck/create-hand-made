package com.alben.createhandmade.command;

import com.alben.createhandmade.CreateHandMade;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = CreateHandMade.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HandMadeCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // ★ 查重：/chm 可能被其他模组占用
        if (dispatcher.getRoot().getChild("chm") != null) {
            CreateHandMade.LOGGER.warn(
                    "[HandMade] Command '/chm' already taken by another mod, skipping registration.");
            return;
        }

        dispatcher.register(
                Commands.literal("chm")
                        .then(Commands.literal("reload")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> reload(ctx.getSource())))
        );
    }

    private static int reload(CommandSourceStack source) {
        try {
            // ★ SERVER 类型的配置（主配置 + 女仆配置）不在 .minecraft/config 下，
            //   而是在存档目录的 serverconfig/ 里（Forge 自己的加载路径就是这样）。
            //   以前传 FMLPaths.CONFIGDIR 会找不到文件，反而把内存里的值刷成默认值。
            MinecraftServer server = source.getServer();
            Path serverConfigDir = server.getWorldPath(new LevelResource("serverconfig"));
            ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, serverConfigDir);
            CreateHandMade.LOGGER.info(
                    "[HandMade] Server configs reloaded from {}", serverConfigDir);
        } catch (Throwable t) {
            CreateHandMade.LOGGER.error("[HandMade] Config reload failed", t);
            source.sendFailure(Component.translatable(
                    "command.create_hand_made.reload.failed"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(
                "command.create_hand_made.reload.success"), true);
        // ★ blocked_mods 需重启才生效的提示
        source.sendSuccess(() -> Component.translatable(
                "command.create_hand_made.reload.restart_hint"), false);
        return 1;
    }
}