package com.mlsoft.backend.domain.leave.service;

import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.leave.dto.ApprovalRequest;
import com.mlsoft.backend.domain.leave.dto.CancelRequest;
import com.mlsoft.backend.domain.leave.dto.LeaveCreateRequest;
import com.mlsoft.backend.domain.leave.dto.LeaveResponse;
import com.mlsoft.backend.domain.leave.dto.LeaveSummaryResponse;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.leave.repository.LeaveRequestRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 연차 서비스 통합 테스트 (H2, MySQL 모드) — 실제 SQL 실행 + 커밋 경계 검증.
 * 단위 테스트(Mockito)가 못 잡는 "JPQL이 DB에서 실제로 도는가 / @Modifying 조건부 갱신이 커밋되는가"를 확인한다.
 * 부서 팀장을 직접 지정해 승인자를 통제하므로 OAuth 인증 우회가 필요 없다.
 */
@SpringBootTest
class LeaveServiceIntegrationTest {

    @Autowired
    private LeaveService leaveService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Test
    @DisplayName("신청 → 요약·조회 6종 → 승인 → 취소 전체 흐름이 실제 DB에서 동작한다")
    void applyApproveCancel_endToEnd() {
        // given: 팀장이 있는 부서 + 소속 사원
        User leader = saveUser("leader", Role.TEAM_LEADER, null);
        Department department = Department.create("개발팀", "통합테스트 부서", null);
        department.assignLeader(leader);
        departmentRepository.save(department);
        User applicant = saveUser("emp", Role.EMPLOYEE, department);
        List<LocalDate> dates = futureWeekdays(2);

        // when: 신청 — 선차감
        LeaveResponse created = leaveService.apply(
                applicant.getId(), new LeaveCreateRequest(LeaveType.ANNUAL, dates, "휴가", null));
        Long leaveId = created.id();

        // then: 승인자는 팀장, 잔여 2일 차감
        assertNotNull(leaveId);
        assertEquals("PENDING", created.status());
        assertEquals(leader.getId(), created.primaryApproverId());
        assertEquals(0, new BigDecimal("2.0").compareTo(reload(applicant).getUseDays()));

        // 요약 — 사용 2 / 대기 2 / 잔여 13
        LeaveSummaryResponse summary = leaveService.getMySummary(applicant.getId());
        assertEquals(0, new BigDecimal("2.0").compareTo(summary.useDays()));
        assertEquals(0, new BigDecimal("2.0").compareTo(summary.pendingDays()));
        assertEquals(0, new BigDecimal("13.0").compareTo(summary.remainingDays()));

        // 조회 쿼리 6종이 실제로 실행되는지 (JPQL 실행 검증)
        assertEquals(1, leaveService.getMyLeaves(applicant.getId(), null, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, leaveService.getMyLeaves(applicant.getId(), RequestStatus.APPROVED, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, leaveService.getPending(leader.getId(), PageRequest.of(0, 10)).getTotalElements());
        assertNotNull(leaveService.getAllForAdmin(null, null, PageRequest.of(0, 10)));
        assertEquals(1, leaveService.getTeam(applicant.getId(), dates.get(0), dates.get(dates.size() - 1)).size());
        assertEquals(1, leaveService.getHistories(leaveId, applicant.getId()).size()); // 신청(PENDING) 이력 1건

        // when: 팀장 승인 — 조건부 갱신
        leaveService.processApproval(leaveId, leader.getId(), new ApprovalRequest(true, "승인"));

        // then: 상태 APPROVED, 이력 2건, 잔여 유지
        assertEquals(RequestStatus.APPROVED, reloadLeave(leaveId).getStatus());
        assertEquals(2, leaveService.getHistories(leaveId, applicant.getId()).size());
        assertEquals(0, new BigDecimal("2.0").compareTo(reload(applicant).getUseDays()));
        // 승인 연차는 캘린더에 노출 (사유 열람 권한자이므로 마스킹 해제)
        assertNotNull(leaveService.getCalendar(applicant.getId(), dates.get(0).getYear(), dates.get(0).getMonthValue()));

        // when: 본인 취소 (미래 날짜만) — 즉시 취소 + 복구
        RequestStatus result = leaveService.cancel(leaveId, applicant.getId(), new CancelRequest("일정 변경"));

        // then: CANCELLED, 선차감 복구
        assertEquals(RequestStatus.CANCELLED, result);
        assertEquals(RequestStatus.CANCELLED, reloadLeave(leaveId).getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(reload(applicant).getUseDays()));
    }

    // ---- 헬퍼 ----

    private User saveUser(String name, Role role, Department department) {
        return userRepository.save(User.builder()
                .name(name)
                .email(name + "-" + System.nanoTime() + "@mlsoft.com")
                .role(role)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .department(department)
                .build());
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private LeaveRequest reloadLeave(Long leaveId) {
        return leaveRequestRepository.findById(leaveId).orElseThrow();
    }

    private List<LocalDate> futureWeekdays(int count) {
        List<LocalDate> result = new java.util.ArrayList<>();
        LocalDate date = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(1);
        while (result.size() < count) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                result.add(date);
            }
            date = date.plusDays(1);
        }
        return result;
    }
}
