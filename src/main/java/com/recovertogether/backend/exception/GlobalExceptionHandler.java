package com.recovertogether.backend.exception;

import com.recovertogether.backend.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler
{
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException ex)
    {
        ErrorResponse response =
                new ErrorResponse(
                        LocalDateTime.now(),
                        ex.getStatusCode().value(),
                        ex.getReason()
                );

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(response);
    }
}