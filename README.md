# web-template-server

사용자 가입, 세션 로그인·로그아웃, 내 정보 수정, 비밀번호 변경, 탈퇴 및 관리자 조회 API를 제공합니다.
요청 형식과 실행 설정은 [사용자 API 문서](docs/user-api.md)를 참고하세요.

웹 템플릿에서 공통으로 사용할 Java Spring Boot 백엔드 서버입니다.

현재 서버 상태를 확인하는 API와 프런트엔드 연결을 위한 기본 설정이 들어 있습니다.

## 빠른 시작

JDK 21을 설치한 뒤 실행합니다.

```powershell
.\gradlew.bat bootRun
```

서버 상태 확인:

```text
http://localhost:8100/api/health
```

swagger
```text
http://localhost:8100/swagger-ui/index.html
```

개발 명령어, 기술 및 파일 설명은 [HELP.md](HELP.md)를 참고하세요.
