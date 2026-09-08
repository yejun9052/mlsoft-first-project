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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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

    /**
     * 입사일. <b>채워져 있다고 온보딩이 끝난 것이 아니다</b> — 확정 여부는 {@link #onboardingStatus}가 갖는다
     * (리뷰 S-1). 승인 대기 중에도 값은 저장된다(관리자가 무엇을 승인할지 봐야 하므로).
     */
    private LocalDate hireDate;

    /** 승인 대기 중 입사일을 스스로 고쳤는가 — 수정은 1회뿐이다 */
    @Column(nullable = false)
    @Builder.Default
    private boolean onboardingRevised = false;

    /** 온보딩 확정 여부 (리뷰 S-1) — 연차 부여·인터셉터·스케줄러가 모두 이 값을 본다 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OnboardingStatus onboardingStatus = OnboardingStatus.NOT_STARTED;

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

    /**
     * 1년 미만 월차 누적 적립 횟수 (docs/09 §3, 갭분석 B-1).
     *
     * <p>docs/09 §3은 {@code last_monthly_grant_date}(날짜)를 두고 거기서 횟수를 역산할 생각이었으나,
     * <b>말일 클램프 때문에 역산이 성립하지 않는다</b> — {@code 1/31.plusMonths(1)}은 2/28인데
     * {@code MONTHS.between(1/31, 2/28)}은 0이라 적립하고도 0회로 읽힌다. 그래서 횟수를 직접 센다.
     *
     * <p>다음 적립일은 {@code hire_date + (횟수+1)개월}로 계산한다 — 직전 지급일에서 한 달씩
     * 더해 나가면 클램프가 누적돼 지급일이 앞당겨진다(1/31 → 2/28 → 3/28 → 4/28…).
     * {@code base_days} 역산을 쓰지 않는 이유는 docs/09 §3(관리자 직접 설정으로 오염) 그대로다.
     */
    @Column(nullable = false)
    @Builder.Default
    private int monthlyGrantedCount = 0;

    /** 마지막 생일 반차 지급 연도 — 같은 해 중복 지급 차단 (docs/09 §3). null이면 미지급 */
    private Integer lastBirthdayGrantYear;

    /** true: 재직 / false: 퇴직 */
    @Column(nullable = false)
    private boolean isActive;

    /** 퇴사일 */
    private LocalDate retiredAt;

    /** 실제 데이터 파기 시각 — 재파기 방지와 파기 증적. */
    @Column(name = "purged_at")
    private LocalDateTime purgedAt;

    /** 파기 보류 사유 — null이면 보류 아님. */
    @Column(name = "purge_hold_reason", length = 255)
    private String purgeHoldReason;

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

    /**
     * 온보딩 확정 여부 (검증 Y-2, 리뷰 S-1).
     * <b>{@code hire_date != null}로 판별하지 말 것</b> — 승인 대기 중에도 입사일은 채워져 있다.
     */
    public boolean isOnboardingCompleted() {
        return onboardingStatus == OnboardingStatus.COMPLETED;
    }

    /**
     * 온보딩 확정: 생일·입사일 입력. 기산일은 입사일로 초기화.
     * base_days 산정(정책 조회)은 서비스에서 resetAnnualLeave로 수행한다.
     *
     * <p>자동 승인 범위 안이면 신청 즉시, 밖이면 관리자 승인 시점에 호출된다 (리뷰 S-1).
     */
    public void completeOnboarding(LocalDate hireDate, LocalDate birthDay) {
        this.hireDate = hireDate;
        this.birthDay = birthDay;
        this.lastResetDate = hireDate;
        this.onboardingStatus = OnboardingStatus.COMPLETED;
    }

    /**
     * 온보딩 승인 요청 — 입사일이 자동 승인 범위를 벗어난 경우 (리뷰 S-1).
     *
     * <p><b>기산일을 세우지 않고 연차도 부여하지 않는다.</b> 이 상태에서 연차가 붙으면 승인 절차가
     * 무의미해진다. 스케줄러도 {@code COMPLETED}만 대상으로 삼으므로 이 사원은 월차·리셋·생일 반차
     * 어디에도 걸리지 않는다.
     */
    public void requestOnboardingApproval(LocalDate hireDate, LocalDate birthDay) {
        this.hireDate = hireDate;
        this.birthDay = birthDay;
        this.onboardingStatus = OnboardingStatus.PENDING_APPROVAL;
    }

    /**
     * 승인 대기 중 수정권 사용 표시.
     * 서비스가 상태·설정·미래일을 먼저 검증한 뒤 호출해야 거부된 요청이 수정권을 소모하지 않는다.
     */
    public void markOnboardingRevised() {
        this.onboardingRevised = true;
    }

    /**
     * 온보딩 승인 반려 — 입력값을 지우고 처음으로 되돌린다 (리뷰 S-1).
     * 되돌리지 않으면 사원이 올바른 입사일로 다시 낼 방법이 없다 — 그게 S-1에서 관리자가
     * DB를 직접 고쳐야 했던 이유다.
     *
     * <p>반려는 새 제출 사이클을 여는 동작이므로 이전 사이클에서 사용한 수정권도 함께 되돌린다.
     */
    public void rejectOnboarding() {
        this.hireDate = null;
        this.birthDay = null;
        this.onboardingRevised = false;
        this.onboardingStatus = OnboardingStatus.NOT_STARTED;
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
     * 저장 직전 파생값 재계산 — <b>불변식이 깨진 채로는 DB에 들어갈 수 없게 하는 마지막 관문</b>
     * (1차 테스트 J).
     *
     * <p>클래스 수준 {@code @Builder}가 공개돼 있어
     * {@code User.builder().baseDays(10).useDays(13).advanceDays(0)}처럼 네 필드를 개별 지정하면
     * 저장 전부터 (B)가 깨진다. 빌더를 막으면 호출부 27곳(대부분 테스트)을 다 고쳐야 해서,
     * <b>영속화 경계에서 강제</b>하는 쪽을 택했다.
     *
     * <p>파생값을 다시 계산할 뿐이라 정상 경로에서는 값이 바뀌지 않는다(멱등).
     * 도메인 메서드의 {@code syncAdvanceDays()} 호출을 대체하지 않는다 — 그쪽은 flush 전에도
     * 메모리 상태가 맞아야 하기 때문이다. 이건 그물이지 대체재가 아니다.
     */
    @PrePersist
    @PreUpdate
    private void enforceAdvanceDaysInvariant() {
        syncAdvanceDays();
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
     * <p><b>채무는 리셋 직전의 현재 회차 사용분으로 계산한다.</b> 다음 회차 예약분은 신청 시
     * {@code use_days}에 들어가지 않으므로 {@code carriedUse}를 빼지 않는다(설계 §3):
     * <pre>
     * oldYearUse  = use
     * oldYearDebt = max(0, use − base − bonus)
     * newBase     = 정책연차 − oldYearDebt
     * newUse      = carriedUse
     * </pre>
     *
     * <p>{@code carryOverDebt()}는 리셋 직전 {@code advance_days}와 같은 값이지만,
     * 채무는 리셋의 개념으로 독립 계산한다. 파생 필드를 그대로 읽어 쓰지 않는 이유는
     * 채무 식의 의미와 파생값의 저장 시점을 분리하기 위해서다.
     *
     * <pre>
     * 검산 1 base=15, 현재 사용 10, 다음 회차 예약 3
     *   채무 0 → newBase 15, newUse 3 → 잔여 12
     *
     * 검산 2 base=15, 현재 사용 20, 다음 회차 예약 3
     *   채무 5 → newBase 10, newUse 3 → 잔여 7
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
        // useDays를 새 회차 이월분으로 바꾸기 전에 리셋 직전 회차의 채무를 읽는다.
        BigDecimal debt = carryOverDebt();
        this.baseDays = newBaseDays.subtract(debt);
        this.useDays = carriedUse;
        this.bonusDays = carriedBonus;
        this.lastResetDate = resetDate;
        // advance_days는 여기서 대입하지 않는다 — 파생값의 writer는 syncAdvanceDays 하나뿐이다.
        syncAdvanceDays();
    }

    /**
     * 이전 회차에 귀속되는 채무 — 리셋이 새 연차에서 깎는 양이자 감사 이력의 {@code advance_settled}다 (설계 §3).
     *
     * <pre>oldYearDebt = max(0, use − base − bonus)</pre>
     *
     * <p><b>리셋 직전 상태에서만 의미가 있다.</b> {@link #resetAnnualLeave}와
     * {@code LeaveResetHistory.create}가 <b>같은 식을 두 번 쓰지 않도록</b> 여기로 모았다 —
     * 다음 회차 예약분은 신청 시 {@code use_days}에 들어가지 않으므로 {@code carriedUse}를
     * 빼지 않는다. 결과적으로 이 값은 리셋 직전 {@code advance_days}와 같지만, 파생 필드를
     * 그대로 쓰지 않고 리셋의 채무 식을 계산한다.
     *
     * @return 리셋 직전 현재 회차 사용분에서 기본·보너스를 초과한 채무
     */
    public BigDecimal carryOverDebt() {
        BigDecimal bonus = bonusDays != null ? bonusDays : BigDecimal.ZERO;
        return this.useDays.subtract(this.baseDays).subtract(bonus).max(BigDecimal.ZERO);
    }

    /**
     * 1년 미만 신입 월차 적립 — 매월 1일씩 (갭분석 B-1). 상한 판정은 서비스 책임.
     * 적립 횟수를 함께 올린다 — {@link #monthlyGrantedCount} 주석 참고.
     */
    public void addMonthlyLeave() {
        this.baseDays = this.baseDays.add(BigDecimal.ONE);
        this.monthlyGrantedCount += 1;
        syncAdvanceDays();
    }

    /**
     * 온보딩 소급 월차의 적립 횟수 기록 — {@code base_days}는 {@link #resetAnnualLeave}가 이미 세팅했으므로
     * 횟수만 맞춘다. 이걸 빼면 스케줄러가 소급분을 <b>한 번 더</b> 적립한다.
     */
    public void markMonthlyGranted(int count) {
        this.monthlyGrantedCount = count;
    }

    /**
     * 생일 반차 지급 (요구사항 11) — 보너스 가산 + 지급 연도 기록.
     * 연도를 남기는 것이 멱등성의 근거다. 하루에 두 번 실행돼도 두 번 지급되지 않는다.
     */
    public void grantBirthdayLeave(BigDecimal days, int year) {
        this.lastBirthdayGrantYear = year;
        addBonusDays(days); // syncAdvanceDays 포함
    }

    /** 퇴직 처리 (SYSTEM_ADMIN 전용) — 소프트 삭제, 데이터 3년 보존 */
    public void retire(LocalDate retiredAt) {
        this.isActive = false;
        this.retiredAt = retiredAt;
    }

    /**
     * 퇴직자 개인정보 파기 — users 행은 유지하고 식별정보만 익명화한다.
     *
     * <p>부서·권한·연차 잔액은 통계와 과거 정산 검증에 필요하므로 건드리지 않는다.
     * 이 메서드가 users의 파기 상태 전이를 담당하는 유일한 경로다.
     */
    public void purge(LocalDateTime at) {
        this.name = "퇴직사원#" + this.id;
        this.email = "deleted-" + this.id + "@invalid";
        this.birthDay = null;
        this.hireDate = null;
        this.position = null;
        this.purgedAt = at;
    }

    /** 파기 보류 설정 — 사유의 공백·필수 검증은 서비스와 요청 DTO가 담당한다. */
    public void placePurgeHold(String reason) {
        this.purgeHoldReason = reason;
    }

    /** 파기 보류 해제. */
    public void releasePurgeHold() {
        this.purgeHoldReason = null;
    }

    /**
     * 퇴직 복구 (SYSTEM_ADMIN 전용) — 잘못 처리한 퇴직을 되돌린다.
     *
     * <p><b>두 플래그만 되돌린다.</b> 연차 잔액·기산일·역할·부서는 퇴직이 건드리지 않았으므로
     * 그대로 살아 있고, 반대로 퇴직이 함께 수행한 <b>팀장직 해제와 대기 결재 이관은 되살리지 않는다</b>
     * — 그사이 다른 사람이 팀장이 됐거나 이관된 결재가 이미 처리됐을 수 있어서, 되돌리면
     * 그쪽을 덮어쓴다. 자세한 이유는 {@code UserService.restore} 주석에 있다.
     */
    public void restore() {
        this.isActive = true;
        this.retiredAt = null;
    }

    /** 권한 변경 (SYSTEM_ADMIN 전용) */
    public void changeRole(Role role) {
        this.role = role;
    }

    /** 부서 배정 */
    public void assignDepartment(Department department) {
        this.department = department;
    }

    /** 내 정보 수정 — 본인이 관리하도록 허용된 이름·생일·직책만 한 경로에서 바꾼다 */
    public void updateProfile(String name, LocalDate birthDay, String position) {
        this.name = name;
        this.birthDay = birthDay;
        this.position = position;
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
