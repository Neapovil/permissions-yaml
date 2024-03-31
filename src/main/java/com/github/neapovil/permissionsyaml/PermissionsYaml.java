package com.github.neapovil.permissionsyaml;

import org.bukkit.plugin.java.JavaPlugin;

public final class PermissionsYaml extends JavaPlugin
{
    private static PermissionsYaml instance;

    @Override
    public void onEnable()
    {
        instance = this;
    }

    public static PermissionsYaml instance()
    {
        return instance;
    }
}
