package kr.rtustudio.continuous.handler;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import kr.rtustudio.continuous.Continuous;
import kr.rtustudio.continuous.configuration.QueueConfig;
import kr.rtustudio.continuous.configuration.ReconnectConfig;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class ReconnectHandler extends AbstractHandler {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ReconnectConfig config;
    private final QueueConfig queueConfig;
    private final PingOptions pingOptions;

    private State state = null;

    public ReconnectHandler(Continuous plugin, Player player, RegisteredServer server) {
        super(plugin, server);
        this.config = plugin.getReconnectConfig();
        this.queueConfig = plugin.getQueueConfig();
        this.pingOptions = PingOptions.builder()
                .timeout(Duration.ofMillis(config.server.timeout()))
                .build();
    }

    @Override
    protected void onJoin(Limbo server, LimboPlayer player) {
        Player proxyPlayer = player.getProxyPlayer();
        boolean isPriority = proxyPlayer.hasPermission("continuous.priority");
        plugin.getQueueManager().addToReconnect(proxyPlayer, targetServer, isPriority);
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
                showOfflineState(player);
            } else {
                handleOnlineServer(player, ping);
            }
        });
    }

    private void showOfflineState(Player player) {
        Title title = Title.title(
                MM.deserialize(config.offline.title().title()),
                MM.deserialize(config.offline.title().subtitle()),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(30_000), Duration.ZERO));
        player.showTitle(title);

        if (this.state != State.OFFLINE) {
            player.sendMessage(MM.deserialize(config.offline.message()));
        }
        this.state = State.OFFLINE;
        scheduleNextTick(config.server.check());
    }

    private void handleOnlineServer(Player player, ServerPing ping) {
        boolean isFull = isServerFull(player, ping);

        if (isFull) {
            // 서버가 꽉 찼을 때만 큐로 이동
            moveToQueue(player);
        } else {
            // 자리가 있으면 곧바로 연결 시도
            showConnectState(player);
        }
    }

    private void moveToQueue(Player player) {
        boolean isPriority = player.hasPermission("continuous.priority");
        plugin.getQueueManager().addFromReconnectToQueue(player, targetServer, isPriority);
        plugin.getHandlerManager().unregister(player);
        plugin.getQueue().send(player, targetServer);
    }

    private boolean isServerFull(Player player, ServerPing ping) {
        if (player.hasPermission("continuous.admin"))
            return false;
        QueueConfig.MaxPlayer mp = queueConfig.queue.maxPlayer();
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

    private enum State {
        OFFLINE, CONNECT
    }
}
