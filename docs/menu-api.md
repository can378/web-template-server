# 메뉴 API

기존 MariaDB의 `web_menus`, `web_menu_roles`, `web_roles`, `web_users`, `web_user_roles`를 사용합니다.
운영 테이블을 생성하거나 변경하지 않습니다. 부모 메뉴는 `parent_menu_id`, 접근 권한은 `web_menu_roles`를 사용합니다. `is_public` 컬럼은 사용하지 않습니다.

## 엔드포인트

| 메서드 | 주소 | 기능 |
| --- | --- | --- |
| GET | `/api/menus` | 현재 사용자에게 보이는 메뉴 트리. 비로그인도 호출 가능 |
| GET | `/api/admin/menus` | 숨김·비활성 포함 전체 메뉴의 평면 목록과 역할 ID |
| GET | `/api/admin/menus/{menuId}` | 메뉴 상세와 역할 ID |
| POST | `/api/admin/menus` | 메뉴 생성 (201, Location 헤더) |
| PATCH | `/api/admin/menus/{menuId}` | 전달한 필드만 수정 |
| PUT | `/api/admin/menus/{menuId}/roles` | 허용 역할 ID 전체 교체 |
| DELETE | `/api/admin/menus/{menuId}` | 하위메뉴가 없는 메뉴 삭제 (204) |

관리 API는 기존 Spring Security의 `ROLE_ADMIN` 권한이 필요합니다. 역할 코드는 이 프로젝트에서는 `ADMIN`이 아니라 `ROLE_ADMIN`입니다.
변경 요청은 로그인 세션 쿠키와 CSRF 토큰이 필요합니다. `/api/auth/csrf`에서 받은 `headerName`과 `token`을 헤더로 전송하세요.
로그인 직후에는 CSRF 토큰을 다시 발급받습니다. Swagger에도 API가 자동으로 표시됩니다.

## 생성 예시

`POST /api/admin/menus`

```json
{
  "parentMenuId": null,
  "menuCode": "menu1",
  "menuName": "메뉴1",
  "menuPath": "/menu1",
  "icon": null,
  "sortOrder": 1,
  "isVisible": true,
  "isActive": true
}
```

`menuCode`, `menuName`, `sortOrder`, `isVisible`, `isActive`는 필수입니다.
DB의 0/1 플래그는 JSON에서는 `false`/`true`로 전달합니다. `menuPath`는 `/`로 시작하는 내부 경로 또는 `null`입니다.
`parentMenuId`가 `null`이면 최상위 메뉴입니다. `menuPath`가 `null`이면 이동 주소가 없는 그룹입니다.
생성·상세·수정 응답은 `{ "menu": { "menuId": 1, ... }, "roleIds": [] }` 형태입니다.

## 수정 및 역할 지정

`PATCH /api/admin/menus/1`

```json
{ "menuName": "수정 메뉴", "sortOrder": 2, "parentMenuId": null }
```

생략한 필드는 유지됩니다. `parentMenuId`, `menuPath`, `icon`은 명시적인 `null`로 초기화할 수 있습니다.
알 수 없는 필드, 잘못된 자료형, 필수 값의 `null`은 400입니다.

`PUT /api/admin/menus/1/roles`

```json
{ "roleIds": [1, 2] }
```

실제 DB에 존재하는 역할 ID를 사용하세요. 하나라도 일치하면 접근 가능하며 중복 ID는 제거합니다.
빈 배열은 모든 역할 연결을 해제하여 메뉴를 공개합니다(상위 메뉴의 제한은 유지). 없는 역할 ID가 있으면 기존 연결을 유지하고 400을 반환합니다.

## 메뉴 조회 규칙

- 자신과 모든 상위메뉴가 활성·노출 상태여야 합니다.
- `web_menu_roles`에 연결된 역할이 없는 메뉴는 비로그인 포함 누구나 볼 수 있습니다.
- 메뉴에 역할 연결이 있으면 현재 활성 사용자의 역할 중 하나 이상이 메뉴 허용 역할과 일치해야 합니다.
- 사용자에게 역할이 없으면 공개 메뉴만 보입니다. 관리자에게도 역할 제한 메뉴의 조회 우회를 적용하지 않습니다.
- 사용자 역할은 조회할 때 DB에서 읽습니다. 부모에 역할 제한이 있다면 공개 하위메뉴도 부모 권한을 만족해야 합니다.
- 형제 메뉴는 `sort_order`, `menu_id` 순서입니다. 순환·고아 데이터는 공개 트리에서 제외합니다.
- 응답에 `Cache-Control: no-store`를 적용합니다.

```json
[
  {
    "id": 1,
    "label": "메뉴1",
    "path": "/menu1",
    "icon": null,
    "children": [
      { "id": 2, "label": "하위메뉴1", "path": "/menu1/sub1", "icon": null, "children": [] }
    ]
  }
]
```

프론트 헤더는 그룹 메뉴와 여러 단계 트리를 표시합니다.
프론트 `services/menu.js`는 이 API를 호출하며 초기 접속 및 로그인·로그아웃 시 재조회합니다. 메뉴 페이지 진입 시에는 이미 불러온 메뉴로 접근 가능한 경로인지 확인하며 매번 API를 호출하지 않습니다. DB에서 직접 바꾼 메뉴·역할은 브라우저 새로고침 또는 로그인·로그아웃 후 반영됩니다.
메뉴 노출 권한은 업무 API의 접근 권한을 대신하지 않습니다. 각 업무 API에도 권한 검사를 설정해야 합니다.

## 오류 및 무결성

- 400: 잘못된 입력, 없는 부모·역할, 자기 자신 또는 자손을 부모로 지정
- 401/403: 인증·관리자 권한·CSRF 오류
- 404: 없는 메뉴
- 409: 중복 코드, 하위메뉴가 있는 메뉴 삭제, DB 참조 또는 동시 변경 충돌

계층 변경은 트랜잭션에서 메뉴 행을 잠근 뒤 검증합니다. 역할 교체도 원자적으로 처리합니다.
메뉴 삭제 시 역할 연결도 삭제됩니다. 하위메뉴는 먼저 다른 부모로 옮기거나 삭제해야 합니다.

## 검증

`./gradlew test`로 H2 MariaDB 호환 모드에서 통합 테스트를 실행합니다. 실제 MariaDB 연결 테스트와는 별개입니다.
