package com.mlsoft.backend.domain.welfare.service;

import com.mlsoft.backend.domain.welfare.dto.WelfarePolicyRequest;
import com.mlsoft.backend.domain.welfare.dto.WelfarePolicyResponse;
import com.mlsoft.backend.domain.welfare.entity.WelfarePolicy;
import com.mlsoft.backend.domain.welfare.entity.WelfareTarget;
import com.mlsoft.backend.domain.welfare.repository.WelfarePolicyRepository;
import com.mlsoft.backend.global.exception.BusinessException;
import com.mlsoft.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 복리후생 정책 도메인 서비스 — 목록·카테고리 조회 + 관리자 CRUD (docs/03 복리후생 정책).
 * 삭제 대신 비활성화(소프트 삭제)로 통일 — 기존 신청(WelfareRequest)의 스냅샷 근거를 보존한다.
 */
@Service
@RequiredArgsConstructor
public class WelfarePolicyService {

    private final WelfarePolicyRepository welfarePolicyRepository;

    /** 정책 목록 (GET /api/welfare-policies) — keyword·category 필터, 활성 정책만 노출 */
    @Transactional(readOnly = true)
    public Page<WelfarePolicyResponse> getPolicies(String keyword, String category, Pageable pageable) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String normalizedCategory = (category == null || category.isBlank()) ? null : category.trim();
        return welfarePolicyRepository.search(normalizedCategory, normalizedKeyword, pageable)
                .map(WelfarePolicyResponse::of);
    }

    /** 카테고리 목록 (GET /api/welfare-policies/categories) */
    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return welfarePolicyRepository.findDistinctCategories();
    }

    /** 활성 정책 전체 — 신청 폼용 (GET /api/welfare-policies/all) */
    @Transactional(readOnly = true)
    public List<WelfarePolicyResponse> getAllActive() {
        return welfarePolicyRepository.findByActiveTrueOrderByCategoryAscIdAsc().stream()
                .map(WelfarePolicyResponse::of)
                .toList();
    }

    /** 정책 추가 (POST /api/welfare-policies, SA) — 구분·대상 조합 중복 검증 */
    @Transactional
    public WelfarePolicyResponse create(WelfarePolicyRequest request) {
        validateNoDuplicate(request.category(), request.target());
        WelfarePolicy policy = WelfarePolicy.create(
                request.category().trim(), request.target(), request.defaultDays(),
                request.defaultEvidence(), request.description());
        welfarePolicyRepository.save(policy);
        return WelfarePolicyResponse.of(policy);
    }

    /** 정책 수정 (PATCH /api/welfare-policies/{id}, SA) — 구분·대상 변경 시에만 중복 재검증 */
    @Transactional
    public WelfarePolicyResponse update(Long id, WelfarePolicyRequest request) {
        WelfarePolicy policy = findActivePolicyOrThrow(id);
        boolean comboChanged = !policy.getCategory().equals(request.category()) || policy.getTarget() != request.target();
        if (comboChanged) {
            validateNoDuplicate(request.category(), request.target());
        }
        policy.update(request.category().trim(), request.target(), request.defaultDays(),
                request.defaultEvidence(), request.description());
        return WelfarePolicyResponse.of(policy);
    }

    /** 정책 비활성화 (DELETE /api/welfare-policies/{id}, SA) — 소프트 삭제 */
    @Transactional
    public void deactivate(Long id) {
        WelfarePolicy policy = findActivePolicyOrThrow(id);
        policy.deactivate();
    }

    // ---------------------------------------------------------------------
    // 내부 헬퍼
    // ---------------------------------------------------------------------

    private void validateNoDuplicate(String category, WelfareTarget target) {
        if (welfarePolicyRepository.existsByCategoryAndTargetAndActiveTrue(category, target)) {
            throw new BusinessException(ErrorCode.DUPLICATE_WELFARE_POLICY);
        }
    }

    private WelfarePolicy findActivePolicyOrThrow(Long id) {
        return welfarePolicyRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.WELFARE_POLICY_NOT_FOUND));
    }
}
