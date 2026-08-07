package com.mlsoft.backend.domain.schedule.service;

import com.mlsoft.backend.domain.department.entity.Department;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 개인 일정 서비스 단위 테스트.
 * <p>
 * 이 도메인의 핵심 성질 — <b>연차 잔액을 건드리지 않고, 결재를 거치지 않는다</b> — 를 고정한다.
 * 잔액 관련 협력자(UserRepository의 저장 경로)를 아예 호출하지 않는다는 것까지 검증해,
 * 나중에 누군가 연차 로직을 여기 복사해 오면 테스트가 깨지게 한다.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleEntryRepository scheduleEntryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ScheduleService scheduleService;

    private static final LocalDate D1 = LocalDate.of(2026, 8, 10);
    private static final LocalDate D2 = LocalDate.of(2026, 8, 11);

    @Test
    @DisplayName("등록 — 승인 절차 없이 즉시 확정되고 연차 잔액을 건드리지 않는다")
    void create_외근등록_잔액변경없음() {
        User user = user(1L, "박민수", department(10L, "개발팀"));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(scheduleEntryRepository.existsOverlapping(any(), any(), anyList(), isNull()))
                .willReturn(false);
        given(scheduleEntryRepository.save(any(ScheduleEntry.class)))
                .willAnswer(inv -> inv.getArgument(0));

        ScheduleResponse response = scheduleService.create(1L,
                new ScheduleCreateRequest("FIELD_WORK", List.of(D1, D2), "고객사 방문"));

        assertEquals("FIELD_WORK", response.scheduleType());
        assertEquals("외근", response.typeLabel());
        assertEquals(List.of(D1, D2), response.dates());
        assertTrue(response.editable());

        // 잔액은 그대로 — 연차 3필드 중 어느 것도 변하지 않아야 한다
        assertEquals(0, user.getUseDays().compareTo(BigDecimal.ZERO));
        assertEquals(0, user.getAdvanceDays().compareTo(BigDecimal.ZERO));
        assertEquals(0, user.getBaseDays().compareTo(new BigDecimal("15.0")));
        // 결재가 없으므로 승인자 조회도 일어나지 않는다
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록 — 중복 날짜는 제거하고 오름차순으로 저장한다")
    void create_중복날짜_정규화() {
        User user = user(1L, "박민수", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(scheduleEntryRepository.existsOverlapping(any(), any(), anyList(), isNull()))
                .willReturn(false);
        given(scheduleEntryRepository.save(any(ScheduleEntry.class)))
                .willAnswer(inv -> inv.getArgument(0));

        scheduleService.create(1L,
                new ScheduleCreateRequest("REMOTE", List.of(D2, D1, D2), null));

        ArgumentCaptor<ScheduleEntry> captor = ArgumentCaptor.forClass(ScheduleEntry.class);
        verify(scheduleEntryRepository).save(captor.capture());
        assertEquals(List.of(D1, D2), captor.getValue().getDates());
    }

    @Test
    @DisplayName("등록 — 빈 메모는 null로 저장한다 (메모 없음의 표현을 하나로 고정)")
    void create_빈메모_null저장() {
        User user = user(1L, "박민수", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(scheduleEntryRepository.existsOverlapping(any(), any(), anyList(), isNull()))
                .willReturn(false);
        given(scheduleEntryRepository.save(any(ScheduleEntry.class)))
                .willAnswer(inv -> inv.getArgument(0));

        ScheduleResponse response = scheduleService.create(1L,
                new ScheduleCreateRequest("TRAINING", List.of(D1), "   "));

        assertNull(response.memo());
    }

    @Test
    @DisplayName("등록 — 같은 날짜에 같은 종류가 이미 있으면 409")
    void create_중복일정_예외() {
        User user = user(1L, "박민수", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(scheduleEntryRepository.existsOverlapping(any(), any(), anyList(), isNull()))
                .willReturn(true);

        BusinessException e = assertThrows(BusinessException.class, () -> scheduleService.create(1L,
                new ScheduleCreateRequest("FIELD_WORK", List.of(D1), null)));

        assertEquals(ErrorCode.DUPLICATE_SCHEDULE, e.getErrorCode());
        verify(scheduleEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록 — 알 수 없는 종류 문자열은 400")
    void create_잘못된종류_예외() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L, "박민수", null)));

        BusinessException e = assertThrows(BusinessException.class, () -> scheduleService.create(1L,
                new ScheduleCreateRequest("VACATION_ON_MARS", List.of(D1), null)));

        assertEquals(ErrorCode.INVALID_INPUT_VALUE, e.getErrorCode());
    }

    @Test
    @DisplayName("등록 — 주말·과거 날짜를 막지 않는다 (지난주 외근을 뒤늦게 기록하는 것이 정상)")
    void create_과거주말날짜_허용() {
        User user = user(1L, "박민수", null);
        LocalDate 지난토요일 = LocalDate.of(2026, 8, 1); // 토요일, 과거
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(scheduleEntryRepository.existsOverlapping(any(), any(), anyList(), isNull()))
                .willReturn(false);
        given(scheduleEntryRepository.save(any(ScheduleEntry.class)))
                .willAnswer(inv -> inv.getArgument(0));

        ScheduleResponse response = scheduleService.create(1L,
                new ScheduleCreateRequest("BUSINESS_TRIP", List.of(지난토요일), null));

        assertEquals(List.of(지난토요일), response.dates());
    }

    @Test
    @DisplayName("삭제 — 남의 일정은 403이고 실제로 지우지 않는다")
    void delete_타인일정_예외() {
        User owner = user(1L, "박민수", null);
        ScheduleEntry entry = ScheduleEntry.create(owner, ScheduleType.REMOTE, List.of(D1), null);
        given(scheduleEntryRepository.findById(99L)).willReturn(Optional.of(entry));

        BusinessException e = assertThrows(BusinessException.class, () -> scheduleService.delete(99L, 2L));

        assertEquals(ErrorCode.ACCESS_DENIED, e.getErrorCode());
        verify(scheduleEntryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("삭제 — 본인 일정은 삭제되고 잔액 복구 같은 후처리가 없다")
    void delete_본인일정_성공() {
        User owner = user(1L, "박민수", null);
        ScheduleEntry entry = ScheduleEntry.create(owner, ScheduleType.REMOTE, List.of(D1), null);
        given(scheduleEntryRepository.findById(99L)).willReturn(Optional.of(entry));

        scheduleService.delete(99L, 1L);

        verify(scheduleEntryRepository).delete(entry);
        assertEquals(0, owner.getUseDays().compareTo(BigDecimal.ZERO));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("수정 — 본인 일정의 종류·날짜·메모를 갈아끼운다 (컬렉션 인스턴스는 유지)")
    void update_본인일정_성공() {
        User owner = user(1L, "박민수", null);
        ScheduleEntry entry = ScheduleEntry.create(owner, ScheduleType.REMOTE, List.of(D1), "재택");
        List<LocalDate> 원본컬렉션 = entry.getDates();
        given(scheduleEntryRepository.findById(99L)).willReturn(Optional.of(entry));
        given(scheduleEntryRepository.existsOverlapping(any(), any(), anyList(), eq(99L)))
                .willReturn(false);

        ScheduleResponse response = scheduleService.update(99L, 1L,
                new ScheduleCreateRequest("FIELD_WORK", List.of(D2), "고객사"));

        assertEquals("FIELD_WORK", response.scheduleType());
        assertEquals(List.of(D2), response.dates());
        assertEquals("고객사", response.memo());
        // Hibernate가 orphan 삭제를 추적하려면 컬렉션 인스턴스가 바뀌면 안 된다
        assertTrue(원본컬렉션 == entry.getDates());
    }

    @Test
    @DisplayName("캘린더 — 타인 메모는 마스킹하고 editable=false로 내린다")
    void getCalendar_타인메모_마스킹() {
        Department dept = department(10L, "개발팀");
        User viewer = user(1L, "박민수", dept);
        User other = user(2L, "김지훈", dept);
        ScheduleEntry mine = ScheduleEntry.create(viewer, ScheduleType.REMOTE, List.of(D1), "내 메모");
        ScheduleEntry theirs = ScheduleEntry.create(other, ScheduleType.FIELD_WORK, List.of(D1), "남 메모");
        given(userRepository.findById(1L)).willReturn(Optional.of(viewer));
        given(scheduleEntryRepository.findInDateRange(any(), any(), isNull(), isNull()))
                .willReturn(List.of(mine, theirs));

        List<ScheduleResponse> responses = scheduleService.getCalendar(1L, 2026, 8, null, null);

        assertEquals("내 메모", responses.get(0).memo());
        assertTrue(responses.get(0).editable());
        assertNull(responses.get(1).memo());
        assertFalse(responses.get(1).editable());
    }

    @Test
    @DisplayName("캘린더 — SYSTEM_ADMIN은 타인 메모도 열람한다")
    void getCalendar_관리자_메모열람() {
        User admin = user(1L, "관리자", null);
        admin.changeRole(Role.SYSTEM_ADMIN);
        User other = user(2L, "김지훈", null);
        ScheduleEntry theirs = ScheduleEntry.create(other, ScheduleType.FIELD_WORK, List.of(D1), "남 메모");
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));
        given(scheduleEntryRepository.findInDateRange(any(), any(), isNull(), isNull()))
                .willReturn(List.of(theirs));

        List<ScheduleResponse> responses = scheduleService.getCalendar(1L, 2026, 8, null, null);

        assertEquals("남 메모", responses.get(0).memo());
        assertFalse(responses.get(0).editable()); // 열람은 되지만 수정 권한은 없다
    }

    @Test
    @DisplayName("캘린더 — 빈 검색어는 필터 없음으로 넘긴다 (쿼리의 null 분기)")
    void getCalendar_빈검색어_null전달() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L, "박민수", null)));
        given(scheduleEntryRepository.findInDateRange(any(), any(), isNull(), isNull()))
                .willReturn(List.of());

        scheduleService.getCalendar(1L, 2026, 8, "   ", null);

        verify(scheduleEntryRepository).findInDateRange(any(), any(), isNull(), isNull());
    }

    @Test
    @DisplayName("캘린더 — 검색어는 공백을 제거해 전달한다")
    void getCalendar_검색어_trim() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L, "박민수", null)));
        given(scheduleEntryRepository.findInDateRange(any(), any(), eq("김지훈"), eq(10L)))
                .willReturn(List.of());

        scheduleService.getCalendar(1L, 2026, 8, "  김지훈  ", 10L);

        verify(scheduleEntryRepository).findInDateRange(any(), any(), eq("김지훈"), eq(10L));
    }

    @Test
    @DisplayName("캘린더 — 잘못된 월은 400")
    void getCalendar_잘못된월_예외() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> scheduleService.getCalendar(1L, 2026, 13, null, null));

        assertEquals(ErrorCode.INVALID_INPUT_VALUE, e.getErrorCode());
    }

    // ==== 헬퍼 ====

    private static Department department(Long id, String name) {
        return Department.builder().id(id).name(name).description(name + " 설명").active(true).build();
    }

    private static User user(Long id, String name, Department department) {
        return User.builder()
                .id(id)
                .name(name)
                .email(name + "@mlsoft.com")
                .role(Role.EMPLOYEE)
                .department(department)
                .baseDays(new BigDecimal("15.0"))
                .useDays(BigDecimal.ZERO)
                .bonusDays(BigDecimal.ZERO)
                .advanceDays(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }
}
