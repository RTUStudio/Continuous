package kr.rtustudio.continuous.manager;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import kr.rtustudio.continuous.Continuous;
import lombok.Getter;

import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class QueueManager {

    private final Continuous plugin;

    private final Map<String, ServerQueue> serverQueues = new ConcurrentHashMap<>();
    @Getter
    private final Map<UUID, QueueEntry> playerEntries = new ConcurrentHashMap<>();
    @Getter
    private final Set<UUID> passingPlayers = ConcurrentHashMap.newKeySet();

    public QueueManager(Continuous plugin) {
        this.plugin = plugin;
    }

    private ServerQueue getQueueForServer(RegisteredServer server) {
        return serverQueues.computeIfAbsent(server.getServerInfo().getName(), k -> new ServerQueue());
    }

    public void addToReconnect(Player player, RegisteredServer targetServer, boolean isPriority) {
        UUID uuid = player.getUniqueId();
        removeFromQueue(uuid);

        QueueEntry entry = new QueueEntry(player, targetServer, isPriority, QueueType.RECONNECT,
                System.currentTimeMillis());
        playerEntries.put(uuid, entry);
        getQueueForServer(targetServer).addToQueueWithPriority(ServerQueue.Type.RECONNECT, entry, isPriority);

        plugin.verbose("Added " + player.getUsername() + " to reconnect queue for "
                + targetServer.getServerInfo().getName() + (isPriority ? " (priority)" : ""));
    }

    public void addFromReconnectToQueue(Player player, RegisteredServer targetServer, boolean isPriority) {
        UUID uuid = player.getUniqueId();
        removeFromQueue(uuid);

        QueueEntry entry = new QueueEntry(player, targetServer, isPriority, QueueType.EXISTING,
                System.currentTimeMillis());
        playerEntries.put(uuid, entry);
        getQueueForServer(targetServer).addToQueueWithPriority(ServerQueue.Type.EXISTING, entry, isPriority);

        plugin.verbose("Added " + player.getUsername() + " to existing queue for "
                + targetServer.getServerInfo().getName() + " (from reconnect)" + (isPriority ? " (priority)" : ""));
    }

    public void addToNewQueue(Player player, RegisteredServer targetServer, boolean isPriority) {
        UUID uuid = player.getUniqueId();
        removeFromQueue(uuid);

        QueueEntry entry = new QueueEntry(player, targetServer, isPriority, QueueType.NEW, System.currentTimeMillis());
        playerEntries.put(uuid, entry);
        getQueueForServer(targetServer).addToQueueWithPriority(ServerQueue.Type.NEW, entry, isPriority);

        plugin.verbose("Added " + player.getUsername() + " to new queue for " + targetServer.getServerInfo().getName()
                + (isPriority ? " (priority)" : ""));
    }

    public void moveQueueToReconnect(Player player, RegisteredServer targetServer) {
        UUID uuid = player.getUniqueId();
        QueueEntry existing = playerEntries.get(uuid);
        boolean isPriority = existing != null && existing.priority();

        removeFromQueue(uuid);

        QueueEntry entry = new QueueEntry(player, targetServer, isPriority, QueueType.RECONNECT,
                System.currentTimeMillis());
        playerEntries.put(uuid, entry);
        getQueueForServer(targetServer).addToQueueWithPriority(ServerQueue.Type.RECONNECT, entry, isPriority);

        plugin.verbose("Moved " + player.getUsername() + " from queue to reconnect for "
                + targetServer.getServerInfo().getName() + (isPriority ? " (priority)" : ""));
    }

    public void removeFromQueue(Player player) {
        removeFromQueue(player.getUniqueId());
    }

    public void removeFromQueue(UUID uuid) {
        QueueEntry entry = playerEntries.remove(uuid);
        if (entry != null) {
            getQueueForServer(entry.targetServer()).remove(entry);
            plugin.verbose("Removed player from queue");
        }
    }

    public void addPassingPlayer(Player player) {
        passingPlayers.add(player.getUniqueId());
    }

    public boolean removePassingPlayer(Player player) {
        return passingPlayers.remove(player.getUniqueId());
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
        if (entry == null)
            return -1;

        return getQueueForServer(entry.targetServer()).getPosition(uuid);
    }

    public int getTotalSize() {
        return serverQueues.values().stream().mapToInt(ServerQueue::getTotalSize).sum();
    }

    public int getQueuedPlayersCountForServer(RegisteredServer server) {
        ServerQueue queue = serverQueues.get(server.getServerInfo().getName());
        return queue != null ? queue.getTotalSize() : 0;
    }

    public boolean isEmpty() {
        return serverQueues.values().stream().allMatch(ServerQueue::isEmpty);
    }

    public boolean hasQueuedPlayersForServer(RegisteredServer server) {
        ServerQueue queue = serverQueues.get(server.getServerInfo().getName());
        return queue != null && !queue.isEmpty();
    }

    public enum QueueType {
        RECONNECT,
        EXISTING,
        NEW
    }

    public record QueueEntry(Player player, RegisteredServer targetServer, boolean priority, QueueType queueType,
                             long joinTime) {
    }

    // A helper class to group queues by target server
    public static class ServerQueue {
        private final Deque<QueueEntry> reconnectQueue = new ConcurrentLinkedDeque<>();
        private final Deque<QueueEntry> existingQueue = new ConcurrentLinkedDeque<>();
        private final Deque<QueueEntry> newQueue = new ConcurrentLinkedDeque<>();

        public void addToQueueWithPriority(Type type, QueueEntry entry, boolean isPriority) {
            Deque<QueueEntry> queue = switch (type) {
                case RECONNECT -> reconnectQueue;
                case EXISTING -> existingQueue;
                case NEW -> newQueue;
            };

            if (isPriority) {
                QueueEntry[] entries = queue.toArray(new QueueEntry[0]);
                int insertIndex = 0;
                for (int i = 0; i < entries.length; i++) {
                    if (entries[i].priority()) {
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

        public void remove(QueueEntry entry) {
            reconnectQueue.remove(entry);
            existingQueue.remove(entry);
            newQueue.remove(entry);
        }

        public int getPosition(UUID uuid) {
            int position = 0;
            for (QueueEntry e : reconnectQueue) {
                position++;
                if (e.player().getUniqueId().equals(uuid))
                    return position;
            }
            for (QueueEntry e : existingQueue) {
                position++;
                if (e.player().getUniqueId().equals(uuid))
                    return position;
            }
            for (QueueEntry e : newQueue) {
                position++;
                if (e.player().getUniqueId().equals(uuid))
                    return position;
            }
            return -1;
        }

        public int getTotalSize() {
            return reconnectQueue.size() + existingQueue.size() + newQueue.size();
        }

        public boolean isEmpty() {
            return reconnectQueue.isEmpty() && existingQueue.isEmpty() && newQueue.isEmpty();
        }

        public enum Type {
            RECONNECT, EXISTING, NEW
        }
    }
}
