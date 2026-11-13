package com.cockroach.backend.service;

import com.cockroach.backend.dto.CardResponse;
import com.cockroach.backend.dto.PlayerSummaryResponse;
import com.cockroach.backend.dto.PlayerViewResponse;
import com.cockroach.backend.enums.CreatureType;
import com.cockroach.backend.enums.GameStatus;
import com.cockroach.backend.exception.GameAlreadyStartedException;
import com.cockroach.backend.exception.InvalidGameStateException;
import com.cockroach.backend.exception.InvalidPlayerTokenException;
import com.cockroach.backend.exception.NotRoomHostException;
import com.cockroach.backend.exception.RoomFullException;
import com.cockroach.backend.exception.RoomNotFoundException;
import com.cockroach.backend.model.Card;
import com.cockroach.backend.model.Player;
import com.cockroach.backend.model.Room;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomServiceTests {

    private final RoomService roomService = new RoomService(new CardDistributionService(new CardService()));

    @Test
    void createRoomCreatesWaitingRoomWithCreatorAsHost() {
        Room room = roomService.createRoom("Alice");

        assertTrue(room.getCode().matches("[A-Z0-9]{6}"));
        assertEquals(GameStatus.WAITING, room.getStatus());
        assertEquals(1, room.getPlayers().size());

        Player host = room.getPlayers().get(0);
        assertEquals("Alice", host.getName());
        assertEquals(host.getId(), room.getHostPlayerId());
        assertSame(room, roomService.getRoom(room.getCode()));
    }

    @Test
    void roomCodesAreUnique() {
        Set<String> roomCodes = new HashSet<>();

        for (int i = 0; i < 200; i++) {
            roomCodes.add(roomService.createRoom("Player " + i).getCode());
        }

        assertEquals(200, roomCodes.size());
    }

    @Test
    void joinRoomAddsPlayerToWaitingRoom() {
        Room room = roomService.createRoom("Alice");

        Player joinedPlayer = roomService.joinRoom(room.getCode(), "Bob");

        assertEquals("Bob", joinedPlayer.getName());
        assertSame(joinedPlayer, room.getPlayers().get(1));
        assertEquals(2, room.getPlayers().size());
        assertEquals(List.of("Alice", "Bob"), room.getPlayers().stream()
                .map(Player::getName)
                .toList());
    }

    @Test
    void getPlayerViewReturnsOnlyTheTokenOwnersHand() {
        Room room = createWaitingRoom(2);
        roomService.startGame(room.getCode(), room.getHostPlayerId());
        Player alice = room.getPlayers().get(0);
        Player bob = room.getPlayers().get(1);

        PlayerViewResponse view = roomService.getPlayerView(room.getCode(), bob.getToken());

        assertEquals(bob.getId(), view.playerId());
        assertEquals(room.getHostPlayerId(), view.hostPlayerId());
        assertEquals(alice.getId(), view.currentPlayerId());
        assertEquals(GameStatus.IN_PROGRESS, view.status());
        assertEquals(bob.getHand().stream().map(Card::getId).toList(),
                view.hand().stream().map(CardResponse::id).toList());
        assertEquals(bob.getHand().stream().map(Card::getCreatureType).toList(),
                view.hand().stream().map(CardResponse::creatureType).toList());
    }

    @Test
    void getPlayerViewListsEveryPlayerWithPublicInformationOnly() {
        Room room = createWaitingRoom(3);
        roomService.startGame(room.getCode(), room.getHostPlayerId());
        Player first = room.getPlayers().get(0);
        first.addCollectedCard(new Card(CreatureType.RAT));

        PlayerViewResponse view = roomService.getPlayerView(room.getCode(), first.getToken());

        assertEquals(room.getPlayers().stream().map(Player::getId).toList(),
                view.players().stream().map(PlayerSummaryResponse::id).toList());
        assertEquals(List.of("Player 1", "Player 2", "Player 3"),
                view.players().stream().map(PlayerSummaryResponse::name).toList());
        assertEquals(List.of(22, 21, 21),
                view.players().stream().map(PlayerSummaryResponse::handCount).toList());
        assertEquals(List.of(CreatureType.RAT), view.players().get(0).collectedCards());
        assertTrue(view.players().get(1).collectedCards().isEmpty());
    }

    @Test
    void getPlayerViewWorksBeforeTheGameStarts() {
        Room room = createWaitingRoom(2);
        Player host = room.getPlayers().get(0);

        PlayerViewResponse view = roomService.getPlayerView(room.getCode(), host.getToken());

        assertEquals(GameStatus.WAITING, view.status());
        assertEquals(host.getId(), view.hostPlayerId());
        assertTrue(view.hand().isEmpty());
        assertEquals(2, view.players().size());
    }

    @Test
    void getPlayerViewRejectsUnknownToken() {
        Room room = createWaitingRoom(2);

        assertThrows(InvalidPlayerTokenException.class,
                () -> roomService.getPlayerView(room.getCode(), "not-a-token"));
    }

    @Test
    void getPlayerViewRejectsPlayerIdUsedAsToken() {
        Room room = createWaitingRoom(2);
        String playerId = room.getPlayers().get(1).getId();

        assertThrows(InvalidPlayerTokenException.class,
                () -> roomService.getPlayerView(room.getCode(), playerId));
    }

    @Test
    void getPlayerViewRejectsTokenFromAnotherRoom() {
        Room room = createWaitingRoom(2);
        Room otherRoom = createWaitingRoom(2);
        String otherToken = otherRoom.getPlayers().get(0).getToken();

        assertThrows(InvalidPlayerTokenException.class,
                () -> roomService.getPlayerView(room.getCode(), otherToken));
    }

    @Test
    void getPlayerViewRejectsBlankTokenAndUnknownRoom() {
        Room room = createWaitingRoom(2);
        String token = room.getPlayers().get(0).getToken();

        assertThrows(IllegalArgumentException.class, () -> roomService.getPlayerView(room.getCode(), null));
        assertThrows(IllegalArgumentException.class, () -> roomService.getPlayerView(room.getCode(), "  "));
        assertThrows(RoomNotFoundException.class, () -> roomService.getPlayerView("ZZZZZZ", token));
    }

    @Test
    void getRoomRejectsNonexistentRoom() {
        assertThrows(RoomNotFoundException.class, () -> roomService.getRoom("ZZZZZZ"));
    }

    @Test
    void joinRoomRejectsNonexistentRoom() {
        assertThrows(RoomNotFoundException.class, () -> roomService.joinRoom("ZZZZZZ", "Alice"));
    }

    @Test
    void joinRoomRejectsFullRoom() {
        Room room = roomService.createRoom("Player 1");
        for (int i = 2; i <= 6; i++) {
            roomService.joinRoom(room.getCode(), "Player " + i);
        }

        assertTrue(room.isFull());
        assertThrows(RoomFullException.class, () -> roomService.joinRoom(room.getCode(), "Player 7"));
        assertEquals(6, room.getPlayers().size());
    }

    @Test
    void joinRoomRejectsStartedRoom() {
        Room room = roomService.createRoom("Alice");
        roomService.joinRoom(room.getCode(), "Bob");
        room.getGameState().startGame(room.getHostPlayerId());

        assertEquals(GameStatus.IN_PROGRESS, room.getStatus());
        assertThrows(GameAlreadyStartedException.class,
                () -> roomService.joinRoom(room.getCode(), "Carol"));
        assertEquals(2, room.getPlayers().size());
    }

    @Test
    void createRoomRejectsBlankPlayerNames() {
        assertThrows(IllegalArgumentException.class, () -> roomService.createRoom(null));
        assertThrows(IllegalArgumentException.class, () -> roomService.createRoom(""));
        assertThrows(IllegalArgumentException.class, () -> roomService.createRoom("   "));
    }

    @Test
    void joinRoomRejectsBlankPlayerNames() {
        Room room = roomService.createRoom("Alice");

        assertThrows(IllegalArgumentException.class, () -> roomService.joinRoom(room.getCode(), null));
        assertThrows(IllegalArgumentException.class, () -> roomService.joinRoom(room.getCode(), ""));
        assertThrows(IllegalArgumentException.class, () -> roomService.joinRoom(room.getCode(), "   "));
    }

    @Test
    void startGameByHostDistributesCardsAndStartsGame() {
        Room room = createWaitingRoom(2);
        String hostPlayerId = room.getHostPlayerId();

        Room startedRoom = roomService.startGame(room.getCode(), hostPlayerId);

        assertSame(room, startedRoom);
        assertEquals(GameStatus.IN_PROGRESS, room.getStatus());
        assertEquals(hostPlayerId, room.getGameState().getCurrentPlayerId());
        assertEquals(List.of(32, 32), handSizes(room));
    }

    @ParameterizedTest
    @MethodSource("distributionCases")
    void startGameDistributesCardsCorrectlyForSupportedPlayerCounts(
            int playerCount, int[] expectedHandSizes) {
        Room room = createWaitingRoom(playerCount);

        roomService.startGame(room.getCode(), room.getHostPlayerId());

        List<Player> players = room.getPlayers();
        List<Card> distributedCards = new ArrayList<>();
        Set<String> cardIds = new HashSet<>();
        for (int i = 0; i < players.size(); i++) {
            assertEquals(expectedHandSizes[i], players.get(i).getHand().size(),
                    "Unexpected hand size for player " + (i + 1));
            for (Card card : players.get(i).getHand()) {
                distributedCards.add(card);
                cardIds.add(card.getId());
            }
        }

        assertEquals(64, distributedCards.size());
        assertEquals(64, cardIds.size());
    }

    @Test
    void startGameRejectsNonHost() {
        Room room = createWaitingRoom(2);
        String nonHostPlayerId = room.getPlayers().get(1).getId();

        assertThrows(NotRoomHostException.class,
                () -> roomService.startGame(room.getCode(), nonHostPlayerId));
        assertEquals(GameStatus.WAITING, room.getStatus());
        assertTrue(allHandsEmpty(room));
    }

    @Test
    void startGameRejectsUnknownPlayerAsNonHost() {
        Room room = createWaitingRoom(2);

        assertThrows(NotRoomHostException.class,
                () -> roomService.startGame(room.getCode(), "unknown-player"));
        assertEquals(GameStatus.WAITING, room.getStatus());
    }

    @Test
    void startGameRejectsRoomWithFewerThanTwoPlayers() {
        Room room = roomService.createRoom("Alice");

        assertThrows(InvalidGameStateException.class,
                () -> roomService.startGame(room.getCode(), room.getHostPlayerId()));
        assertEquals(GameStatus.WAITING, room.getStatus());
        assertTrue(allHandsEmpty(room));
    }

    @Test
    void startGameRejectsAlreadyStartedGame() {
        Room room = createWaitingRoom(2);
        roomService.startGame(room.getCode(), room.getHostPlayerId());

        assertThrows(GameAlreadyStartedException.class,
                () -> roomService.startGame(room.getCode(), room.getHostPlayerId()));
        assertEquals(64, collectCards(room).size());
    }

    @Test
    void startGameRejectsUnknownRoom() {
        assertThrows(RoomNotFoundException.class,
                () -> roomService.startGame("ZZZZZZ", "player-id"));
    }

    @Test
    void startGameRejectsBlankPlayerId() {
        Room room = createWaitingRoom(2);

        assertThrows(IllegalArgumentException.class,
                () -> roomService.startGame(room.getCode(), null));
        assertThrows(IllegalArgumentException.class,
                () -> roomService.startGame(room.getCode(), "   "));
        assertEquals(GameStatus.WAITING, room.getStatus());
    }

    private static Stream<Arguments> distributionCases() {
        return Stream.of(
                Arguments.of(2, new int[]{32, 32}),
                Arguments.of(3, new int[]{22, 21, 21}),
                Arguments.of(4, new int[]{16, 16, 16, 16}),
                Arguments.of(5, new int[]{13, 13, 13, 13, 12}),
                Arguments.of(6, new int[]{11, 11, 11, 11, 10, 10})
        );
    }

    private Room createWaitingRoom(int playerCount) {
        Room room = roomService.createRoom("Player 1");
        for (int i = 2; i <= playerCount; i++) {
            roomService.joinRoom(room.getCode(), "Player " + i);
        }
        return room;
    }

    private List<Integer> handSizes(Room room) {
        return room.getPlayers().stream()
                .map(player -> player.getHand().size())
                .toList();
    }

    private List<Card> collectCards(Room room) {
        return room.getPlayers().stream()
                .flatMap(player -> player.getHand().stream())
                .toList();
    }

    private boolean allHandsEmpty(Room room) {
        return room.getPlayers().stream().allMatch(player -> player.getHand().isEmpty());
    }
}
