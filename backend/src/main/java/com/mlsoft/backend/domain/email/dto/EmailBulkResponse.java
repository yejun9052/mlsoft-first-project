package com.mlsoft.backend.domain.email.dto;

/** 관리자 일괄 메일 처리 결과. */
public record EmailBulkResponse(
        int requested,
        int queued,
        int skipped
) {
}
