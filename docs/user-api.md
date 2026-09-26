# 사용자 API

## Swagger

서버 실행 후 [Swagger UI](http://localhost:8100/docs)에서 API를 확인하고 실행할 수 있습니다.
OpenAPI JSON은 `/v3/api-docs`입니다. 문서 페이지는 로그인 없이 열립니다.
회원가입을 실행한 뒤 `인증` 그룹의 `/api/auth/login`으로 로그인하면 같은 브라우저의 세션을 사용합니다.
Swagger는 변경 요청마다 CSRF 토큰을 자동으로 가져옵니다. API의 기존 인증·권한 검사는 그대로 적용됩니다.

라이브러리 설정: [springdoc 공식 문서](https://springdoc.org/getting-started.html).

## 구성

Spring Security + 서버 메모리 세션 + Spring JDBC를 사용합니다.
기존 `web_users`, `web_roles`, `web_user_roles`를 그대로 사용하며 운영 DB의 테이블을 자동 생성하거나 변경하지 않습니다.
`DATETIME`과 `DATETIME(6)` 모두 사용할 수 있습니다.

DB 접속은 프로젝트 루트 `.env`의 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`를 사용합니다.
회원가입 전 `web_roles`에 `NORMAL_USER`가 있어야 합니다. 없으면 가입 요청은 503으로 실패하며 사용자는 생성되지 않습니다.
`ROLE_ADMIN`은 관리자가 DB에서 별도로 부여합니다. 공개 API를 통한 관리자 가입이나 역할 변경은 제공하지 않습니다.

## API 목록

| 메서드 | 경로 | 설명 | 성공 |
|---|---|---|---|
| GET | `/api/auth/csrf` | CSRF 토큰 발급, 비회원 가능 | 200 |
| POST | `/api/users` | 회원가입, 일반 회원 권한만 부여 | 201 |
| POST | `/api/auth/login` | 세션 로그인 | 204 |
| POST | `/api/auth/logout` | 현재 세션 로그아웃 | 204 |
| GET | `/api/users/me` | 내 정보 및 역할 | 200 |
| PATCH | `/api/users/me` | 이름·이메일 수정 | 200 |
| PUT | `/api/users/me/password` | 현재 비밀번호 확인 후 변경, 전체 세션 만료 | 204 |
| DELETE | `/api/users/me` | 비밀번호 확인 후 탈퇴, 전체 세션 만료 | 204 |
| GET | `/api/admin/users?page=0&size=20` | 관리자 사용자 목록, size 최대 100 | 200 |
| GET | `/api/admin/users/{userId}` | 관리자 사용자 상세 | 200 |

로그인을 제외한 본문은 JSON입니다. **로그인은 `application/x-www-form-urlencoded`**로 `loginId`, `password`를 전송합니다.
204 응답에는 본문이 없으므로 `response.json()`을 호출하지 않습니다.
가입은 자동 로그인하지 않습니다. 로그인 성공 후 `/api/users/me`로 정보를 조회합니다.

## 브라우저 요청 예시

모든 요청에 `credentials: 'include'`를 지정합니다.
변경 요청(POST/PATCH/PUT/DELETE)은 발급받은 토큰을 반환된 `headerName` 헤더로 전달해야 합니다.
로그인 및 로그아웃 후에는 기존 CSRF 토큰을 버리고 다시 발급받습니다.

```javascript
const base = 'http://localhost:8100'

async function getCsrf() {
  const response = await fetch(`${base}/api/auth/csrf`, {
    credentials: 'include',
  })
  if (!response.ok) throw new Error('CSRF 토큰 발급 실패')
  return response.json()
}

let csrf = await getCsrf()

// 회원가입: 로그인 ID는 영문·숫자·밑줄 4~50자.
// 비밀번호는 12~64자이면서 UTF-8 72바이트 이하.
const signup = await fetch(`${base}/api/users`, {
  method: 'POST',
  credentials: 'include',
  headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
  body: JSON.stringify({
    loginId: 'member01',
    password: 'replace-with-your-password',
    name: '홍길동',
    email: 'member@example.com', // 미입력 시 null 또는 빈 문자열
  }),
})
if (!signup.ok) throw new Error(`회원가입 실패: ${signup.status}`)

const login = await fetch(`${base}/api/auth/login`, {
  method: 'POST',
  credentials: 'include',
  headers: { [csrf.headerName]: csrf.token },
  body: new URLSearchParams({
    loginId: 'member01',
    password: 'replace-with-your-password',
  }),
})
if (!login.ok) throw new Error(`로그인 실패: ${login.status}`)
csrf = await getCsrf()

const me = await fetch(`${base}/api/users/me`, { credentials: 'include' })
if (!me.ok) throw new Error(`조회 실패: ${me.status}`)
console.log(await me.json())
```

내 정보 수정은 `{ "name": "변경 이름", "email": null }` 형태입니다.
이름은 필수이고 이메일을 생략하거나 null/빈 문자열로 보내면 기존 이메일을 지웁니다.
비밀번호 변경은 `{ "currentPassword": "현재 비밀번호", "newPassword": "새 비밀번호" }`,
탈퇴는 `{ "password": "현재 비밀번호" }`를 보냅니다.
변경·탈퇴 후 다른 기기의 세션도 다음 요청에서 401로 거절됩니다.

## 동작 및 오류

- 비밀번호는 BCrypt(cost 12)로 해시 처리하며 응답에 포함하지 않습니다.
- `ACTIVE` 계정만 로그인 가능합니다. `INACTIVE`, `LOCKED`, `WITHDRAWN`은 모두 동일한 로그인 실패 응답입니다.
- 로그인 시 세션 ID를 교체합니다. 세션 유효 기간은 미사용 30분, 동시 로그인은 최대 5개입니다.
- 탈퇴는 상태를 `WITHDRAWN`으로 바꾸는 논리 삭제입니다. 기존 글과의 관계를 유지하며 아이디·이메일은 재사용하지 않습니다.
- 사용자 목록은 페이지 단위이며 상세 정보는 상세 API에서 확인합니다.
- 400: 입력 오류 또는 현재 비밀번호 불일치, 401: 인증 실패, 403: 권한 또는 CSRF 오류, 404: 사용자 없음, 409: 아이디/이메일 중복, 429: 로그인 요청 제한.
- 로그인은 IP당 1분에 20회까지 허용합니다. 직접 연결 주소를 사용하며 임의의 `X-Forwarded-For` 헤더는 신뢰하지 않습니다.

## 운영 설정

- HTTPS에서 `SESSION_COOKIE_SECURE=true`를 설정합니다. 세션 쿠키는 HttpOnly, SameSite=Lax입니다.
- 프론트 허용 Origin은 `FRONTEND_ORIGIN`으로 지정합니다. 기본값은 `http://localhost:3100`입니다.
- 현재 세션·요청 제한은 서버 메모리에 있습니다. 서버 재시작 시 로그아웃됩니다. 다중 서버 운영 시 공유 저장소와 프록시의 요청 제한 설정이 필요합니다.
- 관리자 DB 직접 변경은 기존 인증 세션의 역할을 자동 갱신하지 않습니다. 역할·상태 관리 API를 추가할 때 세션 만료도 함께 처리해야 합니다.
- 이메일 인증, 비밀번호 찾기, 관리자 역할 변경은 이번 범위에 포함되지 않습니다.

## 검증

`./gradlew.bat test`는 H2의 MariaDB 호환 모드로 실행하며 실제 DB 접속 정보와 분리됩니다.
가입/중복/역할/CSRF/세션 교체/로그아웃/비밀번호 변경/탈퇴/관리자 접근/CORS를 검증합니다.
실제 MariaDB 테이블과의 연동 검증은 별도입니다.

세션 처리 참고: [Spring Security 공식 문서](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/session-management.html)
