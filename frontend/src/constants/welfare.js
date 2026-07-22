// 복리후생 카테고리·대상 라벨/아이콘 매핑 — 백엔드 seed 카테고리(결혼/회갑/칠순/출산/졸업/조의)
// 기준. 관리자가 나중에 새 카테고리를 추가해도 화면이 깨지지 않도록, 매핑에 없는 카테고리는
// 기본 아이콘(WELFARE_CATEGORY_FALLBACK)으로 폴백한다.
// 카테고리별 색(danger/warn/purple/ok/accent/muted)은 StatusBadge의 승인/대기/반려 색과
// 팔레트가 겹쳐 의미가 충돌했다 — 아이콘 색은 여기서 관리하지 않고, 전 카테고리 동일하게
// "중립 서피스 + accent 아이콘"으로 WelfarePage.jsx에서 고정 스타일링한다(아이콘 종류만 구분용).
import { HeartHandshake, Cake, Baby, GraduationCap, HeartCrack, Gift } from 'lucide-react';

export const WELFARE_CATEGORY_META = {
  결혼: { icon: HeartHandshake },
  회갑: { icon: Cake },
  칠순: { icon: Cake },
  출산: { icon: Baby },
  졸업: { icon: GraduationCap },
  조의: { icon: HeartCrack },
};

export const WELFARE_CATEGORY_FALLBACK = { icon: Gift };

// 카테고리 → { icon } (모르는 카테고리는 폴백)
export function getWelfareCategoryMeta(category) {
  return WELFARE_CATEGORY_META[category] ?? WELFARE_CATEGORY_FALLBACK;
}

// WelfareTarget enum(백엔드 WelfareTarget.java) → 한글 라벨 — 1:1 대응
export const WELFARE_TARGET_LABEL = {
  SELF: '본인',
  PARENT: '부모',
  SPOUSE: '배우자',
  CHILD: '자녀',
  SIBLING: '형제·자매',
  GRANDPARENT: '조부모',
  SPOUSE_PARENT: '배우자 부모',
  SPOUSE_GRANDPARENT: '배우자 조부모',
  OTHER: '기타',
};

// 대상 enum 값 → 한글 라벨 (모르는 값은 원문 그대로)
export function getWelfareTargetLabel(target) {
  return WELFARE_TARGET_LABEL[target] ?? target;
}
