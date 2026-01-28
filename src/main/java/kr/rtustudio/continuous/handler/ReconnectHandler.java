package kr.rtustudio.continuous.handler;

import kr.rtustudio.continuous.Continuous;
import kr.rtustudio.continuous.configuration.QueueConfig;
import kr.rtustudio.continuous.configuration.ReconnectConfig;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class ReconnectHandler extends AbstractHandler {

    private final ReconnectConfig config;
    private final QueueConfig queueConfig;
    private final PingOptions pingOptions;
    private final MiniMessage serializer = MiniMessage.miniMessage();

    private State state = null;

    public ReconnectHandler(Continuous plugin, Player player, RegisteredServer server) {
        super(plugin, server, true);
        this.config = plugin.getReconnectConfig();
        this.queueConfig = plugin.getQueueConfig();
        this.pingOptions = PingOptions.builder().timeout(Duration.ofMillis(config.server.timeout())).build();
    }

    @Override
    public void onJoin(Limbo server, LimboPlayer player) {
        Player proxyPlayer = player.getProxyPlayer();
        boolean isPriority = proxyPlayer.hasPermission("continuous.priority");
        plugin.getQueueManager().addToReconnect(proxyPlayer, targetServer, isPriority);
        
        player.setGameMode(config.world.gamemode());
        scheduleNextTick(config.server.check());
    }

    @Override
    protected void tick() {
        if (!active || limboPlayer == null) return;
        
        Player player = limboPlayer.getProxyPlayer();
        
        targetServer.ping(pingOptions).whenComplete((ping, exception) -> {
            if (!active || limboPlayer == null) return;
            
            ReconnectConfig.Server serverConfig = config.server;
            if (exception != null) {
                showOfflineState(player, serverConfig);
            } else {
                handleOnlineServer(player, ping, serverConfig);
            }
        });
    }

    private void showOfflineState(Player player, ReconnectConfig.Server serverConfig) {
        Title title = Title.title(
                serializer.deserialize(config.offline.title().title()),
                serializer.deserialize(config.offline.title().subtitle()),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(30000), Duration.ZERO));
        player.showTitle(title);
        if (this.state != State.OFFLINE) {
            Component message = serializer.deserialize(config.offline.message());
            player.sendMessage(message);
        }
        this.state = State.OFFLINE;
        scheduleNextTick(serverConfig.check());
    }

    private void handleOnlineServer(Player player, ServerPing ping, ReconnectConfig.Server serverConfig) {
        boolean isFull = isServerFull(player, ping);
        int position = plugin.getQueueManager().getPosition(player);
        
        if (isFull) {
            moveToQueue(player);
        } else if (position == 1) {
            showConnectState(player, serverConfig);
        } else {
            scheduleNextTick(serverConfig.check());
        }
    }

    private void moveToQueue(Player player) {
        boolean isPriority = player.hasPermission("continuous.priority");
        plugin.getQueueManager().addFromReconnectToQueue(player, targetServer, isPriority);
        plugin.getHandlerManager().unregister(player);
        plugin.getQueue().send(player, targetServer);
    }

    private boolean isServerFull(Player player, ServerPing ping) {
        if (player.hasPermission("continuous.admin")) return false;
        QueueConfig.MaxPlayer mp = queueConfig.queue.maxPlayer();
        if (ping.getPlayers().isPresent()) {
            ServerPing.Players players = ping.getPlayers().get();
            int max = mp.enabled() ? Math.min(mp.size(), players.getMax()) : players.getMax();
            return players.getOnline() >= max;
        }
        return false;
    }

    private void showConnectState(Player player, ReconnectConfig.Server serverConfig) {
        Title title = Title.title(
                serializer.deserialize(config.connect.title().title()),
                serializer.deserialize(config.connect.title().subtitle()),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(30000), Duration.ZERO));
        player.showTitle(title);
        if (this.state != State.CONNECT) {
            Component message = serializer.deserialize(config.connect.message());
            player.sendMessage(message);
        }
        this.state = State.CONNECT;
        
        limboPlayer.getScheduledExecutor().schedule(() -> {
            if (!active || limboPlayer == null) return;
            player.clearTitle();
            disconnect(targetServer);
        }, serverConfig.delay(), TimeUnit.MILLISECONDS);
    }

    enum State {
        OFFLINE, CONNECT
    }
}
