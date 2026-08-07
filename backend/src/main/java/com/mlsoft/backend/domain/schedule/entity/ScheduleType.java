package com.mlsoft.backend.domain.schedule.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 개인 일정 종류 — <b>연차를 차감하지 않는</b> 근무 형태를 캘린더에 기록하기 위한 것이다.
 * <p>
 * {@code LeaveType}(연차·반차)과 의도적으로 분리했다. 연차는 잔액을 차감하고 결재를 거치지만
 * 이쪽은 둘 다 없다 — 본인이 자기 근무 형태를 기록하는 성격이라 등록 즉시 확정된다.
 * 같은 엔티티에 얹으면 신청·결재·취소 흐름마다 "차감 안 하면 건너뛰기" 분기가 생기는데,
 * 그 조건 분기 산재가 리뷰 I-1·I-5의 원인이었다.
 * <p>
 * 종류를 추가할 땐 여기 상수 한 줄과 프론트 {@code SCHEDULE_TYPE_LABEL}만 맞추면 된다.
 */
@Getter
@RequiredArgsConstructor
public enum ScheduleType {

    FIELD_WORK("외근"),
    BUSINESS_TRIP("출장"),
    REMOTE("재택근무"),
    TRAINING("교육·연수");

    /** 화면 표시용 한글 라벨 — 서버가 응답에 함께 실어 보내 프론트 하드코딩을 줄인다 */
    private final String label;
}
