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

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

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
                        // ★ /chm book 要求权限等级 2（给物品属于作弊类操作）。
                        //   子命令（reset / <targets>）不必各自再写 requires：
                        //   Brigadier 在**解析阶段**就对沿途每个节点调 canUse，
                        //   父节点 book 这个门一旦不通过，整棵子树（reset、targets）都进不来，
                        //   tab 补全和用法提示也一样看不到。
                        //   对比：/chm config 故意**不设**权限 —— 它只是打开自己的配置界面。
                        .then(Commands.literal("book")
                                .requires(src -> src.hasPermission(2))
                                // /chm book           → 给自己
                                .executes(ctx -> giveBook(ctx.getSource(), self(ctx.getSource())))
                                // /chm book reset [玩家们] → 清掉「已赠送」标记，下次登录重新送
                                .then(Commands.literal("reset")
                                        .executes(ctx -> resetBook(ctx.getSource(), self(ctx.getSource())))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> resetBook(ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "targets")))))
                                // /chm book <玩家们>  → 批量补发（老存档统一补书就用 @a）
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> giveBook(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets")))))
                        .then(Commands.literal("reload")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> reload(ctx.getSource())))
        );
    }

    /** 命令执行者自己（控制台没有玩家，返回空集合）。 */
    private static Collection<ServerPlayer> self(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? List.of() : List.of(player);
    }

    /**
     * 手动发手册（补发用）。
     *
     * <p>无视「已赠送」标记，所以：老存档 / 老服务器想统一补书，直接 {@code /chm book @a} 即可。
     * 日志里会打印书物品是从哪来的（PatchouliAPI / NBT 兜底 / 独立物品）。</p>
     */
    private static int giveBook(CommandSourceStack source, Collection<ServerPlayer> targets) {
        if (targets.isEmpty()) {
            source.sendFailure(Component.translatable("command.create_hand_made.config.player_only"));
            return 0;
        }
        if (!ModList.get().isLoaded("patchouli")) {
            source.sendFailure(Component.translatable("command.create_hand_made.book.no_patchouli"));
            return 0;
        }
        int given = 0;
        for (ServerPlayer target : targets) {
            if (GuideBookGiver.tryGive(target, true)) {
                given++;
            }
        }
        if (given == 0) {
            source.sendFailure(Component.translatable("command.create_hand_made.book.failed"));
            return 0;
        }
        int count = given;
        source.sendSuccess(() -> Component.translatable("command.create_hand_made.book.success", count), true);
        return count;
    }

    /**
     * 清掉「已赠送」标记：下次登录会重新送一本。
     *
     * <p>用于「玩家说没收到 / 数据被别的模组洗过」这类情况，也可以先 reset 再 book 一步到位。</p>
     */
    private static int resetBook(CommandSourceStack source, Collection<ServerPlayer> targets) {
        if (targets.isEmpty()) {
            source.sendFailure(Component.translatable("command.create_hand_made.config.player_only"));
            return 0;
        }
        int cleared = 0;
        for (ServerPlayer target : targets) {
            if (GuideBookGiver.hasBeenGiven(target)) {
                GuideBookGiver.resetGivenFlag(target);
                cleared++;
            }
        }
        int count = cleared;
        source.sendSuccess(() -> Component.translatable("command.create_hand_made.book.reset", count), true);
        return count;
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