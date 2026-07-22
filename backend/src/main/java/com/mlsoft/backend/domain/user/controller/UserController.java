package com.mlsoft.backend.domain.user.controller;

import com.mlsoft.backend.domain.user.dto.BaseDaysUpdateRequest;
import com.mlsoft.backend.domain.user.dto.DepartmentAssignRequest;
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

    /** 퇴직자 목록 (페이징) */
    @GetMapping("/retired")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Page<UserResponse>>> getRetiredUsers(Pageable pageable) {
        Page<UserResponse> response = userService.getRetiredUsers(pageable);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_INFO_FETCHED, response));
    }

    /** 내 정보 수정 (이름·생일) */
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
            @Valid @RequestBody RoleUpdateRequest request
    ) {
        UserResponse response = userService.changeRole(id, request.role());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_ROLE_UPDATED, response));
    }

    /** 부서 변경 */
    @PatchMapping("/{id}/department")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<UserResponse>> changeDepartment(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentAssignRequest request
    ) {
        UserResponse response = userService.changeDepartment(id, request.departmentId());
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_DEPARTMENT_UPDATED, response));
    }

    /** 연차 직접 설정 */
    @PatchMapping("/{id}/base-days")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<UserResponse>> updateBaseDays(
            @PathVariable Long id,
            @Valid @RequestBody BaseDaysUpdateRequest request
    ) {
        UserResponse response = userService.updateBaseDays(id, request);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_BASE_DAYS_UPDATED, response));
    }

    /** 퇴직 처리 — leader 해제·결재 이관 포함 */
    @PostMapping("/{id}/retire")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<CommonResponse<Void>> retire(@PathVariable Long id) {
        userService.retire(id);
        return ResponseEntity.ok(CommonResponse.success(ResponseMessage.USER_RETIRED));
    }
}
