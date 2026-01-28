package kr.rtustudio.continuous.configuration;

import kr.rtustudio.configurate.ConfigurationPart;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.file.BuiltInWorldFileType;
import net.elytrium.limboapi.api.player.GameMode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;


public class QueueConfig extends ConfigurationPart {
    public String trigger = "((?i)^(server closed|server is restarting|multiplayer\\.disconnect\\.server_shutdown))+$";
    public Server server = new Server(1000L, 500L, 2000L, true);
    public World world = new World(
            Dimension.OVERWORLD, GameMode.SPECTATOR, 15, 6000L, 2, 2,
            new Location(0, 100, 0, 90.0f, 0.0f),
            new Schematic(false, BuiltInWorldFileType.WORLDEDIT_SCHEM, "queue.schem", new Offset(0, 64, 0))
    );
    public Message full = new Message("Server is full", new Title("", "<red>Server is full</red>"));
    public Queue queue = new Queue("Queue: {0}", new Title("", "Queue: {0}"), new MaxPlayer(false, 100));
    public Message connect = new Message("Connecting!", new Title("", "<green>Connecting...</green>"));

    @ConfigSerializable
    public record Server(long check, long timeout, long delay, boolean reducedDebugInfo) {}

    @ConfigSerializable
    public record Title(String title, String subtitle) {}

    @ConfigSerializable
    public record Message(String message, Title title) {}

    @ConfigSerializable
    public record Location(int x, int y, int z, float yaw, float pitch) {}

    @ConfigSerializable
    public record Offset(int x, int y, int z) {}

    @ConfigSerializable
    public record Schematic(boolean load, BuiltInWorldFileType type, String file, Offset offset) {}

    @ConfigSerializable
    public record World(Dimension dimension, GameMode gamemode, int lightLevel, long time,
                        int viewDistance, int simulationDistance, Location location, Schematic schematic) {}

    @ConfigSerializable
    public record MaxPlayer(boolean enabled, int size) {}

    @ConfigSerializable
    public record Queue(String message, Title title, MaxPlayer maxPlayer) {}
}
