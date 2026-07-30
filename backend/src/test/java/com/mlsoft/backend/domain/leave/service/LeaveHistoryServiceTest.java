package com.mlsoft.backend.domain.leave.service;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.leave.dto.LeaveHistoryLogResponse;
import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import com.mlsoft.backend.domain.leave.entity.LeaveRequest;
import com.mlsoft.backend.domain.leave.entity.LeaveType;
import com.mlsoft.backend.domain.leave.repository.LeaveActionHistoryRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 연차 처리 로그 서비스 단위 테스트 (GET /api/leave-histories — docs/03 처리 이력).
 * 핵심 관심사는 두 가지 — LAZY 연관(신청자·부서·신청)을 응답 DTO로 정확히 옮기는지,
 * 그리고 팀 스코프를 요청 파라미터가 아니라 요청자의 부서로 결정하는지.
 */
@ExtendWith(MockitoExtension.class)
class LeaveHistoryServiceTest {

    @Mock
    private LeaveActionHistoryRepository leaveActionHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LeaveHistoryService leaveHistoryService;

    private static final Pageable PAGEABLE = PageRequest.of(0, 20);

    @Test
    @DisplayName("전체 로그 — action 미지정이면 전체를 조회하고 신청자·부서·신청 정보를 함께 내려준다")
    void getHistories_withoutActionFilter_mapsAllAssociations() {
        Department department = department(10L, "개발팀");
        User applicant = user(1L, "박민수", department);
        User approver = user(2L, "김팀장", department);
        LeaveActionHistory history = history(applicant, approver, RequestAction.APPROVED, "확인했습니다");
        given(leaveActionHistoryRepository.findAll(PAGEABLE))
                .willReturn(new PageImpl<>(List.of(history), PAGEABLE, 1));

        Page<LeaveHistoryLogResponse> responses = leaveHistoryService.getHistories(null, PAGEABLE);

        assertEquals(1, responses.getTotalElements());
        LeaveHistoryLogResponse response = responses.getContent().get(0);
        assertEquals("APPROVED", response.action());
        assertEquals("김팀장", response.actorName());
        assertEquals("박민수", response.userName());
        assertEquals("개발팀", response.departmentName());
        assertEquals("ANNUAL", response.leaveType());
        assertEquals(0, new BigDecimal("2.0").compareTo(response.days()));
        assertEquals("확인했습니다", response.comment());
    }

    @Test
    @DisplayName("전체 로그 — action을 주면 해당 액션만 조회한다")
    void getHistories_withActionFilter_delegatesToFilteredQuery() {
        given(leaveActionHistoryRepository.findByAction(RequestAction.REJECTED, PAGEABLE))
                .willReturn(new PageImpl<>(List.of(), PAGEABLE, 0));

        leaveHistoryService.getHistories(RequestAction.REJECTED, PAGEABLE);

        verify(leaveActionHistoryRepository).findByAction(RequestAction.REJECTED, PAGEABLE);
        verify(leaveActionHistoryRepository, never()).findAll(PAGEABLE);
    }

    @Test
    @DisplayName("팀 로그 — 요청자의 소속 부서로 스코프한다")
    void getMyTeamHistories_scopesToRequesterDepartment() {
        Department department = department(10L, "개발팀");
        User leader = user(2L, "김팀장", department);
        User applicant = user(1L, "박민수", department);
        given(userRepository.findById(2L)).willReturn(Optional.of(leader));
        given(leaveActionHistoryRepository.findByUserDepartmentId(10L, PAGEABLE))
                .willReturn(new PageImpl<>(
                        List.of(history(applicant, leader, RequestAction.PENDING, "신청")), PAGEABLE, 1));

        Page<LeaveHistoryLogResponse> responses = leaveHistoryService.getMyTeamHistories(2L, null, PAGEABLE);

        assertEquals(1, responses.getTotalElements());
        assertEquals("개발팀", responses.getContent().get(0).departmentName());
        verify(leaveActionHistoryRepository).findByUserDepartmentId(10L, PAGEABLE);
    }

    @Test
    @DisplayName("팀 로그 — 부서 미배정 요청자는 조회 없이 빈 페이지를 받는다")
    void getMyTeamHistories_withoutDepartment_returnsEmptyPage() {
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L, "김팀장", null)));

        Page<LeaveHistoryLogResponse> responses = leaveHistoryService.getMyTeamHistories(2L, null, PAGEABLE);

        assertTrue(responses.isEmpty());
        verify(leaveActionHistoryRepository, never()).findByUserDepartmentId(anyLong(), any());
    }

    @Test
    @DisplayName("팀 로그 — 존재하지 않는 요청자면 USER_NOT_FOUND")
    void getMyTeamHistories_withUnknownRequester_throws() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> leaveHistoryService.getMyTeamHistories(99L, null, PAGEABLE));
    }

    @Test
    @DisplayName("전체 로그 — 부서 미배정 신청자의 로그도 departmentName만 null로 정상 변환된다")
    void getHistories_withoutApplicantDepartment_mapsNullDepartmentName() {
        User applicant = user(1L, "박민수", null);
        User approver = user(2L, "김관리자", null);
        given(leaveActionHistoryRepository.findAll(PAGEABLE))
                .willReturn(new PageImpl<>(
                        List.of(history(applicant, approver, RequestAction.APPROVED, "확인")), PAGEABLE, 1));

        Page<LeaveHistoryLogResponse> responses = leaveHistoryService.getHistories(null, PAGEABLE);

        assertNull(responses.getContent().get(0).departmentName());
        assertEquals("박민수", responses.getContent().get(0).userName());
    }

    // ---------------------------------------------------------------------
    // 픽스처
    // ---------------------------------------------------------------------

    private static Department department(Long id, String name) {
        return Department.builder().id(id).name(name).description(name + " 설명").active(true).build();
    }

    private static User user(Long id, String name, Department department) {
        return User.builder()
                .id(id)
                .name(name)
                .email(name + "@mlsoft.com")
                .role(Role.EMPLOYEE)
                .department(department)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }

    /** 연차 2일(ANNUAL × 2일)을 actor가 처리한 이력 */
    private static LeaveActionHistory history(User applicant, User actor, RequestAction action, String comment) {
        LeaveRequest request = LeaveRequest.create(
                applicant,
                LeaveType.ANNUAL,
                List.of(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 4)),
                "여름 휴가",
                actor,
                null);
        return LeaveActionHistory.create(request, actor, action, comment);
    }
}
