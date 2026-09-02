# web-template-server

웹 템플릿에서 공통으로 사용할 Java Spring Boot 백엔드 서버입니다.

현재 서버 상태를 확인하는 API와 프런트엔드 연결을 위한 기본 설정이 들어 있습니다.

## 빠른 시작

JDK 21을 설치한 뒤 실행합니다.

```powershell
.\gradlew.bat bootRun
```

서버 상태 확인:

```text
http://localhost:8080/api/health
```

개발 명령어, 기술 및 파일 설명은 [HELP.md](HELP.md)를 참고하세요.
