package com.mlsoft.backend.domain.welfare.repository;

import com.mlsoft.backend.domain.welfare.entity.WelfarePolicy;
import com.mlsoft.backend.domain.welfare.entity.WelfareTarget;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 복리후생 정책 저장소.
 * 삭제 대신 active=false 소프트 삭제이므로 수정·비활성화 대상 조회는 항상 활성 정책으로 한정한다.
 */
public interface WelfarePolicyRepository extends JpaRepository<WelfarePolicy, Long> {

    /** 활성 정책 전체 — 신청 폼용 (GET /api/welfare-policies/all) */
    List<WelfarePolicy> findByActiveTrueOrderByCategoryAscIdAsc();

    /** 활성 상태에서 구분+대상 조합 존재 여부 — 정책 추가/수정 시 중복 방지 */
    boolean existsByCategoryAndTargetAndActiveTrue(String category, WelfareTarget target);

    /** 활성 정책 단건 조회 — 수정·비활성화·신청 시 근거 정책 조회 */
    Optional<WelfarePolicy> findByIdAndActiveTrue(Long id);

    /** 활성 정책의 카테고리 목록(중복 제거) (GET /api/welfare-policies/categories) */
    @Query("select distinct p.category from WelfarePolicy p where p.active = true order by p.category")
    List<String> findDistinctCategories();

    /** 정책 목록 — category(정확히 일치)·keyword(카테고리 부분일치) 필터, 둘 다 선택 (GET /api/welfare-policies) */
    @Query("select p from WelfarePolicy p "
            + "where p.active = true "
            + "and (:category is null or p.category = :category) "
            + "and (:keyword is null or lower(p.category) like lower(concat('%', :keyword, '%'))) "
            + "order by p.category asc, p.id asc")
    Page<WelfarePolicy> search(@Param("category") String category,
                               @Param("keyword") String keyword,
                               Pageable pageable);
}
