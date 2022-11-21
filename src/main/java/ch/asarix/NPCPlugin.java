package ch.asarix;

import ch.asarix.creation.NPC;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class NPCPlugin extends JavaPlugin {
    public static List<NPC> NPCS = new ArrayList<>();
    private static NPCPlugin instance;

    public static NPCPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        Bukkit.getPluginManager().registerEvents(new NPCListener(), this);
        try {
            Util.init();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Plugin startup logic
        getCommand("npc").setExecutor(new NPCCommand());
    }

    @Override
    public void onDisable() {
        for (NPC NPC : NPCS) {
            NPC.remove();
        }
        // Plugin shutdown logic
    }
}
