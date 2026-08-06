package com.mlsoft.backend.domain.leave.service;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.leave.dto.LeaveHistoryLogResponse;
import com.mlsoft.backend.domain.leave.entity.LeaveActionHistory;
import com.mlsoft.backend.domain.leave.repository.LeaveActionHistoryRepository;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연차 처리 로그 조회 (docs/03 처리 이력 — 관리자·팀장 로그 화면).
 *
 * <p>이력 적재는 {@link LeaveService}가 담당하고 여기서는 조회만 한다.
 * 팀 스코프는 요청 파라미터가 아니라 요청자의 소속 부서로 서버가 결정한다 — 팀장이 다른 팀 로그를
 * 조회하도록 파라미터를 바꿔치기하는 것을 막기 위함 (docs/04 본인 식별 규칙).
 */
@Service
@RequiredArgsConstructor
public class LeaveHistoryService {

    private final LeaveActionHistoryRepository leaveActionHistoryRepository;
    private final UserRepository userRepository;

    /** 전체 처리 로그 (GET /api/leave-histories, SA) — action이 null이면 전체 */
    @Transactional(readOnly = true)
    public Page<LeaveHistoryLogResponse> getHistories(RequestAction action, Pageable pageable) {
        Page<LeaveActionHistory> histories = action == null
                ? leaveActionHistoryRepository.findAll(pageable)
                : leaveActionHistoryRepository.findByAction(action, pageable);
        return histories.map(LeaveHistoryLogResponse::of);
    }

    /**
     * 팀 처리 로그 (GET /api/leave-histories/my-team, TL) — 요청자 소속 부서의 신청 건만.
     * 부서 미배정 팀장은 스코프가 없으므로 빈 페이지를 돌려준다(오류 아님).
     */
    @Transactional(readOnly = true)
    public Page<LeaveHistoryLogResponse> getMyTeamHistories(Long requesterId, RequestAction action,
                                                            Pageable pageable) {
        Department department = findUserOrThrow(requesterId).getDepartment();
        if (department == null) {
            return Page.empty(pageable);
        }
        Page<LeaveActionHistory> histories = action == null
                ? leaveActionHistoryRepository.findByUserDepartmentId(department.getId(), pageable)
                : leaveActionHistoryRepository.findByUserDepartmentIdAndAction(department.getId(), action, pageable);
        return histories.map(LeaveHistoryLogResponse::of);
    }

    /**
     * 내가 처리한 로그 (GET /api/leave-histories/my-actions) — actor가 요청자인 이력만.
     *
     * <p>결재 화면의 "승인·반려 완료" 목록이 필요한 것이 이것이다. 팀 로그(my-team)는 <b>신청자 부서</b>
     * 기준이라 남이 처리한 건도 섞이고, 전사 로그는 범위가 너무 넓다.
     * actor를 토큰에서 가져오므로 부서 스코프 논쟁(리뷰 S-6)과도 무관하다 — 본인이 한 일만 보인다.
     */
    @Transactional(readOnly = true)
    public Page<LeaveHistoryLogResponse> getMyActionHistories(Long actorId, RequestAction action,
                                                              Pageable pageable) {
        Page<LeaveActionHistory> histories = action == null
                ? leaveActionHistoryRepository.findByActorId(actorId, pageable)
                : leaveActionHistoryRepository.findByActorIdAndAction(actorId, action, pageable);
        return histories.map(LeaveHistoryLogResponse::of);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
