package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.email.dto.ReminderTarget;
import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.LeaveReminderDispatch;
import com.mlsoft.backend.domain.email.entity.ReminderCycle;
import com.mlsoft.backend.domain.email.event.ReminderTemplateData;
import com.mlsoft.backend.domain.email.repository.LeaveReminderDispatchRepository;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.service.PolicyConfigReader;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 연차 소진 안내 대상 판정과 자동 발송 업무 이력을 담당한다.
 *
 * <p>대상 조회는 날짜를 명시적으로 받고, 실제 처리 메서드는 사원 한 명만 다룬다.
 * {@link com.mlsoft.backend.domain.leave.scheduler.LeaveScheduler}가 대상 id를 순회하며
 * 이 메서드를 프록시 경유로 호출하므로 사원별 {@code REQUIRES_NEW} 경계가 유지된다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveReminderService {

    /** 분기 첫날부터 장애 catch-up을 허용하는 기간(당일 포함 8회 실행 창). */
    private static final int QUARTER_CATCH_UP_DAYS = 7;
    private static final String PERIOD_SEPARATOR = ":";

    private final UserRepository userRepository;
    private final LeaveReminderDispatchRepository leaveReminderDispatchRepository;
    private final PolicyConfigReader policyConfigReader;
    private final EmailNotificationPublisher emailNotificationPublisher;

    /**
     * 현재 설정 주기의 자동 발송 대상 id.
     * {@code NONE} 또는 잘못된 값이면 조회·이력 생성·이벤트 발행을 모두 하지 않는다.
     */
    @Transactional(readOnly = true)
    public List<Long> findTargetIds(LocalDate today) {
        Optional<ReminderCycle> configured = configuredCycle();
        if (configured.isEmpty()) {
            return List.of();
        }
        ReminderCycle cycle = configured.get();
        int rangeDays = cycle == ReminderCycle.QUARTER
                ? policyConfigReader.getInt(PolicyConfigKey.REMINDER_LIST_DAYS)
                : cycle.rangeDays();
        return candidateUsers(today, rangeDays).stream()
                .filter(user -> isTarget(user, cycle, today))
                .map(User::getId)
                .toList();
    }

    /**
     * W4 대상 목록용 조회. 수신자 원문 이메일은 공개하지 않고 발송 가능 여부만 표시한다.
     * 목록 범위는 {@code reminder_list_days}이며, 자동 주기와 무관하게 미리보기 대상으로 쓴다.
     */
    @Transactional(readOnly = true)
    public List<ReminderTarget> findReminderTargets(LocalDate today) {
        int listDays = policyConfigReader.getInt(PolicyConfigKey.REMINDER_LIST_DAYS);
        return candidateUsers(today, listDays).stream()
                .map(user -> toTarget(user, today))
                .filter(target -> target.daysUntilReset() >= 0 && target.daysUntilReset() <= listDays)
                .toList();
    }

    /**
     * 사원 한 명의 자동 발송을 처리한다 — 사원별 {@code REQUIRES_NEW} 트랜잭션.
     * 중복 키 INSERT 충돌은 정상적인 선점 실패로 간주해 0을 반환한다.
     *
     * @return 큐에 넣었거나 이메일 없음으로 기록했으면 1, 대상 아님·중복이면 0
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int dispatch(Long userId, LocalDate today) {
        Optional<ReminderCycle> configured = configuredCycle();
        if (configured.isEmpty()) {
            return 0;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        ReminderCycle cycle = configured.get();
        if (!isTarget(user, cycle, today)) {
            return 0;
        }

        LocalDate nextResetDate = nextResetDate(user);
        String periodKey = periodKey(cycle, nextResetDate, today);
        // 순차 재실행은 INSERT 전에 빠르게 종료한다. 동시에 두 인스턴스가 들어오는 경합은
        // 아래 saveAndFlush의 UNIQUE 예외가 최종 방어선이다.
        if (leaveReminderDispatchRepository.existsByUserAndCycleAndPeriodKey(user, cycle, periodKey)) {
            return 0;
        }
        LeaveReminderDispatch dispatch = LeaveReminderDispatch.create(
                user,
                cycle,
                periodKey,
                today,
                nextResetDate,
                user.getRemainingDays());
        try {
            // saveAndFlush로 여기서 unique 충돌을 확인한다. save만 하면 커밋 시점까지
            // 예외가 미뤄져 정상적인 중복 선점이 스케줄러 실패로 보일 수 있다.
            leaveReminderDispatchRepository.saveAndFlush(dispatch);
        } catch (DataIntegrityViolationException e) {
            log.debug("[연차 소진 안내] 이미 선점된 대상 — userId={}, cycle={}, periodKey={}",
                    userId, cycle, periodKey);
            return 0;
        }

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            dispatch.markSkippedNoEmail();
            log.info("[연차 소진 안내] 이메일 없음으로 건너뜀 — userId={}, periodKey={}", userId, periodKey);
            return 1;
        }

        long daysUntilReset = ChronoUnit.DAYS.between(today, nextResetDate);
        EmailHistory history = emailNotificationPublisher.publishLeaveBalanceReminder(
                user,
                new ReminderTemplateData(
                        user.getName(),
                        user.getRemainingDays().stripTrailingZeros().toPlainString(),
                        nextResetDate.toString(),
                        String.valueOf(daysUntilReset),
                        ""));
        dispatch.attachEmailHistory(history);
        return 1;
    }

    private Optional<ReminderCycle> configuredCycle() {
        return ReminderCycle.fromConfig(policyConfigReader.getString(PolicyConfigKey.REMINDER_AUTO_CYCLE));
    }

    /**
     * 다음 기산일 범위를 last_reset_date 범위로 역산한다.
     * LocalDate.plusYears의 2월 29일 보정을 포함하므로 양 끝을 하루 넓힌 뒤
     * 서비스에서 정확한 날짜를 재확인한다.
     */
    private List<User> candidateUsers(LocalDate today, int rangeDays) {
        LocalDate resetFrom = today.minusYears(1).minusDays(1);
        LocalDate resetTo = today.plusDays(Math.max(rangeDays, 0)).minusYears(1).plusDays(1);
        List<Long> ids = userRepository.findIdsForReminderWindow(resetFrom, resetTo);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return ids.stream()
                .map(users::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(User::getId))
                .toList();
    }

    private boolean isTarget(User user, ReminderCycle cycle, LocalDate today) {
        if (!user.isActive() || !user.isOnboardingCompleted() || user.getLastResetDate() == null) {
            return false;
        }
        BigDecimal remaining = user.getRemainingDays();
        if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        LocalDate nextResetDate = nextResetDate(user);
        long daysUntilReset = ChronoUnit.DAYS.between(today, nextResetDate);
        if (daysUntilReset < 0) {
            // 리셋 실패로 기산일이 지난 사원은 리셋 잡의 로그·재시도를 우선한다.
            return false;
        }
        if (cycle != ReminderCycle.QUARTER) {
            return daysUntilReset <= cycle.rangeDays();
        }
        int listDays = policyConfigReader.getInt(PolicyConfigKey.REMINDER_LIST_DAYS);
        return daysUntilReset <= listDays && inQuarterCatchUpWindow(today);
    }

    private LocalDate nextResetDate(User user) {
        return user.getLastResetDate().plusYears(1);
    }

    private String periodKey(ReminderCycle cycle, LocalDate nextResetDate, LocalDate today) {
        if (cycle != ReminderCycle.QUARTER) {
            return cycle.name() + PERIOD_SEPARATOR + nextResetDate;
        }
        int quarter = (today.getMonthValue() - 1) / 3 + 1;
        return cycle.name() + PERIOD_SEPARATOR + today.getYear() + "-Q" + quarter;
    }

    private boolean inQuarterCatchUpWindow(LocalDate today) {
        int firstMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
        LocalDate quarterStart = LocalDate.of(today.getYear(), Month.of(firstMonth), 1);
        long daysSinceQuarterStart = ChronoUnit.DAYS.between(quarterStart, today);
        return daysSinceQuarterStart >= 0 && daysSinceQuarterStart <= QUARTER_CATCH_UP_DAYS;
    }

    private ReminderTarget toTarget(User user, LocalDate today) {
        LocalDate nextReset = nextResetDate(user);
        String departmentName = user.getDepartment() == null ? null : user.getDepartment().getName();
        return new ReminderTarget(
                user.getId(),
                user.getName(),
                departmentName,
                user.getRemainingDays(),
                nextReset,
                ChronoUnit.DAYS.between(today, nextReset),
                user.getEmail() != null && !user.getEmail().isBlank());
    }
}
