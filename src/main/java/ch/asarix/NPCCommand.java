package ch.asarix;

import ch.asarix.creation.NPC;
import ch.asarix.creation.NPCBuilder;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NPCCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player p = (Player) sender;
        NPC techno = new NPCBuilder()
                .setMotionLess(false)
                .setLocation(p)
                .setPitch(30)
                .setPlayerSkin("Technoblade")
                .setTitles("Technoblade", "" + ChatColor.YELLOW + ChatColor.BOLD + "CLICK")
                .setConsumer(player -> player.sendMessage("[Technoblade] Technoblade (never) dies !"))
                .build();
        techno.spawn();

        NPC self = new NPCBuilder()
                .setMotionLess(false)
                .setLocation(p)
                .addX(2)
                .setSkin(p)
                .setName("")
                .build();
        self.spawn();
        return false;
    }
}
