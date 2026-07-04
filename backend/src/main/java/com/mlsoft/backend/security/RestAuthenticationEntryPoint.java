package com.mlsoft.backend.security;

import tools.jackson.databind.ObjectMapper; // Spring Boot 4 = Jackson 3 (패키지 tools.jackson)
import com.mlsoft.backend.global.exception.ErrorCode;
import com.mlsoft.backend.global.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증 실패(미로그인·토큰 무효) 응답 — 401 + CommonResponse.fail JSON.
 * 401 수신 시 프론트는 로그인 페이지로 강제 리다이렉트한다 (검증 Y-5).
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;
        response.setStatus(errorCode.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), CommonResponse.fail(errorCode.getMessage()));
    }
}
