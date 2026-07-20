package com.mlsoft.backend.domain.welfare.service;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.common.RequestStatus;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.welfare.dto.WelfareApprovalRequest;
import com.mlsoft.backend.domain.welfare.dto.WelfareCreateRequest;
import com.mlsoft.backend.domain.welfare.dto.WelfareResponse;
import com.mlsoft.backend.domain.welfare.entity.WelfareActionHistory;
import com.mlsoft.backend.domain.welfare.entity.WelfarePolicy;
import com.mlsoft.backend.domain.welfare.entity.WelfareRequest;
import com.mlsoft.backend.domain.welfare.repository.WelfareActionHistoryRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfarePolicyRepository;
import com.mlsoft.backend.domain.welfare.repository.WelfareRequestRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복리후생 신청 도메인 서비스 — 신청·조회·승인/반려·취소 (docs/01, docs/03 복리후생).
 *
 * <p>승인자 확정(primary/sub)과 병렬 선착순 처리 방식은 {@code LeaveService}와 동일한 규칙을 따른다
 * (docs/02 3-7: primary = 부서 팀장, sub = 재직 중 TEAM_LEADER·SYSTEM_ADMIN 중 선택).
 * 다만 취소는 PENDING 상태에서 본인만 가능하고, 승인 후에는 bonus_days 차감 롤백이 복잡해 취소를
 * 허용하지 않는다(docs/07 갭분석 A-9) — 소급 취소(CANCEL_PENDING) 흐름은 복리후생에 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WelfareService {

    private final WelfareRequestRepository welfareRequestRepository;
    private final WelfareActionHistoryRepository welfareActionHistoryRepository;
    private final WelfarePolicyRepository welfarePolicyRepository;
    private final UserRepository userRepository;

    // ---------------------------------------------------------------------
    // 신청
    // ---------------------------------------------------------------------

    /**
     * 복리후생 신청 (POST /api/welfare-requests).
     * 활성 정책 확인 → 승인자 확정 → 정책 값 스냅샷(category/target/evidenceGuide/addDays) 포함 PENDING 저장 + 이력.
     * reason은 클라이언트가 입력한 값을 그대로 저장한다(서버 자동 생성 금지 — docs/03 확정).
     */
    @Transactional
    public WelfareResponse apply(Long userId, WelfareCreateRequest request) {
        User applicant = findUserOrThrow(userId);
        WelfarePolicy policy = findActivePolicyOrThrow(request.policyId());

        User primaryApprover = resolvePrimaryApprover(applicant);
        User subApprover = resolveSubApprover(request.subApproverId(), applicant);

        WelfareRequest welfare = WelfareRequest.create(
                policy, applicant, request.reason(),
                primaryApprover.getId(), subApprover != null ? subApprover.getId() : null);
        welfareRequestRepository.save(welfare);

        saveHistory(welfare, applicant, RequestAction.PENDING, request.reason());
        // TODO(email): 신청 알림 — 당사자·primary·sub에게 @Async + AFTER_COMMIT 이벤트 발행 (다음 마일스톤)
        log.info("[복리후생 신청] userId={}, welfareId={}, addDays={}", userId, welfare.getId(), welfare.getAddDays());
        return WelfareResponse.of(welfare);
    }

    // ---------------------------------------------------------------------
    // 조회
    // ---------------------------------------------------------------------

    /** 내 신청 내역 (GET /api/welfare-requests/me) */
    @Transactional(readOnly = true)
    public Page<WelfareResponse> getMyRequests(Long userId, Pageable pageable) {
        User user = findUserOrThrow(userId);
        return welfareRequestRepository.findByUser(user, pageable).map(WelfareResponse::of);
    }

    /** 내가 승인자인 대기 목록 (GET /api/welfare-requests/pending) */
    @Transactional(readOnly = true)
    public Page<WelfareResponse> getPending(Long approverId, Pageable pageable) {
        return welfareRequestRepository
                .findPendingForApprover(approverId, RequestStatus.PENDING, pageable)
                .map(WelfareResponse::of);
    }

    /** 전체 신청 목록 (GET /api/welfare-requests, SA) */
    @Transactional(readOnly = true)
    public Page<WelfareResponse> getAllForAdmin(Pageable pageable) {
        return welfareRequestRepository.findAll(pageable).map(WelfareResponse::of);
    }

    // ---------------------------------------------------------------------
    // 승인 / 반려
    // ---------------------------------------------------------------------

    /**
     * 승인/반려 (POST /api/welfare-requests/{id}/approval) — PENDING에서만, 조건부 갱신으로 이중 처리 차단.
     * 승인 시 신청자에게 addDays만큼 bonus_days 즉시 가산.
     */
    @Transactional
    public void processApproval(Long welfareId, Long actorId, WelfareApprovalRequest request) {
        WelfareRequest welfare = findWelfareOrThrow(welfareId);
        User actor = findUserOrThrow(actorId);
        validateApprover(welfare, actor);
        if (welfare.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }

        boolean approved = Boolean.TRUE.equals(request.approved());
        RequestStatus next = approved ? RequestStatus.APPROVED : RequestStatus.REJECTED;
        if (welfareRequestRepository.updateStatusIfCurrent(welfareId, RequestStatus.PENDING, next) == 0) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED); // 동시 처리에서 패배
        }

        WelfareRequest fresh = findWelfareOrThrow(welfareId);
        if (approved) {
            fresh.getUser().addBonusDays(fresh.getAddDays());
        }
        saveHistory(fresh, actor, approved ? RequestAction.APPROVED : RequestAction.REJECTED, request.comment());
        // TODO(email): 승인/반려 알림 (다음 마일스톤)
        log.info("[복리후생 {}] welfareId={}, actorId={}", approved ? "승인" : "반려", welfareId, actorId);
    }

    // ---------------------------------------------------------------------
    // 취소
    // ---------------------------------------------------------------------

    /**
     * 취소 (POST /api/welfare-requests/{id}/cancel) — 본인만, PENDING 상태일 때만 가능.
     * 승인 후에는 bonus_days 차감 롤백이 복잡해 취소를 허용하지 않는다(docs/07 갭분석 A-9).
     */
    @Transactional
    public void cancel(Long welfareId, Long ownerId) {
        WelfareRequest welfare = findWelfareOrThrow(welfareId);
        if (!welfare.getUser().getId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (welfareRequestRepository.updateStatusIfCurrent(
                welfareId, RequestStatus.PENDING, RequestStatus.CANCELLED) == 0) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }

        WelfareRequest fresh = findWelfareOrThrow(welfareId);
        saveHistory(fresh, fresh.getUser(), RequestAction.CANCELLED, "");
        log.info("[복리후생 취소] welfareId={}, ownerId={}", welfareId, ownerId);
    }

    // ---------------------------------------------------------------------
    // 내부 헬퍼
    // ---------------------------------------------------------------------

    /** 기본 승인자 = 소속 부서 팀장. 미배정·공석·팀장 퇴직·본인이 팀장이면 SYSTEM_ADMIN fallback (LeaveService와 동일 규칙) */
    private User resolvePrimaryApprover(User applicant) {
        Department department = applicant.getDepartment();
        if (department != null && department.getLeader() != null) {
            User leader = department.getLeader();
            if (leader.isActive() && !leader.getId().equals(applicant.getId())) {
                return leader;
            }
        }
        return userRepository.findFirstByRoleAndIsActiveTrueOrderByIdAsc(Role.SYSTEM_ADMIN)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_APPROVER));
    }

    /** 서브 승인자 검증 — 선택 시 재직 중 TEAM_LEADER·SYSTEM_ADMIN, 본인 제외 (LeaveService와 동일 규칙) */
    private User resolveSubApprover(Long subApproverId, User applicant) {
        if (subApproverId == null) {
            return null;
        }
        User sub = userRepository.findById(subApproverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_APPROVER));
        boolean eligible = sub.isActive()
                && (sub.getRole() == Role.TEAM_LEADER || sub.getRole() == Role.SYSTEM_ADMIN)
                && !sub.getId().equals(applicant.getId());
        if (!eligible) {
            throw new BusinessException(ErrorCode.INVALID_APPROVER);
        }
        return sub;
    }

    /** 처리자가 이 건의 primary·sub 승인자인지 검증 (docs/03 approval 권한) */
    private void validateApprover(WelfareRequest welfare, User actor) {
        Long actorId = actor.getId();
        boolean isPrimary = welfare.getPrimaryApproverId().equals(actorId);
        boolean isSub = welfare.getSubApproverId() != null && welfare.getSubApproverId().equals(actorId);
        if (!isPrimary && !isSub) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    /** 처리 이력 저장 — comment 없으면 빈 문자열 (컬럼 not-null) */
    private void saveHistory(WelfareRequest welfare, User actor, RequestAction action, String comment) {
        String safeComment = (comment != null) ? comment : "";
        welfareActionHistoryRepository.save(WelfareActionHistory.create(welfare, actor, action, safeComment));
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private WelfarePolicy findActivePolicyOrThrow(Long policyId) {
        return welfarePolicyRepository.findByIdAndActiveTrue(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WELFARE_POLICY_NOT_FOUND));
    }

    private WelfareRequest findWelfareOrThrow(Long welfareId) {
        return welfareRequestRepository.findById(welfareId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WELFARE_REQUEST_NOT_FOUND));
    }
}
