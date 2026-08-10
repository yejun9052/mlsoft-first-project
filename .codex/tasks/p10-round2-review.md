# 리뷰 대상: 2026-08-10 2라운드 커밋 4건

```
git diff ae2be47..HEAD
git diff --stat ae2be47..HEAD
```

| 커밋 | 내용 |
|---|---|
| `4e31fdf` | 복리후생 정책 관리 화면 (프론트 신규) |
| `4fbbaa3` | 1라운드 Codex 리뷰 반영 — S-2 동시성(PESSIMISTIC_WRITE) + 화면 문구 |
| `39ae9c9` | F-7·F-8 — 권한을 서버 응답 기준으로, 결재 배지 기준 통일 |
| `4de4fc3` | D-5(승인자 FK) · D-2(N+1 9곳) · D-6(문서 정정) |

## 특히 확인해 줄 것 (우선순위 순)

1. **`@EntityGraph` 추가가 만든 부작용** (`4de4fc3`)
   - 연차 목록 5종에 `user`, `user.department`, `primaryApprover`, `subApprover`를 넣었다.
     `subApprover`는 **nullable**이다 — `@EntityGraph`가 LEFT JOIN을 쓰는지 확인해라.
     INNER면 서브 승인자 없는 건이 목록에서 전부 빠진다. **이게 이번 변경의 최대 위험이다.**
   - `findOverlapping`·`findInDateRange`·`findByDepartmentInDateRange`는 `join lr.dates d`가 있는
     `distinct` 쿼리다. 여기에 to-one 그래프를 얹었을 때 결과 행이 달라지지 않는지.
   - `WelfareRequestRepository.findAll`에 `@Override + @EntityGraph`를 붙였다.
     `JpaRepository.findAll(Pageable)`을 덮어쓴 것이 count 쿼리에 영향을 주는가.

2. **`findActiveByRoleForUpdate`의 잠금 범위** (`4fbbaa3`)
   - `PESSIMISTIC_WRITE` + `order by u.id`. 이 잠금이 **다른 트랜잭션과 교착**할 수 있는가.
     특히 `retire`가 잠금 후 `reassignPendingApprovals`에서 다른 `User`(fallback)를 읽는다 —
     잠금 순서가 역전되는 경로가 있는지.
   - 관리자가 많은 조직에서 이 잠금이 연차 신청(같은 users 행에 낙관적 락)과 부딪히는가.

3. **`RequireAuth`의 "확인 전에는 거부하지 않는다" 규칙** (`39ae9c9`)
   - `dataUpdatedAt > 0`을 서버 확인 신호로 쓴다. 이 값이 **재검증 실패 후에도** 0으로
     남는가 아니면 이전 성공 시각을 유지하는가. 후자면 오프라인 상태에서 낡은 권한으로 통과한다.
   - `isPending`이 false + `userInfo` null인 조합이 실제로 도달 가능한 상태인가.
   - 보호된 화면이 한 프레임이라도 렌더되는 경로가 있는가 (권한 미달인데 `null` 대신 children).

4. **`useCurrentUser`의 localStorage 쓰기** (`39ae9c9`)
   - `useEffect`가 `query.data`마다 `setItem`한다. 로그아웃 직후 다른 컴포넌트가 이 훅을
     쓰고 있으면 방금 지운 값이 되살아나는가. Sidebar가 `queryClient.clear()`를 부르지만
     그것만으로 충분한가.

5. **`WelfareRequest.isApprover`** (`4de4fc3`)
   - `primaryApprover.getId()`가 프록시에서 쿼리 없이 되는 것이 전제다.
     `primaryApprover`가 `null`일 수 있는 경로가 있는가 (nullable=false지만 저장 전 상태).

6. **복리후생 정책 화면** (`4e31fdf`)
   - `defaultDays`를 `Number(...).toFixed(1)`로 보낸다. `0.25` 같은 입력이 `0.3`으로
     조용히 반올림되는데 사용자에게 알리지 않는다 — 문제인가.
   - 수정 모달이 `String(Number(policy.defaultDays))`로 값을 채운다. `7.0` → `"7"`이 되는데
     저장하면 `7.0`으로 돌아간다. 왕복이 안전한가.

## 참고

- 백엔드 270 · 프론트 54 그린 상태다. **테스트 통과가 옳다는 뜻은 아니다** —
  테스트가 틀린 것을 고정하고 있으면 지적해라.
- `db/backfill-2026-08-10-welfare-fk-indexes.sql`과 `db/schema.sql`의 welfare_requests 블록이
  **서로 같은 결과를 만드는지** 대조해 달라 (인덱스 3 + FK 2, 제약 이름 포함).
