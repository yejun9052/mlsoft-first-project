package com.mlsoft.backend.config;

import tools.jackson.databind.JsonNode; // Spring Boot 4 = Jackson 3 (패키지 tools.jackson)
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 페이지 응답의 <b>JSON 모양</b>을 고정한다.
 *
 * <p>이 테스트가 없어서 실제로 놓쳤다 (2026-08-17). 프론트는 {@code data.page.totalPages}를
 * 읽는데 백엔드는 {@code PageImpl}을 그대로 내보내 그 값이 최상위에 있었다. 필드가 없으면
 * JS에서는 {@code undefined}일 뿐 예외가 아니라, 페이지 넘김 바와 각종 건수 배지가
 * <b>조용히 사라졌다</b>.
 *
 * <p>프론트 테스트는 응답을 목으로 만들면서 원하는 형태를 직접 적어 넣었기 때문에 전부 통과했다.
 * 양쪽이 각자 옳은 형태를 가정하면 아무도 어긋남을 못 본다 — 그 접점을 여기서 잡는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class WebConfigPageSerializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("페이지 메타는 최상위가 아니라 page 객체 안에 담긴다 (PagedModel)")
    void page_직렬화형태() throws Exception {
        Page<String> page = new PageImpl<>(List.of("가", "나"), PageRequest.of(1, 2), 7);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(page));

        assertTrue(json.has("content"), "content가 없다");
        assertTrue(json.has("page"), "page 메타 객체가 없다 — 프론트가 읽는 자리다");

        JsonNode meta = json.get("page");
        assertEquals(4, meta.get("totalPages").asInt());
        assertEquals(7, meta.get("totalElements").asInt());
        assertEquals(2, meta.get("size").asInt());
        assertEquals(1, meta.get("number").asInt());
    }

    @Test
    @DisplayName("최상위에는 페이지 메타를 두지 않는다 — 두 곳에 있으면 어느 쪽을 읽어야 하는지 갈린다")
    void page_최상위에는없다() throws Exception {
        Page<String> page = new PageImpl<>(List.of("가"), PageRequest.of(0, 10), 1);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(page));

        assertFalse(json.has("totalPages"), "최상위에 totalPages가 남아 있다 (PageImpl 형태)");
        assertFalse(json.has("totalElements"), "최상위에 totalElements가 남아 있다 (PageImpl 형태)");
        assertFalse(json.has("pageable"), "최상위에 pageable이 남아 있다 (PageImpl 형태)");
    }
}
