package dev.unnm3d.zelsync.commands.providers;

import com.jonahseguin.drink.argument.CommandArg;
import com.jonahseguin.drink.exception.CommandExitMessage;
import com.jonahseguin.drink.parametric.DrinkProvider;
import dev.unnm3d.zelsync.configs.Messages;
import dev.unnm3d.zelsync.core.managers.PlayerListManager;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.List;

@RequiredArgsConstructor
public class TargetProvider extends DrinkProvider<PlayerListManager.Target> {

    private final PlayerListManager playerListManager;

    @Override
    public boolean doesConsumeArgument() {
        return true;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Nullable
    @Override
    public PlayerListManager.Target provide(@NotNull CommandArg arg, @NotNull List<? extends Annotation> annotations) throws CommandExitMessage {
        String targetName = arg.get();
        if (targetName == null || targetName.isEmpty()) {
            return null;
        }
        if (targetName.equals("*") || targetName.equalsIgnoreCase("-ALL-")) {
            return new PlayerListManager.Target(targetName);
        }
        if (playerListManager.getPlayerList(arg.getSender())
          .stream()
          .anyMatch(s -> s.equalsIgnoreCase(targetName))) {
            return new PlayerListManager.Target(targetName);
        }
        throw new CommandExitMessage(MiniMessage.miniMessage().deserialize(Messages.instance().playerNotFound.replace("%player%", targetName)));
    }

    @Override
    public String argumentDescription() {
        return "playerName";
    }


    @Override
    public List<String> getSuggestions(CommandSender sender, @NotNull String prefix) {
        return playerListManager.getPlayerList(sender)
          .stream()
          .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()) && !sender.getName().equalsIgnoreCase(s))
          .toList();
    }

}