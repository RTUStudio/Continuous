package kr.rtustudio.continuous.configuration;

import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.file.BuiltInWorldFileType;
import net.elytrium.limboapi.api.player.GameMode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

public interface LimboConfig {

    Server getServer();

    World getWorld();

    @ConfigSerializable
    record Server(long check, long timeout, long delay, boolean reducedDebugInfo) {
    }

    @ConfigSerializable
    record Title(String title, String subtitle) {
    }

    @ConfigSerializable
    record Message(String message, Title title) {
    }

    @ConfigSerializable
    record Location(int x, int y, int z, float yaw, float pitch) {
    }

    @ConfigSerializable
    record Offset(int x, int y, int z) {
    }

    @ConfigSerializable
    record Schematic(boolean load, BuiltInWorldFileType type, String file, Offset offset) {
    }

    @ConfigSerializable
    record World(Dimension dimension, GameMode gamemode, int lightLevel, long time,
                 int viewDistance, int simulationDistance, Location location, Schematic schematic) {
    }
}
