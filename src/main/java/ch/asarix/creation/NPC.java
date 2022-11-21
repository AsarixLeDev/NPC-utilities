package ch.asarix.creation;

import ch.asarix.NPCPlugin;
import ch.asarix.Util;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import lombok.Getter;
import net.minecraft.server.v1_15_R1.*;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class NPC extends EntityPlayer {

    private final List<String> dialogues;
    private final Skin skin;
    private final boolean motionLess;
    private final boolean hideTab;
    private final boolean hideName;
    public BukkitTask task = null;
    private ArmorStand titleStand = null;
    private ArmorStand subTitleStand = null;
    private final Consumer<Player> onInteract;
    @Getter private final String npcId;

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
        if (builder.getSubTitle() != null) {
            loc = new Location(loc.getWorld(), loc.getX(), loc.getY() - 0.05, loc.getZ(), loc.getPitch(), loc.getYaw());
            titleStand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            titleStand.setCustomName(builder.getTitle());
            titleStand.setGravity(false);
            titleStand.setVisible(false);
            titleStand.setCustomNameVisible(true);
            loc = new Location(loc.getWorld(), loc.getX(), loc.getY() - 0.25, loc.getZ(), loc.getPitch(), loc.getYaw());
            subTitleStand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            subTitleStand.setCustomName(builder.getSubTitle());
            subTitleStand.setGravity(false);
            subTitleStand.setVisible(false);
            subTitleStand.setCustomNameVisible(true);
        }
        this.hideName = builder.isHideName();

        this.hideTab = builder.isHideTab();
        this.dialogues = builder.getDialogues();
    }

    public void spawn() {
        Bukkit.getOnlinePlayers().forEach(this::spawn);
    }

    public void spawn(Player player) {
        if (this.hideName) this.hideDisplayName();
        Util.sendPacket(player, new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, this));
        Util.sendPacket(player, new PacketPlayOutNamedEntitySpawn(this));
        PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook packet =
                new PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook(this.getId(), (short) 0, (short) 0, (short) 0,
                        getConvertedRotation(lastYaw), getConvertedRotation(lastPitch), false);
        PacketPlayOutEntityHeadRotation packet2 =
                new PacketPlayOutEntityHeadRotation(this, getConvertedRotation(lastYaw));
        Util.sendPacket(player, packet);
        Util.sendPacket(player, packet2);


        if (this.hideTab)
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

    private void behave() {
        Location location = this.getLocation();
        List<Player> nearby = new ArrayList<>(location.getNearbyPlayers(10));
        if (nearby.isEmpty()) return;
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
        Location t = nearbiest.getLocation();
        Vector vector = location.toVector().subtract(t.toVector()).multiply(-1);
        location.setDirection(vector);
        this.setYawPitch(location.getYaw(), location.getPitch());
        updateNPCPacket(location.getYaw(), location.getPitch());
    }

    public void remove() {
        if (this.task != null)
            this.task.cancel();
        if (this.titleStand != null)
            this.titleStand.remove();
        if (this.subTitleStand != null)
            this.subTitleStand.remove();
        NPCPlugin.NPCS.remove(this);
        Bukkit.getOnlinePlayers().forEach(
                p -> {
                    PlayerConnection connection = Util.getEntityPlayer(p).playerConnection;
                    connection.sendPacket(new PacketPlayOutEntityDestroy(this.getId()));
                }
        );
    }

    private void updateNPCPacket(float yaw, float pitch) {
        PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook packet =
                new PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook(this.getId(), (short) 0, (short) 0, (short) 0,
                        getConvertedRotation(yaw), getConvertedRotation(pitch), false);
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
                String bw = response.substring(i1+1, i2);
                if (bw.equalsIgnoreCase("player"))
                    response = response.substring(0, i1) + player.getName() + response.substring(i2+1);
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
        String title = this.titleStand != null ? this.titleStand.getCustomName() : this.getName();
        String subTitle = this.subTitleStand != null ? this.subTitleStand.getCustomName() : null;
        String skinSignature = this.skin != null ? this.skin.getSignature() : null;
        String skinTexture = this.skin != null ? this.skin.getValue() : null;
        Location location = this.getLocation();
        this.saveField(config, this.npcId + ".title", title);
        this.saveField(config, this.npcId + ".subTitle", subTitle);
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
        if (titleStand != null)
            titleStand.remove();
        if (subTitleStand != null)
            subTitleStand.remove();
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
