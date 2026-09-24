package com.example.webtemplate.global.exception;

import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ApiError(String code, String message, List<String> fields) { }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ApiError> duplicate(DuplicateKeyException exception) {
        return ResponseEntity.status(409).body(new ApiError("DUPLICATE_USER",
                "이미 사용 중인 로그인 아이디 또는 이메일입니다.", List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        // Never echo rejected values (they may contain passwords).
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "입력값을 확인해 주세요.",
                exception.getBindingResult().getFieldErrors().stream().map(e -> e.getField()).distinct().toList()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformed(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "요청 형식을 확인해 주세요.", List.of()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ApiError("REQUEST_FAILED", exception.getReason(), List.of()));
    }
}
