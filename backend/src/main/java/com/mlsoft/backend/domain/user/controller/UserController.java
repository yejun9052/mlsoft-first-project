package com.mlsoft.backend.domain.user.controller;

import com.mlsoft.backend.domain.user.dto.BaseDaysUpdateRequest;
import com.mlsoft.backend.domain.user.dto.DepartmentAssignRequest;
import com.mlsoft.backend.domain.user.dto.PurgeHoldRequest;
import com.mlsoft.backend.domain.user.dto.RehireRequest;
import com.mlsoft.backend.domain.user.dto.RoleDepartmentUpdateRequest;
import com.mlsoft.backend.domain.user.dto.RoleUpdateRequest;
import com.mlsoft.backend.domain.user.dto.UserProfileUpdateRequest;
import com.mlsoft.backend.domain.user.dto.UserResponse;
import com.mlsoft.backend.domain.user.dto.UserSummaryResponse;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.service.UserService;
import com.mlsoft.backend.global.response.CommonResponse;
import com.mlsoft.backend.global.response.ResponseMessage;
import com.mlsoft.backend.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 사용자 API (docs/03 사용자 섹션).
 * - 본인 식별은 Authentication(AuthUser)에서 추출 — 요청 body의 사용자 ID 신뢰 금지 (docs/04)
 * - 온보딩/퇴직/권한 신선도는 OnboardingCheckInterceptor가 /api/** 전역 가드
 * - 대상 소유·상태 검증은 서비스 계층에서 (@PreAuthorize는 역할 게이트만)
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 전체 목록 (페이징, keyword·role 필터) */
    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Page<UserResponse>>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role,
            Pageable pageable
    ) {
        Page<UserResponse> response = userService.getUsers(keyword, role, pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_INFO_FETCHED, response));
    }

    /** 내 부서 팀원 목록 */
    @GetMapping("/team-members")
    public ResponseEntity<CommonResponse<List<UserSummaryResponse>>> getTeamMembers(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        List<UserSummaryResponse> response = userService.getTeamMembers(authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_INFO_FETCHED, response));
    }

    /** 서브 승인자 후보 (재직 중 TEAM_LEADER·SYSTEM_ADMIN, 본인 제외) */
    @GetMapping("/approvers")
    public ResponseEntity<CommonResponse<List<UserSummaryResponse>>> getApproverCandidates(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        List<UserSummaryResponse> response = userService.getApproverCandidates(authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_INFO_FETCHED, response));
    }

    /** 팀장 후보 (재직 중 TEAM_LEADER·SYSTEM_ADMIN — 승인자 후보와 달리 본인도 포함) */
    @GetMapping("/leader-candidates")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<List<UserSummaryResponse>>> getLeaderCandidates() {
        List<UserSummaryResponse> response = userService.getLeaderCandidates();
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_INFO_FETCHED, response));
    }

    /** 퇴직자 목록 (페이징) */
    @GetMapping("/retired")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Page<UserResponse>>> getRetiredUsers(Pageable pageable) {
        Page<UserResponse> response = userService.getRetiredUsers(pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_INFO_FETCHED, response));
    }

    /** 내 정보 수정 (이름·생일·직책) */
    @PatchMapping("/me")
    public ResponseEntity<CommonResponse<UserResponse>> updateMyProfile(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody UserProfileUpdateRequest request
    ) {
        UserResponse response = userService.updateMyProfile(authUser.id(), request);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_PROFILE_UPDATED, response));
    }

    /** 권한 변경 */
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<UserResponse>> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        UserResponse response = userService.changeRole(id, request.role(), authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_ROLE_UPDATED, response));
    }

    /** 부서 변경 */
    @PatchMapping("/{id}/department")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<UserResponse>> changeDepartment(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentAssignRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        UserResponse response = userService.changeDepartment(id, request.departmentId(), authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_DEPARTMENT_UPDATED, response));
    }

    /**
     * 역할·부서 동시 변경 — 미배정 사원의 팀장 승격에서 부분 성공을 막는다.
     * 기존 역할·부서 개별 엔드포인트는 독립 변경 화면이 계속 사용하므로 유지한다.
     */
    @PatchMapping("/{id}/role-and-department")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<UserResponse>> changeRoleAndDepartment(
            @PathVariable Long id,
            @Valid @RequestBody RoleDepartmentUpdateRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        UserResponse response = userService.changeRoleAndDepartment(
                id, request.role(), request.departmentId(), authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_ROLE_UPDATED, response));
    }

    /** 연차 직접 설정 */
    @PatchMapping("/{id}/base-days")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<UserResponse>> updateBaseDays(
            @PathVariable Long id,
            @Valid @RequestBody BaseDaysUpdateRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        UserResponse response = userService.updateBaseDays(id, request, authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_BASE_DAYS_UPDATED, response));
    }

    /** 퇴직 처리 — leader 해제·결재 이관 포함 */
    @PostMapping("/{id}/retire")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Void>> retire(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        userService.retire(id, authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_RETIRED));
    }

    /** 퇴직자 개인정보 수동 파기 — users 익명화와 본문 파기를 같은 트랜잭션으로 처리한다. */
    @PostMapping("/{id}/purge")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Void>> purge(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        userService.purge(id, authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_PURGED));
    }

    /** 퇴직자 개인정보 파기 보류 설정 — 사유는 필수다. */
    @PostMapping("/{id}/purge-hold")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Void>> placePurgeHold(
            @PathVariable Long id,
            @Valid @RequestBody PurgeHoldRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        userService.placePurgeHold(id, request.reason(), authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_PURGE_HOLD_PLACED));
    }

    /** 퇴직자 개인정보 파기 보류 해제. */
    @DeleteMapping("/{id}/purge-hold")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Void>> releasePurgeHold(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        userService.releasePurgeHold(id, authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_PURGE_HOLD_RELEASED));
    }

    /**
     * 퇴직 복구 — 재직 상태로 되돌린다. 팀장직·이관된 결재는 되살리지 않는다 (서비스 주석).
     * 경로를 {@code /restoration}이 아니라 {@code /restore}로 둔 것은 바로 위 {@code /retire}와
     * 짝을 이루게 하기 위함이다 — 역연산 두 개가 다른 규칙으로 쓰여 있으면 찾을 때 헷갈린다.
     */
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Void>> restore(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        userService.restore(id, authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_RESTORED));
    }

    /** 재입사 처리 — 이전 근속을 닫고 재입사일부터 연차를 새로 시작한다. */
    @PostMapping("/{id}/rehire")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<UserResponse>> rehire(
            @PathVariable Long id,
            @Valid @RequestBody RehireRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        UserResponse response = userService.rehire(id, request, authUser.id());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_REHIRED, response));
    }
}
