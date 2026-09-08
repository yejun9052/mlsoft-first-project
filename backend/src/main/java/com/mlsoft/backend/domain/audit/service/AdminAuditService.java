package com.mlsoft.backend.domain.audit.service;

import com.mlsoft.backend.domain.audit.dto.AdminActionOption;
import com.mlsoft.backend.domain.audit.dto.AdminAuditLogResponse;
import com.mlsoft.backend.domain.audit.entity.AdminAction;
import com.mlsoft.backend.domain.audit.entity.AdminAuditLog;
import com.mlsoft.backend.domain.audit.repository.AdminAuditLogRepository;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 관리자 조작 감사 로그 (리뷰 S-3).
 *
 * <p><b>기록은 조작과 같은 트랜잭션에 참여한다</b>(기본 전파). 조작이 롤백되면 기록도 함께
 * 사라지는 것이 의도다 — 일어나지 않은 일이 감사 기록에 남으면 대조가 더 어려워진다.
 * {@code REQUIRES_NEW}로 떼어내면 그 반대가 된다.
 *
 * <p>기록 메서드를 조작 종류별로 나누지 않고 {@code record*} 두 개로 둔 이유는
 * <b>before/after 문자열을 만드는 책임을 호출부에 두기 위해서</b>다. 도메인 값의 표현
 * (연차 15.0일, 권한 라벨 등)은 각 도메인이 알고 있고, 여기서 타입별 분기를 만들면
 * 조작이 하나 늘 때마다 이 서비스가 같이 커진다.
 */
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final UserRepository userRepository;

    /**
     * 사원을 대상으로 한 조작 기록.
     *
     * <p>actor는 {@code getReferenceById}로 프록시만 잡는다 — insert에 FK 값만 필요해서
     * 조작 경로마다 관리자 SELECT를 한 번씩 더 태울 이유가 없다. 존재는 이미
     * {@code JwtFilter} + {@code OnboardingCheckInterceptor}가 매 요청 DB로 확인한 뒤다.
     */
    @Transactional
    public void recordUserChange(Long actorId, AdminAction action, User target,
                                 String beforeValue, String afterValue) {
        User actor = userRepository.getReferenceById(actorId);
        adminAuditLogRepository.save(
                AdminAuditLog.create(actor, action, target, target.getName(), beforeValue, afterValue));
    }

    /**
     * 퇴직자 파기 감사 기록 — 파기 전 이름을 어떤 값에도 남기지 않는다.
     * 대상은 target_user_id와 숫자 식별자만으로 확인한다.
     */
    @Transactional
    public void recordUserPurged(Long actorId, User target) {
        User actor = userRepository.getReferenceById(actorId);
        String targetLabel = "user_id=" + target.getId();
        adminAuditLogRepository.save(
                AdminAuditLog.create(actor, AdminAction.USER_PURGED, target, targetLabel, null, "파기 완료"));
    }

    /** 시스템 설정 변경 기록 — 대상 사원이 없고 설정 키가 대상 표시명이 된다 */
    @Transactional
    public void recordConfigChange(Long actorId, String configKey, String beforeValue, String afterValue) {
        User actor = userRepository.getReferenceById(actorId);
        adminAuditLogRepository.save(
                AdminAuditLog.create(actor, AdminAction.CONFIG_CHANGED, null, configKey, beforeValue, afterValue));
    }

    /** 이메일·연동처럼 사원 한 명을 직접 대상으로 하지 않는 관리자 조작 기록. */
    @Transactional
    public void recordEmailAction(Long actorId, AdminAction action, String targetLabel, String details) {
        User actor = userRepository.getReferenceById(actorId);
        adminAuditLogRepository.save(
                AdminAuditLog.create(actor, action, null, targetLabel, null, details));
    }

    /** 감사 로그 목록 (GET /api/admin/audit-logs, SA) — 최신순은 컨트롤러의 PageableDefault */
    @Transactional(readOnly = true)
    public Page<AdminAuditLogResponse> getLogs(AdminAction action, Long targetUserId, Pageable pageable) {
        return adminAuditLogRepository.search(action, targetUserId, pageable).map(AdminAuditLogResponse::of);
    }

    /** 필터용 액션 목록 (GET /api/admin/audit-logs/actions, SA) */
    public List<AdminActionOption> getActions() {
        return Arrays.stream(AdminAction.values()).map(AdminActionOption::of).toList();
    }
}
