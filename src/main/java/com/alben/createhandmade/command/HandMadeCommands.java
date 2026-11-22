package com.alben.createhandmade.command;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.guidebook.GuideBookGiver;
import com.alben.createhandmade.network.ModNetwork;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import javax.annotation.Nullable;
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
                        .then(Commands.literal("config")
                                .executes(ctx -> openConfig(ctx.getSource())))
                        .then(Commands.literal("book")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> giveBook(ctx.getSource(), ctx.getSource().getPlayer()))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> giveBook(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target")))))
                        .then(Commands.literal("reload")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> reload(ctx.getSource())))
        );
    }

    /**
     * 手动发一本手册（调试 / 补发用）。
     *
     * <p>无视「已赠送」标记，方便验证「动态取书」这条路通不通 ——
     * 日志里会打印书物品是从哪来的（PatchouliAPI / NBT 兜底 / 独立物品）。</p>
     */
    private static int giveBook(CommandSourceStack source, @Nullable ServerPlayer target) {
        if (target == null) {
            source.sendFailure(Component.translatable("command.create_hand_made.config.player_only"));
            return 0;
        }
        if (!ModList.get().isLoaded("patchouli")) {
            source.sendFailure(Component.translatable("command.create_hand_made.book.no_patchouli"));
            return 0;
        }
        if (!GuideBookGiver.tryGive(target, true)) {
            source.sendFailure(Component.translatable("command.create_hand_made.book.failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.create_hand_made.book.success",
                target.getDisplayName()), true);
        return 1;
    }

    /**
     * 打开配置界面。
     *
     * <p>命令在服务端执行、界面在客户端，所以这里只发一个「请打开」的空包；
     * 客户端收到后自己 {@code setScreen}。多人游戏里打开的是只读视图（SERVER 配置由服务端权威）。</p>
     */
    private static int openConfig(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable(
                    "command.create_hand_made.config.player_only"));
            return 0;
        }
        ModNetwork.sendOpenConfigScreen(player);
        return 1;
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