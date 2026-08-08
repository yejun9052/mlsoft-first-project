package com.mlsoft.backend.domain.leave.repository;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 미래 승인분 재차감 집계의 <b>실제 SQL 검증</b> (H2, MySQL 모드) — docs/09 §5.
 *
 * <p>Mockito 단위 테스트는 이 집계를 stub으로 대체하므로 JPQL이 실제로 도는지,
 * {@code @ElementCollection} 조인에서 날짜가 중복 계상되지 않는지, 단가를 곱한 합계가 맞는지를
 * 확인하지 못한다. 리셋 전체가 이 한 숫자 위에 서 있으므로 여기서 DB로 고정한다.
 *
 * <p>특히 <b>경계일 포함 여부</b>가 {@code LeaveRequest.daysOnOrAfter}(자바 {@code !isBefore})와
 * 같아야 한다 — 어긋나면 리셋이 넣은 값과 취소가 되돌리는 값이 달라져 {@code use_days}가 어긋난다.
 */
@SpringBootTest
@ActiveProfiles("test")
class LeaveRequestRepositoryIntegrationTest {

    private static final List<RequestStatus> PRE_DEDUCTED =
            List.of(RequestStatus.APPROVED, RequestStatus.PENDING, RequestStatus.CANCEL_PENDING);

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("기산일 이후 선차감 합계 — 경계일을 포함하고 종류별 단가를 곱해 더한다")
    void sumPreDeductedDaysOnOrAfter_경계포함_단가적용() {
        User user = saveUser();
        LocalDate boundary = LocalDate.of(2026, 3, 1);
        // 연차 3일: 2/28(경계 이전) · 3/1(경계 당일) · 3/2  → 경계 이후 2.0
        LeaveRequest annual = save(user, LeaveType.ANNUAL,
                List.of(boundary.minusDays(1), boundary, boundary.plusDays(1)));
        // 오전 반차 2일: 3/3 · 3/4 → 경계 이후 1.0
        save(user, LeaveType.HALF_AM, List.of(boundary.plusDays(2), boundary.plusDays(3)));

        BigDecimal sum = leaveRequestRepository.sumPreDeductedDaysOnOrAfter(user, boundary, PRE_DEDUCTED);

        // 2.0(연차) + 1.0(반차 0.5 × 2) = 3.0
        assertEquals(0, new BigDecimal("3.0").compareTo(sum), "실제 " + sum);
        // 같은 신청을 자바 쪽 계산으로 봐도 경계 처리가 같아야 한다 (리뷰 I-10과 짝을 이루는 값)
        assertEquals(0, new BigDecimal("2.0").compareTo(annual.daysOnOrAfter(boundary)));
    }

    @Test
    @DisplayName("복구가 끝난 상태(CANCELLED·REJECTED)는 이월 집계에서 빠진다")
    void sumPreDeductedDaysOnOrAfter_복구된건_제외() {
        // 넣으면 이중 계상이 된다 — 이미 use_days에서 빠진 일수를 새 연도에 다시 차감하게 된다
        User user = saveUser();
        LocalDate boundary = LocalDate.of(2026, 3, 1);
        LeaveRequest cancelled = save(user, LeaveType.ANNUAL, List.of(boundary, boundary.plusDays(1)));
        cancelled.cancel("취소");
        leaveRequestRepository.save(cancelled);

        assertEquals(0, BigDecimal.ZERO.compareTo(
                leaveRequestRepository.sumPreDeductedDaysOnOrAfter(user, boundary, PRE_DEDUCTED)));
    }

    @Test
    @DisplayName("기산일 이후 날짜가 하나도 없으면 0을 돌려준다 — null이 아니다")
    void sumPreDeductedDaysOnOrAfter_대상없음_0반환() {
        // 집계가 null을 주면 리셋의 BigDecimal 연산이 NPE로 터지고 그 사원의 리셋이 통째로 실패한다
        User user = saveUser();
        LocalDate boundary = LocalDate.of(2026, 3, 1);
        save(user, LeaveType.ANNUAL, List.of(boundary.minusDays(5), boundary.minusDays(4)));

        assertEquals(0, BigDecimal.ZERO.compareTo(
                leaveRequestRepository.sumPreDeductedDaysOnOrAfter(user, boundary, PRE_DEDUCTED)));
    }

    // ---- 헬퍼 ----

    private User saveUser() {
        return userRepository.save(User.builder()
                .name("reset-target")
                // 시연 데이터 시더 테스트가 @mlsoft.com 계정을 자기 것으로 세지 않게 도메인을 분리한다
                .email("reset-" + System.nanoTime() + "@integration.test")
                .role(Role.EMPLOYEE)
                .hireDate(LocalDate.of(2025, 3, 1))
                .lastResetDate(LocalDate.of(2025, 3, 1))
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build());
    }

    /** 승인자는 본인으로 둔다 — 이 테스트는 집계 SQL만 보므로 결재 규칙과 무관하다 */
    private LeaveRequest save(User user, LeaveType type, List<LocalDate> dates) {
        return leaveRequestRepository.save(
                LeaveRequest.create(user, type, dates, "테스트", user, null));
    }
}
