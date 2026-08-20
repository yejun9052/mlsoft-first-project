# 과제: 팀장 승격 시 부서 함께 지정 (백엔드 + 프론트) — **출력 형식 예외 적용**

## ⚠️ 이번 과제에 한해 산출물 형식을 바꾼다

역할 지시서는 "변경 후 전문"을 요구하지만, `UserService.java`·`AdminMembersPage.jsx`가
커서 두 번 연속 응답 한도에 걸렸다. **이번 과제만** 아래 형식을 쓴다.

**신규 파일**은 전문을 낸다. **기존 파일**은 아래처럼 **수술 단위**로 낸다:

````
### 수정 — `<파일 경로>`

#### 변경 N: <무엇을>
**찾을 것** (이 문자열이 파일에 정확히 한 번 나와야 한다):
```
<기존 코드 3~8줄. 공백·들여쓰기까지 그대로>
```
**바꿀 것**:
```
<새 코드 전문>
```
````

- **찾을 것**은 파일에 **정확히 한 번만** 나오는 문자열이어야 한다. 애매하면 앞뒤 줄을 더 넣어라
- import 추가도 같은 형식으로 낸다
- `...(생략)`·diff 기호(`+`/`-`)를 쓰지 마라. 위 두 블록은 **그대로 치환**될 것이다

## 확정된 설계 (다시 판단하지 말 것)

네가 앞서 내린 결론을 채택했다:

> 기존 API 두 번 호출은 부분 성공을 막을 수 없다. 기존 엔드포인트는 유지하고,
> **서버 트랜잭션 안에서 `changeDepartment → changeRole`을 실행하는 전용 엔드포인트**를 추가한다.
> 두 번째 단계가 실패하면 부서 배정도 함께 롤백된다.

사용자 결정: **미배정 사원을 팀장으로 승격할 때 부서를 같은 자리에서 함께 고른다.**
기존 가드 `DEPARTMENT_REQUIRED_FOR_LEADER`는 **그대로 둔다** — 마지막 그물이다.

## 해야 할 일

### 백엔드
- 새 엔드포인트 + 요청 DTO(record)
- `UserService`에 새 메서드 — 한 트랜잭션 안에서 부서 배정 후 역할 변경.
  **기존 `changeRole`·`changeDepartment`·`assignDepartmentLeader`를 재사용**할 것.
  팀장 교체 규칙(docs/01 §2-9(b))을 다시 구현하지 마라
- 감사 로그는 **부서 변경과 역할 변경 두 건 모두** 남아야 한다
- JUnit: 둘 다 반영 / **역할 변경 실패 시 부서도 롤백** / 기존 팀장이 사원으로 내려감 / 감사 2건

### 프론트
- `AdminMembersPage`의 인라인 역할 변경에서 `TEAM_LEADER`로 바꿀 때
  - 부서가 **있으면** 지금과 동일 (건드리지 말 것)
  - 부서가 **없으면** 확인 창에 부서 선택을 함께. 고르지 않으면 진행 불가
- 부서 선택지는 **계층 순서** — `frontend/src/utils/departmentTree.js`의
  `orderByHierarchy`·`departmentOptionLabel`을 쓸 것. 새로 만들지 마라
- vitest: 부서 있으면 선택이 안 뜬다 / 없으면 고르기 전 진행 불가 /
  확정 시 **새 엔드포인트 한 번**만 호출된다 / 선택지가 계층 순서다
- `createMemoryRouter` + `RouterProvider` (앱은 데이터 라우터다).
  `AdminMembersPage.test.jsx`의 기존 모킹 방식을 따를 것

## 먼저 읽을 것
- `backend/.../domain/user/service/UserService.java`, `controller/UserController.java`, `dto/`
- `backend/src/test/java/.../domain/user/service/UserServiceTest.java`
- `frontend/src/pages/AdminMembersPage.jsx`, `AdminMembersPage.test.jsx`
- `frontend/src/utils/departmentTree.js`, `hooks/useUsers.js`, `api/users.js`
- `frontend/src/components/ui/ConfirmDialog.jsx`, `Select.jsx`, `Field.jsx`
- `docs/01-요구사항-기획.md` §2-9(b), `docs/03-API-설계.md`, `CLAUDE.md`

## 규칙
- 화살표 함수 컴포넌트 금지. 주석 한국어로 **왜**를 쓴다
- URL kebab-case, 응답 `ResponseEntity<CommonResponse<?>>`, 실패는 `BusinessException(ErrorCode)`
- 본인 식별은 `@AuthenticationPrincipal AuthUser`
- 색·반경은 `index.css`의 `@theme` 토큰만
- 개별 호출부에서 에러 토스트 중복 금지

## 마지막에
`## 반영 순서` — 치환 순서와 확인 명령(`cd backend && .\gradlew.bat test` 346건,
`cd frontend && npm test` 184건, `npm run lint`).
`## 되돌릴 수 없는 것` — 수동 확인이 필요한 회귀.
