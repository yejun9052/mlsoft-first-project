package com.mlsoft.backend.domain.user.service;

import com.mlsoft.backend.domain.email.service.EmailNotificationPublisher;
import com.mlsoft.backend.domain.policy.entity.PolicyConfigKey;
import com.mlsoft.backend.domain.policy.service.PolicyConfigReader;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 퇴직자 자동 파기 잡의 대상 판정과 예고·결과 메일을 담당한다 (설계 §5).
 *
 * <p>실제 익명화는 P2의 {@link UserService#purgeAutomatically(Long, LocalDate)}를 호출한다.
 * 이 서비스는 정책·날짜·예고 상태만 관리하고, 사원별 트랜잭션 경계는 호출 대상 메서드가 갖는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetireePurgeService {

    /** 예고 후 파기까지의 기간 — 정책 키로 늘리지 않는다. */
    public static final int NOTICE_LEAD_DAYS = 30;

    private final UserRepository userRepository;
    private final PolicyConfigReader policyConfigReader;
    private final UserService userService;
    private final EmailNotificationPublisher emailNotificationPublisher;

    /** 자동 모드 여부 — 스케줄러가 MANUAL이면 다섯 번째 잡을 즉시 건너뛴다. */
    @Transactional(readOnly = true)
    public boolean isAutoMode() {
        return "AUTO".equals(policyConfigReader.getString(PolicyConfigKey.RETIREE_PURGE_MODE));
    }

    /** 30일 뒤 보존기간이 끝나는 퇴직자 중 아직 예고하지 않은 사원 id. */
    @Transactional(readOnly = true)
    public List<Long> findNoticeTargetIds(LocalDate today) {
        if (!isAutoMode()) {
            return List.of();
        }
        int retentionYears = policyConfigReader.getInt(PolicyConfigKey.RETIREE_PURGE_YEARS);
        LocalDate retiredAtOnOrBefore = today.plusDays(NOTICE_LEAD_DAYS).minusYears(retentionYears);
        return userRepository.findIdsForRetireePurgeNotice(retiredAtOnOrBefore);
    }

    /**
     * 예고 메일 한 건을 처리한다 — 사원 1명당 {@code REQUIRES_NEW} 트랜잭션.
     * 예고 시각을 먼저 저장해 같은 날 재실행해도 중복 메일이 나가지 않게 한다.
     *
     * @return 예고를 보냈으면 1, 대상이 아니거나 이미 보냈으면 0
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int sendNotice(Long userId, LocalDate today) {
        if (!isAutoMode()) {
            return 0;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        int retentionYears = policyConfigReader.getInt(PolicyConfigKey.RETIREE_PURGE_YEARS);
        if (!isNoticeTarget(user, today, retentionYears)) {
            return 0;
        }
        if (user.getPurgeNoticeSentAt() != null) {
            return 0;
        }

        user.markPurgeNoticeSent(today.atStartOfDay());
        log.info("[퇴직자 파기 예고] userId={}, 예정일={}", userId, today.plusDays(NOTICE_LEAD_DAYS));
        return 1;
    }

    /** 이번 실행에서 새로 예고한 대상만 모아 관리자별 NOTICE 한 통으로 보낸다. */
    @Transactional
    public int publishNotice(List<Long> noticeTargetIds, LocalDate today) {
        if (noticeTargetIds.isEmpty() || !isAutoMode()) {
            return 0;
        }
        List<String> targetNames = userRepository.findAllById(noticeTargetIds).stream()
                .filter(user -> user.getPurgeNoticeSentAt() != null
                        && today.equals(user.getPurgeNoticeSentAt().toLocalDate()))
                .map(User::getName)
                .toList();
        if (targetNames.isEmpty()) {
            return 0;
        }
        emailNotificationPublisher.publishRetireePurgeNotice(
                targetNames, today.plusDays(NOTICE_LEAD_DAYS));
        return targetNames.size();
    }

    /** 예고 후 30일이 지난 자동 파기 대상 id. */
    @Transactional(readOnly = true)
    public List<Long> findTargetIds(LocalDate today) {
        if (!isAutoMode()) {
            return List.of();
        }
        int retentionYears = policyConfigReader.getInt(PolicyConfigKey.RETIREE_PURGE_YEARS);
        LocalDate retiredAtOnOrBefore = today.minusYears(retentionYears);
        LocalDateTime noticeSentOnOrBefore = today.minusDays(NOTICE_LEAD_DAYS).atStartOfDay();
        return userRepository.findIdsForRetireePurge(retiredAtOnOrBefore, noticeSentOnOrBefore);
    }

    /** P2 파기 도메인을 프록시 경유로 호출한다. 실제 사원별 트랜잭션은 UserService에 있다. */
    public int purge(Long userId, LocalDate today) {
        if (!isAutoMode()) {
            return 0;
        }
        return userService.purgeAutomatically(userId, today);
    }

    /** 이번 실행에서 파기된 사원 수를 재직 SYSTEM_ADMIN 전원에게 한 번 알린다. */
    @Transactional
    public int publishResult(int purgedCount, LocalDate today) {
        if (purgedCount <= 0 || !isAutoMode()) {
            return 0;
        }
        emailNotificationPublisher.publishRetireePurgeResult(purgedCount, today);
        return purgedCount;
    }

    private boolean isNoticeTarget(User user, LocalDate today, int retentionYears) {
        if (user.isActive() || user.getPurgedAt() != null
                || user.getPurgeHoldReason() != null || user.getRetiredAt() == null) {
            return false;
        }
        LocalDate purgeDate = user.getRetiredAt().plusYears(retentionYears);
        return !purgeDate.isAfter(today.plusDays(NOTICE_LEAD_DAYS));
    }
}
