package kr.rtustudio.continuous.listener;

import kr.rtustudio.continuous.Continuous;
import kr.rtustudio.continuous.configuration.QueueConfig;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;

public class ServerPing {

    private final Continuous plugin;

    public ServerPing(Continuous plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    private void onPing(ProxyPingEvent e) {
        QueueConfig.MaxPlayer mp = plugin.getQueueConfig().queue.maxPlayer();
        if (mp.enabled()) {
            com.velocitypowered.api.proxy.server.ServerPing.Builder pong = e.getPing().asBuilder();
            pong.maximumPlayers(mp.size());
            e.setPing(pong.build());
        }
    }
}
