package com.mlsoft.backend.domain.schedule.service;

import com.mlsoft.backend.domain.schedule.dto.ScheduleCreateRequest;
import com.mlsoft.backend.domain.schedule.dto.ScheduleResponse;
import com.mlsoft.backend.domain.schedule.entity.ScheduleEntry;
import com.mlsoft.backend.domain.schedule.entity.ScheduleType;
import com.mlsoft.backend.domain.schedule.repository.ScheduleEntryRepository;
import com.mlsoft.backend.domain.user.entity.Role;
import com.mlsoft.backend.domain.user.entity.User;
import com.mlsoft.backend.domain.user.repository.UserRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

/**
 * 개인 일정 (외근·출장·재택·교육) 서비스.
 * <p>
 * <b>연차와 다른 점 셋</b> — 잔액을 차감하지 않고, 결재를 거치지 않고, 상태 전이가 없다.
 * 그래서 이 클래스에는 승인·복구·동시성 방어가 없다. 연차 쪽 로직을 여기 복사해 오지 말 것.
 * <p>
 * 소유권 검증은 여기 책임이다 (컨트롤러의 {@code @PreAuthorize}는 역할 게이트 전용 — docs/04).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleEntryRepository scheduleEntryRepository;
    private final UserRepository userRepository;

    /**
     * 일정 등록 (POST /api/schedules) — 승인 절차가 없어 등록 즉시 확정된다.
     * <p>
     * 연차와 달리 주말·과거 날짜를 막지 않는다. 주말 출장·지난주 외근을 뒤늦게 기록하는 것이
     * 정상 사용이기 때문이다 (연차는 잔액이 걸려 있어 과거 신청을 막는 것과 대비된다).
     */
    @Transactional
    public ScheduleResponse create(Long userId, ScheduleCreateRequest request) {
        User user = findUserOrThrow(userId);
        ScheduleType type = parseType(request.scheduleType());

        // 같은 날짜에 같은 종류를 두 번 등록하면 캘린더에 중복으로 쌓인다 — 등록 시점에 막는다
        if (scheduleEntryRepository.existsOverlapping(user, type, request.dates(), null)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SCHEDULE);
        }

        ScheduleEntry saved = scheduleEntryRepository.save(
                ScheduleEntry.create(user, type, request.dates(), request.memo()));
        log.info("[일정] 등록 userId={} type={} 날짜수={}", userId, type, saved.getDates().size());
        return ScheduleResponse.of(saved, userId, false);
    }

    /** 일정 수정 (PUT /api/schedules/{id}) — 본인만 */
    @Transactional
    public ScheduleResponse update(Long scheduleId, Long userId, ScheduleCreateRequest request) {
        ScheduleEntry entry = findScheduleOrThrow(scheduleId);
        requireOwner(entry, userId);
        ScheduleType type = parseType(request.scheduleType());

        // 자기 자신은 중복 검사에서 제외 — 날짜를 그대로 두고 메모만 고치는 경우를 막지 않기 위해
        if (scheduleEntryRepository.existsOverlapping(entry.getUser(), type, request.dates(), scheduleId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SCHEDULE);
        }

        entry.update(type, request.dates(), request.memo());
        return ScheduleResponse.of(entry, userId, false);
    }

    /** 일정 삭제 (DELETE /api/schedules/{id}) — 본인만. 잔액 복구 같은 후처리가 없다 */
    @Transactional
    public void delete(Long scheduleId, Long userId) {
        ScheduleEntry entry = findScheduleOrThrow(scheduleId);
        requireOwner(entry, userId);
        scheduleEntryRepository.delete(entry);
        log.info("[일정] 삭제 scheduleId={} userId={}", scheduleId, userId);
    }

    /**
     * 캘린더 (GET /api/schedules/calendar) — 전 직원 대상, 이름·부서로 좁힐 수 있다.
     * <p>
     * 검색 범위를 전 직원으로 둔 이유: 캘린더가 이미 전사 일정을 보여주므로 이름 필터가
     * 노출 범위를 넓히지 않는다. 메모는 본인·관리자에게만 채워진다 (ScheduleResponse).
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getCalendar(Long viewerId, int year, int month,
                                              String keyword, Long departmentId) {
        if (month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        User viewer = findUserOrThrow(viewerId);
        YearMonth yearMonth = YearMonth.of(year, month);
        List<ScheduleEntry> entries = scheduleEntryRepository.findInDateRange(
                yearMonth.atDay(1), yearMonth.atEndOfMonth(), normalizeKeyword(keyword), departmentId);

        boolean isAdmin = viewer.getRole() == Role.SYSTEM_ADMIN;
        return entries.stream()
                .map(entry -> ScheduleResponse.of(entry, viewerId, isAdmin))
                .toList();
    }

    /** 내 일정 목록 (GET /api/schedules/me) */
    @Transactional(readOnly = true)
    public Page<ScheduleResponse> getMySchedules(Long userId, Pageable pageable) {
        User user = findUserOrThrow(userId);
        return scheduleEntryRepository.findByUserOrderByIdDesc(user, pageable)
                .map(entry -> ScheduleResponse.of(entry, userId, false));
    }

    // ==== 헬퍼 ====

    private ScheduleType parseType(String raw) {
        try {
            return ScheduleType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /** 빈 검색어는 "필터 없음"으로 — 쿼리의 :keyword is null 분기를 타게 한다 */
    private String normalizeKeyword(String keyword) {
        return (keyword == null || keyword.isBlank()) ? null : keyword.trim();
    }

    private void requireOwner(ScheduleEntry entry, Long userId) {
        if (!entry.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private ScheduleEntry findScheduleOrThrow(Long scheduleId) {
        return scheduleEntryRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    }
}
