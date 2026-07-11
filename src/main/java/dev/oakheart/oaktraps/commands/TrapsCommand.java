package dev.oakheart.oaktraps.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.oakheart.command.CommandRegistrar;
import dev.oakheart.message.MessageManager;
import dev.oakheart.oaktraps.OakTraps;
import dev.oakheart.oaktraps.managers.TrapManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public final class TrapsCommand {

    private final OakTraps plugin;
    private final TrapManager traps;
    private final MessageManager messages;

    public TrapsCommand(OakTraps plugin, TrapManager traps, MessageManager messages) {
        this.plugin = plugin;
        this.traps = traps;
        this.messages = messages;
    }

    public void register() {
        CommandRegistrar.register(plugin, buildCommand(), "Mob trap cages", List.of("traps"));
    }

    private LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("oaktraps")
                .requires(src -> src.getSender().hasPermission("oaktraps.use"))
                .executes(ctx -> {
                    messages.sendCommand(ctx.getSource().getSender(), "help");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission("oaktraps.reload"))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            try {
                                plugin.reloadPluginConfig();
                                messages.sendCommand(sender, "reload-success");
                            } catch (Exception e) {
                                messages.sendCommand(sender, "reload-failed");
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("give")
                        .requires(src -> src.getSender().hasPermission("oaktraps.give"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> give(ctx, 1))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))))
                .build();
    }

    private int give(CommandContext<CommandSourceStack> ctx, int amount) {
        CommandSender sender = ctx.getSource().getSender();
        try {
            List<Player> players = ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
                    .resolve(ctx.getSource());
            if (players.isEmpty()) {
                messages.sendCommand(sender, "player-not-found");
                return Command.SINGLE_SUCCESS;
            }
            Player target = players.getFirst();

            ItemStack item = traps.createTrapItem(false, null);
            item.setAmount(amount);
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
            for (ItemStack drop : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }

            messages.sendCommand(sender, "give-success",
                    Placeholder.unparsed("player", target.getName()),
                    Placeholder.unparsed("amount", String.valueOf(amount)));
        } catch (Exception e) {
            messages.sendCommand(sender, "player-not-found");
        }
        return Command.SINGLE_SUCCESS;
    }
}
