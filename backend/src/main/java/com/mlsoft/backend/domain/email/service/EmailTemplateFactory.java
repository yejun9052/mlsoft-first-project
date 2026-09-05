package com.mlsoft.backend.domain.email.service;

import com.mlsoft.backend.config.AppProperties;
import com.mlsoft.backend.domain.email.event.EmailTemplateData;
import com.mlsoft.backend.domain.email.event.EmailTemplateKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 건별 이메일 제목·본문의 단일 출처.
 *
 * <p>템플릿 엔진 없이 HTML을 조립한다. 사유는 수신자별 권한이 있을 때만 넣는다 (검증 Y-4).
 *
 * <p><b>본문이 HTML인 이유</b> (2026-08-16): 예전에는 {@code "구분: 연차"}처럼 줄바꿈으로 나열한
 * 평문이라 항목이 늘어날수록 읽기 어려웠다. 지금은 항목을 표 한 장으로 묶는다.
 *
 * <p><b>이메일 HTML은 웹 HTML과 다르다.</b> 지켜야 할 제약:
 * <ul>
 *   <li>flexbox·grid·외부 CSS가 안 통한다 — 레이아웃은 {@code <table>}, 스타일은 전부 인라인</li>
 *   <li>Gmail은 {@code <style>} 블록을 지우기도 한다. 그래서 클래스를 쓰지 않는다</li>
 *   <li>다크 테마를 쓰지 않는다 — 클라이언트마다 색을 제멋대로 반전시켜 대비가 무너진다.
 *       밝은 배경 + 짙은 헤더 띠로 브랜드만 살린다</li>
 * </ul>
 *
 * <p><b>사용자 입력은 반드시 {@link #escape}를 통과시킨다.</b> 이름·사유가 그대로 들어가는데,
 * {@code <}가 섞이면 표가 깨지고 링크를 심을 수도 있다.
 */
@Component
public class EmailTemplateFactory {

    private static final String SERVICE_NAME = "MLsoft 연차관리";
    private static final String MASKED_REASON = "권한이 없어 표시되지 않습니다.";
    private static final String FOOTER_NOTE = "이 메일은 " + SERVICE_NAME + " 시스템이 자동으로 발송했습니다.";

    // 색은 앱 토큰(index.css)에서 가져오되 밝은 배경 기준으로 고른 값이다
    private static final String COLOR_PAGE_BG = "#f4f6fa";
    private static final String COLOR_CARD_BG = "#ffffff";
    private static final String COLOR_HEADER_BG = "#0e1a2e";
    private static final String COLOR_BORDER = "#e3e8f0";
    private static final String COLOR_TEXT = "#1b2c47";
    private static final String COLOR_LABEL = "#64748b";
    private static final String COLOR_FOOTER_BG = "#f7f9fc";
    // 버튼은 accent가 아니라 accent-dark다 — 밝은 accent(#3b7dff) 위 흰 글자는 3.77:1로 미달이고,
    // 이메일에서는 앱처럼 글자를 딥네이비로 뒤집으면 "누르는 것"으로 안 읽힌다. #1b4ad9 위 흰 글자는 6.94:1
    private static final String COLOR_BUTTON_BG = "#1b4ad9";
    private static final String FONT_STACK =
            "'Apple SD Gothic Neo','Malgun Gothic','맑은 고딕',Arial,sans-serif";

    private final AppProperties appProperties;

    public EmailTemplateFactory(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * 메일 한 종류의 짧은 이름 — 제목의 대괄호와 카드 헤더 띠가 <b>같은 값</b>을 쓴다.
     *
     * <p>예전에는 헤더 띠가 종류와 무관하게 서비스명("MLsoft 연차관리")이었다. 그래서
     * 복리후생 신청 메일을 열어도 맨 위 큰 글자가 "연차"라 <b>연차 메일로 읽혔다</b>
     * (2026-08-16 지적). 브랜드는 푸터가 들고, 헤더 띠는 그 메일이 무엇인지를 말한다.
     */
    private String heading(EmailTemplateKind kind) {
        return switch (kind) {
            case LEAVE_APPLIED -> "연차 신청";
            case LEAVE_APPROVED -> "연차 승인";
            case LEAVE_REJECTED -> "연차 반려";
            case LEAVE_CANCELLED -> "연차 취소";
            case LEAVE_CANCEL_PENDING -> "소급취소 신청";
            case LEAVE_CANCEL_APPROVED -> "소급취소 승인";
            case LEAVE_CANCEL_REJECTED -> "소급취소 반려";
            case WELFARE_APPLIED -> "복리후생 신청";
            case WELFARE_APPROVED -> "복리후생 승인";
            case WELFARE_REJECTED -> "복리후생 반려";
            case BIRTHDAY_LEAVE_GRANTED -> "생일 반차 지급";
            case ONBOARDING_PENDING -> "온보딩 승인 대기";
            case ONBOARDING_APPROVED -> "온보딩 확정";
            case ONBOARDING_REJECTED -> "온보딩 반려";
            case ONBOARDING_REVISED -> "온보딩 수정";
        };
    }

    /**
     * @param reasonVisible 이 수신자가 사유를 볼 권한이 있는가 (검증 Y-4)
     * @param forApplicant  이 수신자가 <b>신청 당사자</b>인가 — 바로가기 버튼의 행선지가 갈린다
     */
    public EmailMessage create(EmailTemplateData data, boolean reasonVisible, boolean forApplicant) {
        return switch (data.kind()) {
            case LEAVE_APPLIED -> requestMessage("연차 신청이 접수되었습니다.", data, reasonVisible, forApplicant);
            case LEAVE_APPROVED -> resultMessage("연차 신청이 승인되었습니다.", data, reasonVisible, forApplicant);
            case LEAVE_REJECTED -> resultMessage("연차 신청이 반려되었습니다.", data, reasonVisible, forApplicant);
            case LEAVE_CANCELLED -> resultMessage("연차 신청이 취소되었습니다.", data, reasonVisible, forApplicant);
            case LEAVE_CANCEL_PENDING -> resultMessage(
                    "연차 소급취소가 승인 대기 상태로 접수되었습니다.", data, reasonVisible, forApplicant);
            case LEAVE_CANCEL_APPROVED -> resultMessage(
                    "연차 소급취소가 승인되었습니다.", data, reasonVisible, forApplicant);
            case LEAVE_CANCEL_REJECTED -> resultMessage(
                    "연차 소급취소가 반려되었습니다.", data, reasonVisible, forApplicant);
            case WELFARE_APPLIED -> requestMessage(
                    "복리후생 신청이 접수되었습니다.", data, reasonVisible, forApplicant);
            case WELFARE_APPROVED -> resultMessage(
                    "복리후생 신청이 승인되었습니다.", data, reasonVisible, forApplicant);
            case WELFARE_REJECTED -> resultMessage(
                    "복리후생 신청이 반려되었습니다.", data, reasonVisible, forApplicant);
            case BIRTHDAY_LEAVE_GRANTED -> birthdayMessage(data, forApplicant);
            case ONBOARDING_PENDING, ONBOARDING_APPROVED,
                 ONBOARDING_REJECTED, ONBOARDING_REVISED -> onboardingMessage(data, forApplicant);
        };
    }

    /** 메일 제목 — 대괄호 안은 헤더 띠와 같은 값이다 (둘이 갈라지지 않게 한 곳에서 만든다) */
    private String subject(EmailTemplateData data) {
        return "[" + heading(data.kind()) + "] " + data.applicantName();
    }

    private EmailMessage requestMessage(
            String summary, EmailTemplateData data, boolean reasonVisible, boolean forApplicant) {
        return new EmailMessage(
                subject(data), document(summary, rows(data, reasonVisible), data.kind(), forApplicant));
    }

    private EmailMessage resultMessage(
            String summary, EmailTemplateData data, boolean reasonVisible, boolean forApplicant) {
        List<Row> rows = rows(data, reasonVisible);
        // 처리자는 결과 메일에만 붙는다 — 신청 접수 시점에는 처리한 사람이 없다
        if (!data.actorName().isBlank()) {
            rows.add(new Row("처리자", data.actorName()));
        }
        return new EmailMessage(subject(data), document(summary, rows, data.kind(), forApplicant));
    }

    private EmailMessage birthdayMessage(EmailTemplateData data, boolean forApplicant) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("지급일", data.dates()));
        rows.add(new Row("지급 일수", data.days() + "일"));
        return new EmailMessage(
                subject(data),
                document(
                        data.applicantName() + " 님에게 생일 반차가 지급되었습니다.",
                        rows,
                        data.kind(),
                forApplicant));
    }

    /** 온보딩 승인 흐름 전용 문구 — 수정 결과는 자동 승인 여부에 따라 발행부가 전달한다. */
    private EmailMessage onboardingMessage(EmailTemplateData data, boolean forApplicant) {
        String summary = switch (data.kind()) {
            case ONBOARDING_PENDING -> "온보딩 정보가 승인 대기 상태로 접수되었습니다.";
            case ONBOARDING_APPROVED -> "온보딩이 확정되었습니다.";
            case ONBOARDING_REJECTED -> "온보딩이 반려되었습니다. 입사일 정보를 다시 입력해 주세요.";
            case ONBOARDING_REVISED -> data.actorName().isBlank()
                    ? "온보딩 정보가 수정되었습니다."
                    : data.actorName();
            default -> "";
        };

        List<Row> rows = new ArrayList<>();
        rows.add(new Row("사원", data.applicantName()));
        if (!data.itemName().isBlank()) {
            rows.add(new Row("상태", data.itemName()));
        }
        if (!data.dates().isBlank()) {
            rows.add(new Row("입사일", data.dates()));
        }
        if (!data.days().isBlank()) {
            rows.add(new Row("부여 일수", data.days() + "일"));
        }
        return new EmailMessage(
                subject(data), document(summary, rows, data.kind(), forApplicant));
    }

    /**
     * 바로가기 버튼이 열 화면 — 그 메일을 받고 <b>바로 하려는 일</b>이 있는 곳으로 보낸다.
     *
     * <p><b>같은 메일도 신청자와 결재자가 할 일이 다르다.</b> 한 건이 신청자·승인자·관리자에게
     * 함께 나가는데, 2026-08-16에는 종류(kind)만 보고 행선지를 정해서 <b>신청자에게도
     * "결재하러 가기"가 갔다</b>(2026-08-17 지적). 자기 신청을 자기가 결재할 수는 없으므로
     * 그 버튼은 눌러도 할 일이 없는 화면으로 데려간다.
     *
     * <p>본문은 이미 수신자별로 만들고 있었다(사유 마스킹). 버튼만 그 갈래를 안 타고 있었다.
     *
     * <p>신청자가 아닌 수신자는 결재선에 있는 사람이거나 관리자다. 혹시 권한이 없는 화면으로
     * 보내지더라도 {@code RequireAuth}가 대시보드로 되돌리므로 오류가 아니라 한 번 더 클릭하는
     * 정도의 비용이다. 반대로 전부 대시보드로 보내면 누구에게도 도움이 안 된다.
     */
    private Destination destination(EmailTemplateKind kind, boolean forApplicant) {
        return switch (kind) {
            // 접수 알림 — 결재자는 결재하러, 신청자는 자기 신청이 어떻게 됐는지 보러 간다
            case LEAVE_APPLIED, LEAVE_CANCEL_PENDING -> forApplicant
                    ? new Destination("/history", "내 신청 확인하기")
                    : new Destination("/approvals", "결재하러 가기");
            case WELFARE_APPLIED -> forApplicant
                    ? new Destination("/welfare", "내 신청 확인하기")
                    : new Destination("/approvals", "결재하러 가기");
            // 결과 알림 — 신청자는 자기 내역으로, 결재자·관리자는 처리한 건이 모인 곳으로
            case LEAVE_APPROVED, LEAVE_REJECTED, LEAVE_CANCELLED,
                 LEAVE_CANCEL_APPROVED, LEAVE_CANCEL_REJECTED -> forApplicant
                    ? new Destination("/history", "연차 내역 확인하기")
                    : new Destination("/approvals", "결재 내역 보기");
            case WELFARE_APPROVED, WELFARE_REJECTED -> forApplicant
                    ? new Destination("/welfare", "복리후생 내역 확인하기")
                    : new Destination("/approvals", "결재 내역 보기");
            // 생일 반차는 지급받은 본인과 관리자가 함께 받는다. 둘 다 볼 곳은 현황 화면 하나뿐이다
            case BIRTHDAY_LEAVE_GRANTED -> new Destination("/dashboard", "연차 현황 보기");
            case ONBOARDING_PENDING, ONBOARDING_APPROVED,
                 ONBOARDING_REJECTED, ONBOARDING_REVISED ->
                    new Destination("/dashboard", "온보딩 상태 확인하기");
        };
    }

    /**
     * 버튼이 열 화면과 그 라벨 — <b>한 곳에서 함께</b> 정한다.
     * 따로 두면 "복리후생 내역 확인하기"를 눌렀는데 연차 화면이 열리는 식으로 갈라진다.
     */
    private record Destination(String path, String label) {
    }

    /**
     * 바로가기 버튼 — 이메일에서는 {@code <a>}에 준 padding을 Outlook(Word 엔진)이 무시하므로
     * 배경색을 {@code <td>}에 얹은 표로 감싼다(이른바 bulletproof button).
     *
     * <p>{@code frontend-url}이 비어 있으면 버튼 자체를 그리지 않는다 — 깨진 링크를 보내느니 없는 편이 낫다.
     */
    private String button(EmailTemplateKind kind, boolean forApplicant) {
        String origin = appProperties.frontendUrl();
        if (origin == null || origin.isBlank()) {
            return "";
        }
        Destination destination = destination(kind, forApplicant);
        String url = origin.replaceAll("/+$", "") + destination.path();
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:22px 0 0\">"
                + "<tr><td align=\"center\" bgcolor=\"" + COLOR_BUTTON_BG + "\" style=\"border-radius:8px\">"
                + "<a href=\"" + escape(url) + "\" target=\"_blank\" rel=\"noopener\" "
                + "style=\"display:inline-block;padding:11px 22px;font-family:" + FONT_STACK
                + ";font-size:13px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:8px\">"
                + escape(destination.label())
                + "</a></td></tr></table>";
    }

    /** 표에 넣을 항목 — 값이 빈 항목은 행 자체를 만들지 않는다 (빈 칸이 남으면 누락처럼 보인다) */
    private List<Row> rows(EmailTemplateData data, boolean reasonVisible) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("신청자", data.applicantName()));
        if (data.requestId() != null) {
            rows.add(new Row("신청 번호", String.valueOf(data.requestId())));
        }
        if (!data.itemName().isBlank()) {
            rows.add(new Row("구분", data.itemName()));
        }
        if (!data.dates().isBlank()) {
            rows.add(new Row("날짜", data.dates()));
        }
        if (!data.days().isBlank()) {
            rows.add(new Row("일수", data.days() + "일"));
        }
        if (!data.reason().isBlank()) {
            rows.add(new Row("사유", reasonVisible ? data.reason() : MASKED_REASON));
        }
        return rows;
    }

    /** 카드 한 장 — 헤더 띠 / 요약 문장 / 항목 표 / 바로가기 버튼 / 푸터 */
    private String document(
            String summary, List<Row> rows, EmailTemplateKind kind, boolean forApplicant) {
        StringBuilder html = new StringBuilder(1024);
        html.append("<div style=\"margin:0;padding:24px 12px;background:").append(COLOR_PAGE_BG)
                .append(";font-family:").append(FONT_STACK).append("\">")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" ")
                .append("style=\"max-width:520px;margin:0 auto;background:").append(COLOR_CARD_BG)
                .append(";border:1px solid ").append(COLOR_BORDER)
                .append(";border-radius:12px;border-collapse:separate;overflow:hidden\">");

        // 헤더 띠 — 이 메일이 무엇인지를 말한다. 브랜드는 푸터가 든다 (heading 주석 참고)
        html.append("<tr><td style=\"background:").append(COLOR_HEADER_BG)
                .append(";padding:16px 24px;color:#eaf3ff;font-size:15px;font-weight:700;letter-spacing:-0.2px\">")
                .append(escape(heading(kind)))
                .append("</td></tr>");

        // 본문
        html.append("<tr><td style=\"padding:24px\">")
                .append("<p style=\"margin:0 0 18px;font-size:15px;font-weight:700;color:").append(COLOR_TEXT)
                .append("\">").append(escape(summary)).append("</p>")
                .append(table(rows))
                .append(button(kind, forApplicant))
                .append("</td></tr>");

        // 푸터
        html.append("<tr><td style=\"padding:14px 24px;background:").append(COLOR_FOOTER_BG)
                .append(";border-top:1px solid ").append(COLOR_BORDER)
                .append(";color:").append(COLOR_LABEL).append(";font-size:11px\">")
                .append(escape(FOOTER_NOTE))
                .append("</td></tr>");

        return html.append("</table></div>").toString();
    }

    /** 항목 표 — 라벨/값 2열. 마지막 행만 아래 테두리를 지워 표가 카드 안에서 닫히게 한다 */
    private String table(List<Row> rows) {
        StringBuilder html = new StringBuilder(512);
        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" ")
                .append("style=\"border-collapse:collapse;font-size:13px;color:").append(COLOR_TEXT).append("\">");

        for (int i = 0; i < rows.size(); i++) {
            String border = (i == rows.size() - 1) ? "none" : "1px solid " + COLOR_BORDER;
            Row row = rows.get(i);
            html.append("<tr>")
                    .append("<th align=\"left\" style=\"width:86px;padding:10px 12px 10px 0;border-bottom:").append(border)
                    .append(";color:").append(COLOR_LABEL)
                    .append(";font-weight:600;vertical-align:top;white-space:nowrap\">")
                    .append(escape(row.label()))
                    .append("</th>")
                    .append("<td style=\"padding:10px 0;border-bottom:").append(border)
                    .append(";font-weight:600;vertical-align:top;word-break:break-word\">")
                    .append(escape(row.value()))
                    .append("</td>")
                    .append("</tr>");
        }
        return html.append("</table>").toString();
    }

    /**
     * HTML 이스케이프 — 이름·사유가 그대로 본문에 들어가므로 반드시 통과시킨다.
     * {@code <}만 막아도 표가 깨지는 것은 피하지만, 속성 안에 값이 들어갈 여지를 남기지 않도록
     * 따옴표까지 함께 바꾼다.
     */
    private String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** 표 한 행 */
    private record Row(String label, String value) {
    }
}
