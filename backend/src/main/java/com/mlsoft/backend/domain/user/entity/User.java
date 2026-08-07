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
     * 당겨쓴 연차 재계산 — advance_days는 <b>파생값</b>이므로 이 메서드만 이 필드에 쓴다
     * (리뷰 I-1·I-2·I-8을 한 원인으로 묶어 해소).
     *
     * <pre>advance_days = max(0, use_days − base_days − bonus_days)</pre>
     *
     * 이전에는 deductLeave가 부족분을 더하고 restoreLeave가 신청별 스냅샷만큼 빼는 식으로
     * 여러 경로가 각자 이 필드를 조작했다. 그래서 신청 2건을 역순이 아닌 순서로 취소하면
     * (A·B 신청 후 A 취소) B가 잔여로 충당되는데도 advance가 남아, 다음 기산일에 쓰지 않은
     * 연차가 차감됐다(I-1). 보너스 가산(I-2)·관리자 연차 직접 설정(I-8)·월차 적립도 같은 이유로
     * 정산이 누락됐다. 따라서 잔액 3필드(base/bonus/use)를 바꾸는 도메인 메서드는
     * <b>마지막에 반드시 이 메서드를 호출</b>한다. {@link #resetAnnualLeave}도 예외가 아니다 —
     * 사유는 그쪽 주석 참고.
     */
    private void syncAdvanceDays() {
        // getRemainingDays() = base + bonus − use 이므로 negate() = use − base − bonus
        this.advanceDays = getRemainingDays().negate().max(BigDecimal.ZERO);
    }

    /**
     * 연차 차감 (신청 시 선차감).
     * <ul>
     *   <li>잔여로 충당되면 그대로 차감</li>
     *   <li>부족한데 당겨쓰기 비허용이면 INSUFFICIENT_LEAVE_BALANCE</li>
     *   <li>부족하고 당겨쓰기 허용이면 부족분이 advance_days에 잡힌다 (갭분석 A-3).
     *       단 누적 당겨쓰기가 상한을 넘으면 ADVANCE_LIMIT_EXCEEDED (리뷰 I-3)</li>
     * </ul>
     *
     * <p>상한은 <b>이번 신청의 부족분이 아니라 누적 당겨쓰기 총량</b>에 걸린다. 신청 건마다 조금씩
     * 당겨쓰면 얼마든지 누적되던 것이 I-3의 실제 결함이었다 — 평일 100일을 신청하면 advance가 85가 되고,
     * 다음 기산일에 base가 −70이 되어 그 계정은 관리자가 DB를 고치기 전까지 연차를 쓸 수 없었다.
     *
     * <p>상한을 넘는지는 <b>차감 전에</b> 판정한다. 도메인 메서드가 예외를 던지기 전에 상태를 바꾸면
     * 호출부가 롤백에 의존하게 된다.
     *
     * @param advanceMaxDays 누적 당겨쓰기 상한 (설정 advance_max_days). 이미 상한에 도달한 사원의
     *                       추가 신청도 여기서 막힌다 — 상한을 낮춘 직후에도 더 깊어지지 않는다
     * @return 이번 차감으로 늘어난 당겨쓰기 일수 — LeaveRequest.recordAdvanceUsage로 스냅샷해
     *         <b>감사 기록</b>으로 남긴다. 복구는 이 값에 의존하지 않는다 (syncAdvanceDays 참고)
     */
    public BigDecimal deductLeave(BigDecimal days, boolean advanceLeaveEnabled, BigDecimal advanceMaxDays) {
        // 차감 후의 초과 사용량 = 이번 신청으로 확정될 advance_days (syncAdvanceDays와 같은 식)
        BigDecimal advanceAfter = getRemainingDays().subtract(days).negate().max(BigDecimal.ZERO);
        if (advanceAfter.signum() > 0) {
            if (!advanceLeaveEnabled) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_LEAVE_BALANCE);
            }
            if (advanceAfter.compareTo(advanceMaxDays) > 0) {
                throw new BusinessException(ErrorCode.ADVANCE_LIMIT_EXCEEDED);
            }
        }
        BigDecimal advanceBefore = this.advanceDays;
        this.useDays = this.useDays.add(days);
        syncAdvanceDays();
        return this.advanceDays.subtract(advanceBefore).max(BigDecimal.ZERO);
    }

    /**
     * 연차 복구 (반려·취소 시 선차감 원복).
     * use_days를 되돌리면 advance_days는 재계산으로 따라온다 — 신청별 당겨쓰기 스냅샷을
     * 빼는 방식이 아니다(리뷰 I-1). 덕분에 취소 순서에 결과가 달라지지 않고,
     * 리셋 후 잔존 신청이 반려돼도 advance_days가 음수로 내려가지 않는다(리뷰 I-4).
     */
    public void restoreLeave(BigDecimal days) {
        this.useDays = this.useDays.subtract(days);
        syncAdvanceDays();
    }

    /** 보너스 연차 가산 (복리후생 승인) — 가산분으로 충당되는 만큼 당겨쓰기가 정산된다 (리뷰 I-2) */
    public void addBonusDays(BigDecimal days) {
        BigDecimal bonus = bonusDays != null ? bonusDays : BigDecimal.ZERO;
        this.bonusDays = bonus.add(days);
        syncAdvanceDays();
    }

    /**
     * 기산일 리셋 (기산일 스케줄러 — docs/01 2-7).
     * - 미사용 연차는 이월 없이 소멸 (호출 전 leave_reset_history 기록은 서비스 책임)
     * - 당겨쓴 연차(advance_days)는 새 base_days에서 차감 후 재계산
     *
     * <b>여기서도 syncAdvanceDays를 호출한다.</b> 빚이 새 정책 연차보다 크면 base_days가 음수로
     * 남는데(newBase 15, advance 20 → base −5), 다음 리셋은 base를 <i>가감이 아니라 덮어쓰기</i>
     * 하므로(위 첫 줄) 그 음수는 버려진다. 즉 남은 빚을 advance_days로 옮겨 담지 않으면
     * 다음 기산일에 <b>빚이 면제된다.</b> 재계산하면 다음 리셋의 `newBase − advance`가 그 빚을
     * 정확히 한 번 이어받는다.
     *
     * 재계산을 빼면 "리셋 후 다른 잔액 변경이 있었는지"에 따라 결과가 갈린다 — 활동이 없으면
     * 빚이 사라지고, 월차가 1일이라도 적립되면 재계산이 걸려 빚이 살아난다. 리뷰 I-1과 같은
     * 종류의 상태 의존 결함이다. 다년 검산(Σ부여 − Σ사용)으로 확인했다:
     * <pre>
     * Y1 부여 15 · 사용 35 → advance 20
     * 리셋 → base −5, advance 5     (재계산 제외 시 advance 0)
     * Y2 활동 없음
     * 리셋 → base 15 − 5 = 10       (재계산 제외 시 15 − 0 = 15)
     * 정답: 부여 45 − 사용 35 = 10  → 제외 시 5일 과다
     * </pre>
     *
     * advance는 매 리셋마다 새 정책 연차만큼 줄어들어(50 → 35 → 20 → 5 → 0) 반드시 종료한다.
     * docs/09 §5의 "advance를 다시 쌓으면 정산이 재귀적으로 이어진다"는 서술은 이 검산과
     * 어긋나므로 docs/09에서 정정했다.
     */
    public void resetAnnualLeave(BigDecimal newBaseDays, LocalDate resetDate) {
        resetAnnualLeave(newBaseDays, BigDecimal.ZERO, BigDecimal.ZERO, resetDate);
    }

    /**
     * 기산일 리셋 (미래 승인분 이월 포함 — docs/09 §4·§5, 리뷰 I-11).
     *
     * <p><b>채무는 "이전 연도에 귀속되는 사용분"으로만 계산한다.</b> 이것이 I-11의 답이다:
     * <pre>
     * oldYearUse  = use − carriedUse            ← 이월분을 빼야 그 해 실제 사용분이 된다
     * oldYearDebt = max(0, oldYearUse − base − bonus)
     * newBase     = 정책연차 − oldYearDebt
     * newUse      = carriedUse
     * </pre>
     *
     * <p>기존 {@code advance_days}를 그대로 빼면 <b>같은 일수를 두 번 센다.</b> 이월되는 날짜는
     * 이전 연도에 선차감돼 이미 advance를 만들었는데, {@code carriedUse}로 새 연도에 또 차감되기
     * 때문이다. 그래서 advance가 아니라 "이월분을 제외한 사용분"에서 채무를 다시 구한다.
     *
     * <pre>
     * 검산 1 (I-11 시나리오) base=15, use=20, 20일 전부 이월
     *   oldYearUse 0 → 채무 0 → newBase 15, newUse 20 → advance 5   ← 경제적 초과분과 일치
     *   (기존 방식은 base 10 · advance 10 으로 5일을 과다 계상했다)
     *
     * 검산 2 (다년) Y1 부여15·사용35, 이월 0
     *   채무 20 → base −5, advance 5
     *   Y2 무활동 리셋 → 채무 5 → base 10
     *   Σ부여 45 − Σ사용 35 = 10                                    ← 일치
     * </pre>
     *
     * {@code base}가 음수(=남은 빚)여도 식이 그대로 성립한다 — 검산 2의 Y2가 그 경우다.
     *
     * @param newBaseDays  근속년수 정책이 정한 새 연차
     * @param carriedUse   기산일 이후 날짜의 선차감 유지분 (APPROVED·PENDING·CANCEL_PENDING)
     * @param carriedBonus 이월할 보너스 (정책상 이월 안 하면 0)
     */
    public void resetAnnualLeave(BigDecimal newBaseDays, BigDecimal carriedUse,
                                 BigDecimal carriedBonus, LocalDate resetDate) {
        BigDecimal bonus = bonusDays != null ? bonusDays : BigDecimal.ZERO;
        // 이월되는 날짜는 새 연도에서 다시 차감되므로, 이전 연도 채무 계산에서는 빼야 한다 (I-11)
        BigDecimal oldYearUse = this.useDays.subtract(carriedUse);
        BigDecimal oldYearDebt = oldYearUse.subtract(this.baseDays).subtract(bonus).max(BigDecimal.ZERO);

        this.baseDays = newBaseDays.subtract(oldYearDebt);
        this.useDays = carriedUse;
        this.bonusDays = carriedBonus;
        this.lastResetDate = resetDate;
        // advance_days는 여기서 대입하지 않는다 — 파생값의 writer는 syncAdvanceDays 하나뿐이다.
        syncAdvanceDays();
    }

    /** 1년 미만 신입 월차 적립 — 매월 1일씩, 최대 11일 (갭분석 B-1). 상한 검증은 서비스에서. */
    public void addMonthlyLeave() {
        this.baseDays = this.baseDays.add(BigDecimal.ONE);
        syncAdvanceDays();
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

    /**
     * 연차 직접 설정 (관리자 조작, PATCH /api/users/{id}/base-days) — 음수 검증은 서비스 책임.
     * 연차를 늘리면 당겨쓴 분이 그만큼 정산되고, 줄이면 초과분이 당겨쓰기로 잡힌다 (리뷰 I-8).
     */
    public void updateBaseDays(BigDecimal baseDays) {
        this.baseDays = baseDays;
        syncAdvanceDays();
    }
}
