package com.mlsoft.backend.domain.leave.service;

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
import java.util.List;

/**
 * ③ 생일 반차 지급 (docs/09 §2, docs/01 요구사항 11).
 *
 * <p>생일이 지난 사원에게 연 1회 반차(0.5일)를 {@code bonus_days}로 가산한다.
 *
 * <p><b>리셋보다 뒤에 실행돼야 한다</b> — 리셋은 {@code bonus_days}를 갈아 끼우므로, 생일과 기산일이
 * 같은 날인 사원은 순서가 뒤바뀌면 그 해 반차가 지급 즉시 증발한다 (docs/09 §1).
 *
 * <p>멱등성은 {@code last_birthday_grant_year}가 보장한다 — 같은 날 두 번 실행되든 하루에 여러 번
 * 실행되든 그 해 지급은 한 번뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BirthdayLeaveGrantService {

    /** 생일 반차 = 0.5일 (docs/01 요구사항 11) */
    private static final BigDecimal BIRTHDAY_LEAVE_DAYS = new BigDecimal("0.5");

    private final UserRepository userRepository;

    /** 올해 아직 생일 반차를 못 받은 사원 id — 생일 도래 여부는 {@link #grant}가 판정한다 */
    @Transactional(readOnly = true)
    public List<Long> findTargetIds(LocalDate today) {
        return userRepository.findIdsWithoutBirthdayLeave(today.getYear());
    }

    /**
     * 생일이 지났으면 반차를 지급한다 — 사원 1명 = 1트랜잭션 (docs/09 §7).
     * 연 1회라 catch-up 반복이 없다.
     *
     * @return 지급했으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean grant(Long userId, LocalDate today) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // 온보딩 확정 전에는 대상이 아니다 — 미확정 입사일이 소급 차단 조건의 기준이 되면 안 된다 (리뷰 S-1)
        if (!user.isActive() || !user.isOnboardingCompleted()
                || user.getHireDate() == null || user.getBirthDay() == null) {
            return false;
        }
        int year = today.getYear();
        if (user.getLastBirthdayGrantYear() != null && user.getLastBirthdayGrantYear() >= year) {
            return false; // 올해 이미 지급 — 멱등
        }

        // 2/29 생일은 평년에 2/28로 조정된다 (LocalDate.withYear의 말일 보정)
        LocalDate birthdayThisYear = user.getBirthDay().withYear(year);
        if (birthdayThisYear.isAfter(today)) {
            return false; // 아직 안 옴
        }
        // 입사 전에 지나간 생일까지 소급 지급되는 것을 막는다 —
        // 이 조건이 없으면 3월 생일·7월 입사자가 입사 당일 0.5일을 받는다 (docs/09 §2)
        if (birthdayThisYear.isBefore(user.getHireDate())) {
            return false;
        }

        user.grantBirthdayLeave(BIRTHDAY_LEAVE_DAYS, year);
        // TODO(email): 생일 반차 지급 알림 — 당사자 + SYSTEM_ADMIN (docs/01 요구사항 11, docs/09 §10)
        log.info("[생일 반차] userId={}, 생일={}, 지급={}일, 보너스 누계={}",
                userId, birthdayThisYear, BIRTHDAY_LEAVE_DAYS, user.getBonusDays());
        return true;
    }
}
