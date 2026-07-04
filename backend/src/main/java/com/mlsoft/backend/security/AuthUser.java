package com.mlsoft.backend.security;

import com.mlsoft.backend.domain.user.entity.Role;

/**
 * JWT 인증 주체 — SecurityContext의 principal.
 * 컨트롤러는 요청 body가 아니라 이 객체에서 본인 식별 정보를 추출한다 (docs/04).
 */
public record AuthUser(
        Long id,
        String email,
        Role role
) {
}
