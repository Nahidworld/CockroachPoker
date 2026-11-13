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
import com.cockroach.backend.dto.RoomResponse;
import com.cockroach.backend.dto.StartGameRequest;
import com.cockroach.backend.model.Room;
import com.cockroach.backend.service.RoomEventPublisher;
import com.cockroach.backend.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "Room management and gameplay endpoints")
public class RoomController {

    private static final String ROOM_CODE_EXAMPLE = "ABC123";

    private final RoomService roomService;
    private final RoomEventPublisher roomEventPublisher;

    @Operation(
            summary = "Create a room",
            description = "Creates a room and adds the caller as host and first player. "
                    + "No WebSocket event is published for room creation.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Room created"),
            @ApiResponse(responseCode = "400", description = "Player name is blank",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(@RequestBody CreateRoomRequest request) {
        return RoomResponse.from(roomService.createRoom(request.playerName()));
    }

    @Operation(
            summary = "Join a room",
            description = "Adds a player to a WAITING room and publishes PLAYER_JOINED "
                    + "to /topic/rooms/{roomCode}.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Player joined"),
            @ApiResponse(responseCode = "400", description = "Player name is blank",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Room is full or the game already started",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{roomCode}/join")
    public RoomResponse joinRoom(
            @Parameter(description = "6-character room code", example = ROOM_CODE_EXAMPLE)
            @PathVariable String roomCode,
            @RequestBody JoinRoomRequest request) {
        Room room = roomService.joinRoom(roomCode, request.playerName());
        roomEventPublisher.publishPlayerJoined(room, room.getPlayers().getLast());
        return RoomResponse.from(room);
    }

    @Operation(
            summary = "Get public room state",
            description = "Returns room code, player names, counts, and status. "
                    + "Never returns hands, player IDs, or collected cards.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Room found"),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{roomCode}")
    public RoomResponse getRoom(
            @Parameter(description = "6-character room code", example = ROOM_CODE_EXAMPLE)
            @PathVariable String roomCode) {
        return RoomResponse.from(roomService.getRoom(roomCode));
    }

    @Operation(
            summary = "Start the game",
            description = "Starts the game as the host, deals all 64 cards, and publishes GAME_STARTED. "
                    + "Requires 2-6 players; the host starts.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Game started"),
            @ApiResponse(responseCode = "400", description = "Player ID is blank",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not the host",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Game already started or player count is invalid",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{roomCode}/start")
    public GameResponse startGame(
            @Parameter(description = "6-character room code", example = ROOM_CODE_EXAMPLE)
            @PathVariable String roomCode,
            @RequestBody StartGameRequest request) {
        Room room = roomService.startGame(roomCode, request.playerId());
        roomEventPublisher.publishGameStarted(room);
        return GameResponse.from(room);
    }

    @Operation(
            summary = "Play a card",
            description = "Plays a card face down to another player and declares a creature type, "
                    + "truthfully or as a bluff. This endpoint does not publish WebSocket events.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Card is pending"),
            @ApiResponse(responseCode = "400", description = "Invalid IDs, card not in hand, self-target, "
                    + "or target not in room",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Not in progress, pending card exists, "
                    + "or not the player's turn",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{roomCode}/play")
    public PendingCardResponse playCard(
            @Parameter(description = "6-character room code", example = ROOM_CODE_EXAMPLE)
            @PathVariable String roomCode,
            @RequestBody PlayCardRequest request) {
        return PendingCardResponse.from(roomService.playCard(
                roomCode,
                request.playerId(),
                request.cardId(),
                request.targetPlayerId(),
                request.declaredCreature()));
    }

    @Operation(
            summary = "Challenge the declaration",
            description = "Challenges as the current receiver. guess=true means the declaration is "
                    + "truthful; guess=false calls a bluff. A correct challenge gives the card to the "
                    + "latest claimant; an incorrect one gives it to the challenger. "
                    + "This endpoint does not publish WebSocket events.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Challenge resolved"),
            @ApiResponse(responseCode = "400", description = "Player ID is blank or guess is missing",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Not in progress, no pending card, "
                    + "or caller is not the current receiver",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{roomCode}/challenge")
    public ChallengeResponse resolveChallenge(
            @Parameter(description = "6-character room code", example = ROOM_CODE_EXAMPLE)
            @PathVariable String roomCode,
            @RequestBody ChallengeRequest request) {
        return roomService.resolveChallenge(roomCode, request.playerId(), request.guess());
    }

    @Operation(
            summary = "Peek at the pending card",
            description = "Secretly inspects the pending card as the current receiver. "
                    + "A receiver may peek only once per card. This endpoint does not publish "
                    + "WebSocket events.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Card revealed to the caller"),
            @ApiResponse(responseCode = "400", description = "Player ID is blank",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Not in progress, no pending card, "
                    + "caller is not the receiver, or already peeked",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{roomCode}/peek")
    public PeekResponse peekCard(
            @Parameter(description = "6-character room code", example = ROOM_CODE_EXAMPLE)
            @PathVariable String roomCode,
            @RequestBody PeekRequest request) {
        return roomService.peekCard(roomCode, request.playerId());
    }

    @Operation(
            summary = "Pass the pending card",
            description = "Passes the pending card as the current receiver. The receiver must have "
                    + "peeked, and the target must not have seen the card. If no eligible target "
                    + "remains, the receiver must challenge. This endpoint does not publish "
                    + "WebSocket events.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Card passed"),
            @ApiResponse(responseCode = "400", description = "Invalid IDs, self-target, target not in room, "
                    + "or target already saw the card",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Room not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Not in progress, no pending card, "
                    + "caller is not the receiver, caller has not peeked, or no eligible target exists",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{roomCode}/pass")
    public PendingCardResponse passCard(
            @Parameter(description = "6-character room code", example = ROOM_CODE_EXAMPLE)
            @PathVariable String roomCode,
            @RequestBody PassCardRequest request) {
        return PendingCardResponse.from(
                roomService.passCard(roomCode, request.playerId(), request.targetPlayerId()));
    }
}
