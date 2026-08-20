# 과제 (1/2): 팀장 승격 + 부서 배정을 한 트랜잭션으로 — 백엔드만

앞선 시도가 응답 한도를 넘어 쪼갠다. **이번에는 백엔드만 낸다.** 프론트는 다음 회차다.

## 이미 확정된 설계 (다시 판단하지 말 것)

네가 직전 회차에서 내린 결론을 그대로 채택했다:

> 기존 API 두 번 호출은 부분 성공을 막을 수 없으므로, 기존 엔드포인트는 유지하고
> 서버 트랜잭션 안에서 `changeDepartment → changeRole`을 실행하는 전용 엔드포인트를 추가한다.
> 두 번째 단계가 실패하면 부서 배정도 함께 롤백된다.

사용자 결정: **미배정 사원을 팀장으로 승격할 때 부서를 같은 자리에서 함께 고른다.**
기존 방어선 `DEPARTMENT_REQUIRED_FOR_LEADER`는 **그대로 둔다** — 마지막 그물이다.

## 이번 회차에 낼 것 (이것만)

- 새 엔드포인트 (컨트롤러 + 요청/응답 DTO record)
- `UserService`의 새 메서드 — 한 트랜잭션 안에서 부서 배정 후 역할 변경
- 그 메서드의 JUnit 테스트
- 필요하면 `ErrorCode`·`ResponseMessage` 추가분

**프론트 파일은 내지 말 것.** `docs/13`도 내지 말 것 (사람이 따로 고친다).

## 반드시 지킬 것

- **기존 `changeRole`·`changeDepartment`·`assignDepartmentLeader`의 동작을 바꾸지 말 것.**
  새 메서드가 그것들을 **재사용**해야 한다. 팀장 교체 규칙(기존 팀장을 사원으로 내리고
  `leader_id`를 채우는 것, docs/01 §2-9(b))을 다시 구현하지 마라
- 감사 로그(`AdminAuditService`)가 **부서 변경과 역할 변경 양쪽 모두** 남아야 한다 —
  한 번의 조작이지만 두 가지가 바뀐 것이다
- URL은 kebab-case 리소스명만. 응답은 `ResponseEntity<CommonResponse<?>>`
- 실패는 `BusinessException(ErrorCode)`. 컨트롤러 try-catch 금지
- 본인 식별은 `@AuthenticationPrincipal AuthUser`. 요청 body의 userId 신뢰 금지
- `@PreAuthorize`는 역할 게이트 전용(SYSTEM_ADMIN), 소유권·정합성 검증은 서비스 계층
- DTO는 Java record. 주석은 한국어로 **왜**를 쓴다
- 엔티티를 바꾸면 `db/schema.sql`과 `db/backfill-<날짜>-<주제>.sql` **둘 다** —
  다만 이 과제는 엔티티 변경이 필요 없을 가능성이 높다. 필요하다고 판단하면 근거를 쓸 것

## JUnit에 반드시 넣을 것

- 부서 배정과 역할 변경이 **둘 다** 반영된다
- 역할 변경 단계가 실패하면 **부서 배정도 롤백된다** (이 엔드포인트의 존재 이유다)
- 기존 팀장이 있던 부서면 **그 팀장이 사원으로 내려가고** `leader_id`가 새 사람으로 바뀐다
- 감사 로그가 두 건 남는다

## 먼저 읽을 것

- `backend/.../domain/user/service/UserService.java`
- `backend/.../domain/user/controller/UserController.java`
- `backend/.../domain/user/dto/` 전체
- `backend/.../domain/user/service/AdminAuditService.java` (또는 감사 로그 담당 클래스)
- `backend/src/test/java/.../domain/user/service/UserServiceTest.java`
- `docs/01-요구사항-기획.md` §2-9(b)
- `docs/03-API-설계.md` 공통 규칙
- `CLAUDE.md` 코드 컨벤션

## 산출물

역할 지시서 형식. 파일은 **변경 후 전문**.
`## 반영 순서`에 `cd backend && .\gradlew.bat test` (현재 346건) 포함.
마지막에 **다음 회차(프론트)가 알아야 할 계약**을 한 문단으로 적을 것 —
엔드포인트 URL·요청 형태·응답 형태·실패 코드.
