package ch.asarix.creation;

import ch.asarix.NPCPlugin;
import ch.asarix.Util;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import lombok.Getter;
import net.minecraft.server.v1_15_R1.MinecraftServer;
import net.minecraft.server.v1_15_R1.WorldServer;
import org.apache.commons.lang.Validate;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_15_R1.CraftOfflinePlayer;
import org.bukkit.craftbukkit.v1_15_R1.CraftServer;
import org.bukkit.craftbukkit.v1_15_R1.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.util.Consumer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

public class NPCBuilder {
    @Getter
    private final String id;
    @Getter
    private final List<String> dialogues = Lists.newArrayList();
    @Getter
    private final List<String> holograms = Lists.newArrayList();
    @Getter
    private Location location;
    @Getter
    private boolean motionLess;
    @Getter
    private boolean hideName;
    @Getter
    private boolean hideTab;
    @Getter
    private Consumer<Player> onInteract = null;
    @Getter
    private Skin skin = null;
    @Getter
    private String entityName = null;

    public NPCBuilder() {
        this.id = generateID();
        this.motionLess = true;
        this.hideTab = true;
        this.hideName = false;
        File dataFolder = NPCPlugin.getInstance().getDataFolder();
        File pluginFolder = dataFolder.getParentFile().getAbsoluteFile();
        File serverFolder = pluginFolder.getParentFile();
        File serverPropertiesFile = new File(serverFolder, "server.properties");
        Properties pr = new Properties();
        World world;
        try {
            FileInputStream in = new FileInputStream(serverPropertiesFile);
            pr.load(in);
            String worldName = pr.getProperty("level-name");
            world = Bukkit.getWorld(worldName);
        } catch (Exception e) {
            world = Bukkit.getWorlds().get(0);
        }
        this.location = new Location(world, 0, 0, 0);
    }

