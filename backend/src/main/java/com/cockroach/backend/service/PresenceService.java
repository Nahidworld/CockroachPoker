package com.cockroach.backend.service;

import com.cockroach.backend.dto.LeaveRoomResponse;
import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.enums.LeaveOutcome;
import com.cockroach.backend.exception.RoomNotFoundException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tracks which players have a live WebSocket connection. A client links its connection to a player
 * by sending the room code and the player's secret token as STOMP CONNECT headers. When a player's
 * last connection drops, the room is told, and if they do not reconnect within the grace period
 * they leave the room (which, during a game, means they forfeit).
 */
@Slf4j
@Service
public class PresenceService {

    public static final String ROOM_CODE_HEADER = "X-Room-Code";
    public static final String PLAYER_TOKEN_HEADER = "X-Player-Token";

    private final RoomService roomService;
    private final RoomEventPublisher roomEventPublisher;
    private final long graceMillis;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "presence-grace");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<String, PlayerKey> sessionOwners = new HashMap<>();
    private final Map<PlayerKey, Set<String>> sessionsByPlayer = new HashMap<>();
    private final Map<PlayerKey, ScheduledFuture<?>> pendingLeaves = new HashMap<>();

    public PresenceService(
            RoomService roomService,
            RoomEventPublisher roomEventPublisher,
            @Value("${game.disconnect-grace-millis:60000}") long graceMillis) {
        this.roomService = roomService;
        this.roomEventPublisher = roomEventPublisher;
        this.graceMillis = graceMillis;
    }

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String roomCode = accessor.getFirstNativeHeader(ROOM_CODE_HEADER);
        String token = accessor.getFirstNativeHeader(PLAYER_TOKEN_HEADER);
        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            return;
        }
        roomService.findPlayerIdByToken(roomCode, token)
                .ifPresent(playerId -> connected(sessionId, roomService.getRoom(roomCode).getCode(), playerId));
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        disconnected(event.getSessionId());
    }

    /** Links a connection to a player. Reconnecting within the grace period cancels their removal. */
    public void connected(String sessionId, String roomCode, String playerId) {
        PlayerKey key = new PlayerKey(roomCode, playerId);
        boolean cameBack;
        synchronized (this) {
            sessionOwners.put(sessionId, key);
            sessionsByPlayer.computeIfAbsent(key, k -> new HashSet<>()).add(sessionId);
            ScheduledFuture<?> pending = pendingLeaves.remove(key);
            cameBack = pending != null && pending.cancel(false);
        }
        if (cameBack) {
            announce(key, true);
        }
    }

    /** Called when a connection closes. Starts the grace period if it was the player's last one. */
    public void disconnected(String sessionId) {
        PlayerKey key;
        synchronized (this) {
            key = sessionOwners.remove(sessionId);
            if (key == null) {
                return;
            }
            Set<String> sessions = sessionsByPlayer.get(key);
            sessions.remove(sessionId);
            if (!sessions.isEmpty()) {
                return;
            }
            sessionsByPlayer.remove(key);
            if (!roomService.isActivePlayer(key.roomCode(), key.playerId())) {
                return;
            }
            pendingLeaves.put(key, scheduler.schedule(() -> expire(key), graceMillis, TimeUnit.MILLISECONDS));
        }
        announce(key, false);
    }

    public synchronized boolean isConnected(String roomCode, String playerId) {
        return sessionsByPlayer.containsKey(new PlayerKey(roomCode, playerId));
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    private void expire(PlayerKey key) {
        synchronized (this) {
            if (pendingLeaves.remove(key) == null || sessionsByPlayer.containsKey(key)) {
                return;
            }
        }
        try {
            LeaveRoomResponse leave = roomService.leaveRoom(key.roomCode(), key.playerId());
            if (leave.outcome() != LeaveOutcome.ROOM_CLOSED) {
                roomEventPublisher.publishPlayerLeft(leave, roomService.getRoom(key.roomCode()));
            }
        } catch (RoomNotFoundException | IllegalArgumentException alreadyGone) {
            // the room was deleted or the player had already left
        } catch (RuntimeException exception) {
            log.warn("Could not remove disconnected player {} from room {}", key.playerId(), key.roomCode(), exception);
        }
    }

    private void announce(PlayerKey key, boolean reconnected) {
        try {
            GameStatus status = roomService.getRoom(key.roomCode()).getStatus();
            String name = roomService.findPlayerName(key.roomCode(), key.playerId()).orElse("A player");
            if (reconnected) {
                roomEventPublisher.publishPlayerReconnected(key.roomCode(), status, key.playerId(), name);
            } else {
                roomEventPublisher.publishPlayerDisconnected(
                        key.roomCode(), status, key.playerId(), name, TimeUnit.MILLISECONDS.toSeconds(graceMillis));
            }
        } catch (RoomNotFoundException ignored) {
            // the room is gone; nobody to tell
        }
    }

    private record PlayerKey(String roomCode, String playerId) {
    }
}
