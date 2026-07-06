package com.mlsoft.backend.security;

import tools.jackson.databind.ObjectMapper; // Spring Boot 4 = Jackson 3 (패키지 tools.jackson)
import com.mlsoft.backend.global.exception.ErrorCode;
import com.mlsoft.backend.global.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인가 실패(권한 부족) 응답 — 403 + CommonResponse.fail JSON.
 * 컨트롤러 계층(@PreAuthorize) 거부는 GlobalExceptionHandler가, 필터 계층 거부는 이 핸들러가 담당한다.
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorCode errorCode = ErrorCode.ACCESS_DENIED;
        response.setStatus(errorCode.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), CommonResponse.fail(errorCode.getMessage()));
    }
}
