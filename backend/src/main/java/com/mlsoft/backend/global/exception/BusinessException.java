package com.mlsoft.backend.global.exception;

import lombok.Getter;

/**
 * 비즈니스 예외 — ErrorCode를 물고 다니며 GlobalExceptionHandler에서 일괄 변환된다.
 * RuntimeException 직접 사용 금지 (docs/04).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
