package kr.rtustudio.continuous.handler;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import kr.rtustudio.continuous.Continuous;
import lombok.Getter;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;

import java.util.concurrent.TimeUnit;

public abstract class AbstractHandler implements LimboSessionHandler {

    protected final Continuous plugin;
    protected final RegisteredServer targetServer;

    @Getter
    protected volatile boolean active = false;
    @Getter
    protected LimboPlayer limboPlayer;

    protected AbstractHandler(Continuous plugin, RegisteredServer targetServer) {
        this.plugin = plugin;
        this.targetServer = targetServer;
    }

    @Override
    public final void onSpawn(Limbo server, LimboPlayer player) {
        this.active = true;
        this.limboPlayer = player;

        plugin.verbose("Player '" + player.getProxyPlayer().getUsername() + "' spawned in limbo.");

        player.disableFalling();
        onJoin(server, player);
    }

    protected void onJoin(Limbo server, LimboPlayer player) {
    }

    @Override
    public final void onDisconnect() {
        this.active = false;
        LimboPlayer lp = this.limboPlayer;
        if (lp != null) {
            lp.getProxyPlayer().clearTitle();
            plugin.getQueueManager().removeFromQueue(lp.getProxyPlayer());
        }
        onQuit();
        plugin.verbose("Player disconnected from limbo.");
    }

    protected void onQuit() {
    }

    protected void scheduleNextTick(long delayMs) {
        if (active && limboPlayer != null) {
            limboPlayer.getScheduledExecutor().schedule(this::tick, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    protected abstract void tick();

    /**
     * Disconnects the player from Limbo and connects them to the target server.
     * Registers the player as "passing" to prevent the ServerPreConnectEvent from
     * re-routing them back into the queue.
     */
    public void disconnect(RegisteredServer server) {
        if (!active)
            return;
        this.active = false;

        LimboPlayer lp = this.limboPlayer;
        if (lp != null) {
            Player proxyPlayer = lp.getProxyPlayer();
            plugin.getQueueManager().removeFromQueue(proxyPlayer);
            plugin.getQueueManager().addPassingPlayer(proxyPlayer);
            proxyPlayer.clearTitle();
            lp.disconnect(server);
        }
    }

    public Player getPlayer() {
        return limboPlayer != null ? limboPlayer.getProxyPlayer() : null;
    }

    /**
     * Stops the handler's active tick loop and clears the player's title.
     * Does NOT call removeFromQueue, as stop() is called when the handler
     * is being replaced (e.g., transitioning to another limbo server), not
     * when the player is fully leaving the queue system.
     */
    public void stop() {
        this.active = false;
        LimboPlayer lp = this.limboPlayer;
        if (lp != null) {
            lp.getProxyPlayer().clearTitle();
        }
    }
}
