package com.mlsoft.backend.domain.leave.service;

import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
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

import java.time.LocalDate;
import java.util.List;

/**
 * ② 1년 미만 신입 월차 적립 (docs/09 §2·§6, 갭분석 B-1, 근로기준법 §60②).
 *
 * <p>입사 1년이 안 된 사원에게 매월 1일씩, 상한({@code monthly_leave_max_days}, 기본 11)까지 적립한다.
 * 1주년에 남은 월차는 <b>소멸</b>한다 — 리셋 잡이 먼저 돌아 정책 연차로 갈아 끼우고, 그 시점부터
 * 이 잡의 "1년 미만" 조건이 거짓이 되기 때문이다 (docs/09 §1 실행 순서).
 *
 * <p><b>지급일을 {@code hire_date + N개월}로 계산</b>하는 것이 이 잡의 핵심이다. 직전 지급일에서
 * 한 달씩 더해 나가면 말일 클램프가 누적돼 지급일이 앞당겨진다 — 1/31 입사자가
 * 2/28 → 3/28 → 4/28로 밀려 원래 날짜를 영영 회복하지 못한다. {@code hire_date}에서 매번 다시
 * 더하면 2/28 → 3/31 → 4/30으로 제자리를 찾는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyLeaveGrantService {

    private final UserRepository userRepository;
    private final PolicyConfigReader policyConfigReader;

    /** 입사 1년 미만 사원 id — 실제 적립 시점 도래 여부는 {@link #grant}가 판정한다 */
    @Transactional(readOnly = true)
    public List<Long> findTargetIds(LocalDate today) {
        return userRepository.findIdsUnderOneYear(today.minusYears(1));
    }

    /**
     * 밀린 월차를 한 번에 적립한다 — 사원 1명 = 1트랜잭션 (docs/09 §7).
     * 서버가 3개월 꺼져 있었으면 3회분이 이번 실행에서 함께 적립된다 (§6 catch-up).
     *
     * @return 이번에 적립한 횟수 (0이면 도래한 회차 없음)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int grant(Long userId, LocalDate today) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        LocalDate hireDate = user.getHireDate();
        if (!user.isActive() || hireDate == null) {
            return 0;
        }
        // 1주년이 지났으면 대상이 아니다 — 남은 월차는 소멸하고 리셋 잡이 정책 연차를 부여한다 (갭분석 B-2)
        if (!hireDate.plusYears(1).isAfter(today)) {
            return 0;
        }

        int maxDays = policyConfigReader.getInt(PolicyConfigKey.MONTHLY_LEAVE_MAX_DAYS);
        LocalDate firstAnniversary = hireDate.plusYears(1);
        int granted = 0;
        while (user.getMonthlyGrantedCount() < maxDays) {
            LocalDate dueDate = hireDate.plusMonths(user.getMonthlyGrantedCount() + 1L);
            if (dueDate.isAfter(today) || !dueDate.isBefore(firstAnniversary)) {
                break; // 아직 안 됐거나, 1주년 이후분(상한을 12로 올린 경우)이라 적립 대상이 아니다
            }
            user.addMonthlyLeave();
            granted++;
        }

        if (granted > 0) {
            log.info("[월차 적립] userId={}, {}회분 적립, 누적={}회, 총 연차={}",
                    userId, granted, user.getMonthlyGrantedCount(), user.getBaseDays());
        }
        return granted;
    }
}
