package com.mlsoft.backend.domain.leave.repository;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 결재자 기준 이력 조회의 <b>실제 SQL 검증</b> (H2, MySQL 모드) — 리뷰 S-6.
 *
 * <p>단위 테스트는 이 조회를 stub으로 대체하므로 정작 위험한 부분이 검증되지 않는다:
 * {@code h.leaveRequest.primaryApprover.id}가 만드는 <b>암묵 조인</b>이 실제로 도는지,
 * {@code @EntityGraph}와 {@code @Query}를 함께 쓴 것이 충돌하지 않는지,
 * primary·sub <b>둘 중 하나</b>만 나여도 걸리는지, 남의 결재 건이 새지 않는지.
 *
 * <p>스코프 변경 전에는 신청자 소속 부서로 묶었다. 부서를 옮긴 사원의 과거 이력이 새 팀장에게
 * 보이고, 퇴직 이관으로 넘겨받은 건은 내 부서가 아니라 안 보였던 것이 바꾼 이유다.
 */
@SpringBootTest
@ActiveProfiles("test")
class LeaveActionHistoryRepositoryIntegrationTest {

    private static final PageRequest LATEST_FIRST =
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));

    @Autowired
    private LeaveActionHistoryRepository leaveActionHistoryRepository;
    @Autowired
    private LeaveRequestRepository leaveRequestRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("primary 승인자인 건이 걸린다 — 부서를 보지 않는다")
    void findByApprover_primary() {
        User applicant = saveUser("신청자");
        User primary = saveUser("주결재자");
        saveHistory(applicant, primary, null, primary, RequestAction.PENDING);

        Page<LeaveActionHistory> page =
                leaveActionHistoryRepository.findByApprover(primary.getId(), null, LATEST_FIRST);

        assertEquals(1, page.getTotalElements());
        assertEquals(applicant.getId(), page.getContent().get(0).getUser().getId());
    }

    @Test
    @DisplayName("sub 승인자만 나여도 걸린다 — or 조건이 실제로 동작한다")
    void findByApprover_sub() {
        User applicant = saveUser("신청자");
        User primary = saveUser("주결재자");
        User sub = saveUser("서브결재자");
        saveHistory(applicant, primary, sub, primary, RequestAction.APPROVED);

        Page<LeaveActionHistory> page =
                leaveActionHistoryRepository.findByApprover(sub.getId(), null, LATEST_FIRST);

        assertEquals(1, page.getTotalElements());
    }

    @Test
    @DisplayName("내가 결재자가 아닌 건은 새지 않는다")
    void findByApprover_excludesOthers() {
        User applicant = saveUser("신청자");
        User primary = saveUser("주결재자");
        User outsider = saveUser("무관한팀장");
        saveHistory(applicant, primary, null, primary, RequestAction.PENDING);

        Page<LeaveActionHistory> page =
                leaveActionHistoryRepository.findByApprover(outsider.getId(), null, LATEST_FIRST);

        assertTrue(page.isEmpty(), "실제 " + page.getTotalElements() + "건");
    }

    @Test
    @DisplayName("남이 처리한 이력도 포함된다 — actor가 아니라 승인자 지정 기준이다")
    void findByApprover_includesRowsActedByOthers() {
        // my-actions(actor 기준)와 구분되는 지점이다. 내가 primary인 건을 서브 승인자가 승인하면
        // 그 기록도 내 결재 이력에 남아야 한다.
        User applicant = saveUser("신청자");
        User primary = saveUser("주결재자");
        User sub = saveUser("서브결재자");
        saveHistory(applicant, primary, sub, sub, RequestAction.APPROVED); // actor = sub

        Page<LeaveActionHistory> page =
                leaveActionHistoryRepository.findByApprover(primary.getId(), null, LATEST_FIRST);

        assertEquals(1, page.getTotalElements());
        assertEquals(sub.getId(), page.getContent().get(0).getActor().getId());
    }

    @Test
    @DisplayName("action 필터 — null이면 전체, 지정하면 그것만")
    void findByApprover_actionFilter() {
        User applicant = saveUser("신청자");
        User primary = saveUser("주결재자");
        LeaveRequest leave = saveLeave(applicant, primary, null);
        saveHistoryFor(leave, primary, RequestAction.PENDING);
        saveHistoryFor(leave, primary, RequestAction.APPROVED);

        assertEquals(2, leaveActionHistoryRepository
                .findByApprover(primary.getId(), null, LATEST_FIRST).getTotalElements());
        assertEquals(1, leaveActionHistoryRepository
                .findByApprover(primary.getId(), RequestAction.APPROVED, LATEST_FIRST).getTotalElements());
    }

    // ---- 헬퍼 ----

    private void saveHistory(User applicant, User primary, User sub, User actor, RequestAction action) {
        saveHistoryFor(saveLeave(applicant, primary, sub), actor, action);
    }

    private void saveHistoryFor(LeaveRequest leave, User actor, RequestAction action) {
        leaveActionHistoryRepository.save(LeaveActionHistory.create(leave, actor, action, "테스트"));
    }

    private LeaveRequest saveLeave(User applicant, User primary, User sub) {
        return leaveRequestRepository.save(LeaveRequest.create(
                applicant, LeaveType.ANNUAL, List.of(LocalDate.now().plusDays(1)), "휴식", primary, sub));
    }

    private User saveUser(String name) {
        return userRepository.save(User.builder()
                .name(name)
                // 시연 데이터 시더 테스트가 @mlsoft.com 계정을 자기 것으로 세지 않게 도메인을 분리한다
                .email("history-" + System.nanoTime() + "@integration.test")
                .role(Role.TEAM_LEADER)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build());
    }
}
