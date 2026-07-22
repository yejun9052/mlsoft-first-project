package com.mlsoft.backend.domain.user.entity;

import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.global.entity.BaseTimeEntity;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사원 (docs/02 3-1 users).
 * - 잔여 연차 = base_days + bonus_days - use_days
 * - 신청(PENDING) 시 use_days 선차감, 반려/취소 시 복구
 * - @Version 낙관적 락으로 동시 신청 초과 방지 (검증 R-5)
 */
@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사원 이름 */
    @Column(nullable = false)
    private String name;

    /** 사원 이메일 (OAuth 키) */
    @Column(nullable = false, unique = true)
    private String email;

    /** 입사일 — null이면 온보딩 미완료 (검증 Y-2) */
    private LocalDate hireDate;

    /** 생일 */
    private LocalDate birthDay;

    /** 권한 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** 직책 (사원, 대리 등) */
    private String position;

    /** 총 연차 */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal baseDays;

    /** 사용 연차 (PENDING 선차감 포함) */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal useDays;

    /** 보너스 연차 (복리후생 가산분) */
    @Column(precision = 4, scale = 1)
    private BigDecimal bonusDays;

    /** 당겨쓴 연차 — 다음 기산일 정산용 (갭분석 A-1) */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal advanceDays;

    /** 낙관적 락 버전 (검증 R-5) */
    @Version
    @Column(nullable = false)
    private Long version;

    /** 소속 부서 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /** 마지막 기산일 — 스케줄러는 last_reset_date + 1년 <= 오늘 조건으로 검색 (검증 Y-1) */
    private LocalDate lastResetDate;

    /** true: 재직 / false: 퇴직 */
    @Column(nullable = false)
    private boolean isActive;

    /** 퇴사일 */
    private LocalDate retiredAt;

    /** 정보 업데이트 시점 */
    @LastModifiedDate
    @Column(name = "update_at")
    private LocalDateTime updateAt;

    /**
     * 신규 가입 (OAuth 첫 로그인 자동 가입).
     * - 연차 0으로 시작, 온보딩(입사일 입력) 후 정책 연차가 계산·부여된다 (갭분석 C-1)
     * - ADMIN_EMAILS에 포함된 이메일은 role=SYSTEM_ADMIN으로 호출한다 (검증 R-3)
     */
    public static User create(String name, String email, Role role) {
        return User.builder()
                .name(name)
                .email(email)
                .role(role)
                .baseDays(BigDecimal.ZERO)
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }

    /** 잔여 연차 = base + bonus - use (bonus null 방어) */
    public BigDecimal getRemainingDays() {
        BigDecimal bonus = bonusDays != null ? bonusDays : BigDecimal.ZERO;
        return baseDays.add(bonus).subtract(useDays);
    }

    /** 온보딩 미완료 여부 — hire_date null 판별 (검증 Y-2) */
    public boolean isOnboardingCompleted() {
        return hireDate != null;
    }

    /**
     * 온보딩 완료: 생일·입사일 입력. 기산일은 입사일로 초기화.
     * base_days 산정(정책 조회)은 서비스에서 resetAnnualLeave로 수행한다.
     */
    public void completeOnboarding(LocalDate hireDate, LocalDate birthDay) {
        this.hireDate = hireDate;
        this.birthDay = birthDay;
        this.lastResetDate = hireDate;
    }

    /**
     * 연차 차감 (신청 시 선차감).
     * - 잔여 부족 시: 당겨쓰기 비허용이면 INSUFFICIENT_LEAVE_BALANCE,
     *   허용(advance_leave_enabled=true)이면 부족분을 advance_days에 누적 (갭분석 A-3)
     *
     * @return 이번 차감에서 당겨쓰기로 충당된 일수 — LeaveRequest.recordAdvanceUsage로 스냅샷해
     *         반려·취소 복구의 근거로 쓴다 (검증 B2)
     */
    public BigDecimal deductLeave(BigDecimal days, boolean advanceLeaveEnabled) {
        BigDecimal remaining = getRemainingDays();
        BigDecimal advanceUsed = BigDecimal.ZERO;
        if (remaining.compareTo(days) < 0) {
            if (!advanceLeaveEnabled) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_LEAVE_BALANCE);
            }
            // 부족분만 당겨쓰기로 누적 — 다음 기산일에 정산
            advanceUsed = days.subtract(remaining.max(BigDecimal.ZERO));
            this.advanceDays = this.advanceDays.add(advanceUsed);
        }
        this.useDays = this.useDays.add(days);
        return advanceUsed;
    }

    /**
     * 연차 복구 (반려·취소 시 선차감 원복).
     * use_days와 함께 해당 신청이 당겨쓰기로 충당했던 일수(advance_days)도 되돌린다 (검증 B2 —
     * 미복구 시 다음 기산일에 쓰지 않은 연차가 차감되는 결함).
     *
     * @param advanceUsedDays 해당 신청의 당겨쓰기 충당분 (LeaveRequest.advanceUsedDays 스냅샷)
     */
    public void restoreLeave(BigDecimal days, BigDecimal advanceUsedDays) {
        this.useDays = this.useDays.subtract(days);
        this.advanceDays = this.advanceDays.subtract(advanceUsedDays);
    }

    /** 보너스 연차 가산 (복리후생 승인) */
    public void addBonusDays(BigDecimal days) {
        BigDecimal bonus = bonusDays != null ? bonusDays : BigDecimal.ZERO;
        this.bonusDays = bonus.add(days);
    }

    /**
     * 기산일 리셋 (기산일 스케줄러 — docs/01 2-7).
     * - 미사용 연차는 이월 없이 소멸 (호출 전 leave_reset_history 기록은 서비스 책임)
     * - 당겨쓴 연차(advance_days)는 새 base_days에서 차감 후 초기화
     */
    public void resetAnnualLeave(BigDecimal newBaseDays, LocalDate resetDate) {
        this.baseDays = newBaseDays.subtract(this.advanceDays);
        this.useDays = BigDecimal.ZERO;
        this.bonusDays = BigDecimal.ZERO;
        this.advanceDays = BigDecimal.ZERO;
        this.lastResetDate = resetDate;
    }

    /** 1년 미만 신입 월차 적립 — 매월 1일씩, 최대 11일 (갭분석 B-1). 상한 검증은 서비스에서. */
    public void addMonthlyLeave() {
        this.baseDays = this.baseDays.add(BigDecimal.ONE);
    }

    /** 퇴직 처리 (SYSTEM_ADMIN 전용) — 소프트 삭제, 데이터 3년 보존 */
    public void retire(LocalDate retiredAt) {
        this.isActive = false;
        this.retiredAt = retiredAt;
    }

    /** 권한 변경 (SYSTEM_ADMIN 전용) */
    public void changeRole(Role role) {
        this.role = role;
    }

    /** 부서 배정 */
    public void assignDepartment(Department department) {
        this.department = department;
    }

    /** 내 정보 수정 — 이름·생일만 (PATCH /api/users/me) */
    public void updateProfile(String name, LocalDate birthDay) {
        this.name = name;
        this.birthDay = birthDay;
    }

    /** 연차 직접 설정 (관리자 조작, PATCH /api/users/{id}/base-days) — 음수 검증은 서비스 책임 */
    public void updateBaseDays(BigDecimal baseDays) {
        this.baseDays = baseDays;
    }
}
