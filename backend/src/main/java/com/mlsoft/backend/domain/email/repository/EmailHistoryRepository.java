package com.mlsoft.backend.domain.email.repository;

import com.mlsoft.backend.domain.email.entity.EmailHistory;
import com.mlsoft.backend.domain.email.entity.EmailStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 이메일 발송 이력 저장소.
 */
public interface EmailHistoryRepository extends JpaRepository<EmailHistory, Long> {

    /**
     * 재발송 대상 id — 오래된 순.
     *
     * <p>두 부류를 함께 집는다:
     * <ul>
     *   <li><b>FAILED</b> — 발송을 시도했으나 실패한 건 (리뷰 D-4의 상한 {@code retry_count < N} 적용)</li>
     *   <li><b>오래 묶인 PENDING</b> — 비동기 발송이 <b>시작조차 못 한</b> 건.
     *       큐 포화나 프로세스 강제 종료로 생긴다 (1차 테스트 B의 안전망)</li>
     * </ul>
     *
     * <p>PENDING에 시간 조건을 두지 않으면 정상 발송 중인 건까지 집어 중복 발송이 된다.
     *
     * <p>id만 반환하는 이유: 실제 발송은 건마다 새 트랜잭션에서 다시 조회한다 (1차 테스트 A).
     * 여기서 엔티티를 들고 가면 그 사이 상태가 바뀐 것을 못 본다.
     */
    @Query("""
            select e.id from EmailHistory e
             where e.retryCount < :maxAttempts
               and (e.status = :failed
                    or (e.status = :pending and e.createdAt < :staleBefore))
             order by e.id asc
            """)
    List<Long> findDispatchTargetIds(
            @Param("maxAttempts") int maxAttempts,
            @Param("failed") EmailStatus failed,
            @Param("pending") EmailStatus pending,
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable);
}
