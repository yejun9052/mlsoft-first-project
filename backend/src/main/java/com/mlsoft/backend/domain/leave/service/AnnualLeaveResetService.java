package com.mlsoft.backend.domain.leave.service;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.leave.entity.LeaveResetHistory;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.leave.repository.LeaveResetHistoryRepository;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.service.LeavePolicyService;
import com.mlsoft.backend.domain.policy.service.PolicyConfigReader;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * ① 기산일 리셋 (docs/09 §4·§5·§6, docs/01 2-7).
 *
 * <p>한 해가 끝나면 미사용 연차는 소멸하고 근속년수에 맞는 새 연차가 부여된다. 단순히 값을 갈아 끼우는
 * 게 아니라 세 가지를 함께 정산한다:
 * <ol>
 *   <li><b>미래 승인분 재차감</b> — 기산일 이후 날짜로 이미 승인·대기 중인 건은 선차감이 유지돼야 한다.
 *       그러지 않으면 리셋이 그 차감을 지우고, 사원은 같은 연차를 두 번 받는다 (§5)</li>
 *   <li><b>이전 연도 채무 이월</b> — 당겨쓴 만큼을 새 연차에서 뺀다. 빼지 않으면 빚이 면제된다 (리뷰 I-11)</li>
 *   <li><b>보너스 이월</b> — 설정이 켜져 있을 때만, 이미 써버린 몫을 제외하고 넘긴다 (docs/02 메모 10)</li>
 * </ol>
 *
 * <p><b>catch-up</b>: 서버가 며칠·몇 년 꺼져 있었어도 회차를 하나씩 되짚는다 (§6). 최근 기산일로 한 번에
 * 점프하지 않는 이유는 중간 연도의 {@code leave_reset_history}가 통째로 비어 사후 검증이 불가능해지기
 * 때문이다. <b>회차마다 그 회차의 기산일로 {@code carriedUse}를 다시 집계</b>한다 — 최종 기산일 기준으로
 * 한 번에 계산하면 1차 연도에 귀속돼야 할 날짜가 한 회차 일찍 빠져나가 그 해 이력이 틀린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnualLeaveResetService {

    /** 선차감이 유지되고 있는 상태 — 이월 집계 대상 (CANCELLED·REJECTED는 이미 복구됨, §5) */
    private static final List<RequestStatus> PRE_DEDUCTED_STATUSES =
            List.of(RequestStatus.APPROVED, RequestStatus.PENDING, RequestStatus.CANCEL_PENDING);

    /**
     * catch-up 회차 상한 — 정상 데이터에서는 도달할 수 없다(입사 50년).
     * 데이터가 깨져 {@code last_reset_date}가 비정상적으로 과거면 루프가 사실상 멈추지 않으므로,
     * 배치 전체를 붙잡기 전에 끊고 WARN을 남긴다.
     */
    private static final int MAX_CATCH_UP_ROUNDS = 50;

    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveResetHistoryRepository leaveResetHistoryRepository;
    private final LeavePolicyService leavePolicyService;
    private final PolicyConfigReader policyConfigReader;

    /** 오늘 기준 리셋 대상 사원 id — {@code last_reset_date + 1년 <= 오늘} (검증 Y-1) */
    @Transactional(readOnly = true)
    public List<Long> findTargetIds(LocalDate today) {
        return userRepository.findIdsDueForAnnualReset(today.minusYears(1));
    }

    /**
     * 한 사원의 밀린 기산일을 전부 소급 처리한다 — <b>사원 1명 = 1트랜잭션</b> (docs/09 §7).
     *
     * <p>{@code User}에 낙관적 락이 걸려 있어(검증 R-5) 배치 도중 그 사원이 연차를 신청하면 충돌한다.
     * 전체를 한 트랜잭션으로 묶으면 수백 명 중 1명의 충돌로 전원이 롤백되므로 사원별로 끊는다.
     * 실패한 사원은 다음 실행에서도 같은 조건에 걸리므로 <b>재시도 큐 없이 자동 복구</b>된다.
     *
     * @return 이번에 처리한 리셋 회차 수 (0이면 대상이 아니었음)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int reset(Long userId, LocalDate today) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // 조회 이후 상태가 바뀌었을 수 있다 — 트랜잭션 안에서 가드를 다시 본다.
        // 온보딩은 hire_date가 아니라 확정 여부로 본다 — 승인 대기 중에도 입사일은 채워져 있다 (리뷰 S-1)
        if (!user.isActive() || !user.isOnboardingCompleted() || user.getLastResetDate() == null) {
            return 0;
        }
        // 기산일이 입사일보다 앞서면 데이터가 깨진 것이다. 그냥 돌리면 입사 전 날짜의 리셋 이력이 쌓이고,
        // 근속년수가 음수라 calculateAnnualLeaveDays가 1년차로 보정해 버려 오류가 드러나지도 않는다
        if (user.getLastResetDate().isBefore(user.getHireDate())) {
            log.warn("[기산일 리셋] 기산일이 입사일보다 이르다 — 건너뜀. userId={}, hireDate={}, lastResetDate={}",
                    userId, user.getHireDate(), user.getLastResetDate());
            return 0;
        }

        boolean carryOverEnabled = policyConfigReader.getBoolean(PolicyConfigKey.BONUS_CARRY_OVER_ENABLED);
        int rounds = 0;
        while (rounds < MAX_CATCH_UP_ROUNDS && !user.getLastResetDate().plusYears(1).isAfter(today)) {
            applyOneRound(user, user.getLastResetDate().plusYears(1), carryOverEnabled);
            rounds++;
        }
        if (rounds == MAX_CATCH_UP_ROUNDS) {
            log.warn("[기산일 리셋] catch-up 상한 도달 — 데이터 확인 필요. userId={}, lastResetDate={}",
                    userId, user.getLastResetDate());
        }
        return rounds;
    }

    /**
     * 한 회차. <b>이력을 상태 전이보다 먼저 쓴다</b> — {@code LeaveResetHistory.create()}가 리셋 직전 상태를
     * 스냅샷하므로, 먼저 전이시키면 기록할 원본이 사라진다 (docs/09 §4).
     */
    private void applyOneRound(User user, LocalDate resetDate, boolean carryOverEnabled) {
        int yearsOfService = (int) ChronoUnit.YEARS.between(user.getHireDate(), resetDate);
        BigDecimal policyDays = leavePolicyService.calculateAnnualLeaveDays(yearsOfService);
        BigDecimal carriedUse = leaveRequestRepository.sumPreDeductedDaysWithin(
                user, resetDate, resetDate.plusYears(1), PRE_DEDUCTED_STATUSES);
        BigDecimal carriedBonus = carryOverEnabled ? carriedBonus(user) : BigDecimal.ZERO;

        leaveResetHistoryRepository.save(
                LeaveResetHistory.create(user, resetDate, policyDays, carriedUse, carriedBonus));
        user.resetAnnualLeave(policyDays, carriedUse, carriedBonus, resetDate);

        if (user.getRemainingDays().signum() < 0) {
            // 이미 승인된 건이라 되돌리지 않는다. 음수 폭은 advance_max_days 상한이 실질적으로 제한한다 (§5)
            log.warn("[기산일 리셋] 재차감 후 잔여 음수 — userId={}, resetDate={}, 잔여={}, 이월사용={}",
                    user.getId(), resetDate, user.getRemainingDays(), carriedUse);
        }
        log.info("[기산일 리셋] userId={}, resetDate={}, {}년차, 새 연차={}, 이월사용={}, 이월보너스={}",
                user.getId(), resetDate, yearsOfService, user.getBaseDays(), carriedUse, carriedBonus);
    }

    /**
     * 이월 보너스 = {@code max(0, bonus − max(0, 이전연도 사용분 − base))} (docs/02 메모 10).
     *
     * <p>이전 연도 사용분이 {@code base}를 넘은 만큼이 <b>보너스에서 쓴 분량</b>이다. 리셋 때
     * {@code use}가 0이 되므로 이 보정 없이 {@code bonus}를 그대로 넘기면 이미 써버린 보너스가 부활한다.
     *
     * <p>새 모델에서는 다음 회차 예약분이 신청 시 {@code use_days}에 들어가지 않으므로
     * {@code carriedUse}를 빼지 않는다. 리셋 직전 {@code use_days}가 곧 이전 회차의 실제 사용분이다
     * (설계 §3). 이월분을 다시 빼면 같은 날짜를 두 번 제외하게 된다.
     */
    private BigDecimal carriedBonus(User user) {
        BigDecimal bonus = user.getBonusDays() != null ? user.getBonusDays() : BigDecimal.ZERO;
        BigDecimal oldYearUse = user.getUseDays();
        BigDecimal spentFromBonus = oldYearUse.subtract(user.getBaseDays()).max(BigDecimal.ZERO);
        return bonus.subtract(spentFromBonus).max(BigDecimal.ZERO);
    }
}
