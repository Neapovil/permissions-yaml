package com.github.neapovil.permissionsyaml.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.github.neapovil.permissionsyaml.PermissionsYaml;

public final class PlayersResource
{
    public final List<Player> players = new ArrayList<>();

    public Player findOrCreate(UUID uuid)
    {
        return this.find(uuid).orElseGet(() -> {
            final Player player = new Player(uuid);
            this.players.add(player);
            return player;
        });
    }

    public Optional<Player> find(UUID uuid)
    {
        return this.players.stream().filter(i -> i.uuid.equals(uuid)).findFirst();
    }

    public static class Player
    {
        public UUID uuid;
        @Nullable
        public String group;

        public Player(UUID uuid)
        {
            this.uuid = uuid;
        }

        @Nullable
        public String prefix()
        {
            final PermissionsYaml plugin = PermissionsYaml.instance();
            return plugin.fileConfiguration.getString(this.group + ".prefix");
        }
    }
}
