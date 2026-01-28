package kr.rtustudio.continuous.listener;

import kr.rtustudio.continuous.Continuous;
import kr.rtustudio.continuous.configuration.QueueConfig;
import kr.rtustudio.continuous.configuration.ReconnectConfig;
import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

@Slf4j
public class PlayerLogin {

    public static final PlainTextComponentSerializer SERIALIZER = PlainTextComponentSerializer.builder().flattener(
            ComponentFlattener.basic()
    ).build();

    private final Continuous plugin;

    public PlayerLogin(Continuous plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    private void onLogin(ServerPreConnectEvent e, Continuation continuation) {
        if (e.getPreviousServer() != null) {
            continuation.resume();
            return;
        }
        Player player = e.getPlayer();
        RegisteredServer server = e.getOriginalServer();
        
        server.ping().whenCompleteAsync((pong, throwable) -> {
            if (pong != null) {
                boolean hasAdmin = player.hasPermission("continuous.admin");
                boolean hasBypass = player.hasPermission("continuous.bypass");
                
                if (hasAdmin) {
                    continuation.resume();
                    return;
                }
                
                boolean isFull = false;
                QueueConfig queueConfig = plugin.getQueueConfig();
                QueueConfig.MaxPlayer mp = queueConfig.queue.maxPlayer();
                if (pong.getPlayers().isPresent()) {
                    ServerPing.Players players = pong.getPlayers().get();
                    int max = mp.enabled() ? Math.min(mp.size(), players.getMax()) : players.getMax();
                    plugin.verbose("online: " + players.getOnline() + ", max: " + max);
                    isFull = players.getOnline() >= max;
                }
                
                if (hasBypass && !isFull) {
                    continuation.resume();
                    return;
                }
                
                boolean hasQueuedPlayers = !plugin.getQueueManager().isEmpty();
                
                if (isFull || hasQueuedPlayers) {
                    e.setResult(ServerPreConnectEvent.ServerResult.denied());
                    plugin.getQueue().send(player, server);
                    if (hasQueuedPlayers && !isFull) {
                        plugin.verbose("Server has space but queue has " + plugin.getQueueManager().getTotalSize() + " players waiting. Sending " + player.getUsername() + " to queue.");
                    }
                }
            } else {
                e.setResult(ServerPreConnectEvent.ServerResult.denied());
                plugin.getReconnect().send(player, server);
            }
            continuation.resume();
        });
    }

    @Subscribe
    private void onKickedFromServer(KickedFromServerEvent e) {
        Player player = e.getPlayer();
        RegisteredServer server = e.getServer();
        
        Component component = e.getServerKickReason().orElse(Component.empty());
        String reason = SERIALIZER.serialize(component);
        plugin.verbose("Kick reason: " + reason);
        
        QueueConfig queueConfig = plugin.getQueueConfig();
        ReconnectConfig reconnectConfig = plugin.getReconnectConfig();
        
        if (player.getCurrentServer().isEmpty()) {
            if (reason.isEmpty() || reason.matches(queueConfig.trigger)) {
                e.setResult(KickedFromServerEvent.Notify.create(Component.empty()));
                plugin.getQueue().send(player, server);
                plugin.verbose("Player " + player.getUsername() + " kicked, sending to queue");
            }
        } else {
            if (reason.matches(reconnectConfig.trigger)) {
                e.setResult(KickedFromServerEvent.Notify.create(Component.empty()));
                plugin.getReconnect().send(player, server);
                plugin.verbose("Player " + player.getUsername() + " kicked, sending to reconnect");
            }
        }
    }
}
