package com.mlsoft.backend.domain.schedule.repository;

import com.mlsoft.backend.domain.schedule.entity.ScheduleEntry;
import com.mlsoft.backend.domain.schedule.entity.ScheduleType;
import com.mlsoft.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, Long> {

    /**
     * 캘린더 — 날짜범위와 겹치는 전체 일정 (GET /api/schedules/calendar).
     * <p>
     * keyword(등록자명 부분일치)·departmentId는 둘 다 선택이다. null이면 조건이 무력화되게
     * {@code :param is null or ...} 형태로 썼다 — 조합마다 메서드를 따로 만들지 않기 위해서다.
     * <p>
     * {@code join se.dates d}가 날짜 수만큼 행을 늘리므로 distinct가 필수다.
     * 등록자·부서는 응답에 항상 쓰이므로 @EntityGraph로 함께 적재한다 (리뷰 D-2 N+1).
     */
    @EntityGraph(attributePaths = {"user", "user.department"})
    @Query("select distinct se from ScheduleEntry se join se.dates d "
            + "where d between :start and :end "
            + "and (:keyword is null or lower(se.user.name) like lower(concat('%', :keyword, '%'))) "
            + "and (:departmentId is null or se.user.department.id = :departmentId)")
    List<ScheduleEntry> findInDateRange(@Param("start") LocalDate start,
                                        @Param("end") LocalDate end,
                                        @Param("keyword") String keyword,
                                        @Param("departmentId") Long departmentId);

    /** 내 일정 목록 (GET /api/schedules/me) — 최신 등록순 */
    @EntityGraph(attributePaths = {"user", "user.department"})
    Page<ScheduleEntry> findByUserOrderByIdDesc(User user, Pageable pageable);

    /** 같은 날짜에 같은 종류를 이미 등록했는지 — 중복 등록 차단용 (등록 시 검사) */
    @Query("select count(se) > 0 from ScheduleEntry se join se.dates d "
            + "where se.user = :user and se.scheduleType = :scheduleType and d in :dates "
            + "and (:excludeId is null or se.id <> :excludeId)")
    boolean existsOverlapping(@Param("user") User user,
                              @Param("scheduleType") ScheduleType scheduleType,
                              @Param("dates") List<LocalDate> dates,
                              @Param("excludeId") Long excludeId);
}
