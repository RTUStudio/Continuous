package kr.rtustudio.continuous.configuration;

import kr.rtustudio.configurate.ConfigurationPart;
import lombok.Getter;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.file.BuiltInWorldFileType;
import net.elytrium.limboapi.api.player.GameMode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
public class QueueConfig extends ConfigurationPart implements LimboConfig {
    public String trigger = "((?i)^(server closed|server is restarting|multiplayer\\.disconnect\\.server_shutdown))+$";
    public Server server = new Server(1000L, 500L, 2000L, true);
    public World world = new World(
            Dimension.OVERWORLD, GameMode.SPECTATOR, 15, 6000L, 2, 2,
            new Location(0, 100, 0, 90.0f, 0.0f),
            new Schematic(false, BuiltInWorldFileType.WORLDEDIT_SCHEM, "queue.schem", new Offset(0, 64, 0)));
    public Message full = new Message("Server is full", new Title("", "<red>Server is full</red>"));
    public Queue queue = new Queue("Queue: {0}", new Title("", "Queue: {0}"), new MaxPlayer(false, 100));
    public Message connect = new Message("Connecting!", new Title("", "<green>Connecting...</green>"));

    @ConfigSerializable
    public record MaxPlayer(boolean enabled, int size) {
    }

    @ConfigSerializable
    public record Queue(String message, Title title, MaxPlayer maxPlayer) {
    }
}
