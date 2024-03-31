package com.github.neapovil.change;

import org.bukkit.plugin.java.JavaPlugin;

public final class Change extends JavaPlugin
{
    private static Change instance;

    @Override
    public void onEnable()
    {
        instance = this;
    }

    public static Change instance()
    {
        return instance;
    }
}
