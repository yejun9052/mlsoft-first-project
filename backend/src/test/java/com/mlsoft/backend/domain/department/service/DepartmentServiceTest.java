package com.mlsoft.backend.domain.department.service;

import com.mlsoft.backend.domain.department.dto.DepartmentCreateRequest;
import com.mlsoft.backend.domain.department.dto.DepartmentResponse;
import com.mlsoft.backend.domain.department.dto.DepartmentUpdateRequest;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.user.service.ApproverResolver;
import com.mlsoft.backend.domain.user.service.UserService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private static final Long ACTOR_ID = 99L;

    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private UserRepository userRepository;
    // 팀장 자격 판정 — 승인자 자격과 같은 기준 (리뷰 I-5a)
    @Mock
    private ApproverResolver approverResolver;
    // 팀장 교체 불변식은 UserService가 갖는다 — 여기서는 "그쪽에 맡겼는가"만 본다
    @Mock
    private UserService userService;

    @InjectMocks
    private DepartmentService departmentService;

    @Test
    @DisplayName("생성 — parentId 없이 리더 지정: 정상 생성")
    void create_root_withLeader() {
        User leader = user(1L, Role.TEAM_LEADER);
        given(userRepository.findById(1L)).willReturn(Optional.of(leader));
        // 결재할 수 있는 사람만 팀장이 될 수 있다 (리뷰 I-5a)
        given(approverResolver.canApprove(leader)).willReturn(true);
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

    /**
     * 1차 테스트 F — 2단계 강제의 나머지 반쪽.
     *
     * <p>"상위가 루트인가"만 보면, <b>자식이 있는 부서를 다른 루트 밑으로 옮겨</b> 3단계를 만들 수 있다.
     * 화면에서만 막고 있어서 API를 직접 호출하면 뚫렸다.
     */
    @Test
    @DisplayName("수정 — 자식이 있는 부서를 다른 루트의 하위로 옮기면 INVALID_INPUT_VALUE (3단계 방지)")
    void update_selfHasChildren_throws() {
        Department root = Department.create("본부", "설명", null);
        Department movingParent = Department.create("개발팀", "설명", null); // 이 부서에 자식이 있다
        given(departmentRepository.findByIdAndActiveTrue(7L)).willReturn(Optional.of(movingParent));
        given(departmentRepository.findByIdAndActiveTrue(1L)).willReturn(Optional.of(root));
        given(departmentRepository.existsByParentId(7L)).willReturn(true);
        DepartmentUpdateRequest request = new DepartmentUpdateRequest("개발팀", "설명", null, 1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> departmentService.update(7L, request, ACTOR_ID));

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

    // 공석 처리는 부서 필드만 비우는 일이 아니다. 예전에는 department.clearLeader()만 불러
    // 화면에는 공석으로 보이는데 그 사람이 역할과 기존 결재선을 그대로 들고 있었다 (2026-08-17 감사).
    // 그래서 "leader == null"만 단정하면 안 되고, 해제 경로를 실제로 탔는지를 본다.
    @Test
    @DisplayName("수정 — leaderId 미포함(null): 팀장 해제 경로를 탄다 (부서 필드만 비우지 않는다)")
    void update_withoutLeaderId_releasesLeaderThroughSharedPath() {
        User leader = user(1L, Role.TEAM_LEADER);
        Department department = Department.create("개발팀", "설명", null);
        department.assignLeader(leader);
        given(departmentRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(department));
        DepartmentUpdateRequest request = new DepartmentUpdateRequest("개발팀(수정)", "설명 수정", null, null);

        departmentService.update(10L, request, ACTOR_ID);

        // 역할 강등·대기 결재 이관·감사 기록은 UserService가 한 곳에서 한다.
        // 여기서 직접 clearLeader()를 부르면 그 셋이 통째로 빠진다.
        verify(userService).releaseDepartmentLeader(department, ACTOR_ID);
    }

    @Test
    @DisplayName("수정 — 존재하지 않거나 비활성화된 부서: DEPARTMENT_NOT_FOUND")
    void update_inactiveDepartment_throws() {
        given(departmentRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.empty());
        DepartmentUpdateRequest request = new DepartmentUpdateRequest("개발팀", "설명", null, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> departmentService.update(10L, request, ACTOR_ID));

        assertEquals(ErrorCode.DEPARTMENT_NOT_FOUND, ex.getErrorCode());
        verify(userRepository, never()).findById(any());
    }

    // ===== 시스템 기본 "미배정" 부서 잠금 (2026-08-21, docs/12 B-9) =====
    //
    // 이 부서는 첫 기동에 만들어지고 신규 자동 가입자가 전부 배속된다.
    // 이름이 바뀌면 사람이 그 부서가 무엇인지 알 수 없게 되고, 비활성화되면 배속할 곳이
    // 사라져 **신규 가입이 통째로 끊긴다.** 지금까지 그걸 막는 것이 아무것도 없었다.

    @Test
    @DisplayName("수정 — 시스템 기본 부서는 이름을 바꿀 수 없다")
    void update_systemDefaultRename_throws() {
        Department unassigned = Department.createSystemDefault("미배정", "부서 배정 전 기본 소속");
        given(departmentRepository.findByIdAndActiveTrue(4L)).willReturn(Optional.of(unassigned));
        DepartmentUpdateRequest request = new DepartmentUpdateRequest("영업팀", "설명", null, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> departmentService.update(4L, request, ACTOR_ID));

        assertEquals(ErrorCode.SYSTEM_DEFAULT_DEPARTMENT_LOCKED, ex.getErrorCode());
        assertEquals("미배정", unassigned.getName(), "거부됐는데 이름이 바뀌었다");
    }

    // 기본 부서가 어느 부서의 하위로 들어가면, 소속이 정해지지 않은 사원이
    // 그 상위 부서의 결재선에 딸려 들어간다
    @Test
    @DisplayName("수정 — 시스템 기본 부서는 다른 부서 밑으로 옮길 수 없다")
    void update_systemDefaultMove_throws() {
        Department unassigned = Department.createSystemDefault("미배정", "부서 배정 전 기본 소속");
        given(departmentRepository.findByIdAndActiveTrue(4L)).willReturn(Optional.of(unassigned));
        DepartmentUpdateRequest request = new DepartmentUpdateRequest("미배정", "설명", null, 1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> departmentService.update(4L, request, ACTOR_ID));

        assertEquals(ErrorCode.SYSTEM_DEFAULT_DEPARTMENT_LOCKED, ex.getErrorCode());
    }

    @Test
    @DisplayName("수정 — 시스템 기본 부서도 설명은 바꿀 수 있다")
    void update_systemDefaultDescription_allowed() {
        Department unassigned = Department.createSystemDefault("미배정", "옛 설명");
        given(departmentRepository.findByIdAndActiveTrue(4L)).willReturn(Optional.of(unassigned));
        DepartmentUpdateRequest request = new DepartmentUpdateRequest("미배정", "새 설명", null, null);

        departmentService.update(4L, request, ACTOR_ID);

        assertEquals("새 설명", unassigned.getDescription());
    }

    @Test
    @DisplayName("비활성화 — 시스템 기본 부서는 끌 수 없다 (끄면 신규 가입이 끊긴다)")
    void deactivate_systemDefault_throws() {
        Department unassigned = Department.createSystemDefault("미배정", "부서 배정 전 기본 소속");
        given(departmentRepository.findByIdAndActiveTrue(4L)).willReturn(Optional.of(unassigned));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> departmentService.deactivate(4L));

        assertEquals(ErrorCode.SYSTEM_DEFAULT_DEPARTMENT_LOCKED, ex.getErrorCode());
        assertTrue(unassigned.isActive(), "거부됐는데 비활성화됐다");
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
