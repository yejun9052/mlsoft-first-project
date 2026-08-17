package com.mlsoft.backend.domain.department.service;

import com.mlsoft.backend.domain.department.dto.DepartmentCreateRequest;
import com.mlsoft.backend.domain.department.dto.DepartmentResponse;
import com.mlsoft.backend.domain.department.dto.DepartmentTreeResponse;
import com.mlsoft.backend.domain.department.dto.DepartmentUpdateRequest;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.user.service.ApproverResolver;
import com.mlsoft.backend.domain.user.service.UserService;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 부서 도메인 서비스 — 조회·관리자 CRUD (docs/03 부서, docs/01 조직 요구사항).
 * 삭제 대신 비활성화(소프트 삭제)로 통일 — 소속 사원·과거 신청의 부서 참조를 보존한다
 * (WelfarePolicyService와 동일 패턴, docs/02 갭: department 테이블에 없던 active 플래그를 엔티티에서 확장).
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    // 팀장 자격 판정 — 승인자 자격과 같은 기준이어야 한다 (리뷰 I-5a)
    private final ApproverResolver approverResolver;
    // 팀장 교체 불변식(부서당 1명 + 이전 팀장 정리)의 단일 구현처. 여기서 다시 만들지 말 것
    private final UserService userService;

    /** 전체 목록 (GET /api/departments) — 활성 부서만, 드롭다운용 플랫 목록 */
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAll() {
        return departmentRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(DepartmentResponse::of)
                .toList();
    }

    /** 2단계 계층 트리 (GET /api/departments/tree) — 활성 부서만 */
    @Transactional(readOnly = true)
    public List<DepartmentTreeResponse> getTree() {
        List<Department> all = departmentRepository.findByActiveTrueOrderByIdAsc();
        Map<Long, List<Department>> childrenByParent = all.stream()
                .filter(d -> d.getParentId() != null)
                .collect(Collectors.groupingBy(Department::getParentId));
        return all.stream()
                .filter(d -> d.getParentId() == null)
                .map(root -> DepartmentTreeResponse.of(root, childrenByParent.getOrDefault(root.getId(), List.of())))
                .toList();
    }

    /** 부서 생성 (POST /api/departments, SA) — parentId 지정 시 2단계 계층 검증 */
    @Transactional
    public DepartmentResponse create(DepartmentCreateRequest request) {
        validateParent(request.parentId(), null); // 생성 시점엔 자기 id가 없다
        Department department = Department.create(request.name().trim(), request.description(), request.parentId());
        if (request.leaderId() != null) {
            department.assignLeader(findEligibleLeaderOrThrow(request.leaderId()));
        }
        departmentRepository.save(department);
        return DepartmentResponse.of(department);
    }

    /**
     * 부서 수정 (PUT /api/departments/{id}, SA) — 전체 갱신.
     * leaderId 미포함(null) 시 팀장 공석으로 처리한다 — PUT은 전체 갱신 의미론을 따른다.
     *
     * <p>팀장 지정은 {@code UserService.assignDepartmentLeader}에, <b>해제는
     * {@code releaseDepartmentLeader}에</b> 맡긴다 — 여기서 직접 {@code assignLeader}·
     * {@code clearLeader}만 부르면 <b>이전 팀장이 역할과 기존 결재선을 그대로 든 채 남는다</b>.
     * 부서당 팀장 1명 불변식이 한쪽 경로에서만 지켜지면 지켜지지 않는 것과 같다 (2026-08-16).
     *
     * <p>08-16에는 지정 경로만 합치고 <b>해제 경로를 놓쳐</b> 공석 처리 후에도 이전 팀장이
     * 기존 신청을 결재할 수 있었다 (2026-08-17 감사).
     */
    @Transactional
    public DepartmentResponse update(Long id, DepartmentUpdateRequest request, Long actorId) {
        Department department = findActiveDepartmentOrThrow(id);
        validateParent(request.parentId(), id);
        department.update(request.name().trim(), request.description(), request.parentId());
        if (request.leaderId() != null) {
            userService.assignDepartmentLeader(department, findEligibleLeaderOrThrow(request.leaderId()), actorId);
        } else {
            userService.releaseDepartmentLeader(department, actorId);
        }
        return DepartmentResponse.of(department);
    }

    /** 부서 비활성화 (DELETE /api/departments/{id}, SA) — 소프트 삭제 */
    @Transactional
    public void deactivate(Long id) {
        findActiveDepartmentOrThrow(id).deactivate();
    }

    // ---------------------------------------------------------------------
    // 내부 헬퍼
    // ---------------------------------------------------------------------

    /**
     * 상위 부서 검증 — 2단계 계층만 허용.
     *
     * <p><b>양쪽을 다 봐야 2단계가 강제된다</b> (1차 테스트 F). 이전에는 "상위가 루트인가"만 봐서
     * <b>자식이 있는 부서를 다른 루트 밑으로 옮기면 3단계가 만들어졌다</b> — 화면에서만 막고 있어서
     * API를 직접 호출하면 뚫렸고, 관리자 화면은 depth 0·1만 렌더하므로 표시가 깨졌다.
     *
     * @param parentId 지정하려는 상위 부서
     * @param selfId   수정 중인 부서 id (생성 시 null). 자기 자신을 상위로 지정하면
     *                 트리 조회에서 사라지고 순환 참조가 된다 (리뷰 I-9)
     */
    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null) {
            return;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new BusinessException(ErrorCode.SELF_PARENT_DEPARTMENT);
        }

        // ① 올라갈 상위가 루트여야 한다 (상위의 상위가 생기면 3단계)
        Department parent = departmentRepository.findByIdAndActiveTrue(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
        if (parent.getParentId() != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // ② 내려갈 자신에게 자식이 없어야 한다 (내 자식이 손자가 되면 3단계)
        if (selfId != null && departmentRepository.existsByParentId(selfId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /**
     * 팀장 지정 검증 — 결재할 수 있는 사람만 (리뷰 I-5a).
     * 검증 없이 지정하면 EMPLOYEE·퇴직자·온보딩 미완료자가 팀장이 되고,
     * 그 부서 신청이 전부 결재 불가 상태로 쌓인다.
     */
    private User findEligibleLeaderOrThrow(Long leaderId) {
        User leader = findUserOrThrow(leaderId);
        // 셀프 결재(팀장 본인의 신청)는 승인자 결정 시점에 fallback으로 걸러지므로 여기서는 자격만 본다
        if (!approverResolver.canApprove(leader)) {
            throw new BusinessException(ErrorCode.INVALID_APPROVER);
        }
        return leader;
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Department findActiveDepartmentOrThrow(Long id) {
        return departmentRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }
}
