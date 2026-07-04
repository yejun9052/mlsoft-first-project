package com.mlsoft.backend.domain.welfare.entity;

/**
 * 복리후생 대상 (docs/02 3-6).
 */
public enum WelfareTarget {
    SELF,               // 자신
    PARENT,             // 부모
    SPOUSE,             // 배우자
    CHILD,              // 자녀
    SIBLING,            // 형제·자매
    GRANDPARENT,        // 조부모
    SPOUSE_PARENT,      // 배우자 부모
    SPOUSE_GRANDPARENT, // 배우자 조부모
    OTHER               // 기타
}
