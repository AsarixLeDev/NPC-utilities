package ch.asarix;

import ch.asarix.creation.NPC;
import ch.asarix.creation.NPCBuilder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class NPCPlugin extends JavaPlugin {

    public static NamespacedKey key;
    public static List<NPC> NPCS = new ArrayList<>();
    private static NPCPlugin instance;

    public static NPCPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        key = new NamespacedKey(this, "itemRegistration");
        Bukkit.getPluginManager().registerEvents(new NPCListener(), this);
        try {
            Util.init();
        } catch (IOException e) {
            e.printStackTrace();
        }
        File file = new File(getDataFolder(), "npcs.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String id : config.getKeys(false)) {
            new NPCBuilder(id).build();
        }
        // Plugin startup logic
        getCommand("npc").setExecutor(new NPCCommand());
    }

    @Override
    public void onDisable() {
        File file = new File(getDataFolder(), "npcs.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String id : config.getKeys(false)) {
            config.set(id, null);
        }
        try {
            config.save(file);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        for (NPC npc : NPCS) {
            npc.save();
            npc.removeStands();
        }
        // Plugin shutdown logic
    }
}
