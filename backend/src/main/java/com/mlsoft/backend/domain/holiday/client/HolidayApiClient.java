package com.mlsoft.backend.domain.holiday.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
// Boot 4는 Jackson 3를 쓴다 — 패키지가 com.fasterxml.jackson.*이 아니라 tools.jackson.*이다.
// (com.fasterxml 쪽은 jjwt-jackson이 런타임에만 끌어오는 Jackson 2다. 섞어 쓰지 말 것)
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 공공데이터포털(data.go.kr) 특일 정보 API 클라이언트 — 연도별 공휴일 조회.
 * <p>
 * <b>이 클래스는 예외를 던지지 않는다.</b> 키가 없거나 외부 API가 죽어도 빈 목록을 돌려준다.
 * 공휴일 조회 실패로 사원의 연차 신청이나 캘린더 렌더가 막히면 안 되기 때문이다
 * (관리자 설정 검증을 저장 시점에 두는 것과 같은 판단 — 리뷰 I-3).
 * 대신 WARN 로그를 남기고, 호출부가 "몇 건 받았는지"로 성공 여부를 판단한다.
 */
@Slf4j
@Component
public class HolidayApiClient {

    private static final String BASE_URL =
            "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";
    /** 공휴일은 연간 20~30일이라 100이면 충분하다 (대체공휴일 포함) */
    private static final int NUM_OF_ROWS = 100;
    private static final DateTimeFormatter LOCDATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient = RestClient.create();
    private final String apiKey;

    public HolidayApiClient(@Value("${holiday.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 해당 연도의 공휴일 목록. 실패 시 빈 목록.
     * <p>
     * {@code isHoliday=Y}인 것만 담는다 — 이 API는 공휴일이 아닌 기념일(식목일 등)도 함께 준다.
     */
    public List<HolidayItem> fetchByYear(int year) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[공휴일] HOLIDAY_API_KEY가 비어 있어 조회를 건너뜁니다 (year={})", year);
            return List.of();
        }
        try {
            // serviceKey는 발급 시 이미 URL 인코딩된 문자열이라 UriBuilder에 맡기면 이중 인코딩된다.
            // 문자열로 조립한 뒤 URI.create로 감싼다.
            URI uri = URI.create(BASE_URL
                    + "?serviceKey=" + apiKey
                    + "&solYear=" + year
                    + "&numOfRows=" + NUM_OF_ROWS
                    + "&_type=json");

            JsonNode root = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            return parse(root, year);
        } catch (Exception e) {
            // 외부 API 장애를 우리 장애로 만들지 않는다 — 캐시에 있는 값으로 계속 돈다
            log.warn("[공휴일] API 조회 실패 (year={}): {}", year, e.getMessage());
            return List.of();
        }
    }

    private List<HolidayItem> parse(JsonNode root, int year) {
        if (root == null) {
            log.warn("[공휴일] 응답 본문이 비어 있습니다 (year={})", year);
            return List.of();
        }
        JsonNode item = root.path("response").path("body").path("items").path("item");
        if (item.isMissingNode() || item.isNull()) {
            // 정상 응답이지만 결과가 없는 경우와, 인증 실패 등으로 형식이 다른 경우를 함께 잡는다
            log.warn("[공휴일] 결과 항목이 없습니다 (year={}, 응답 헤더={})",
                    year, root.path("response").path("header").toString());
            return List.of();
        }

        // 결과가 1건이면 배열이 아니라 객체로 온다 — 이 API의 알려진 특성
        List<JsonNode> nodes = new ArrayList<>();
        if (item.isArray()) {
            item.forEach(nodes::add);
        } else {
            nodes.add(item);
        }

        List<HolidayItem> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            // 공휴일이 아닌 기념일은 제외 (isHoliday=N)
            if (!"Y".equalsIgnoreCase(node.path("isHoliday").asString(""))) {
                continue;
            }
            String locdate = node.path("locdate").asString("");
            String name = node.path("dateName").asString("");
            if (locdate.isBlank() || name.isBlank()) {
                continue;
            }
            try {
                result.add(new HolidayItem(LocalDate.parse(locdate, LOCDATE), name.trim()));
            } catch (Exception e) {
                log.warn("[공휴일] 날짜 파싱 실패 locdate={}", locdate);
            }
        }
        return result;
    }

    /** API 응답 1건 */
    public record HolidayItem(LocalDate date, String name) {
    }
}
