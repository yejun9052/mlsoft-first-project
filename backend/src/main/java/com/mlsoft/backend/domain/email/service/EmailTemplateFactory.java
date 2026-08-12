package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.domain.email.event.EmailTemplateData;
import org.springframework.stereotype.Component;

/**
 * 건별 이메일 제목·본문의 단일 출처.
 *
 * <p>템플릿 엔진을 추가하지 않고 일반 텍스트를 조립한다. 사유는 수신자별 권한이 있을 때만 넣는다
 * (검증 Y-4).
 */
@Component
public class EmailTemplateFactory {

    private static final String SERVICE_NAME = "MLsoft 연차관리";
    private static final String MASKED_REASON = "권한이 없어 표시되지 않습니다.";

    public EmailMessage create(EmailTemplateData data, boolean reasonVisible) {
        return switch (data.kind()) {
            case LEAVE_APPLIED -> requestMessage(
                    "[연차 신청] " + data.applicantName(),
                    "연차 신청이 접수되었습니다.",
                    data,
                    reasonVisible);
            case LEAVE_APPROVED -> resultMessage(
                    "[연차 승인] " + data.applicantName(),
                    "연차 신청이 승인되었습니다.",
                    data,
                    reasonVisible);
            case LEAVE_REJECTED -> resultMessage(
                    "[연차 반려] " + data.applicantName(),
                    "연차 신청이 반려되었습니다.",
                    data,
                    reasonVisible);
            case LEAVE_CANCELLED -> resultMessage(
                    "[연차 취소] " + data.applicantName(),
                    "연차 신청이 취소되었습니다.",
                    data,
                    reasonVisible);
            case LEAVE_CANCEL_PENDING -> resultMessage(
                    "[소급취소 신청] " + data.applicantName(),
                    "연차 소급취소가 승인 대기 상태로 접수되었습니다.",
                    data,
                    reasonVisible);
            case LEAVE_CANCEL_APPROVED -> resultMessage(
                    "[소급취소 승인] " + data.applicantName(),
                    "연차 소급취소가 승인되었습니다.",
                    data,
                    reasonVisible);
            case LEAVE_CANCEL_REJECTED -> resultMessage(
                    "[소급취소 반려] " + data.applicantName(),
                    "연차 소급취소가 반려되었습니다.",
                    data,
                    reasonVisible);
            case WELFARE_APPLIED -> requestMessage(
                    "[복리후생 신청] " + data.applicantName(),
                    "복리후생 신청이 접수되었습니다.",
                    data,
                    reasonVisible);
            case WELFARE_APPROVED -> resultMessage(
                    "[복리후생 승인] " + data.applicantName(),
                    "복리후생 신청이 승인되었습니다.",
                    data,
                    reasonVisible);
            case WELFARE_REJECTED -> resultMessage(
                    "[복리후생 반려] " + data.applicantName(),
                    "복리후생 신청이 반려되었습니다.",
                    data,
                    reasonVisible);
            case BIRTHDAY_LEAVE_GRANTED -> birthdayMessage(data);
        };
    }

    private EmailMessage requestMessage(
            String title,
            String summary,
            EmailTemplateData data,
            boolean reasonVisible
    ) {
        return new EmailMessage(title, commonBody(summary, data, reasonVisible));
    }

    private EmailMessage resultMessage(
            String title,
            String summary,
            EmailTemplateData data,
            boolean reasonVisible
    ) {
        String body = commonBody(summary, data, reasonVisible);
        if (!data.actorName().isBlank()) {
            body += System.lineSeparator() + "처리자: " + data.actorName();
        }
        return new EmailMessage(title, body);
    }

    private String commonBody(
            String summary,
            EmailTemplateData data,
            boolean reasonVisible
    ) {
        StringBuilder body = new StringBuilder()
                .append(summary).append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("신청자: ").append(data.applicantName()).append(System.lineSeparator());

        if (data.requestId() != null) {
            body.append("신청 번호: ").append(data.requestId()).append(System.lineSeparator());
        }
        if (!data.itemName().isBlank()) {
            body.append("구분: ").append(data.itemName()).append(System.lineSeparator());
        }
        if (!data.dates().isBlank()) {
            body.append("날짜: ").append(data.dates()).append(System.lineSeparator());
        }
        if (!data.days().isBlank()) {
            body.append("일수: ").append(data.days()).append("일").append(System.lineSeparator());
        }
        if (!data.reason().isBlank()) {
            body.append("사유: ")
                    .append(reasonVisible ? data.reason() : MASKED_REASON)
                    .append(System.lineSeparator());
        }

        return body.append(System.lineSeparator())
                .append(SERVICE_NAME)
                .toString();
    }

    private EmailMessage birthdayMessage(EmailTemplateData data) {
        String title = "[생일 반차 지급] " + data.applicantName();
        String content = data.applicantName() + " 님에게 생일 반차가 지급되었습니다."
                + System.lineSeparator()
                + System.lineSeparator()
                + "지급일: " + data.dates()
                + System.lineSeparator()
                + "지급 일수: " + data.days() + "일"
                + System.lineSeparator()
                + System.lineSeparator()
                + SERVICE_NAME;
        return new EmailMessage(title, content);
    }
}
