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
    private Consumer<Player> onInteract;
    private final String id;

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
        this.id = generateID();
        this.skin = builder.getSkin();
        if (this.skin != null) {
            Property texture = new Property("textures", this.skin.getValue(), this.skin.getSignature());
            this.getProfile().getProperties().put("textures", texture);
        }
        NPCPlugin.NPCS.add(this);
        Location loc = builder.getLocation();
        this.setLocation(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        this.onInteract = builder.getOnInteract();
        if (this.onInteract == null)
            this.onInteract = p -> p.sendMessage("[" + this.getName() + "] Hi, " + p.getName());
        this.motionLess = builder.isMotionLess();
        if (!this.motionLess) {
            task = Bukkit.getScheduler().runTaskTimer(NPCPlugin.getInstance(), this::behave, 0, 1);
        }
        if (builder.getTitle() != null) {
            loc = new Location(loc.getWorld(), loc.getX(), loc.getY() - 0.05, loc.getZ(), loc.getPitch(), loc.getYaw());
            titleStand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            titleStand.setCustomName(builder.getTitle());
            titleStand.setGravity(false);
            titleStand.setVisible(false);
            titleStand.setCustomNameVisible(true);
            loc = new Location(loc.getWorld(), loc.getX(), loc.getY() - 0.25, loc.getZ(), loc.getPitch(), loc.getYaw());
            subTitleStand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            subTitleStand.setCustomName(this.getName());
            subTitleStand.setGravity(false);
            subTitleStand.setVisible(false);
            subTitleStand.setCustomNameVisible(true);
        }
        this.hideName = builder.isHideName();
        if (this.hideName) {
            this.hideDisplayName();
        }
        this.hideTab = builder.isHideTab();
        this.dialogues = builder.getDialogues();
    }

    public void spawn() {
        Bukkit.getOnlinePlayers().forEach(this::spawn);
    }

    public void spawn(Player player) {
        Util.sendPacket(player, new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, this));
        Util.sendPacket(player, new PacketPlayOutNamedEntitySpawn(this));
        PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook packet =
                new PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook(this.getId(), (short) 0, (short) 0, (short) 0,
                        getConvertedRotation(lastYaw), getConvertedRotation(lastPitch), false);
        PacketPlayOutEntityHeadRotation packet2 =
                new PacketPlayOutEntityHeadRotation(this, getConvertedRotation(lastYaw));
        Util.sendPacket(player, packet);
        Util.sendPacket(player, packet2);
        EntityPlayer entityPlayer = Util.getEntityPlayer(player);
        DataWatcher watcher = entityPlayer.getDataWatcher();
        watcher.set(new DataWatcherObject<>(17, DataWatcherRegistry.a), (byte) 0x40);
        PacketPlayOutEntityMetadata metadata = new PacketPlayOutEntityMetadata(this.getId(), watcher, true);
        Util.sendPacket(player, metadata);

        if (this.hideTab)
            Bukkit.getScheduler().runTaskLater(NPCPlugin.getInstance(),
                    () -> Util.broadcast(
                            new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, this)),
                    50);
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
    }

    private void addNPCPacket() {
        Util.broadcast(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, this));
        Util.broadcast(new PacketPlayOutNamedEntitySpawn(this));
        PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook packet =
                new PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook(this.getId(), (short) 0, (short) 0, (short) 0,
                        getConvertedRotation(lastYaw), getConvertedRotation(lastPitch), false);
        PacketPlayOutEntityHeadRotation packet2 =
                new PacketPlayOutEntityHeadRotation(this, getConvertedRotation(lastYaw));
        Util.broadcast(packet);
        Util.broadcast(packet2);
        for (Player player : Bukkit.getOnlinePlayers()) {
            EntityPlayer entityPlayer = Util.getEntityPlayer(player);
            DataWatcher watcher = entityPlayer.getDataWatcher();
            watcher.set(new DataWatcherObject<>(17, DataWatcherRegistry.a), (byte) 0x40);
            watcher.set(new DataWatcherObject<>(3, DataWatcherRegistry.i), false);
            PacketPlayOutEntityMetadata metadata = new PacketPlayOutEntityMetadata(this.getId(), watcher, true);
            Util.sendPacket(player, metadata);
        }
        Bukkit.getScheduler().runTaskLater(NPCPlugin.getInstance(),
                () -> Util.broadcast(
                        new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, this)),
                50);
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

    private Location getLocation() {
        return new Location(this.getWorld().getWorld(), this.lastX, this.lastY, this.lastZ, this.lastYaw, this.lastPitch);
    }

    private String generateID() {
        final String LOWER = "abcdefghijklmnopqrstuvwxyz";
        final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final String DIGITS = "0123456789";
        List<String> charCategories = List.of(LOWER, UPPER, DIGITS);

        int length = 10;
        String id;

        do {
            // Variables.
            StringBuilder password = new StringBuilder(length);
            Random random = new Random(System.nanoTime());

            // Build the password.
            for (int i = 0; i < length; i++) {
                String charCategory = charCategories.get(random.nextInt(charCategories.size()));
                int position = random.nextInt(charCategory.length());
                password.append(charCategory.charAt(position));
            }
            id = new String(password);
        }
        while (this.idExists(id));

        return id;
    }

    private boolean idExists(String id) {
        for (NPC npc : NPCPlugin.NPCS)
            if (npc.id.equals(id))
                return true;
        return false;
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
            if (response.contains("{player}"))
                response = response.replace("{player}", player.getName());
            player.sendMessage("[" + this.getName() + "] " + response);
        }
    }

    public void save() {
        File npcFile = new File(NPCPlugin.getInstance().getDataFolder(), "npcs.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(npcFile);
        String title = this.titleStand != null ? this.titleStand.getCustomName() : this.getName();
        String subTitle = this.subTitleStand != null ? this.subTitleStand.getCustomName() : null;
        String skinSignature = this.skin != null ? this.skin.getSignature() : null;
        String skinTexture = this.skin != null ? this.skin.getValue() : null;
        Location location = this.getLocation();
        Consumer<Player> onInteract = null;
        Skin skin = null;
        this.saveField(config, this.id + ".subtitle", title);
        this.saveField(config, this.id + ".subtitle", subTitle);
        this.saveField(config, this.id + ".motionLess", this.motionLess);
        this.saveField(config, this.id + ".hideName", this.hideName);
        this.saveField(config, this.id + ".hideTab", this.hideTab);
        this.saveField(config, this.id + ".location.signature", skinSignature);
        this.saveField(config, this.id + ".location.value", skinTexture);
        this.saveField(config, this.id + ".location.x", location.getX());
        this.saveField(config, this.id + ".location.y", location.getY());
        this.saveField(config, this.id + ".location.z", location.getZ());
        this.saveField(config, this.id + ".location.yaw", location.getYaw());
        this.saveField(config, this.id + ".location.pitch", location.getPitch());
    }

    private void saveField(FileConfiguration config, String path, Object value) {
        if (value == null) {
            if (!config.isConfigurationSection(path))
                config.createSection(path);
            return;
        }
        String strValue = String.valueOf(value);
        config.set(path, strValue);
    }
}
