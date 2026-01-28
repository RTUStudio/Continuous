package kr.rtustudio.continuous.handler;

import kr.rtustudio.continuous.Continuous;
import kr.rtustudio.continuous.configuration.QueueConfig;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class QueueHandler extends AbstractHandler {

    private final QueueConfig config;
    private final PingOptions pingOptions;
    private final MiniMessage serializer = MiniMessage.miniMessage();

    private int lastPosition = -1;
    private State state = null;

    public QueueHandler(Continuous plugin, Player player, RegisteredServer server) {
        super(plugin, server, false);
        this.config = plugin.getQueueConfig();
        this.pingOptions = PingOptions.builder().timeout(Duration.ofMillis(config.server.timeout())).build();
    }

    @Override
    public void onJoin(Limbo server, LimboPlayer player) {
        Player proxyPlayer = player.getProxyPlayer();
        boolean isPriority = proxyPlayer.hasPermission("continuous.priority");
        plugin.getQueueManager().addToNewQueue(proxyPlayer, targetServer, isPriority);
        
        player.setGameMode(config.world.gamemode());
        scheduleNextTick(config.server.check());
    }

    @Override
    protected void tick() {
        if (!active || limboPlayer == null) return;
        
        Player player = limboPlayer.getProxyPlayer();

        targetServer.ping(pingOptions).whenComplete((ping, exception) -> {
            if (!active || limboPlayer == null) return;
            
            QueueConfig.Server serverConfig = config.server;
            if (exception != null) {
                handleOfflineServer(player);
            } else {
                handleOnlineServer(player, ping, serverConfig);
            }
        });
    }

    private void handleOfflineServer(Player player) {
        plugin.getQueueManager().moveQueueToReconnect(player, targetServer);
        plugin.getHandlerManager().unregister(player);
        plugin.getReconnect().send(player, targetServer);
    }

    private void handleOnlineServer(Player player, ServerPing ping, QueueConfig.Server serverConfig) {
        boolean isFull = isServerFull(player, ping);
        int position = plugin.getQueueManager().getPosition(player);
        
        if (!isFull && position == 1) {
            showConnectState(player, serverConfig);
        } else if (isFull) {
            showFullState(player, position, serverConfig);
        } else {
            showQueueState(player, position, serverConfig);
        }
    }

    private boolean isServerFull(Player player, ServerPing ping) {
        if (player.hasPermission("continuous.admin")) return false;
        QueueConfig.MaxPlayer mp = config.queue.maxPlayer();
        if (ping.getPlayers().isPresent()) {
            ServerPing.Players players = ping.getPlayers().get();
            int max = mp.enabled() ? Math.min(mp.size(), players.getMax()) : players.getMax();
            return players.getOnline() >= max;
        }
        return false;
    }

    private void showConnectState(Player player, QueueConfig.Server serverConfig) {
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

    private void showFullState(Player player, int position, QueueConfig.Server serverConfig) {
        Title title = Title.title(
                serializer.deserialize(config.full.title().title()),
                serializer.deserialize(config.full.title().subtitle()),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(30000), Duration.ZERO));
        player.showTitle(title);
        if (this.state != State.FULL) {
            Component message = serializer.deserialize(config.full.message());
            player.sendMessage(message);
        }
        this.state = State.FULL;
        scheduleNextTick(serverConfig.check());
    }

    private void showQueueState(Player player, int position, QueueConfig.Server serverConfig) {
        Title title = Title.title(
                serializer.deserialize(
                        MessageFormat.format(config.queue.title().title(), position)),
                serializer.deserialize(
                        MessageFormat.format(config.queue.title().subtitle(), position)),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(30000), Duration.ZERO));
        player.showTitle(title);
        if (this.lastPosition != position) {
            Component message = serializer.deserialize(
                    MessageFormat.format(config.queue.message(), position));
            player.sendMessage(message);
        }
        this.lastPosition = position;
        this.state = State.QUEUE;
        scheduleNextTick(serverConfig.check());
    }

    enum State {
        FULL, QUEUE, CONNECT
    }
}