    public NPCBuilder(String id) {
        File file = new File(NPCPlugin.getInstance().getDataFolder(), "npcs.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection(id);
        this.id = id;
        this.entityName = section.getString("entityName");
        this.motionLess = section.getBoolean("motionLess");
        this.hideTab = section.getBoolean("hideTab");
        this.hideName = section.getBoolean("hideName");
        double x = section.getDouble("location.x");
        double y = section.getDouble("location.y");
        double z = section.getDouble("location.z");
        float yaw = (float) section.getDouble("location.yaw");
        float pitch = (float) section.getDouble("location.pitch");
        String worldName = section.getString("location.worldName");
        this.location = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
        String skinSignature = section.getString("skin.signature");
        String skinTexture = section.getString("skin.value");
        this.skin = new Skin(skinTexture, skinSignature);
        this.dialogues.addAll(section.getStringList("dialogues"));
    }

    /**
     * Builds the npc with the specified attributes.
     *
     * @return instance of the new NPC
     */
    public NPC build() {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        WorldServer world = ((CraftWorld) this.location.getWorld()).getHandle();
        String name = this.entityName == null ? this.id : this.entityName;
        GameProfile gameProfile = new GameProfile(UUID.randomUUID(), name);
        if (this.entityName == null || !this.holograms.isEmpty()) this.hideName = true;
        return new NPC(server, world, gameProfile, this);
    }

    //    /**
//     * Sets the title and the subtitle located on the top
//     * of the npc.
//     * <p>
//     * If you only want a title, just use {@link #setName(String)}
//     *
//     * @param title    Title
//     * @param subTitle Subtitle
//     * @return instance of the builder class for builder pattern
//     */
//    public NPCBuilder setTitles(String title, String subTitle) {
//        Validate.isTrue(title != null && !title.isBlank());
//        Validate.isTrue(subTitle != null && !subTitle.isBlank());
//        this.setName(title);
//        this.subTitle = subTitle;
//        this.hideName = true;
//        return this;
//    }
    public NPCBuilder addHologram(String... holograms) {
        this.holograms.addAll(List.of(holograms));
        return this;
    }

    public NPCBuilder addHologram(int index, String... holograms) {
        this.holograms.addAll(index, List.of(holograms));
        return this;
    }

    public NPCBuilder setEntityName(String name) {
        Validate.isTrue(ChatColor.stripColor(name).length() < 16);
        this.entityName = name;
        return this;
    }

    /**
     * Sets the function called when the npc is clicked.
     * <p>
     * You can also create a class that extends {@link NPC}
     * and extend {@link NPC#onInteract(Player)}
     *
     * @param onInteract Function (lambda most of the time)
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setConsumer(Consumer<Player> onInteract) {
        this.onInteract = onInteract;
        return this;
    }

    /**
     * Sets if the NPC is motionless. Otherwise, it will
     * look at the nearbiest player.
     * <p>
     * This behaviour might change in the future.
     *
     * @param flag Should the npc be motionless ?
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setMotionLess(boolean flag) {
        this.motionLess = flag;
        return this;
    }

    public NPCBuilder setHideTab(boolean flag) {
        this.hideTab = flag;
        return this;
    }

    /**
     * Sets if the NPC should hide the name on top of
     * his head or not.
     * <p>
     * Note that if the holograms have any contents,
     * the value will be true fore sure.
     *
     * @param flag Should the name be hidden ?
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setHideName(boolean flag) {
        this.hideName = flag;
        return this;
    }

//    /**
//     * Sets the name of the npc.
//     * <p>
//     * The name must be under 16 characters (the colours do not count).
//     * If it is blank (empty of made with spaces) It will be generated
//     * automatically with the pattern : NPC#(TotalNumberOfRegisteredNPCS)
//     * and be hidden.
//     *
//     * @param name Name
//     * @return instance of the builder class for builder pattern
//     */
//    public NPCBuilder setName(String name) {
//        Validate.notNull(name);
//        Validate.isTrue(ChatColor.stripColor(name).length() <= 16, "Name must be have maximum 16 characters !");
//        this.hadToReplaceName = name.isBlank();
//        if (this.hadToReplaceName) {
//            name = "NPC#" + (NPCPlugin.NPCS.size() + 1);
//        }
//        this.title = name;
//        return this;
//    }
//
//    /**
//     * Forces the name of the npc to be blank (made of spaces).
//     * if the length specified is negative it will be replaced
//     * with the max value, which is 16.
//     *
//     * @deprecated Why would you use that lol ? Note that a
//     * blank name cannot be hidden on the top of the npc.
//     * @param len number of spaces
//     * @return instance of the builder class for builder pattern
//     */
//    @Deprecated
//    public NPCBuilder forceBlankName(int len) {
//        if (len < 0) len = 16;
//        this.title = StringUtils.repeat(" ", len);
//        this.hadToReplaceName = false;
//        return this;
//    }

    /**
     * Sets the x component of the npc location.
     *
     * @param x X
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setX(double x) {
        this.location.setX(x);
        return this;
    }

    /**
     * Sets the y component of the npc location.
     *
     * @param y Y
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setY(double y) {
        this.location.setY(y);
        return this;
    }

    /**
     * Sets the z component of the npc location.
     *
     * @param z Z
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setZ(double z) {
        this.location.setZ(z);
        return this;
    }

    /**
     * Sets the yaw component of the npc location.
     *
     * @param yaw Yaw
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setYaw(float yaw) {
        this.location.setYaw(yaw);
        return this;
    }

    /**
     * Sets the pitch component of the npc location.
     *
     * @param pitch Pitch
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setPitch(float pitch) {
        this.location.setPitch(pitch);
        return this;
    }

    /**
     * Adds to the x component of the npc location.
     *
     * @param x X to add
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder addX(double x) {
        this.location.setX(this.location.getX() + x);
        return this;
    }

    /**
     * Adds to the y component of the npc location.
     *
     * @param y Y to add
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder addY(double y) {
        this.location.setY(this.location.getY() + y);
        return this;
    }

    /**
     * Adds to the z component of the npc location.
     *
     * @param z Z to add
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder addZ(double z) {
        this.location.setZ(this.location.getZ() + z);
        return this;
    }

    /**
     * Adds to the yaw component of the npc location.
     *
     * @param yaw Yaw to add
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder addYaw(float yaw) {
        this.location.setYaw(this.location.getYaw() + yaw);
        return this;
    }

    /**
     * Adds to the pitch component of the npc location.
     *
     * @param pitch Pitch to add
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder addPitch(float pitch) {
        this.location.setPitch(this.location.getPitch() + pitch);
        return this;
    }

    /**
     * Sets the world of the npc location.
     * <p>
     * The world specified must already have been created.
     *
     * @param worldName Name of the world
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null)
            throw new IllegalStateException("World " + worldName + " does not exist !");
        this.location.setWorld(world);
        return this;
    }

    /**
     * Sets the world of the npc location.
     *
     * @param world World instance
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setWorld(World world) {
        this.location.setWorld(world);
        return this;
    }

    /**
     * Sets the location of the npc.
     *
     * @param location Location
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setLocation(Location location) {
        this.location = location.clone();
        return this;
    }

    /**
     * Sets the location of the npc to the player's location.
     *
     * @param player Player
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setLocation(Player player) {
        return this.setLocation(player.getLocation());
    }

    /**
     * Sets a list of thing that the npc randomly chooses
     * when clicked.
     *
     * @param dialogues list of dialogue
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setDialogues(List<String> dialogues) {
        this.dialogues.clear();
        this.dialogues.addAll(dialogues);
        return this;
    }

    /**
     * Adds a dialogues to the dialogue list.
     * <p>
     * See {@link #setDialogues(List)}
     *
     * @param dialogues dialogues
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder addDialogue(String... dialogues) {
        this.dialogues.addAll(List.of(dialogues));
        return this;
    }

    /**
     * Sets the skin of the npc to the player's one.
     *
     * @param player Player
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setSkin(Player player) {
        String signature;
        String value;
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + player.getName());
            InputStreamReader reader = new InputStreamReader(url.openStream());
            String uuid = new JsonParser().parse(reader).getAsJsonObject().get("id").getAsString();

            URL url2 = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            InputStreamReader reader2 = new InputStreamReader(url2.openStream());
            JsonObject property = new JsonParser().parse(reader2).getAsJsonObject().get("properties").getAsJsonArray().get(0).getAsJsonObject();
            value = property.get("value").getAsString();
            signature = property.get("signature").getAsString();
        } catch (Exception e) {
            GameProfile playerProfile = Util.getEntityPlayer(player).getProfile();
            return setProfileSkin(playerProfile);
        }
        this.skin = new Skin(value, signature);
        return this;
    }

    /**
     * Sets the skin of the npc to the player's one.
     * <p>
     * If the player does not exist, nothing happens.
     * The name must be correctly spelled with the
     * right caps.
     *
     * @param playerName Name of the player
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setPlayerSkin(String playerName) {
        String signature;
        String value;
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + playerName);
            InputStreamReader reader = new InputStreamReader(url.openStream());
            String uuid = new JsonParser().parse(reader).getAsJsonObject().get("id").getAsString();

            URL url2 = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            InputStreamReader reader2 = new InputStreamReader(url2.openStream());
            JsonObject property = new JsonParser().parse(reader2).getAsJsonObject().get("properties").getAsJsonArray().get(0).getAsJsonObject();
            value = property.get("value").getAsString();
            signature = property.get("signature").getAsString();
        } catch (Exception e) {
            UUID uuid = Util.UUIDFromUserName(playerName);
            if (uuid == null) return this;
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            GameProfile playerProfile = ((CraftOfflinePlayer) player).getProfile();
            return setProfileSkin(playerProfile);
        }
        this.skin = new Skin(value, signature);
        return this;
    }

    /**
     * Sets the skin of the npc to the profile's one.
     * <p>
     * if the profile does not have any texture
     * property, nothing happens.
     *
     * @param gameProfile profile
     * @return instance of the builder class for builder pattern
     */
    public NPCBuilder setProfileSkin(GameProfile gameProfile) {
        PropertyMap propertyMap = gameProfile.getProperties();
        if (!propertyMap.containsKey("textures")) return this;
        Collection<Property> properties = propertyMap.get("textures");
        if (properties.isEmpty()) return this;
        Property textureProp = properties.stream().findFirst().get();
        String signature = textureProp.getSignature();
        String value = textureProp.getValue();
        this.skin = new Skin(value, signature);
        return this;
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
            if (npc.getNpcId().equals(id))
                return true;
        return false;
    }
}
