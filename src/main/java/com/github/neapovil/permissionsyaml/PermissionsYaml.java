package com.github.neapovil.permissionsyaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import com.github.neapovil.permissionsyaml.event.PlayerPermissionsChangeEvent;
import com.github.neapovil.permissionsyaml.resource.PlayersResource;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.OfflinePlayerArgument;
import dev.jorel.commandapi.arguments.StringArgument;

public final class PermissionsYaml extends JavaPlugin implements Listener
{
    private static PermissionsYaml instance;
    public Path filePath;
    public FileConfiguration fileConfiguration;
    public PlayersResource playersResource;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    @Override
    public void onEnable()
    {
        instance = this;

        this.filePath = this.getServer().getWorldContainer().toPath().resolve("permissions.yml");

        this.reloadPermissions();

        try
        {
            this.load();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        this.getServer().getPluginManager().registerEvents(this, this);

        new CommandAPICommand("permissionsyaml")
                .withPermission("permissionsyaml.command")
                .withArguments(new LiteralArgument("players").withPermission("permissionsyaml.command.players"))
                .withArguments(new OfflinePlayerArgument("offlinePlayer"))
                .withArguments(new LiteralArgument("group"))
                .withArguments(new LiteralArgument("set"))
                .withArguments(new StringArgument("groupName").replaceSuggestions(ArgumentSuggestions.strings(info -> {
                    return this.groups().toArray(String[]::new);
                })))
                .executes((sender, args) -> {
                    final OfflinePlayer offlineplayer = (OfflinePlayer) args.get("offlinePlayer");
                    final String groupname = (String) args.get("groupName");

                    if (!this.groups().contains(groupname))
                    {
                        throw CommandAPI.failWithString("Group not found");
                    }

                    final PlayersResource.Player permissionsplayer = this.playersResource.findOrCreate(offlineplayer.getUniqueId());

                    if (permissionsplayer.group != null && permissionsplayer.group.equalsIgnoreCase(groupname))
                    {
                        throw CommandAPI.failWithString("Player already has this group");
                    }

                    permissionsplayer.group = groupname;

                    if (offlineplayer.isOnline())
                    {
                        final Player player = offlineplayer.getPlayer();
                        this.permissionAttachment(player).setPermission(groupname, true);
                        player.updateCommands();
                    }

                    offlineplayer.getPlayerProfile().update().thenAcceptAsync(playerprofile -> {
                        try
                        {
                            this.save();
                            sender.sendMessage("Set group %s to %s".formatted(groupname, playerprofile.getName()));
                            this.firePlayerPermissionsChangeEvent(offlineplayer.getPlayer());
                        }
                        catch (IOException e)
                        {
                            final String message = "Unable to set group for player: " + playerprofile.getName();
                            this.getLogger().severe(message);
                            sender.sendRichMessage("<red>" + message);
                        }
                    });
                })
                .register();

        new CommandAPICommand("permissionsyaml")
                .withPermission("permissionsyaml.command")
                .withArguments(new LiteralArgument("players").withPermission("permissionsyaml.command.players"))
                .withArguments(new OfflinePlayerArgument("offlinePlayer"))
                .withArguments(new LiteralArgument("group"))
                .withArguments(new LiteralArgument("unset"))
                .executes((sender, args) -> {
                    final OfflinePlayer offlineplayer = (OfflinePlayer) args.get("offlinePlayer");
                    final PlayersResource.Player permissionsplayer = this.playersResource.findOrCreate(offlineplayer.getUniqueId());

                    if (permissionsplayer.group == null)
                    {
                        throw CommandAPI.failWithString("Player has no group");
                    }

                    final String groupname = permissionsplayer.group;

                    permissionsplayer.group = null;

                    if (offlineplayer.isOnline())
                    {
                        final Player player = offlineplayer.getPlayer();
                        this.permissionAttachment(player).unsetPermission(groupname);
                        player.updateCommands();
                    }

                    offlineplayer.getPlayerProfile().update().thenAcceptAsync(playerprofile -> {
                        try
                        {
                            this.save();
                            sender.sendMessage("%s removed from group %s".formatted(playerprofile.getName(), groupname));
                            this.firePlayerPermissionsChangeEvent(offlineplayer.getPlayer());
                        }
                        catch (IOException e)
                        {
                            final String message = "Unable to unset group for player: " + playerprofile.getName();
                            this.getLogger().severe(message);
                            sender.sendRichMessage("<red>" + message);
                        }
                    });
                })
                .register();

        new CommandAPICommand("permissionsyaml")
                .withPermission("permissionsyaml.command")
                .withArguments(new LiteralArgument("reload").withPermission("permissionsyaml.command.reload"))
                .executes((sender, args) -> {
                    this.reloadPermissions();
                    sender.sendMessage("Permissions reloaded");

                    for (Player i : this.getServer().getOnlinePlayers().toArray(Player[]::new))
                    {
                        i.updateCommands();
                    }
                })
                .register();
    }

    public static PermissionsYaml instance()
    {
        return instance;
    }

    public void reloadPermissions()
    {
        this.getServer().reloadPermissions();
        this.fileConfiguration = YamlConfiguration.loadConfiguration(this.filePath.toFile());
    }

    public void load() throws IOException
    {
        this.saveResource("players.json", false);
        final String string = Files.readString(this.getDataFolder().toPath().resolve("players.json"));
        this.playersResource = this.gson.fromJson(string, PlayersResource.class);
    }

    public void save() throws IOException
    {
        final String string = this.gson.toJson(this.playersResource);
        Files.write(this.getDataFolder().toPath().resolve("players.json"), string.getBytes());
    }

    public Set<String> groups()
    {
        final Set<String> strings = this.fileConfiguration.getKeys(false);
        strings.removeIf(i -> i.equalsIgnoreCase("default"));
        return strings;
    }

    public PermissionAttachment permissionAttachment(Player player)
    {
        return this.attachments.computeIfAbsent(player.getUniqueId(), (k) -> player.addAttachment(this));
    }

    private void firePlayerPermissionsChangeEvent(@Nullable Player player)
    {
        this.getServer().getScheduler().runTask(this, () -> {
            final PlayerPermissionsChangeEvent event = new PlayerPermissionsChangeEvent(player);
            this.getServer().getPluginManager().callEvent(event);
        });
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event)
    {
        final Player player = event.getPlayer();
        final PermissionAttachment permissionattachment = this.permissionAttachment(player);
        final PlayersResource.Player permissionsplayer = this.playersResource.findOrCreate(player.getUniqueId());

        if (permissionsplayer.group != null)
        {
            permissionattachment.setPermission(permissionsplayer.group, true);
            player.updateCommands();
        }
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event)
    {
        this.attachments.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    private void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event)
    {
        if (event.getMessage().startsWith("/op ") || event.getMessage().startsWith("/deop "))
        {
            this.firePlayerPermissionsChangeEvent(event.getPlayer());
        }
    }

    @EventHandler
    private void onServerCommand(ServerCommandEvent event)
    {
        if (event.getCommand().startsWith("op ") || event.getCommand().startsWith("deop "))
        {
            final Player player = Bukkit.getPlayer(event.getCommand().replaceFirst("op ", "").replaceFirst("deop ", ""));
            this.firePlayerPermissionsChangeEvent(player);
        }
    }
}
