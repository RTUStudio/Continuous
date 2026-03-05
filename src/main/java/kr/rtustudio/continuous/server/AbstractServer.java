package kr.rtustudio.continuous.server;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.client.ClientPlaySessionHandler;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.packet.BossBarPacket;
import kr.rtustudio.continuous.Continuous;
import kr.rtustudio.continuous.configuration.LimboConfig;
import kr.rtustudio.continuous.handler.AbstractHandler;
import lombok.Getter;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.chunk.VirtualWorld;
import net.elytrium.limboapi.api.file.WorldFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

@Getter
public abstract class AbstractServer {

    protected final Continuous plugin;
    protected final LimboFactory factory;
    protected final Path schematicPath;
    protected Limbo limbo;

    public AbstractServer(Continuous plugin, LimboFactory factory) {
        this.plugin = plugin;
        this.factory = factory;
        this.schematicPath = plugin.getDir().resolve("Schematics");
        createLimbo();
    }

    protected void createLimbo() {
        LimboConfig.World wc = getLimboConfig().getWorld();
        LimboConfig.Server sc = getLimboConfig().getServer();
        LimboConfig.Location loc = wc.location();

        VirtualWorld world = factory.createVirtualWorld(
                wc.dimension(),
                loc.x(), loc.y(), loc.z(),
                loc.yaw(), loc.pitch());

        LimboConfig.Schematic schem = wc.schematic();
        if (schem.load()) {
            try {
                Path path = schematicPath.resolve(schem.file());
                WorldFile file = factory.openWorldFile(schem.type(), path);
                LimboConfig.Offset offset = schem.offset();
                file.toWorld(factory, world, offset.x(), offset.y(), offset.z(), (byte) wc.lightLevel());
                plugin.getLogger().info("Loaded {} schematic from {}", getLimboName(), path);
            } catch (IOException e) {
                plugin.getLogger().warn("Failed to load {} schematic: {}", getLimboName(), e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().error("Unexpected error while loading {} schematic", getLimboName(), e);
            }
        }

        this.limbo = factory.createLimbo(world)
                .setName("Continuous_" + getLimboName())
                .setShouldRejoin(true)
                .setShouldRespawn(true)
                .setGameMode(wc.gamemode())
                .setViewDistance(wc.viewDistance())
                .setSimulationDistance(wc.simulationDistance())
                .setWorldTime(wc.time())
                .setReducedDebugInfo(sc.reducedDebugInfo());
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
                deletePacket.setAction(BossBarPacket.REMOVE); // This is Action 1 (remove)
                connection.delayedWrite(deletePacket);
            }
            playHandler.getServerBossBars().clear();
        }

        connectedPlayer.getTabList().clearAll();

        AbstractHandler handler = createHandler(player, server);
        plugin.getHandlerManager().register(player, handler);

        limbo.spawnPlayer(player, handler);

        plugin.verbose("Sent " + player.getUsername() + " to " + getLimboName().toLowerCase() + " for "
                + server.getServerInfo().getName());
    }

    protected abstract String getLimboName();

    protected abstract LimboConfig getLimboConfig();

    // Handler factory
    protected abstract AbstractHandler createHandler(Player player, RegisteredServer server);
}
