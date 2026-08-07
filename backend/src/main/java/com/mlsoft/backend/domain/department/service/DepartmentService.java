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
     */
    @Transactional
    public DepartmentResponse update(Long id, DepartmentUpdateRequest request) {
        Department department = findActiveDepartmentOrThrow(id);
        validateParent(request.parentId(), id);
        department.update(request.name().trim(), request.description(), request.parentId());
        if (request.leaderId() != null) {
            department.assignLeader(findEligibleLeaderOrThrow(request.leaderId()));
        } else {
            department.clearLeader();
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

    /** 상위 부서 검증 — 존재·활성 + 그 부서 자신도 루트(parentId null)여야 함 (2단계 계층 강제) */
    /**
     * 상위 부서 검증 — 2단계 계층만 허용.
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
        Department parent = departmentRepository.findByIdAndActiveTrue(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
        if (parent.getParentId() != null) {
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
