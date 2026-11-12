package com.cockroach.backend.controller;

import com.cockroach.backend.dto.CreateRoomRequest;
import com.cockroach.backend.dto.JoinRoomRequest;
import com.cockroach.backend.dto.RoomResponse;
import com.cockroach.backend.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(@RequestBody CreateRoomRequest request) {
        return RoomResponse.from(roomService.createRoom(request.playerName()));
    }

    @PostMapping("/{roomCode}/join")
    public RoomResponse joinRoom(@PathVariable String roomCode, @RequestBody JoinRoomRequest request) {
        return RoomResponse.from(roomService.joinRoom(roomCode, request.playerName()));
    }

    @GetMapping("/{roomCode}")
    public RoomResponse getRoom(@PathVariable String roomCode) {
        return RoomResponse.from(roomService.getRoom(roomCode));
    }
}
