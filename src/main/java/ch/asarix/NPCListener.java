package ch.asarix;

import ch.asarix.creation.NPC;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.server.v1_15_R1.EnumHand;
import net.minecraft.server.v1_15_R1.PacketPlayInUseEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;

public class NPCListener implements Listener {

    @EventHandler()
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        for (NPC npc : NPCPlugin.NPCS)
            npc.spawn(player);
        startListen(player);
    }

    @EventHandler()
    public void onPlayerQuit(PlayerQuitEvent event) {
        final Channel channel = Util.getEntityPlayer(event.getPlayer()).playerConnection.networkManager.channel;
        channel.eventLoop().submit(() -> channel.pipeline().remove(event.getPlayer().getName()));
    }

    public void startListen(Player player) {
        ChannelDuplexHandler channelDuplexHandler = new ChannelDuplexHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object packet) throws Exception {
                if (packet instanceof PacketPlayInUseEntity) {
                    try {
                        PacketPlayInUseEntity packet2 = (PacketPlayInUseEntity) packet;
                        if (packet2.b() != null) {
                            if (packet2.b() == PacketPlayInUseEntity.EnumEntityUseAction.INTERACT) {
                                super.channelRead(ctx, packet);
                                return;
                            }
//                            System.out.println(packet2.b().name());
                        }
                        if (packet2.c() != null) {
                            if (packet2.c() != EnumHand.MAIN_HAND) {
                                super.channelRead(ctx, packet);
                                return;
                            }
//                            System.out.println(packet2.c().name());
                        }
                        Field entityId = PacketPlayInUseEntity.class.getDeclaredField("a");
                        entityId.setAccessible(true);
                        int id = entityId.getInt(packet2);
//                        System.out.println(id);
                        for (NPC npc : NPCPlugin.NPCS) {
                            if (npc.getId() == id) {
                                ItemStack holdItem = player.getInventory().getItemInMainHand();
                                System.out.println("item");
                                ItemMeta meta = holdItem.getItemMeta();
                                if (meta != null) {
                                    System.out.println("meta");
                                    PersistentDataContainer container = meta.getPersistentDataContainer();
                                    if (container.has(NPCPlugin.key, PersistentDataType.STRING)) {
                                        System.out.println("key");
                                        String data = container.get(NPCPlugin.key, PersistentDataType.STRING);
                                        if (data != null) {
                                            System.out.println("data");
                                            if (data.equals("remover")) {
                                                System.out.println("remover");
                                                npc.remove();
                                                super.channelRead(ctx, packet);
                                                return;
                                            }
                                        }
                                    }
                                }
                                npc.onInteract(player);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                super.channelRead(ctx, packet);
            }
        };
        final ChannelPipeline pipeline = Util.getEntityPlayer(player).playerConnection.networkManager.channel.pipeline();
        pipeline.addBefore("packet_handler", player.getName(), channelDuplexHandler);
    }
}
