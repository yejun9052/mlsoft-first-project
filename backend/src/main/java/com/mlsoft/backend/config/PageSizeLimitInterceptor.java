package com.mlsoft.backend.config;

import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 페이지 크기 상한 검증 (리뷰 S-4).
 *
 * <p>{@code spring.data.web.pageable.max-page-size}만으로도 초과값이 상한으로 <b>깎이지만</b>,
 * 그러면 클라이언트는 왜 100건만 오는지 알 수 없다. 이 프로젝트는 잘못된 입력을 조용히 다른 의미로
 * 바꾸지 않는다 — {@code PolicyConfigKey}가 {@code "20.9"}를 20으로 깎는 대신 거부하는 것과 같은 기준이다.
 * 프로퍼티는 이 검사를 우회하는 경로가 생겼을 때를 위한 <b>최후 방어선</b>으로 함께 둔다.
 *
 * <p>{@code Pageable}을 받는 핸들러에만 적용한다 — 그 외 엔드포인트의 {@code size} 파라미터는
 * 의미가 다를 수 있어 건드리지 않는다.
 */
@Component
public class PageSizeLimitInterceptor implements HandlerInterceptor {

    private static final String SIZE_PARAMETER = "size";

    private final int maxPageSize;

    public PageSizeLimitInterceptor(
            @Value("${spring.data.web.pageable.max-page-size:100}") int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod) || !acceptsPageable(handlerMethod)) {
            return true;
        }
        String requested = request.getParameter(SIZE_PARAMETER);
        if (requested == null) {
            return true; // 미지정이면 default-page-size가 적용된다
        }

        int size;
        try {
            size = Integer.parseInt(requested.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (size <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (size > maxPageSize) {
            throw new BusinessException(ErrorCode.PAGE_SIZE_EXCEEDED);
        }
        return true;
    }

    private boolean acceptsPageable(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parameter -> Pageable.class.isAssignableFrom(parameter.getParameterType()));
    }
}
