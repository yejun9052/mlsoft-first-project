package com.mlsoft.backend.security;

import com.mlsoft.backend.domain.user.entity.Role;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * OAuth2 로그인 principal — DB users 정보를 함께 보관해
 * OAuth2SuccessHandler가 JWT(subject=userId) 발급에 사용한다.
 */
@Getter
@RequiredArgsConstructor
public class CustomOAuth2User implements OAuth2User {

    private final Long userId;
    private final String email;
    private final Role role;
    private final Map<String, Object> attributes;

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
