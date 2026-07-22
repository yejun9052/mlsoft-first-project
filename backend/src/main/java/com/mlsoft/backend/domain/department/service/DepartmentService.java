package com.mlsoft.backend.domain.department.service;

import com.mlsoft.backend.domain.department.dto.DepartmentCreateRequest;
import com.mlsoft.backend.domain.department.dto.DepartmentResponse;
import com.mlsoft.backend.domain.department.dto.DepartmentTreeResponse;
import com.mlsoft.backend.domain.department.dto.DepartmentUpdateRequest;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.department.repository.DepartmentRepository;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
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
        validateParent(request.parentId());
        Department department = Department.create(request.name().trim(), request.description(), request.parentId());
        if (request.leaderId() != null) {
            department.assignLeader(findUserOrThrow(request.leaderId()));
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
        validateParent(request.parentId());
        department.update(request.name().trim(), request.description(), request.parentId());
        if (request.leaderId() != null) {
            department.assignLeader(findUserOrThrow(request.leaderId()));
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
    private void validateParent(Long parentId) {
        if (parentId == null) {
            return;
        }
        Department parent = departmentRepository.findByIdAndActiveTrue(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
        if (parent.getParentId() != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
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
