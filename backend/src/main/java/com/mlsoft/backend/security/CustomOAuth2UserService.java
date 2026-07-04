package com.mlsoft.backend.security;

import com.mlsoft.backend.config.AppProperties;
import com.mlsoft.backend.config.DataInitializer;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Google OAuth2 사용자 검증·자동 가입 (docs/01 2-1).
 * - ① email_verified == true 검증
 * - ② 이메일 도메인 == app.allowed-domain 검증 (개발 중 hd claim 대체 — 검증 R-2)
 * - ③ 미가입이면 자동 가입: EMPLOYEE + 미배정 부서, ADMIN_EMAILS면 SYSTEM_ADMIN (검증 R-3)
 * - ④ 퇴직자(is_active=false)는 토큰 발급 거부 (검증 R-2 ③)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    // OAuth2 실패 코드 — OAuth2FailureHandler의 리다이렉트 error 파라미터와 공유
    public static final String ERROR_UNAUTHORIZED_DOMAIN = "unauthorized_domain";
    public static final String ERROR_RETIRED = "retired";

    private static final String ATTR_EMAIL = "email";
    private static final String ATTR_EMAIL_VERIFIED = "email_verified";
    private static final String ATTR_NAME = "name";

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get(ATTR_EMAIL);
        String name = (String) attributes.get(ATTR_NAME);

        // ① email_verified 검증 — 미검증 메일은 위장 가능성이 있어 거부
        if (!isEmailVerified(attributes.get(ATTR_EMAIL_VERIFIED))) {
            throw unauthorizedDomain();
        }

        // ② 이메일 도메인 검증 (개발 중 대체 방식)
        if (email == null || !email.toLowerCase().endsWith("@" + appProperties.allowedDomain().toLowerCase())) {
            throw unauthorizedDomain();
        }

        // TODO(⏳ 검증 R-2): 회사 Workspace 계정 확보 후 ID 토큰 hd claim == allowed-domain 검증 활성화
        //  - OidcUser로 전환해 idToken.getClaim("hd") 확인 예정

        // ③ 미가입이면 자동 가입 (EMPLOYEE + 미배정 부서, ADMIN_EMAILS는 SYSTEM_ADMIN)
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> registerNewUser(email, name));

        // ④ 퇴직자 로그인 거부 — 인증 성공해도 토큰 발급 전 차단
        if (!user.isActive()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(ERROR_RETIRED, ErrorCode.RETIRED_USER.getMessage(), null));
        }

        return new CustomOAuth2User(user.getId(), user.getEmail(), user.getRole(), attributes);
    }

    // 첫 로그인 자동 가입 — 연차 0으로 시작, 온보딩 후 정책 연차 부여 (갭분석 C-1)
    private User registerNewUser(String email, String name) {
        Role role = appProperties.isAdminEmail(email) ? Role.SYSTEM_ADMIN : Role.EMPLOYEE;
        // Google 계정에 name이 없으면 이메일 로컬파트(@ 앞부분)로 대체
        String resolvedName = (name != null && !name.isBlank()) ? name : email.substring(0, email.indexOf('@'));
        User user = User.create(resolvedName, email, role);
        departmentRepository.findByName(DataInitializer.UNASSIGNED_DEPARTMENT_NAME)
                .ifPresent(user::assignDepartment);
        log.info("[OAuth2] 신규 가입: {} (role={})", email, role);
        return userRepository.save(user);
    }

    // Google userinfo의 email_verified는 Boolean 또는 문자열로 올 수 있어 방어적으로 판별
    private boolean isEmailVerified(Object emailVerified) {
        if (emailVerified instanceof Boolean verified) {
            return verified;
        }
        return emailVerified != null && Boolean.parseBoolean(emailVerified.toString());
    }

    private OAuth2AuthenticationException unauthorizedDomain() {
        return new OAuth2AuthenticationException(
                new OAuth2Error(ERROR_UNAUTHORIZED_DOMAIN, ErrorCode.UNAUTHORIZED_DOMAIN.getMessage(), null));
    }
}
