package com.cockroach.backend.service;

import com.cockroach.backend.dto.ChallengeResponse;
import com.cockroach.backend.dto.LeaveRoomResponse;
import com.cockroach.backend.dto.PeekResponse;
import com.cockroach.backend.dto.PlayerViewResponse;
import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.enums.LeaveOutcome;
import com.cockroach.backend.enums.LossReason;
import com.cockroach.backend.exception.GameAlreadyStartedException;
import com.cockroach.backend.exception.InvalidGameStateException;
import com.cockroach.backend.exception.InvalidPlayerTokenException;
import com.cockroach.backend.exception.NotRoomHostException;
import com.cockroach.backend.exception.RoomFullException;
import com.cockroach.backend.exception.RoomNotFoundException;
import com.cockroach.backend.exception.ServerBusyException;
import com.cockroach.backend.model.Card;
import com.cockroach.backend.model.GameState;
import com.cockroach.backend.model.PendingCard;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RoomService {

    public static final int MAX_PLAYER_NAME_LENGTH = 16;
    public static final int MAX_ROOMS = 1000;

    private static final int ROOM_CODE_LENGTH = 6;
    private static final char[] ROOM_CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CardDistributionService cardDistributionService;
    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    public Room createRoom(String playerName) {
        String name = normalizePlayerName(playerName);
        if (rooms.size() >= MAX_ROOMS) {
            throw new ServerBusyException("The server has too many open rooms. Please try again in a few minutes.");
        }

        while (true) {
            String roomCode = generateRoomCode();
            Room room = new Room(roomCode, new Player(name));

            if (rooms.putIfAbsent(roomCode, room) == null) {
                return room;
            }
        }
    }

    public Player joinRoom(String roomCode, String playerName) {
        String name = normalizePlayerName(playerName);
        Room room = getRoom(roomCode);
        Player player = new Player(name);

        synchronized (room) {
            requireOpenRoom(room);
            if (room.getStatus() != GameStatus.WAITING) {
                throw new GameAlreadyStartedException(room.getCode());
            }
            if (room.isFull()) {
                throw new RoomFullException(room.getCode());
            }
            room.getGameState().addPlayer(player);
            room.touch();
        }

        return player;
    }

    /**
     * Removes a player from the room. Before the game starts the player simply leaves (the host role
     * passes to the next player, and an empty room is deleted). During a game, leaving is a forfeit:
     * the player loses and the game ends. After the game ends nothing changes.
     */
    public LeaveRoomResponse leaveRoom(String roomCode, String playerId) {
        validatePlayerId(playerId);
        Room room = getRoom(roomCode);

        synchronized (room) {
            GameState gameState = room.getGameState();
            Player player = gameState.findPlayer(playerId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Player is not a member of room: " + room.getCode()));

            LeaveOutcome outcome;
            switch (gameState.getStatus()) {
                case WAITING -> {
                    gameState.removePlayer(playerId);
                    if (gameState.getPlayers().isEmpty()) {
                        rooms.remove(room.getCode(), room);
                        outcome = LeaveOutcome.ROOM_CLOSED;
                    } else {
                        if (playerId.equals(room.getHostPlayerId())) {
                            room.transferHostTo(gameState.getPlayers().get(0).getId());
                        }
                        outcome = LeaveOutcome.LEFT_LOBBY;
                    }
                }
                case IN_PROGRESS -> {
                    gameState.finishGame(playerId, LossReason.LEFT_GAME);
                    outcome = LeaveOutcome.FORFEITED;
                }
                default -> outcome = LeaveOutcome.ALREADY_FINISHED;
            }
            room.touch();

            return new LeaveRoomResponse(
                    room.getCode(),
                    player.getId(),
                    player.getName(),
                    outcome,
                    outcome == LeaveOutcome.ROOM_CLOSED ? null : gameState.getStatus(),
                    outcome == LeaveOutcome.ROOM_CLOSED ? null : room.getHostPlayerId());
        }
    }

    /**
     * Returns the id of the player in the room that owns this secret token, if any. Used to link a
     * WebSocket connection to a player without trusting a player id sent by the client.
     */
    public Optional<String> findPlayerIdByToken(String roomCode, String playerToken) {
        if (roomCode == null || roomCode.isBlank() || playerToken == null || playerToken.isBlank()) {
            return Optional.empty();
        }
        Room room = rooms.get(normalizeRoomCode(roomCode));
        if (room == null) {
            return Optional.empty();
        }
        synchronized (room) {
            return room.getPlayers().stream()
                    .filter(player -> player.hasToken(playerToken))
                    .map(Player::getId)
                    .findFirst();
        }
    }

    /** True while the player is still in a room whose game has not finished. */
    public boolean isActivePlayer(String roomCode, String playerId) {
        Room room = rooms.get(normalizeRoomCode(roomCode));
        if (room == null || playerId == null) {
            return false;
        }
        synchronized (room) {
            return room.getStatus() != GameStatus.FINISHED && room.getGameState().hasPlayer(playerId);
        }
    }

    public Optional<String> findPlayerName(String roomCode, String playerId) {
        Room room = rooms.get(normalizeRoomCode(roomCode));
        if (room == null || playerId == null) {
            return Optional.empty();
        }
        synchronized (room) {
            return room.getGameState().findPlayer(playerId).map(Player::getName);
        }
    }

    /**
     * Deletes rooms nobody has used for a while: finished games after {@code finishedLimit}, and
     * waiting or in-progress rooms after {@code idleLimit}. Returns how many rooms were removed.
     */
    public int removeExpiredRooms(Instant now, Duration idleLimit, Duration finishedLimit) {
        int removed = 0;
        for (Room room : rooms.values()) {
            Duration limit = room.getStatus() == GameStatus.FINISHED ? finishedLimit : idleLimit;
            if (room.getLastActivityAt().plus(limit).isBefore(now) && rooms.remove(room.getCode(), room)) {
                removed++;
            }
        }
        return removed;
    }

    public int getRoomCount() {
        return rooms.size();
    }

    /**
     * Returns the private view for the player that owns the given secret token: their own hand
     * plus public information about every player. Player ids are public, so the token is what
     * keeps one player from reading another player's hand.
     */
    public PlayerViewResponse getPlayerView(String roomCode, String playerToken) {
        if (playerToken == null || playerToken.isBlank()) {
            throw new IllegalArgumentException("Player token must not be blank");
        }
        Room room = getRoom(roomCode);

        synchronized (room) {
            Player player = room.getPlayers().stream()
                    .filter(candidate -> candidate.hasToken(playerToken))
                    .findFirst()
                    .orElseThrow(() -> new InvalidPlayerTokenException(room.getCode()));
            return PlayerViewResponse.from(room, player);
        }
    }

    /**
     * Confirms that the caller really is {@code playerId}: the player must be in the room and the
     * secret token must be theirs. Player ids are public, so the token is what stops one player from
     * acting as another.
     */
    public void authenticate(String roomCode, String playerId, String playerToken) {
        Room room = getRoom(roomCode);
        validatePlayerId(playerId);

        synchronized (room) {
            Player player = room.getGameState().findPlayer(playerId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Player is not a member of room: " + room.getCode()));
            if (playerToken == null || playerToken.isBlank()) {
                throw new IllegalArgumentException("Player token must not be blank");
            }
            if (!player.hasToken(playerToken)) {
                throw new InvalidPlayerTokenException(room.getCode());
            }
        }
    }

    public Room startGame(String roomCode, String playerId) {
        validatePlayerId(playerId);
        Room room = getRoom(roomCode);

        synchronized (room) {
            if (!room.getHostPlayerId().equals(playerId)) {
                throw new NotRoomHostException(room.getCode());
            }
            if (room.getStatus() != GameStatus.WAITING) {
                throw new GameAlreadyStartedException(room.getCode());
            }

            int playerCount = room.getPlayers().size();
            if (playerCount < GameState.MIN_PLAYERS || playerCount > GameState.MAX_PLAYERS) {
                throw new InvalidGameStateException(
                        "A game requires between " + GameState.MIN_PLAYERS
                                + " and " + GameState.MAX_PLAYERS + " players, but room has " + playerCount);
            }

            cardDistributionService.distributeCards(room.getGameState());
            String startingPlayerId = room.getPlayers().get(0).getId();
            room.getGameState().startGame(startingPlayerId);
            room.touch();
        }

        return room;
    }

    public Room playCard(
            String roomCode,
            String playerId,
            String cardId,
            String targetPlayerId,
            CreatureType declaredCreature) {
        validatePlayerId(playerId);
        validateCardId(cardId);
        validatePlayerId(targetPlayerId);
        if (declaredCreature == null) {
            throw new IllegalArgumentException("Declared creature must not be null");
        }

        Room room = getRoom(roomCode);

        synchronized (room) {
            GameState gameState = room.getGameState();
            if (gameState.getStatus() != GameStatus.IN_PROGRESS) {
                throw new InvalidGameStateException("Game is not in progress in room: " + room.getCode());
            }
            if (gameState.hasPendingCard()) {
                throw new InvalidGameStateException(
                        "A pending card is already awaiting resolution in room: " + room.getCode());
            }
            if (!playerId.equals(gameState.getCurrentPlayerId())) {
                throw new InvalidGameStateException("It is not this player's turn in room: " + room.getCode());
            }

            Player sender = gameState.requirePlayer(playerId);
            if (!sender.hasCardInHand(cardId)) {
                throw new IllegalArgumentException("Card not found in player's hand: " + cardId);
            }
            if (playerId.equals(targetPlayerId)) {
                throw new IllegalArgumentException("A player cannot pass a card to themselves");
            }
            if (!gameState.hasPlayer(targetPlayerId)) {
                throw new IllegalArgumentException("Target player is not in the room: " + targetPlayerId);
            }

            Card card = sender.removeCardFromHand(cardId);
            PendingCard pendingCard = new PendingCard(
                    card,
                    sender.getId(),
                    sender.getId(),
                    declaredCreature,
                    targetPlayerId);
            gameState.setPendingCard(pendingCard);
            gameState.updateCurrentPlayer(targetPlayerId);
            room.touch();
        }

        return room;
    }

    public PeekResponse peekCard(String roomCode, String playerId) {
        validatePlayerId(playerId);
        Room room = getRoom(roomCode);

        synchronized (room) {
            GameState gameState = room.getGameState();
            if (gameState.getStatus() != GameStatus.IN_PROGRESS) {
                throw new InvalidGameStateException("Game is not in progress in room: " + room.getCode());
            }

            PendingCard pendingCard = gameState.getPendingCard()
                    .orElseThrow(() -> new InvalidGameStateException(
                            "No pending card to peek in room: " + room.getCode()));
            if (!playerId.equals(pendingCard.getCurrentReceiverId())) {
                throw new InvalidGameStateException(
                        "Only the current receiver can peek in room: " + room.getCode());
            }
            if (pendingCard.hasSeen(playerId)) {
                throw new InvalidGameStateException(
                        "This player has already seen the pending card in room: " + room.getCode());
            }
            if (!hasEligiblePassTarget(gameState, pendingCard, playerId)) {
                throw new InvalidGameStateException(
                        "No eligible player remains to pass to; the current receiver must challenge in room: "
                                + room.getCode());
            }

            pendingCard.markSeen(playerId);
            room.touch();
            return new PeekResponse(room.getCode(), playerId, pendingCard.getCard().getCreatureType());
        }
    }

    public Room passCard(String roomCode, String playerId, String targetPlayerId, CreatureType declaredCreature) {
        validatePlayerId(playerId);
        validatePlayerId(targetPlayerId);
        if (declaredCreature == null) {
            throw new IllegalArgumentException("Declared creature must not be null");
        }
        Room room = getRoom(roomCode);

        synchronized (room) {
            GameState gameState = room.getGameState();
            if (gameState.getStatus() != GameStatus.IN_PROGRESS) {
                throw new InvalidGameStateException("Game is not in progress in room: " + room.getCode());
            }

            PendingCard pendingCard = gameState.getPendingCard()
                    .orElseThrow(() -> new InvalidGameStateException(
                            "No pending card to pass in room: " + room.getCode()));
            if (!playerId.equals(pendingCard.getCurrentReceiverId())) {
                throw new InvalidGameStateException(
                        "Only the current receiver can pass the pending card in room: " + room.getCode());
            }
            if (!pendingCard.hasSeen(playerId)) {
                throw new InvalidGameStateException(
                        "The current receiver must peek before passing in room: " + room.getCode());
            }

            if (!hasEligiblePassTarget(gameState, pendingCard, playerId)) {
                throw new InvalidGameStateException(
                        "No eligible player remains; the current receiver must challenge in room: " + room.getCode());
            }

            if (playerId.equals(targetPlayerId)) {
                throw new IllegalArgumentException("A player cannot pass a card to themselves");
            }
            if (!gameState.hasPlayer(targetPlayerId)) {
                throw new IllegalArgumentException("Target player is not in the room: " + targetPlayerId);
            }
            if (pendingCard.hasSeen(targetPlayerId)) {
                throw new IllegalArgumentException("Target has already seen the pending card: " + targetPlayerId);
            }

            pendingCard.passTo(playerId, targetPlayerId, declaredCreature);
            gameState.updateCurrentPlayer(targetPlayerId);
            room.touch();
        }

        return room;
    }

    public ChallengeResponse resolveChallenge(String roomCode, String playerId, Boolean guess) {
        validatePlayerId(playerId);
        if (guess == null) {
            throw new IllegalArgumentException("Challenge guess must not be null");
        }

        Room room = getRoom(roomCode);

        synchronized (room) {
            GameState gameState = room.getGameState();
            if (gameState.getStatus() != GameStatus.IN_PROGRESS) {
                throw new InvalidGameStateException("Game is not in progress in room: " + room.getCode());
            }

            PendingCard pendingCard = gameState.getPendingCard()
                    .orElseThrow(() -> new InvalidGameStateException(
                            "No pending card to challenge in room: " + room.getCode()));
            if (!playerId.equals(pendingCard.getCurrentReceiverId())) {
                throw new InvalidGameStateException(
                        "Only the current receiver can challenge in room: " + room.getCode());
            }
            if (pendingCard.hasSeen(playerId)) {
                throw new InvalidGameStateException(
                        "A player who has peeked must pass the card on in room: " + room.getCode());
            }

            boolean truthful = pendingCard.getCard().getCreatureType() == pendingCard.getDeclaredCreature();
            boolean correct = guess == truthful;
            String recipientId = correct ? pendingCard.getLatestClaimantId() : playerId;

            Player recipient = gameState.requirePlayer(recipientId);
            recipient.addCollectedCard(pendingCard.getCard());

            gameState.clearPendingCard();
            gameState.startNextRound(recipientId);
            room.touch();

            return new ChallengeResponse(
                    room.getCode(),
                    gameState.getStatus(),
                    playerId,
                    correct,
                    recipientId,
                    pendingCard.getDeclaredCreature(),
                    pendingCard.getCard().getCreatureType(),
                    gameState.getCurrentPlayerId(),
                    gameState.getLoserPlayerId());
        }
    }

    public Room getRoom(String roomCode) {
        String normalizedCode = normalizeRoomCode(roomCode);
        Room room = rooms.get(normalizedCode);
        if (room == null) {
            throw new RoomNotFoundException(normalizedCode);
        }
        return room;
    }

    private boolean hasEligiblePassTarget(GameState gameState, PendingCard pendingCard, String playerId) {
        return gameState.getPlayers().stream()
                .map(Player::getId)
                .anyMatch(id -> !id.equals(playerId) && !pendingCard.hasSeen(id));
    }

    /** Trims the name and checks it is 1 to {@value #MAX_PLAYER_NAME_LENGTH} characters long. */
    private String normalizePlayerName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank");
        }
        String name = playerName.strip();
        if (name.length() > MAX_PLAYER_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Player name must be at most " + MAX_PLAYER_NAME_LENGTH + " characters");
        }
        return name;
    }

    /** Guards against a room that was deleted (for example, its last player left) after it was looked up. */
    private void requireOpenRoom(Room room) {
        if (rooms.get(room.getCode()) != room) {
            throw new RoomNotFoundException(room.getCode());
        }
    }

    private void validatePlayerId(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("Player id must not be blank");
        }
    }

    private void validateCardId(String cardId) {
        if (cardId == null || cardId.isBlank()) {
            throw new IllegalArgumentException("Card id must not be blank");
        }
    }

    private String normalizeRoomCode(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            throw new IllegalArgumentException("Room code must not be blank");
        }
        return roomCode.trim().toUpperCase(Locale.ROOT);
    }

    private String generateRoomCode() {
        StringBuilder roomCode = new StringBuilder(ROOM_CODE_LENGTH);
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            roomCode.append(ROOM_CODE_CHARACTERS[RANDOM.nextInt(ROOM_CODE_CHARACTERS.length)]);
        }
        return roomCode.toString();
    }
}
