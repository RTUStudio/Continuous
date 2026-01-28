package kr.rtustudio.continuous.manager;

import kr.rtustudio.continuous.Continuous;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.Getter;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class QueueManager {

    private final Continuous plugin;

    @Getter
    private final Deque<QueueEntry> reconnectQueue = new ConcurrentLinkedDeque<>();
    @Getter
    private final Deque<QueueEntry> existingQueue = new ConcurrentLinkedDeque<>();
    @Getter
    private final Deque<QueueEntry> newQueue = new ConcurrentLinkedDeque<>();
    @Getter
    private final Map<UUID, QueueEntry> playerEntries = new ConcurrentHashMap<>();

    public QueueManager(Continuous plugin) {
        this.plugin = plugin;
    }

    public void addToReconnect(Player player, RegisteredServer targetServer, boolean isPriority) {
        UUID uuid = player.getUniqueId();
        removeFromQueue(uuid);
        
        QueueEntry entry = new QueueEntry(player, targetServer, isPriority, QueueType.RECONNECT, System.currentTimeMillis());
        playerEntries.put(uuid, entry);
        addToQueueWithPriority(reconnectQueue, entry, isPriority);

        plugin.verbose("Added " + player.getUsername() + " to reconnect queue" + (isPriority ? " (priority)" : ""));
    }

    public void addFromReconnectToQueue(Player player, RegisteredServer targetServer, boolean isPriority) {
        UUID uuid = player.getUniqueId();
        removeFromQueue(uuid);
        
        QueueEntry entry = new QueueEntry(player, targetServer, isPriority, QueueType.EXISTING, System.currentTimeMillis());
        playerEntries.put(uuid, entry);
        addToQueueWithPriority(existingQueue, entry, isPriority);

        plugin.verbose("Added " + player.getUsername() + " to existing queue (from reconnect)" + (isPriority ? " (priority)" : ""));
    }

    public void addToNewQueue(Player player, RegisteredServer targetServer, boolean isPriority) {
        UUID uuid = player.getUniqueId();
        removeFromQueue(uuid);
        
        QueueEntry entry = new QueueEntry(player, targetServer, isPriority, QueueType.NEW, System.currentTimeMillis());
        playerEntries.put(uuid, entry);
        addToQueueWithPriority(newQueue, entry, isPriority);

        plugin.verbose("Added " + player.getUsername() + " to new queue" + (isPriority ? " (priority)" : ""));
    }

    public void moveQueueToReconnect(Player player, RegisteredServer targetServer) {
        UUID uuid = player.getUniqueId();
        QueueEntry existing = playerEntries.get(uuid);
        boolean isPriority = existing != null && existing.isPriority();
        
        removeFromQueue(uuid);
        
        QueueEntry entry = new QueueEntry(player, targetServer, isPriority, QueueType.RECONNECT, System.currentTimeMillis());
        playerEntries.put(uuid, entry);
        addToQueueWithPriority(reconnectQueue, entry, isPriority);

        plugin.verbose("Moved " + player.getUsername() + " from queue to reconnect" + (isPriority ? " (priority)" : ""));
    }

    private void addToQueueWithPriority(Deque<QueueEntry> queue, QueueEntry entry, boolean isPriority) {
        if (isPriority) {
            QueueEntry[] entries = queue.toArray(new QueueEntry[0]);
            int insertIndex = 0;
            for (int i = 0; i < entries.length; i++) {
                if (entries[i].isPriority()) {
                    insertIndex = i + 1;
                } else {
                    break;
                }
            }
            queue.clear();
            for (int i = 0; i < insertIndex; i++) {
                queue.addLast(entries[i]);
            }
            queue.addLast(entry);
            for (int i = insertIndex; i < entries.length; i++) {
                queue.addLast(entries[i]);
            }
        } else {
            queue.addLast(entry);
        }
    }

    public void removeFromQueue(Player player) {
        removeFromQueue(player.getUniqueId());
    }

    public void removeFromQueue(UUID uuid) {
        QueueEntry entry = playerEntries.remove(uuid);
        if (entry != null) {
            reconnectQueue.remove(entry);
            existingQueue.remove(entry);
            newQueue.remove(entry);
            plugin.verbose("Removed player from queue");
        }
    }

    public QueueEntry getEntry(Player player) {
        return playerEntries.get(player.getUniqueId());
    }

    public QueueEntry getEntry(UUID uuid) {
        return playerEntries.get(uuid);
    }

    public int getPosition(Player player) {
        return getPosition(player.getUniqueId());
    }

    public int getPosition(UUID uuid) {
        QueueEntry entry = playerEntries.get(uuid);
        if (entry == null) return -1;

        int position = 0;
        for (QueueEntry e : reconnectQueue) {
            position++;
            if (e.getPlayer().getUniqueId().equals(uuid)) {
                return position;
            }
        }
        for (QueueEntry e : existingQueue) {
            position++;
            if (e.getPlayer().getUniqueId().equals(uuid)) {
                return position;
            }
        }
        for (QueueEntry e : newQueue) {
            position++;
            if (e.getPlayer().getUniqueId().equals(uuid)) {
                return position;
            }
        }
        return -1;
    }

    public QueueEntry pollNext() {
        QueueEntry entry = reconnectQueue.pollFirst();
        if (entry != null) {
            playerEntries.remove(entry.getPlayer().getUniqueId());
            return entry;
        }
        entry = existingQueue.pollFirst();
        if (entry != null) {
            playerEntries.remove(entry.getPlayer().getUniqueId());
            return entry;
        }
        entry = newQueue.pollFirst();
        if (entry != null) {
            playerEntries.remove(entry.getPlayer().getUniqueId());
        }
        return entry;
    }

    public int getTotalSize() {
        return reconnectQueue.size() + existingQueue.size() + newQueue.size();
    }

    public boolean isEmpty() {
        return reconnectQueue.isEmpty() && existingQueue.isEmpty() && newQueue.isEmpty();
    }

    public enum QueueType {
        RECONNECT,
        EXISTING,
        NEW
    }

    @Getter
    public static class QueueEntry {
        private final Player player;
        private final RegisteredServer targetServer;
        private final boolean priority;
        private final QueueType queueType;
        private final long joinTime;

        public QueueEntry(Player player, RegisteredServer targetServer, boolean priority, QueueType queueType, long joinTime) {
            this.player = player;
            this.targetServer = targetServer;
            this.priority = priority;
            this.queueType = queueType;
            this.joinTime = joinTime;
        }
    }
}
