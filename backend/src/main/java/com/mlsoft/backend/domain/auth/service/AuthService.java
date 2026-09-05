package com.mlsoft.backend.domain.auth.service;

import com.mlsoft.backend.domain.auth.dto.OnboardingRequest;
import com.mlsoft.backend.domain.auth.dto.UserMeResponse;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.service.LeavePolicyService;
import com.mlsoft.backend.domain.policy.service.PolicyConfigReader;
import com.mlsoft.backend.domain.email.service.EmailNotificationPublisher;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
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
    /** 월차 상한·온보딩 판정은 설정 카탈로그의 타입별 접근자로만 읽는다 */
    private final PolicyConfigReader policyConfigReader;
    private final EmailNotificationPublisher emailNotificationPublisher;

    /**
     * 내 정보 조회 (GET /api/auth/me).
     */
    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        User user = findUserOrThrow(userId);
        boolean revisionEnabled =
                policyConfigReader.getBoolean(PolicyConfigKey.ONBOARDING_REVISION_ENABLED);
        return UserMeResponse.from(user, revisionEnabled);
    }

    /**
     * 최초 온보딩 — 생일·입사일 입력 (갭분석 C-1, 리뷰 S-1).
     *
     * <p><b>입사일은 자가 신고라 그대로 믿지 않는다.</b> 예전에는 {@code @PastOrPresent}만 걸려 있어
     * 신입이 {@code 1990-01-01}을 넣으면 그 자리에서 25일이 부여됐고, 완료로 판정된 뒤에는
     * 재입력 경로가 없어 관리자가 DB를 직접 고쳐야 했다.
     *
     * <p>이제 자동 승인 기간({@code onboarding_auto_approve_days}, 기본 90일) 안의 입사일만 즉시
     * 확정하고, 그보다 과거면 <b>연차 없이</b> 승인 대기로 넘긴다. 신입은 대부분 입사 직후에
     * 온보딩하므로 정상 흐름은 그대로고, 악용 경로만 관리자를 거친다.
     *
     * @return 확정됐으면 연차가 채워진 응답, 승인 대기면 {@code onboardingStatus=PENDING_APPROVAL}
     */
    @Transactional
    public UserMeResponse completeOnboarding(Long userId, OnboardingRequest request) {
        User user = findUserOrThrow(userId);
        // 완료뿐 아니라 승인 대기도 재신청을 막는다 — 대기 중에 값을 바꿔 치고 승인받는 것을 차단
        if (user.getOnboardingStatus() != OnboardingStatus.NOT_STARTED) {
            throw new BusinessException(ErrorCode.ALREADY_ONBOARDED);
        }

        LocalDate today = LocalDate.now(KST);
        processOnboarding(user, request, today, false);

        boolean revisionEnabled =
                policyConfigReader.getBoolean(PolicyConfigKey.ONBOARDING_REVISION_ENABLED);
        return UserMeResponse.from(user, revisionEnabled);
    }

    /**
     * 승인 대기 중 입사일·생일 1회 수정.
     *
     * <p>상태 → 기능 설정 → 수정권 순서로 검사해야 호출자가 실제로 취할 수 있는 조치를
     * 정확한 오류로 알려 줄 수 있다. 미래일은 공유 판정 경로에서 검사하며, 통과하기 전에는
     * 수정권을 차감하지 않는다.
     */
    @Transactional
    public UserMeResponse reviseOnboarding(Long userId, OnboardingRequest request) {
        User user = findUserOrThrow(userId);
        if (user.getOnboardingStatus() != OnboardingStatus.PENDING_APPROVAL) {
            throw new BusinessException(ErrorCode.ONBOARDING_NOT_PENDING);
        }

        boolean revisionEnabled =
                policyConfigReader.getBoolean(PolicyConfigKey.ONBOARDING_REVISION_ENABLED);
        if (!revisionEnabled) {
            throw new BusinessException(ErrorCode.ONBOARDING_REVISION_DISABLED);
        }
        if (user.isOnboardingRevised()) {
            throw new BusinessException(ErrorCode.ONBOARDING_REVISION_EXHAUSTED);
        }

        LocalDate previousHireDate = user.getHireDate();
        processOnboarding(user, request, LocalDate.now(KST), true);
        if (emailNotificationPublisher != null) {
            emailNotificationPublisher.publishOnboardingRevised(user);
        }

        log.info("[온보딩 수정] userId={}, {} → {}, 결과={}",
                userId, previousHireDate, request.hireDate(), user.getOnboardingStatus());
        return UserMeResponse.from(user, revisionEnabled);
    }

    /**
     * 최초 제출과 수정에 공통으로 적용하는 온보딩 판정.
     *
     * <p>수정권은 미래일 검증을 통과한 뒤, 자동 승인 여부를 판단하기 전에 사용 처리한다.
     * 따라서 확정·대기 어느 결과가 나오더라도 유효한 수정 요청 한 번은 동일하게 소진된다.
     */
    private void processOnboarding(
            User user,
            OnboardingRequest request,
            LocalDate today,
            boolean revision
    ) {
        LocalDate hireDate = request.hireDate();

        // 미래 입사일 차단 (리뷰 I-7) — DTO 애노테이션이 아니라 여기서 KST로 판정한다.
        // 통과시키면 last_reset_date가 미래가 되어 그 사원이 기산일 스케줄러 대상에서
        // 그만큼 제외되고(조건이 last_reset_date + 1년 <= 오늘), 월차 소급도 음수 개월로 계산된다.
        if (hireDate.isAfter(today)) {
            throw new BusinessException(ErrorCode.FUTURE_HIRE_DATE);
        }

        if (revision) {
            user.markOnboardingRevised();
        }

        int autoApproveDays =
                policyConfigReader.getInt(PolicyConfigKey.ONBOARDING_AUTO_APPROVE_DAYS);
        if (hireDate.isBefore(today.minusDays(autoApproveDays))) {
            user.requestOnboardingApproval(hireDate, request.birthDay());
            if (emailNotificationPublisher != null) {
                emailNotificationPublisher.publishOnboardingPending(user);
            }
            log.info("[온보딩] 자동 승인 범위({}일) 밖 — 승인 대기: userId={}, hireDate={}",
                    autoApproveDays, user.getId(), hireDate);
            return;
        }

        grantInitialLeave(user, hireDate, request.birthDay(), today);
    }

    /**
     * 온보딩 확정 + 초기 연차 부여 — 자동 승인과 관리자 승인이 <b>같은 경로</b>를 쓴다 (리뷰 S-1).
     * 둘로 나뉘면 승인 경로만 연차 산정이 어긋나는 사고가 난다.
     *
     * <ul>
     *   <li>1년 미만 신입: base_days = 입사 후 경과 개월 수 소급 적립(상한 설정), 기산일 = 입사일 (갭분석 B-1)</li>
     *   <li>1년 이상: base_days = N년차 정책 연차, 기산일 = 최근 입사기념일 (스케줄러 중복 리셋 방지)</li>
     * </ul>
     * <b>년차(N) = 만 근속년수</b> 기준 — MIN(15+(N-1)/2, 25)가 근로기준법과 일치한다
     * (만 1~2년 15일, 만 3년 16일, 만 20년 24일, 만 21년 이상 25일).
     */
    void grantInitialLeave(User user, LocalDate hireDate, LocalDate birthDay, LocalDate today) {
        user.completeOnboarding(hireDate, birthDay);

        long elapsedYears = ChronoUnit.YEARS.between(hireDate, today);
        if (elapsedYears < 1) {
            // 1년 미만 신입 — 월차 소급 적립, 이후 매월 적립·1주년 전환은 스케줄러 담당
            // 상한은 스케줄러와 같은 설정을 본다. 여기만 법정 11일로 고정돼 있으면 관리자가 상한을 5로 낮춰도
            // 온보딩이 8일을 주고, 스케줄러는 이미 상한 이상이라 되돌리지 않는다
            BigDecimal maxMonthlyDays = BigDecimal.valueOf(
                    policyConfigReader.getInt(PolicyConfigKey.MONTHLY_LEAVE_MAX_DAYS)).setScale(1);
            BigDecimal monthlyDays = leavePolicyService.calculateRetroactiveMonthlyDays(hireDate, today)
                    .min(maxMonthlyDays);
            user.resetAnnualLeave(monthlyDays, hireDate);
            // 소급으로 몇 회분을 이미 줬는지 기록한다 — 빼면 스케줄러가 같은 개월분을 한 번 더 적립한다 (docs/09 §2)
            user.markMonthlyGranted(monthlyDays.intValue());
            log.info("[온보딩] 신입 월차 소급: userId={}, hireDate={}, days={}", user.getId(), hireDate, monthlyDays);
        } else {
            // 1년 이상 — 년차(= 만 근속년수) 정책 연차 부여, 기산일은 최근 기념일로 설정해
            //   기산일 스케줄러(last_reset_date + 1년 <= 오늘)의 즉시 재리셋을 방지 (검증 Y-1)
            int yearsOfService = Math.max(1, (int) elapsedYears);
            BigDecimal annualDays = leavePolicyService.calculateAnnualLeaveDays(yearsOfService);
            LocalDate lastAnniversary = hireDate.plusYears(elapsedYears);
            user.resetAnnualLeave(annualDays, lastAnniversary);
            log.info("[온보딩] 정책 연차 부여: userId={}, {}년차, days={}, 기산일={}",
                    user.getId(), yearsOfService, annualDays, lastAnniversary);
        }
    }

    // 사용자 조회 검증 헬퍼
    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
