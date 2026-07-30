package com.mlsoft.backend.domain.welfare.service;

import com.mlsoft.backend.domain.common.RequestAction;
import com.mlsoft.backend.domain.department.entity.Department;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.domain.welfare.dto.WelfareHistoryLogResponse;
import com.mlsoft.backend.domain.welfare.entity.WelfareActionHistory;
import com.mlsoft.backend.domain.welfare.repository.WelfareActionHistoryRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복리후생 처리 로그 조회 (docs/03 처리 이력 — 관리자·팀장 로그 화면).
 * 스코프 규칙은 {@link com.mlsoft.backend.domain.leave.service.LeaveHistoryService}와 동일하다.
 */
@Service
@RequiredArgsConstructor
public class WelfareHistoryService {

    private final WelfareActionHistoryRepository welfareActionHistoryRepository;
    private final UserRepository userRepository;

    /** 전체 처리 로그 (GET /api/welfare-histories, SA) — action이 null이면 전체 */
    @Transactional(readOnly = true)
    public Page<WelfareHistoryLogResponse> getHistories(RequestAction action, Pageable pageable) {
        Page<WelfareActionHistory> histories = action == null
                ? welfareActionHistoryRepository.findAll(pageable)
                : welfareActionHistoryRepository.findByAction(action, pageable);
        return histories.map(WelfareHistoryLogResponse::of);
    }

    /** 팀 처리 로그 (GET /api/welfare-histories/my-team, TL) — 부서 미배정이면 빈 페이지 */
    @Transactional(readOnly = true)
    public Page<WelfareHistoryLogResponse> getMyTeamHistories(Long requesterId, RequestAction action,
                                                              Pageable pageable) {
        Department department = findUserOrThrow(requesterId).getDepartment();
        if (department == null) {
            return Page.empty(pageable);
        }
        Page<WelfareActionHistory> histories = action == null
                ? welfareActionHistoryRepository.findByUserDepartmentId(department.getId(), pageable)
                : welfareActionHistoryRepository.findByUserDepartmentIdAndAction(department.getId(), action, pageable);
        return histories.map(WelfareHistoryLogResponse::of);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
