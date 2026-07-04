package com.mlsoft.backend.global.exception;

import com.mlsoft.backend.global.response.CommonResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리 — 컨트롤러 try-catch 금지 (docs/04).
 * 모든 실패 응답은 { success: false, message, data: null } 포맷으로 통일한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 예외 → ErrorCode의 status/message로 변환.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CommonResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: {} - {}", errorCode.name(), errorCode.getMessage());
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(CommonResponse.fail(errorCode.getMessage()));
    }

    /**
     * @Valid 검증 실패 → 400 + 첫 번째 필드 에러 메시지.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.INVALID_INPUT_VALUE.getMessage());
        log.warn("Validation failed: {}", message);
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(CommonResponse.fail(message));
    }

    /**
     * 낙관적 락 충돌 (users.version — 동시 신청/처리) → 409 재시도 안내.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<CommonResponse<Void>> handleOptimisticLockException(OptimisticLockingFailureException e) {
        log.warn("Optimistic lock conflict: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.CONCURRENT_UPDATE.getStatus())
                .body(CommonResponse.fail(ErrorCode.CONCURRENT_UPDATE.getMessage()));
    }

    /**
     * 그 외 미처리 예외 → 500. 내부 정보 노출 방지를 위해 고정 메시지 사용.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(CommonResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
