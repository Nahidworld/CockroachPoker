package com.cockroach.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RoomNotFoundException.class)
    public ProblemDetail handleRoomNotFound(RoomNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Room not found", exception.getMessage());
    }

    @ExceptionHandler({RoomFullException.class, GameAlreadyStartedException.class})
    public ProblemDetail handleRoomConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Room unavailable", exception.getMessage());
    }

    @ExceptionHandler(InvalidGameStateException.class)
    public ProblemDetail handleInvalidGameState(InvalidGameStateException exception) {
        return problem(HttpStatus.CONFLICT, "Invalid game state", exception.getMessage());
    }

    @ExceptionHandler(NotRoomHostException.class)
    public ProblemDetail handleNotRoomHost(NotRoomHostException exception) {
        return problem(HttpStatus.FORBIDDEN, "Not room host", exception.getMessage());
    }

    @ExceptionHandler(InvalidPlayerTokenException.class)
    public ProblemDetail handleInvalidPlayerToken(InvalidPlayerTokenException exception) {
        return problem(HttpStatus.FORBIDDEN, "Invalid player token", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidInput(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        return problemDetail;
    }
}
