package com.mlsoft.backend.global.exception;

import com.mlsoft.backend.global.response.CommonResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
     * 권한 거부(@PreAuthorize 등 메서드 시큐리티, AuthorizationDeniedException 포함) → 403.
     * catch-all이 500으로 삼키지 않도록 명시 처리 (docs/03 ACCESS_DENIED 계약).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.ACCESS_DENIED.getStatus())
                .body(CommonResponse.fail(ErrorCode.ACCESS_DENIED.getMessage()));
    }

    /**
     * 존재하지 않는 경로 → 404 (500으로 오인 방지, 검증 리포트).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("Resource not found: {}", e.getResourcePath());
        return ResponseEntity
                .status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(CommonResponse.fail(ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
    }

    /**
     * 요청 본문 파싱 실패(깨진 JSON·타입 불일치) → 400.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResponse<Void>> handleMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("Message not readable: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(CommonResponse.fail(ErrorCode.INVALID_INPUT_VALUE.getMessage()));
    }

    /**
     * 미지원 HTTP 메서드 → 405.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonResponse<Void>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not supported: {}", e.getMethod());
        return ResponseEntity
                .status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(CommonResponse.fail(ErrorCode.METHOD_NOT_ALLOWED.getMessage()));
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
