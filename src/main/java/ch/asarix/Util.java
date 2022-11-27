package ch.asarix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import net.minecraft.server.v1_15_R1.EntityPlayer;
import net.minecraft.server.v1_15_R1.Packet;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_15_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class Util {

    private static final HashMap<String, UUID> nameToUuid = new HashMap<>();
    private static final HashMap<UUID, String> uuidToName = new HashMap<>();
    private static File dataFile;

    public static void init() throws IOException {
        NPCPlugin plugin = NPCPlugin.getInstance();
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists())
            dataFolder.mkdir();
        dataFile = new File(dataFolder, "playerIds.json");
        if (!dataFile.exists()) {
            dataFile.createNewFile();
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            new ObjectMapper().writeValue(dataFile, node);
        }
        File npcFile = new File(dataFolder, "npcs.yml");
        if (!npcFile.exists()) {
            npcFile.createNewFile();
        }
    }

    public static String userNameFromUUID(UUID uuid) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(dataFile);
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> element = it.next();
                String name = element.getKey();
                UUID id = UUID.fromString(element.getValue().asText());
                if (id == uuid) return name;
            }
            URL url = new URL("https://api.mojang.com/user/profile/" + unDashed(uuid));
            JsonNode node1 = mapper.readTree(url);
            String name = node1.get("name").asText();
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.set(name, new TextNode(uuid.toString()));
            mapper.writeValue(dataFile, objectNode);
            return name;
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static UUID UUIDFromUserName(String userName) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(dataFile);
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> element = it.next();
                String name = element.getKey();
                UUID id = UUID.fromString(element.getValue().asText());
                if (name.equalsIgnoreCase(userName)) return id;
            }
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + userName);
            JsonNode node1 = mapper.readTree(url);
            String name = node1.get("name").asText();
            UUID id = dashed(node1.get("id").asText());
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.set(name, new TextNode(id.toString()));
            mapper.writeValue(dataFile, objectNode);
            return id;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static EntityPlayer getEntityPlayer(Player player) {
        return ((CraftPlayer) player).getHandle();
    }

    private static String unDashed(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    private static UUID dashed(String unDashed) {
        String dashed = unDashed.replaceFirst("([\\da-fA-F]{8})([\\da-fA-F]{4})([\\da-fA-F]{4})([\\da-fA-F]{4})([\\da-fA-F]+)", "$1-$2-$3-$4-$5");
        return UUID.fromString(dashed);
    }

    public static void broadcast(Packet<?> packet) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendPacket(player, packet);
        }
    }

    public static void sendPacket(Player player, Packet<?> packet) {
        EntityPlayer entityPlayer = getEntityPlayer(player);
        entityPlayer.playerConnection.sendPacket(packet);
    }
}
