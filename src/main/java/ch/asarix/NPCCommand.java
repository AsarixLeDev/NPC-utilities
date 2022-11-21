package ch.asarix;

import ch.asarix.creation.NPC;
import ch.asarix.creation.NPCBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;

public class NPCCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        if (args.length < 1) {
            sender.sendMessage("4 possible args : self, techno, remover, fetch");
            return false;
        }
        Player p = (Player) sender;
        switch (args[0].toLowerCase()) {
            case "techno":
                NPC techno = new NPCBuilder()
                        .setMotionLess(false)
                        .setLocation(p)
                        .setPitch(30)
                        .setPlayerSkin("Technoblade")
                        .setTitles("Technoblade", "" + ChatColor.YELLOW + ChatColor.BOLD + "CLICK")
                        .addDialogue("Technoblade (never) dies !")
                        .addDialogue("Sup, {player}. Your name is {player}, right ?")
                        .build();
                techno.spawn();
                break;
            case "self":
                NPC self = new NPCBuilder()
                        .setMotionLess(false)
                        .setLocation(p)
                        .setSkin(p)
                        .setName("")
                        .build();
                self.spawn();
                break;
            case "remover":
                ItemStack remover = new ItemStack(Material.STICK);
                ItemMeta meta = remover.getItemMeta();
                meta.setDisplayName("NPC remover");
                PersistentDataContainer container = meta.getPersistentDataContainer();
                container.set(NPCPlugin.key, PersistentDataType.STRING, "remover");
                remover.setItemMeta(meta);
                if (p.getInventory().firstEmpty() < 0)
                    p.getWorld().dropItem(p.getLocation(), remover);
                else
                    p.getInventory().addItem(remover);
            case "fetch":
                File file = new File(NPCPlugin.getInstance().getDataFolder(), "npcs.yml");
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                for (String id : config.getKeys(false)) {
                    new NPCBuilder(id).build().spawn(p);
                }
        }
        return false;
    }
}
