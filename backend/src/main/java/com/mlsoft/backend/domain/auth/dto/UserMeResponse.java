package com.mlsoft.backend.domain.auth.dto;

import com.mlsoft.backend.domain.user.entity.OnboardingStatus;
import com.mlsoft.backend.domain.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 내 정보 응답 (GET /api/auth/me — docs/03 인증).
 *
 * <p>{@code onboarded=false}면 프론트가 온보딩 페이지로 유도한다. 단 <b>{@code onboardingStatus}를
 * 함께 봐야 한다</b> — 승인 대기 중인 사원에게 온보딩 폼을 다시 보여주면 제출할 때마다
 * {@code ALREADY_ONBOARDED}를 맞는다 (리뷰 S-1).
 */
public record UserMeResponse(
        Long id,
        String name,
        String email,
        String role,
        String position,
        Long departmentId,
        String departmentName,
        BigDecimal baseDays,
        BigDecimal useDays,
        BigDecimal bonusDays,
        BigDecimal advanceDays,
        LocalDate hireDate,
        LocalDate birthDay,
        boolean onboarded,
        /** NOT_STARTED / PENDING_APPROVAL / COMPLETED (리뷰 S-1) */
        String onboardingStatus,
        /** 상태·설정·사용 이력을 서버에서 함께 판정한 현재 수정 가능 여부 */
        boolean onboardingRevisable,
        /** 수정권을 이미 사용했는지 화면 안내 문구를 구분하기 위한 값 */
        boolean onboardingRevised
) {

    /**
     * User 엔티티 → 응답 변환 (부서 LAZY 접근 — 트랜잭션 내 호출 필수).
     * 수정 가능 여부는 프론트가 정책을 재구현하지 않도록 여기서 최종 판정한다.
     */
    public static UserMeResponse from(User user, boolean revisionEnabled) {
        boolean hasDepartment = user.getDepartment() != null;
        boolean onboardingRevisable =
                user.getOnboardingStatus() == OnboardingStatus.PENDING_APPROVAL
                        && revisionEnabled
                        && !user.isOnboardingRevised();

        return new UserMeResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getPosition(),
                hasDepartment ? user.getDepartment().getId() : null,
                hasDepartment ? user.getDepartment().getName() : null,
                user.getBaseDays(),
                user.getUseDays(),
                user.getBonusDays(),
                user.getAdvanceDays(),
                user.getHireDate(),
                user.getBirthDay(),
                user.isOnboardingCompleted(),
                user.getOnboardingStatus().name(),
                onboardingRevisable,
                user.isOnboardingRevised()
        );
    }
}
