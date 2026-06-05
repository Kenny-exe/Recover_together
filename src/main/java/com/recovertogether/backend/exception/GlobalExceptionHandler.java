package com.recovertogether.backend.exception;

import com.recovertogether.backend.dto.MessageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler
{
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<MessageResponse> handleResponseStatusException(ResponseStatusException ex)
    {
        return ResponseEntity.status(ex.getStatusCode()).body(new MessageResponse(ex.getReason()));
    }
}
