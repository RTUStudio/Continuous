package kr.rtustudio.continuous.server;

import kr.rtustudio.continuous.Continuous;
import kr.rtustudio.continuous.configuration.ReconnectConfig;
import kr.rtustudio.continuous.handler.ReconnectHandler;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.chunk.VirtualWorld;
import net.elytrium.limboapi.api.file.WorldFile;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.client.ClientPlaySessionHandler;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.packet.BossBarPacket;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

@Getter
public class ReconnectServer {

    private final Continuous plugin;
    private final LimboFactory factory;
    private final Path schematicPath;
    private Limbo limbo;

    public ReconnectServer(Continuous plugin, LimboFactory factory) {
        this.plugin = plugin;
        this.factory = factory;
        this.schematicPath = plugin.getDir().resolve("Schematics");
        createLimbo();
    }

    private void createLimbo() {
        ReconnectConfig config = plugin.getReconnectConfig();
        ReconnectConfig.World wc = config.world;
        ReconnectConfig.Location loc = wc.location();
        
        VirtualWorld world = factory.createVirtualWorld(
                wc.dimension(),
                loc.x(), loc.y(), loc.z(),
                loc.yaw(), loc.pitch()
        );
        
        ReconnectConfig.Schematic schem = wc.schematic();
        if (schem.load()) {
            try {
                Path path = schematicPath.resolve(schem.file());
                WorldFile file = factory.openWorldFile(schem.type(), path);
                ReconnectConfig.Offset offset = schem.offset();
                file.toWorld(factory, world, offset.x(), offset.y(), offset.z(), wc.lightLevel());
                plugin.getLogger().info("Loaded reconnect schematic from {}", path);
            } catch (IOException e) {
                plugin.getLogger().warn("Failed to load reconnect schematic: {}", e.getMessage());
            }
        }
        
        this.limbo = factory.createLimbo(world)
                .setName("Continuous_Reconnect")
                .setShouldRejoin(true)
                .setShouldRespawn(true)
                .setGameMode(wc.gamemode())
                .setReducedDebugInfo(config.server.reducedDebugInfo())
                .setViewDistance(wc.viewDistance())
                .setSimulationDistance(wc.simulationDistance())
                .setWorldTime(wc.time());
    }

    public void reload() {
        createLimbo();
    }

    public void send(Player player, RegisteredServer server) {
        ConnectedPlayer connectedPlayer = (ConnectedPlayer) player;
        MinecraftConnection connection = connectedPlayer.getConnection();
        MinecraftSessionHandler sessionHandler = connection.getActiveSessionHandler();
        
        if (sessionHandler instanceof ClientPlaySessionHandler playHandler) {
            for (UUID bossBar : playHandler.getServerBossBars()) {
                BossBarPacket deletePacket = new BossBarPacket();
                deletePacket.setUuid(bossBar);
                deletePacket.setAction(BossBarPacket.REMOVE);
                connection.delayedWrite(deletePacket);
            }
            playHandler.getServerBossBars().clear();
        }
        connectedPlayer.getTabList().clearAll();
        
        ReconnectHandler handler = new ReconnectHandler(plugin, player, server);
        plugin.getHandlerManager().register(player, handler);
        limbo.spawnPlayer(player, handler);
        
        plugin.verbose("Sent " + player.getUsername() + " to reconnect for " + server.getServerInfo().getName());
    }
}
