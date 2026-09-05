package com.mlsoft.backend.domain.auth.service;

import com.mlsoft.backend.domain.audit.entity.AdminAction;
import com.mlsoft.backend.domain.audit.service.AdminAuditService;
import com.mlsoft.backend.domain.auth.dto.OnboardingApprovalResponse;
import com.mlsoft.backend.domain.email.service.EmailNotificationPublisher;
import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 온보딩 승인 — 자동 승인 범위를 벗어난 입사일을 관리자가 확인하는 절차 (리뷰 S-1).
 *
 * <p>{@link AuthService}와 같은 패키지에 두는 이유는 <b>연차 부여 경로를 공유</b>하기 위해서다.
 * 승인 시 부여 로직을 여기에 복사하면 정책이 바뀔 때 자동 승인과 관리자 승인이 갈라진다 —
 * 같은 종류의 산재가 리뷰 I-5(승인자 결정 로직이 두 서비스에 복사돼 있던 것)의 원인이었다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingApprovalService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final AuthService authService;
    private final AdminAuditService adminAuditService;
    private final EmailNotificationPublisher emailNotificationPublisher;

    /** 승인 대기 목록 (GET /api/admin/onboardings, SA) — 오래 기다린 순 */
    @Transactional(readOnly = true)
    public Page<OnboardingApprovalResponse> getPending(Pageable pageable) {
        LocalDate today = LocalDate.now(KST);
        return userRepository
                .findByOnboardingStatusOrderByUpdateAtAsc(OnboardingStatus.PENDING_APPROVAL, pageable)
                .map(user -> OnboardingApprovalResponse.of(user, today));
    }

    /**
     * 승인 — 신고한 입사일을 확정하고 그 시점에 연차를 부여한다.
     *
     * <p>연차는 <b>승인 시점</b>에 산정한다. 신청 시점 값을 저장해 뒀다가 쓰면 정책이 그사이 바뀌었을 때
     * 어긋나고, 무엇보다 승인 전까지 연차가 0이어야 한다는 것이 이 절차의 요지다.
     */
    @Transactional
    public void approve(Long userId, Long actorId) {
        User user = findPendingOrThrow(userId);
        // 자동 승인과 완전히 같은 경로 — 부여 규칙이 갈라지지 않는다
        authService.grantInitialLeave(user, user.getHireDate(), user.getBirthDay(), LocalDate.now(KST));
        if (emailNotificationPublisher != null) {
            emailNotificationPublisher.publishOnboardingApproved(user);
        }
        // 승인 한 번으로 연차가 부여되므로 부여량까지 기록에 남긴다 (리뷰 S-3)
        adminAuditService.recordUserChange(actorId, AdminAction.ONBOARDING_APPROVED, user,
                "승인 대기 (입사일 " + user.getHireDate() + ")",
                "확정 · 연차 " + user.getBaseDays() + "일");
        log.info("[온보딩 승인] userId={}, hireDate={}, 부여 연차={}, actorId={}",
                userId, user.getHireDate(), user.getBaseDays(), actorId);
    }

    /**
     * 반려 — 입력값을 지우고 처음으로 되돌린다.
     *
     * <p>되돌리지 않으면 사원이 올바른 입사일로 다시 낼 방법이 없다. 반려를 "거부"로만 처리하고
     * 상태를 그대로 두면 그 계정은 관리자가 DB를 고칠 때까지 잠긴다 — 그게 S-1의 원래 증상이었다.
     */
    @Transactional
    public void reject(Long userId, Long actorId) {
        User user = findPendingOrThrow(userId);
        LocalDate rejected = user.getHireDate();
        user.rejectOnboarding();
        if (emailNotificationPublisher != null) {
            emailNotificationPublisher.publishOnboardingRejected(user, rejected);
        }
        // 반려는 입력값을 지우므로, 무엇을 반려했는지가 여기 말고는 남지 않는다 (리뷰 S-3)
        adminAuditService.recordUserChange(actorId, AdminAction.ONBOARDING_REJECTED, user,
                "승인 대기 (입사일 " + rejected + ")", "반려 · 온보딩 초기화");
        log.info("[온보딩 반려] userId={}, 반려한 입사일={}, actorId={}", userId, rejected, actorId);
    }

    /** 대기 상태가 아니면 승인·반려 대상이 아니다 (이미 처리됐거나 아직 신청 전) */
    private User findPendingOrThrow(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getOnboardingStatus() != OnboardingStatus.PENDING_APPROVAL) {
            throw new BusinessException(ErrorCode.ONBOARDING_NOT_PENDING);
        }
        return user;
    }
}
