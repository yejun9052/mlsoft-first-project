package com.mlsoft.backend.domain.email.repository;

import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 이메일 발송 이력 저장소.
 */
public interface EmailHistoryRepository extends JpaRepository<EmailHistory, Long> {

    /**
     * 재시도 상한 미만 FAILED를 오래된 순으로 최대 100건 조회한다 (리뷰 D-4).
     *
     * <p>발송 시 수신자 이메일·재직 상태를 읽으므로 user를 함께 적재한다.
     */
    @EntityGraph(attributePaths = "user")
    List<EmailHistory> findTop100ByStatusAndRetryCountLessThanOrderByIdAsc(
            EmailStatus status,
            int retryCount);
}
