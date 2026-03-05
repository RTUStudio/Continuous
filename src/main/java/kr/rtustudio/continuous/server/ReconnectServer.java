package kr.rtustudio.continuous.server;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import kr.rtustudio.continuous.Continuous;
import kr.rtustudio.continuous.configuration.LimboConfig;
import kr.rtustudio.continuous.handler.AbstractHandler;
import kr.rtustudio.continuous.handler.ReconnectHandler;
import lombok.Getter;
import net.elytrium.limboapi.api.LimboFactory;

@Getter
public class ReconnectServer extends AbstractServer {

    public ReconnectServer(Continuous plugin, LimboFactory factory) {
        super(plugin, factory);
    }

    @Override
    protected String getLimboName() {
        return "Reconnect";
    }

    @Override
    protected LimboConfig getLimboConfig() {
        return plugin.getReconnectConfig();
    }

    @Override
    protected AbstractHandler createHandler(Player player, RegisteredServer server) {
        return new ReconnectHandler(plugin, player, server);
    }
}
