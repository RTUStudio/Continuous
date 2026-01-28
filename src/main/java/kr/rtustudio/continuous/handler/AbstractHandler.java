package kr.rtustudio.continuous.handler;

import kr.rtustudio.continuous.Continuous;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public abstract class AbstractHandler implements LimboSessionHandler {

    protected final Continuous plugin;
    protected final RegisteredServer targetServer;
    protected final boolean isReconnect;
    @Getter
    protected volatile boolean active = false;
    @Getter
    protected LimboPlayer limboPlayer;

    @Override
    public void onSpawn(Limbo server, LimboPlayer player) {
        this.active = true;
        this.limboPlayer = player;
        
        Player proxyPlayer = player.getProxyPlayer();
        plugin.verbose("Player '" + proxyPlayer.getUsername() + "' spawned in limbo.");
        
        player.disableFalling();
        onJoin(server, player);
    }

    public void onJoin(Limbo server, LimboPlayer player) {
    }

    @Override
    public void onDisconnect() {
        this.active = false;
        if (limboPlayer != null) {
            limboPlayer.getProxyPlayer().clearTitle();
            plugin.getQueueManager().removeFromQueue(limboPlayer.getProxyPlayer());
        }
        onQuit();
        plugin.verbose("Player disconnected from limbo.");
    }

    public void onQuit() {
    }

    protected void scheduleNextTick(long delayMs) {
        if (active && limboPlayer != null) {
            limboPlayer.getScheduledExecutor().schedule(this::tick, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    protected abstract void tick();

    public void disconnect(RegisteredServer server) {
        this.active = false;
        if (limboPlayer != null) {
            plugin.getQueueManager().removeFromQueue(limboPlayer.getProxyPlayer());
            limboPlayer.getProxyPlayer().clearTitle();
            limboPlayer.disconnect(server);
        }
    }

    public Player getPlayer() {
        return limboPlayer != null ? limboPlayer.getProxyPlayer() : null;
    }

    public void stop() {
        this.active = false;
        if (limboPlayer != null) {
            limboPlayer.getProxyPlayer().clearTitle();
            plugin.getQueueManager().removeFromQueue(limboPlayer.getProxyPlayer());
        }
    }
}
