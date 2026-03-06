package kr.rtustudio.continuous.handler;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import kr.rtustudio.continuous.Continuous;
import kr.rtustudio.continuous.configuration.QueueConfig;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class QueueHandler extends AbstractHandler {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final QueueConfig config;
    private final PingOptions pingOptions;

    private int lastPosition = -1;
    private State state = null;

    public QueueHandler(Continuous plugin, Player player, RegisteredServer server) {
        super(plugin, server);
        this.config = plugin.getQueueConfig();
        this.pingOptions = PingOptions.builder()
                .timeout(Duration.ofMillis(config.server.timeout()))
                .build();
    }

    @Override
    protected void onJoin(Limbo server, LimboPlayer player) {
        Player proxyPlayer = player.getProxyPlayer();
        boolean isPriority = proxyPlayer.hasPermission("continuous.priority");
        plugin.getQueueManager().addToNewQueue(proxyPlayer, targetServer, isPriority);
        scheduleNextTick(config.server.check());
    }

    @Override
    protected void tick() {
        if (!active || limboPlayer == null)
            return;

        Player player = limboPlayer.getProxyPlayer();

        targetServer.ping(pingOptions).whenComplete((ping, ex) -> {
            if (!active || limboPlayer == null)
                return;

            if (ex != null) {
                handleOfflineServer(player);
            } else {
                handleOnlineServer(player, ping);
            }
        });
    }

    private void handleOfflineServer(Player player) {
        plugin.getQueueManager().moveQueueToReconnect(player, targetServer);
        plugin.getHandlerManager().unregister(player);
        plugin.getReconnect().send(player, targetServer);
    }

    private void handleOnlineServer(Player player, ServerPing ping) {
        boolean isFull = isServerFull(player, ping);
        int position = plugin.getQueueManager().getPosition(player);

        if (!isFull && position == 1) {
            showConnectState(player);
        } else if (isFull) {
            showFullState(player, position);
        } else {
            showQueueState(player, position);
        }
    }

    private boolean isServerFull(Player player, ServerPing ping) {
        if (player.hasPermission("continuous.admin"))
            return false;
        QueueConfig.MaxPlayer mp = config.queue.maxPlayer();
        if (ping.getPlayers().isPresent()) {
            ServerPing.Players players = ping.getPlayers().get();
            int max = mp.enabled() ? Math.min(mp.size(), players.getMax()) : players.getMax();
            return players.getOnline() >= max;
        }
        return false;
    }

    private void showConnectState(Player player) {
        Title title = Title.title(
                MM.deserialize(config.connect.title().title()),
                MM.deserialize(config.connect.title().subtitle()),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(30_000), Duration.ZERO));
        player.showTitle(title);

        if (this.state != State.CONNECT) {
            player.sendMessage(MM.deserialize(config.connect.message()));
        }
        this.state = State.CONNECT;

        limboPlayer.getScheduledExecutor().schedule(() -> {
            if (!active || limboPlayer == null)
                return;
            player.clearTitle();
            disconnect(targetServer);
        }, config.server.delay(), TimeUnit.MILLISECONDS);
    }

    private void showFullState(Player player, int position) {
        Title title = Title.title(
                MM.deserialize(config.full.title().title()),
                MM.deserialize(config.full.title().subtitle()),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(30_000), Duration.ZERO));
        player.showTitle(title);

        if (this.state != State.FULL) {
            player.sendMessage(MM.deserialize(config.full.message()));
        }
        this.state = State.FULL;
        scheduleNextTick(config.server.check());
    }

    private void showQueueState(Player player, int position) {
        Title title = Title.title(
                MM.deserialize(MessageFormat.format(config.queue.title().title(), position)),
                MM.deserialize(MessageFormat.format(config.queue.title().subtitle(), position)),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(30_000), Duration.ZERO));
        player.showTitle(title);

        if (this.lastPosition != position) {
            player.sendMessage(MM.deserialize(MessageFormat.format(config.queue.message(), position)));
        }
        this.lastPosition = position;
        this.state = State.QUEUE;
        scheduleNextTick(config.server.check());
    }

    private enum State {
        FULL, QUEUE, CONNECT
    }
}
