package com.cockroach.backend.controller;

import com.cockroach.backend.dto.ChallengeRequest;
import com.cockroach.backend.dto.ChallengeResponse;
import com.cockroach.backend.dto.CreateRoomRequest;
import com.cockroach.backend.dto.GameResponse;
import com.cockroach.backend.dto.JoinRoomRequest;
import com.cockroach.backend.dto.PassCardRequest;
import com.cockroach.backend.dto.PeekRequest;
import com.cockroach.backend.dto.PeekResponse;
import com.cockroach.backend.dto.PendingCardResponse;
import com.cockroach.backend.dto.PlayCardRequest;
import com.cockroach.backend.dto.PlayerViewResponse;
import com.cockroach.backend.dto.RoomJoinResponse;
import com.cockroach.backend.dto.RoomResponse;
import com.cockroach.backend.dto.StartGameRequest;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import com.cockroach.backend.service.RoomEventPublisher;
import com.cockroach.backend.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    public static final String PLAYER_TOKEN_HEADER = "X-Player-Token";

    private final RoomService roomService;
    private final RoomEventPublisher roomEventPublisher;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomJoinResponse createRoom(@RequestBody CreateRoomRequest request) {
        Room room = roomService.createRoom(request.playerName());
        Player host = room.getGameState().requirePlayer(room.getHostPlayerId());
        return RoomJoinResponse.from(room, host);
    }

    @PostMapping("/{roomCode}/join")
    public RoomJoinResponse joinRoom(@PathVariable String roomCode, @RequestBody JoinRoomRequest request) {
        Player player = roomService.joinRoom(roomCode, request.playerName());
        Room room = roomService.getRoom(roomCode);
        roomEventPublisher.publishPlayerJoined(room, player);
        return RoomJoinResponse.from(room, player);
    }

    @GetMapping("/{roomCode}")
    public RoomResponse getRoom(@PathVariable String roomCode) {
        return RoomResponse.from(roomService.getRoom(roomCode));
    }

    @GetMapping("/{roomCode}/me")
    public PlayerViewResponse getPlayerView(
            @PathVariable String roomCode,
            @RequestHeader(name = PLAYER_TOKEN_HEADER, required = false) String playerToken) {
        return roomService.getPlayerView(roomCode, playerToken);
    }

    @PostMapping("/{roomCode}/start")
    public GameResponse startGame(@PathVariable String roomCode, @RequestBody StartGameRequest request) {
        Room room = roomService.startGame(roomCode, request.playerId());
        roomEventPublisher.publishGameStarted(room);
        return GameResponse.from(room);
    }

    @PostMapping("/{roomCode}/play")
    public PendingCardResponse playCard(@PathVariable String roomCode, @RequestBody PlayCardRequest request) {
        return PendingCardResponse.from(roomService.playCard(
                roomCode,
                request.playerId(),
                request.cardId(),
                request.targetPlayerId(),
                request.declaredCreature()));
    }

    @PostMapping("/{roomCode}/challenge")
    public ChallengeResponse resolveChallenge(
            @PathVariable String roomCode,
            @RequestBody ChallengeRequest request) {
        return roomService.resolveChallenge(roomCode, request.playerId(), request.guess());
    }

    @PostMapping("/{roomCode}/peek")
    public PeekResponse peekCard(@PathVariable String roomCode, @RequestBody PeekRequest request) {
        return roomService.peekCard(roomCode, request.playerId());
    }

    @PostMapping("/{roomCode}/pass")
    public PendingCardResponse passCard(@PathVariable String roomCode, @RequestBody PassCardRequest request) {
        return PendingCardResponse.from(
                roomService.passCard(
                        roomCode, request.playerId(), request.targetPlayerId(), request.declaredCreature()));
    }
}
