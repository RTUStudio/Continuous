package kr.rtustudio.continuous.server;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import kr.rtustudio.continuous.Continuous;
import kr.rtustudio.continuous.configuration.LimboConfig;
import kr.rtustudio.continuous.handler.AbstractHandler;
import kr.rtustudio.continuous.handler.QueueHandler;
import lombok.Getter;
import net.elytrium.limboapi.api.LimboFactory;

@Getter
public class QueueServer extends AbstractServer {

    public QueueServer(Continuous plugin, LimboFactory factory) {
        super(plugin, factory);
    }

    @Override
    protected String getLimboName() {
        return "Queue";
    }

    @Override
    protected LimboConfig getLimboConfig() {
        return plugin.getQueueConfig();
    }

    @Override
    protected AbstractHandler createHandler(Player player, RegisteredServer server) {
        return new QueueHandler(plugin, player, server);
    }
}
