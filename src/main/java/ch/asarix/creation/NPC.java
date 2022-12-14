package ch.asarix.creation;

import ch.asarix.NPCPlugin;
import ch.asarix.Util;
import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import lombok.Getter;
import net.minecraft.server.v1_15_R1.*;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_15_R1.CraftServer;
import org.bukkit.craftbukkit.v1_15_R1.CraftWorld;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Consumer;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class NPC extends EntityPlayer {

    private final List<String> dialogues;
    private final List<ArmorStand> holograms;
    private final Skin skin;
    private final boolean motionLess;
    private final boolean hideTab;
    private final boolean hideName;
    private final Consumer<Player> onInteract;
    @Getter
    private final String npcId;
    public BukkitTask task = null;

    public NPC(Location loc) {
        this(((CraftServer) Bukkit.getServer()).getServer(), ((CraftWorld) loc.getWorld()).getHandle(), loc);
    }

    public NPC(Location loc, GameProfile profile) {
        this(((CraftServer) Bukkit.getServer()).getServer(), ((CraftWorld) loc.getWorld()).getHandle(), profile,
                new NPCBuilder());
    }

    public NPC(MinecraftServer server, WorldServer world, Location loc) {
        this(server, world, new GameProfile(UUID.randomUUID(), "NPC"), new NPCBuilder().setLocation(loc));
    }

    public NPC(MinecraftServer server, WorldServer world, GameProfile profile, NPCBuilder builder) {
        super(server, world, profile, new PlayerInteractManager(world));
        this.npcId = builder.getId();
        this.skin = builder.getSkin();
        if (this.skin != null) {
            Property texture = new Property("textures", this.skin.getValue(), this.skin.getSignature());
            this.getProfile().getProperties().put("textures", texture);
        }
        NPCPlugin.NPCS.add(this);
        Location loc = builder.getLocation();
        this.setLocation(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        this.onInteract = builder.getOnInteract();
        this.motionLess = builder.isMotionLess();
        if (!this.motionLess) {
            task = Bukkit.getScheduler().runTaskTimer(NPCPlugin.getInstance(), this::behave, 0, 1);
        }
        this.holograms = Lists.newArrayList();
        List<String> holograms = new ArrayList<>(builder.getHolograms());
        Collections.reverse(holograms);
        for (String hologram : holograms) {
            ArmorStand stand = (ArmorStand) world.getWorld().spawnEntity(this.getLocation(), EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setCustomName(hologram);
            stand.setCustomNameVisible(true);
            this.holograms.add(stand);
        }
        teleportStands();
        this.hideName = builder.isHideName();

        this.hideTab = builder.isHideTab();
        this.dialogues = builder.getDialogues();
    }

    public void spawn() {
        Bukkit.getOnlinePlayers().forEach(this::spawn);
    }

    public void spawn(Player player) {
        if (this.hideTab) this.getBukkitEntity().getPlayer().setPlayerListName("");
        if (this.hideName) this.hideDisplayName();
        Util.sendPacket(player, new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, this));
        Util.sendPacket(player, new PacketPlayOutNamedEntitySpawn(this));
        this.updateNPCPacket(this.lastX, this.lastY, this.lastZ, this.lastYaw, this.lastPitch);


        if (this.hideTab) {
            Bukkit.getScheduler().runTaskLater(NPCPlugin.getInstance(),
                    () -> {
                        EntityPlayer entityPlayer = Util.getEntityPlayer(player);
                        DataWatcher watcher = entityPlayer.getDataWatcher();
                        watcher.set(new DataWatcherObject<>(17, DataWatcherRegistry.a), (byte) 0x40);
                        PacketPlayOutEntityMetadata metadata = new PacketPlayOutEntityMetadata(this.getId(), watcher, true);
                        Util.sendPacket(player, metadata);
                        Util.sendPacket(player,
                                new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, this));
                    },
                    5);
        }
    }

    private void teleportStands() {
        int i = 0;
        for (ArmorStand stand : this.holograms) {
            Location location = this.getLocation().clone();
            location.setY(location.getY() - 0.2 + (i++)*0.2);
            stand.teleport(location);
        }
    }

    public void setHologramContent(int index, String content) {
        ArmorStand stand = this.holograms.get(index);
        stand.setCustomName(content);
    }

    public void setHologramsContent(String... content) {
        this.setHologramsContent(List.of(content));
    }

    public void setHologramsContent(List<String> content) {
        Validate.isTrue(content.size() == this.holograms.size());
        for (int i = 0; i < content.size(); i++) {
            ArmorStand stand = this.holograms.get(i);
            stand.setCustomName(content.get(i));
        }
    }

    private void behave() {
        Location location = this.getLocation();
        List<Player> nearby = new ArrayList<>(location.getNearbyPlayers(10));
        if (nearby.isEmpty()) {
            System.out.println("No nearby players");
            return;
        }
        Player nearbiest = nearby.get(0);
        if (nearby.size() > 1) {
            double smallestDist = location.distance(nearbiest.getLocation());
            for (int i = 1; i < nearby.size(); i++) {
                Player p = nearby.get(i);
                double dist = location.distance(p.getLocation());
                if (dist < smallestDist) {
                    smallestDist = dist;
                    nearbiest = p;
                }
            }
        }
        Location goal = nearbiest.getLocation();
        Vector vector = location.toVector().subtract(goal.toVector()).multiply(-1);
        location.setDirection(vector);
//        this.setYawPitch(location.getYaw(), location.getPitch());
        Location newLoc = goal.subtract(location).multiply(0.1);
        double x = newLoc.getX()*128*32;
        double z = newLoc.getZ()*32*128;
        this.setLocation(lastX + newLoc.getX(), lastY, lastZ + newLoc.getZ(), location.getYaw(), location.getPitch());
        updateNPCPacket(x, 0, z, location.getYaw(), location.getPitch());
        teleportStands();
    }

    public void remove() {
        if (this.task != null)
            this.task.cancel();
        this.removeStands();
        NPCPlugin.NPCS.remove(this);
        Bukkit.getOnlinePlayers().forEach(
                p -> {
                    PlayerConnection connection = Util.getEntityPlayer(p).playerConnection;
                    connection.sendPacket(new PacketPlayOutEntityDestroy(this.getId()));
                }
        );
    }

    private void updateNPCPacket(double x, double y, double z, float yaw, float pitch) {
        PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook packet =
                new PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook(
                        this.getId(),
                        (short) x,
                        (short) y,
                        (short) z,
                        getConvertedRotation(yaw),
                        getConvertedRotation(pitch),
                        true
                );
        PacketPlayOutEntityHeadRotation packet2 =
                new PacketPlayOutEntityHeadRotation(this, getConvertedRotation(yaw));
        Util.broadcast(packet);
        Util.broadcast(packet2);
    }

    public void hideDisplayName() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ScoreboardTeam teamScore = new ScoreboardTeam(new Scoreboard(), player.getName());
            teamScore.setNameTagVisibility(ScoreboardTeamBase.EnumNameTagVisibility.NEVER);
            PacketPlayOutScoreboardTeam packet1 = new PacketPlayOutScoreboardTeam(teamScore, 0);
            PacketPlayOutScoreboardTeam packet2 = new PacketPlayOutScoreboardTeam(teamScore, List.of(this.getName()), 3);
            Util.sendPacket(player, packet1);
            Util.sendPacket(player, packet2);
        }
    }

    private byte getConvertedRotation(float a) {
        return (byte) (int) (a * 256.0D / 360.0D);
    }

    /**
     * Called when a player interacts with the npc.
     * <p>
     * Only once per click
     *
     * @param player Player who interacted
     */
    public void onInteract(Player player) {
        if (this.onInteract != null)
            this.onInteract.accept(player);
        else {
            if (this.dialogues.isEmpty()) return;
            int index = new Random().nextInt(this.dialogues.size());
            String response = dialogues.get(index);
            while (response.toLowerCase().contains("{player}")) {
                int i1 = response.indexOf("{");
                int i2 = response.indexOf("}");
                String bw = response.substring(i1 + 1, i2);
                if (bw.equalsIgnoreCase("player"))
                    response = response.substring(0, i1) + player.getName() + response.substring(i2 + 1);
            }
            player.sendMessage("[" + this.getName() + "] " + response);
        }
    }

    public Location getLocation() {
        return new Location(this.getWorld().getWorld(), this.lastX, this.lastY, this.lastZ, this.lastYaw, this.lastPitch);
    }

    public void save() {
        System.out.println("Saving npc " + this.getName() + "...");
        File npcFile = new File(NPCPlugin.getInstance().getDataFolder(), "npcs.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(npcFile);
        String skinSignature = this.skin != null ? this.skin.getSignature() : null;
        String skinTexture = this.skin != null ? this.skin.getValue() : null;
        Location location = this.getLocation();
        this.saveField(config, this.npcId + ".entityName", this.getName());
        this.saveField(config, this.npcId + ".motionLess", this.motionLess);
        this.saveField(config, this.npcId + ".hideName", this.hideName);
        this.saveField(config, this.npcId + ".hideTab", this.hideTab);
        this.saveField(config, this.npcId + ".skin.value", skinTexture);
        this.saveField(config, this.npcId + ".skin.signature", skinSignature);
        this.saveField(config, this.npcId + ".location.x", location.getX());
        this.saveField(config, this.npcId + ".location.y", location.getY());
        this.saveField(config, this.npcId + ".location.z", location.getZ());
        this.saveField(config, this.npcId + ".location.yaw", location.getYaw());
        this.saveField(config, this.npcId + ".location.pitch", location.getPitch());
        this.saveField(config, this.npcId + ".location.worldName", location.getWorld().getName());
        if (this.onInteract != null)
            config.set(this.npcId + ".dialogues", "custom");
        else {
            if (this.dialogues.isEmpty())
                config.createSection(this.npcId + ".dialogues");
            else
                config.set(this.npcId + ".dialogues", this.dialogues);
        }
        try {
            config.save(npcFile);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void removeStands() {
        for (ArmorStand stand : this.holograms)
            stand.remove();
    }

    private void saveField(FileConfiguration config, String path, Object value) {
        if (value == null) {
            if (!config.isConfigurationSection(path))
                config.createSection(path);
            return;
        }
        if (!((value instanceof Boolean) || (value instanceof Number)))
            value = String.valueOf(value);
        config.set(path, value);
    }
}
