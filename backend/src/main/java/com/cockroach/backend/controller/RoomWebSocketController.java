package com.cockroach.backend.controller;

import com.cockroach.backend.dto.ChallengeRequest;
import com.cockroach.backend.dto.ChallengeResponse;
import com.cockroach.backend.dto.ErrorResponse;
import com.cockroach.backend.dto.PassCardRequest;
import com.cockroach.backend.dto.PeekRequest;
import com.cockroach.backend.dto.PeekResponse;
import com.cockroach.backend.dto.PlayCardRequest;
import com.cockroach.backend.exception.GameAlreadyStartedException;
import com.cockroach.backend.exception.InvalidGameStateException;
import com.cockroach.backend.exception.NotRoomHostException;
import com.cockroach.backend.exception.RoomFullException;
import com.cockroach.backend.exception.RoomNotFoundException;
import com.cockroach.backend.model.Room;
import com.cockroach.backend.service.RoomEventPublisher;
import com.cockroach.backend.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket entry point for gameplay commands. It only validates membership and delegates all
 * game rules to {@link RoomService}, then publishes events for successful state changes.
 */
@Controller
@RequiredArgsConstructor
public class RoomWebSocketController {

    private final RoomService roomService;
    private final RoomEventPublisher roomEventPublisher;

    @MessageMapping("/rooms/{roomCode}/play")
    public void playCard(
            @DestinationVariable String roomCode,
            @Payload PlayCardRequest request,
            Principal principal) {
        requireMember(roomCode, request.playerId());
        Room room = roomService.playCard(
                roomCode,
                request.playerId(),
                request.cardId(),
                request.targetPlayerId(),
                request.declaredCreature());
        roomEventPublisher.publishCardPassed(room, request.playerId());
        roomEventPublisher.publishTurnChanged(room);
    }

    @MessageMapping("/rooms/{roomCode}/challenge")
    public void resolveChallenge(
            @DestinationVariable String roomCode,
            @Payload ChallengeRequest request,
            Principal principal) {
        requireMember(roomCode, request.playerId());
        ChallengeResponse response = roomService.resolveChallenge(roomCode, request.playerId(), request.guess());
        Room room = roomService.getRoom(roomCode);
        roomEventPublisher.publishChallengeResolved(room, response);
        roomEventPublisher.publishCardCollected(room, response.recipientId(), response.actualCreature());
        if (response.loserPlayerId() != null) {
            roomEventPublisher.publishGameFinished(room);
        } else {
            roomEventPublisher.publishTurnChanged(room);
        }
    }

    @MessageMapping("/rooms/{roomCode}/peek")
    public void peekCard(
            @DestinationVariable String roomCode,
            @Payload PeekRequest request,
            Principal principal) {
        requireMember(roomCode, request.playerId());
        PeekResponse response = roomService.peekCard(roomCode, request.playerId());
        roomEventPublisher.sendPeekResult(requireUser(principal), response);
    }

    @MessageMapping("/rooms/{roomCode}/pass")
    public void passCard(
            @DestinationVariable String roomCode,
            @Payload PassCardRequest request,
            Principal principal) {
        requireMember(roomCode, request.playerId());
        Room room = roomService.passCard(roomCode, request.playerId(), request.targetPlayerId());
        roomEventPublisher.publishCardPassed(room, request.playerId());
        roomEventPublisher.publishTurnChanged(room);
    }

    @MessageExceptionHandler
    public void handleCommandException(Exception exception, Principal principal) {
        if (principal == null) {
            return;
        }
        roomEventPublisher.sendError(principal.getName(), describe(exception));
    }

    private void requireMember(String roomCode, String playerId) {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("Player id must not be blank");
        }
        Room room = roomService.getRoom(roomCode);
        if (!room.getGameState().hasPlayer(playerId)) {
            throw new IllegalArgumentException("Player is not a member of room: " + room.getCode());
        }
    }

    private String requireUser(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new IllegalStateException("No session user is available for a private message");
        }
        return principal.getName();
    }

    private ErrorResponse describe(Exception exception) {
        if (exception instanceof RoomNotFoundException) {
            return new ErrorResponse("ROOM_NOT_FOUND", exception.getMessage());
        }
        if (exception instanceof RoomFullException) {
            return new ErrorResponse("ROOM_FULL", exception.getMessage());
        }
        if (exception instanceof GameAlreadyStartedException) {
            return new ErrorResponse("GAME_ALREADY_STARTED", exception.getMessage());
        }
        if (exception instanceof NotRoomHostException) {
            return new ErrorResponse("NOT_ROOM_HOST", exception.getMessage());
        }
        if (exception instanceof InvalidGameStateException) {
            return new ErrorResponse("INVALID_GAME_STATE", exception.getMessage());
        }
        if (exception instanceof IllegalArgumentException) {
            return new ErrorResponse("INVALID_REQUEST", exception.getMessage());
        }
        return new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred");
    }
}
