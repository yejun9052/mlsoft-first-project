package com.mlsoft.backend.domain.department.service;

import com.mlsoft.backend.domain.department.dto.DepartmentCreateRequest;
import com.mlsoft.backend.domain.department.dto.DepartmentResponse;
import com.mlsoft.backend.domain.department.dto.DepartmentUpdateRequest;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 부서 서비스 단위 테스트 (docs/03 부서 CRUD, 2단계 계층 제약).
 * 소프트 삭제(active) 패턴 및 상위 부서가 이미 자식인 경우의 계층 위반을 중점적으로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DepartmentService departmentService;

    @Test
    @DisplayName("생성 — parentId 없이 리더 지정: 정상 생성")
    void create_root_withLeader() {
        User leader = user(1L, Role.TEAM_LEADER);
        given(userRepository.findById(1L)).willReturn(Optional.of(leader));
        DepartmentCreateRequest request = new DepartmentCreateRequest("개발팀", "설명", 1L, null);

        DepartmentResponse response = departmentService.create(request);

        assertEquals("개발팀", response.name());
        assertEquals(1L, response.leaderId());
        assertNull(response.parentId());
    }

    @Test
    @DisplayName("생성 — parentId가 이미 자식 부서(부모가 있는 부서)면 INVALID_INPUT_VALUE (2단계 계층 강제)")
    void create_parentIsAlreadyChild_throws() {
        Department child = Department.create("자식팀", "설명", 100L); // 이 부서 자체가 이미 하위 부서
        given(departmentRepository.findByIdAndActiveTrue(5L)).willReturn(Optional.of(child));
        DepartmentCreateRequest request = new DepartmentCreateRequest("손자팀", "설명", null, 5L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> departmentService.create(request));

        assertEquals(ErrorCode.INVALID_INPUT_VALUE, ex.getErrorCode());
    }

    @Test
    @DisplayName("생성 — parentId가 존재하지 않거나 비활성 부서면 DEPARTMENT_NOT_FOUND")
    void create_parentNotFound_throws() {
        given(departmentRepository.findByIdAndActiveTrue(999L)).willReturn(Optional.empty());
        DepartmentCreateRequest request = new DepartmentCreateRequest("자식팀", "설명", null, 999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> departmentService.create(request));

        assertEquals(ErrorCode.DEPARTMENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("수정 — leaderId 미포함(null): 팀장 공석 처리")
    void update_withoutLeaderId_clearsLeader() {
        User leader = user(1L, Role.TEAM_LEADER);
        Department department = Department.create("개발팀", "설명", null);
        department.assignLeader(leader);
        given(departmentRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(department));
        DepartmentUpdateRequest request = new DepartmentUpdateRequest("개발팀(수정)", "설명 수정", null, null);

        DepartmentResponse response = departmentService.update(10L, request);

        assertNull(response.leaderId());
        assertNull(department.getLeader());
    }

    @Test
    @DisplayName("수정 — 존재하지 않거나 비활성화된 부서: DEPARTMENT_NOT_FOUND")
    void update_inactiveDepartment_throws() {
        given(departmentRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.empty());
        DepartmentUpdateRequest request = new DepartmentUpdateRequest("개발팀", "설명", null, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> departmentService.update(10L, request));

        assertEquals(ErrorCode.DEPARTMENT_NOT_FOUND, ex.getErrorCode());
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("비활성화 — 소프트 삭제 (active=false)")
    void deactivate_success() {
        Department department = Department.create("개발팀", "설명", null);
        given(departmentRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(department));

        departmentService.deactivate(10L);

        assertFalse(department.isActive());
    }

    @Test
    @DisplayName("비활성화 — 이미 비활성화되었거나 존재하지 않으면 DEPARTMENT_NOT_FOUND")
    void deactivate_notFound_throws() {
        given(departmentRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> departmentService.deactivate(10L));

        assertEquals(ErrorCode.DEPARTMENT_NOT_FOUND, ex.getErrorCode());
    }

    // ============================ 헬퍼 ============================

    private User user(Long id, Role role) {
        return User.builder()
                .id(id)
                .name("user" + id)
                .email("user" + id + "@mlsoft.com")
                .role(role)
                .baseDays(BigDecimal.TEN)
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }
}
