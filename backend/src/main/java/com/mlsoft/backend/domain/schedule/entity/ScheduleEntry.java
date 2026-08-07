package com.mlsoft.backend.domain.schedule.entity;

import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 개인 일정 (외근·출장·재택·교육) — 연차 잔액을 건드리지 않는 근무 형태 기록.
 * <p>
 * {@code LeaveRequest}와 달리 <b>status·승인자·days가 없다.</b> 결재를 거치지 않으므로
 * 상태 전이 자체가 없고, 잔액을 차감하지 않으므로 일수를 저장할 이유도 없다
 * (필요하면 {@code dates.size()}로 센다). 필드를 두고 안 쓰면 나중에 누군가 채운다.
 * <p>
 * 삭제는 본인만 — 서비스 계층에서 소유권을 검증한다 (docs/04: 소유권 검증은 서비스 책임).
 */
@Entity
@Table(name = "schedule_entries")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleEntry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 등록자 (본인) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 일정 종류 */
    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false)
    private ScheduleType scheduleType;

    /** 메모 — 연차 사유와 달리 선택 사항이다 (본인 기록이라 강제할 이유가 없다) */
    @Column(length = 500)
    private String memo;

    /** 일정 날짜 목록 — leave_dates와 같은 패턴, (일정, 날짜) 중복은 DB 유니크로 차단 */
    @ElementCollection
    @CollectionTable(
            name = "schedule_dates",
            joinColumns = @JoinColumn(name = "schedule_entry_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_schedule_dates_entry_day",
                    columnNames = {"schedule_entry_id", "day"}))
    @OrderBy // 값 오름차순 — 시작일·종료일 표시가 순서에 의존한다
    @Column(name = "day", nullable = false)
    @Builder.Default
    // 목록 조회에서 일정 N건의 날짜를 개별 쿼리로 읽지 않게 배치로 묶는다 (리뷰 D-2 N+1).
    // 컬렉션이라 @EntityGraph로 함께 적재하면 페이징이 메모리에서 처리되므로 배치 fetch를 쓴다.
    @BatchSize(size = 50)
    private List<LocalDate> dates = new ArrayList<>();

    /**
     * 개인 일정 생성 — 승인 절차가 없어 등록 즉시 확정이다.
     * 중복 날짜는 제거 후 오름차순 정렬해 저장한다 (같은 날짜 2회 전달 시 중복 표시 방지).
     */
    public static ScheduleEntry create(User user, ScheduleType scheduleType,
                                       List<LocalDate> dates, String memo) {
        List<LocalDate> distinctDates = dates.stream().distinct().sorted().toList();
        return ScheduleEntry.builder()
                .user(user)
                .scheduleType(scheduleType)
                .dates(new ArrayList<>(distinctDates))
                .memo(normalizeMemo(memo))
                .build();
    }

    /** 종류·날짜·메모 수정 — 삭제 후 재등록 대신 쓸 수 있게 둔다 (소유권 검증은 서비스) */
    public void update(ScheduleType scheduleType, List<LocalDate> dates, String memo) {
        List<LocalDate> distinctDates = dates.stream().distinct().sorted().toList();
        this.scheduleType = scheduleType;
        // 컬렉션 인스턴스를 교체하지 않고 내용만 갈아끼운다 — Hibernate가 orphan 삭제를 추적한다
        this.dates.clear();
        this.dates.addAll(distinctDates);
        this.memo = normalizeMemo(memo);
    }

    /** 빈 문자열은 null로 — "메모 없음"의 표현을 하나로 고정한다 */
    private static String normalizeMemo(String memo) {
        return (memo == null || memo.isBlank()) ? null : memo.trim();
    }

    /** 이 일정의 소유자인지 */
    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
