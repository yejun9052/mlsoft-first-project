package com.mlsoft.backend.domain.holiday.service;

import com.mlsoft.backend.domain.holiday.client.HolidayApiClient;
import com.mlsoft.backend.domain.holiday.entity.Holiday;
import com.mlsoft.backend.domain.holiday.repository.HolidayRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HolidayPersisterTest {

    @Mock
    private HolidayRepository holidayRepository;

    @InjectMocks
    private HolidayPersister holidayPersister;

    @Test
    @DisplayName("동기화 저장은 선택한 연도만 삭제하고 새 응답을 저장한다")
    void replaceYear_선택연도교체() {
        List<HolidayApiClient.HolidayItem> items = List.of(
                new HolidayApiClient.HolidayItem(LocalDate.of(2026, 1, 1), "신정"),
                new HolidayApiClient.HolidayItem(LocalDate.of(2026, 3, 1), "삼일절"));
        given(holidayRepository.saveAll(anyList())).willAnswer(invocation -> {
            List<Holiday> saved = invocation.getArgument(0);
            assertEquals(List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)),
                    saved.stream().map(Holiday::getDate).toList());
            return saved;
        });

        int count = holidayPersister.replaceYear(2026, items);

        assertEquals(2, count);
        InOrder order = inOrder(holidayRepository);
        order.verify(holidayRepository).deleteAllByYear(2026);

        order.verify(holidayRepository).saveAll(anyList());
        verify(holidayRepository).deleteAllByYear(2026);
    }
}
