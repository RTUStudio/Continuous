package kr.rtustudio.continuous.manager;

import kr.rtustudio.continuous.Continuous;
import kr.rtustudio.continuous.handler.AbstractHandler;
import com.velocitypowered.api.proxy.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HandlerManager {

    private final Continuous plugin;
    private final Map<UUID, AbstractHandler> handlers = new ConcurrentHashMap<>();

    public HandlerManager(Continuous plugin) {
        this.plugin = plugin;
    }

    public void register(Player player, AbstractHandler handler) {
        UUID uuid = player.getUniqueId();
        AbstractHandler existing = handlers.remove(uuid);
        if (existing != null) {
            existing.stop();
        }
        handlers.put(uuid, handler);
    }

    public void unregister(Player player) {
        unregister(player.getUniqueId());
    }

    public void unregister(UUID uuid) {
        AbstractHandler handler = handlers.remove(uuid);
        if (handler != null) {
            handler.stop();
        }
    }

    public AbstractHandler getHandler(Player player) {
        return handlers.get(player.getUniqueId());
    }

    public AbstractHandler getHandler(UUID uuid) {
        return handlers.get(uuid);
    }

    public boolean hasHandler(Player player) {
        return handlers.containsKey(player.getUniqueId());
    }

    public void clear() {
        handlers.values().forEach(AbstractHandler::stop);
        handlers.clear();
    }
}
