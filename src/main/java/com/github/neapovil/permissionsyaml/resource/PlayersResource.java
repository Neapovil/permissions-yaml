package com.github.neapovil.permissionsyaml.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayersResource
{
    public final List<Player> players = new ArrayList<>();

    public Player findOrCreate(UUID uuid)
    {
        return this.players.stream().filter(i -> i.uuid.equals(uuid)).findFirst().orElseGet(() -> {
            final Player player = new Player(uuid);
            this.players.add(player);
            return player;
        });
    }

    public static class Player
    {
        public UUID uuid;
        public List<String> groups = new ArrayList<>();

        public Player(UUID uuid)
        {
            this.uuid = uuid;
        }
    }
}
