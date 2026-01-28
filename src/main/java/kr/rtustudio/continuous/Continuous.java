package kr.rtustudio.continuous;

import com.velocitypowered.api.plugin.PluginContainer;
import kr.rtustudio.configurate.Configuration;
import kr.rtustudio.continuous.command.ReloadCommand;
import kr.rtustudio.continuous.configuration.QueueConfig;
import kr.rtustudio.continuous.configuration.ReconnectConfig;
import kr.rtustudio.continuous.configuration.SettingConfig;
import kr.rtustudio.continuous.listener.PlayerLogin;
import kr.rtustudio.continuous.listener.ServerPing;
import kr.rtustudio.continuous.manager.HandlerManager;
import kr.rtustudio.continuous.manager.QueueManager;
import kr.rtustudio.continuous.server.QueueServer;
import kr.rtustudio.continuous.server.ReconnectServer;
import net.elytrium.limboapi.api.LimboFactory;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.nio.file.Path;

@Slf4j(topic = "Continuous")
public class Continuous {

    @Getter
    private final ProxyServer server;
    @Getter
    private final Path dir;
    private final CommandMeta commandMeta;

    private final Configuration<SettingConfig> settingConfiguration;
    private final Configuration<QueueConfig> queueConfiguration;
    private final Configuration<ReconnectConfig> reconnectConfiguration;

    @Getter
    private final SettingConfig settingConfig;
    @Getter
    private final QueueConfig queueConfig;
    @Getter
    private final ReconnectConfig reconnectConfig;

    @Getter
    private final QueueManager queueManager;
    @Getter
    private final HandlerManager handlerManager;
    @Getter
    private ReconnectServer reconnect;
    @Getter
    private QueueServer queue;

    @Inject
    public Continuous(ProxyServer server, @DataDirectory Path dir) {
        this.server = server;
        this.commandMeta = server.getCommandManager().metaBuilder("continuous").plugin(this).build();
        this.dir = dir;

        this.settingConfiguration = new Configuration<>(SettingConfig.class, dir.resolve("config.yml"));
        this.queueConfiguration = new Configuration<>(QueueConfig.class, dir.resolve("queue.yml"));
        this.reconnectConfiguration = new Configuration<>(ReconnectConfig.class, dir.resolve("reconnect.yml"));

        this.settingConfig = settingConfiguration.load();
        this.queueConfig = queueConfiguration.load();
        this.reconnectConfig = reconnectConfiguration.load();

        this.queueManager = new QueueManager(this);
        this.handlerManager = new HandlerManager(this);
    }

    public Logger getLogger() {
        return log;
    }

    public void verbose(String message) {
        if (settingConfig.debug) {
            log.info(message);
        }
    }

    @Subscribe
    private void onInitialize(ProxyInitializeEvent event) {
        LimboFactory limboFactory = (LimboFactory) server.getPluginManager()
                .getPlugin("limboapi")
                .flatMap(PluginContainer::getInstance)
                .orElseThrow(() -> new RuntimeException("LimboAPI not found!"));
        
        reconnect = new ReconnectServer(this, limboFactory);
        queue = new QueueServer(this, limboFactory);

        server.getEventManager().register(this, new PlayerLogin(this));
        server.getEventManager().register(this, new ServerPing(this));

        server.getCommandManager().register(commandMeta, new ReloadCommand(this));
        
        log.info("Continuous plugin enabled!");
    }

    @Subscribe
    private void onDisconnect(DisconnectEvent event) {
        handlerManager.unregister(event.getPlayer());
        queueManager.removeFromQueue(event.getPlayer());
    }

    public void reload() {
        settingConfiguration.reload(settingConfig);
        queueConfiguration.reload(queueConfig);
        reconnectConfiguration.reload(reconnectConfig);

        queue.reload();
        reconnect.reload();
    }
}
