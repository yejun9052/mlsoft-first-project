package com.mlsoft.backend.domain.auth.service;

import com.mlsoft.backend.domain.auth.dto.OnboardingRequest;
import com.mlsoft.backend.domain.auth.dto.UserMeResponse;
import com.mlsoft.backend.domain.policy.service.LeavePolicyService;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 인증 도메인 서비스 — 내 정보 조회·온보딩 (docs/01 2-1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    // 연차 계산 기준일은 한국 시간으로 고정 — 서버 TZ가 UTC여도 KST 자정~09시 사이 하루 오차 방지 (DB도 Asia/Seoul)
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final LeavePolicyService leavePolicyService;

    /**
     * 내 정보 조회 (GET /api/auth/me).
     */
    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        return UserMeResponse.from(findUserOrThrow(userId));
    }

    /**
     * 최초 온보딩 — 생일·입사일 입력 + base_days 정책 자동 계산 (갭분석 C-1).
     * - 이미 온보딩 완료(hire_date 존재)면 ALREADY_ONBOARDED
     * - 1년 미만 신입: base_days = 입사 후 경과 개월 수 소급 적립 (최대 11), 기산일 = 입사일 (갭분석 B-1)
     * - 1년 이상: base_days = N년차 정책 연차, 기산일 = 최근 입사기념일 (스케줄러 중복 리셋 방지)
     * - **년차(N) = 만 근속년수** 기준 — MIN(15+(N-1)/2, 25)가 근로기준법과 일치
     *   (만 1~2년 15일, 만 3년 16일, 만 20년 24일, 만 21년 이상 25일)
     */
    @Transactional
    public UserMeResponse completeOnboarding(Long userId, OnboardingRequest request) {
        User user = findUserOrThrow(userId);
        if (user.isOnboardingCompleted()) {
            throw new BusinessException(ErrorCode.ALREADY_ONBOARDED);
        }

        LocalDate hireDate = request.hireDate();
        LocalDate today = LocalDate.now(KST);
        user.completeOnboarding(hireDate, request.birthDay());

        long elapsedYears = ChronoUnit.YEARS.between(hireDate, today);
        if (elapsedYears < 1) {
            // 1년 미만 신입 — 월차 소급 적립, 이후 매월 적립·1주년 전환은 스케줄러 담당
            BigDecimal monthlyDays = leavePolicyService.calculateRetroactiveMonthlyDays(hireDate, today);
            user.resetAnnualLeave(monthlyDays, hireDate);
            log.info("[온보딩] 신입 월차 소급: userId={}, hireDate={}, days={}", userId, hireDate, monthlyDays);
        } else {
            // 1년 이상 — 년차(= 만 근속년수) 정책 연차 부여, 기산일은 최근 기념일로 설정해
            //   기산일 스케줄러(last_reset_date + 1년 <= 오늘)의 즉시 재리셋을 방지 (검증 Y-1)
            int yearsOfService = Math.max(1, (int) elapsedYears);
            BigDecimal annualDays = leavePolicyService.calculateAnnualLeaveDays(yearsOfService);
            LocalDate lastAnniversary = hireDate.plusYears(elapsedYears);
            user.resetAnnualLeave(annualDays, lastAnniversary);
            log.info("[온보딩] 정책 연차 부여: userId={}, {}년차, days={}, 기산일={}",
                    userId, yearsOfService, annualDays, lastAnniversary);
        }

        return UserMeResponse.from(user);
    }

    // 사용자 조회 검증 헬퍼
    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
