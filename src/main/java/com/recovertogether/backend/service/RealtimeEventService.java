package com.recovertogether.backend.service;

import com.recovertogether.backend.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-Sent Events registry for foreground live updates (new chat messages,
 * notifications, SOS) on mobile clients. Connections are keyed by user id.
 *
 * SSE is complementary to FCM push: the mobile app keeps an SSE stream while
 * in the foreground and relies on push notifications while backgrounded.
 */
@Service
public class RealtimeEventService
{
    private static final Logger log = LoggerFactory.getLogger(RealtimeEventService.class);

    /** Reconnect window for mobile clients before the emitter times out. */
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(User user)
    {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);

        emitter.onCompletion(() -> unsubscribe(user.getId(), emitter));
        emitter.onTimeout(() ->
        {
            unsubscribe(user.getId(), emitter);
            emitter.complete();
        });
        emitter.onError(error -> unsubscribe(user.getId(), emitter));

        emitters.computeIfAbsent(user.getId(), id -> new CopyOnWriteArrayList<>()).add(emitter);

        try
        {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("user", user.getEmail())));
        }
        catch (IOException e)
        {
            unsubscribe(user.getId(), emitter);
        }

        return emitter;
    }

    /**
     * Publishes an event to every live connection of the user. Dead emitters
     * are pruned; publishing to a user with no connections is a no-op.
     */
    public void publish(Long userId, String event, Object payload)
    {
        if (userId == null)
        {
            return;
        }

        List<SseEmitter> connections = emitters.get(userId);
        if (connections == null || connections.isEmpty())
        {
            return;
        }

        for (SseEmitter emitter : connections)
        {
            try
            {
                synchronized (emitter)
                {
                    emitter.send(SseEmitter.event().name(event).data(payload));
                }
            }
            catch (Exception e)
            {
                unsubscribe(userId, emitter);
                try
                {
                    emitter.complete();
                }
                catch (Exception ignored)
                {
                    // Already closed by the container.
                }
            }
        }
    }

    public void unsubscribe(Long userId, SseEmitter emitter)
    {
        List<SseEmitter> connections = emitters.get(userId);
        if (connections == null)
        {
            return;
        }

        connections.remove(emitter);
        if (connections.isEmpty())
        {
            emitters.remove(userId, connections);
        }
    }

    public int numberOfConnections(Long userId)
    {
        List<SseEmitter> connections = emitters.get(userId);
        return connections == null ? 0 : connections.size();
    }

    /** Keepalive so proxies/mobile radios do not drop idle SSE streams. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat()
    {
        emitters.forEach((userId, connections) ->
        {
            for (SseEmitter emitter : connections)
            {
                try
                {
                    synchronized (emitter)
                    {
                        emitter.send(SseEmitter.event().comment("keepalive"));
                    }
                }
                catch (Exception e)
                {
                    log.debug("Dropping stale SSE connection for user {}: {}", userId, e.getMessage());
                    unsubscribe(userId, emitter);
                }
            }
        });
    }
}
