# GitHub Actions 배포 설정

프론트엔드와 백엔드는 별도의 GitHub 저장소입니다. 아래 설정은 **각 저장소의** `Settings → Secrets and variables → Actions`에서 등록하세요.

예시 도메인과 IP는 설명용입니다. 실제 서버 주소와 계정으로 바꿔 입력하세요.

## Front 저장소 (`web-template-front`)

### Secrets

| 이름 | 설명 | 예시값 |
| --- | --- | --- |
| `DEPLOY_HOST` | 배포 서버의 공인 IP 또는 호스트명 | `203.0.113.10` |
| `DEPLOY_USER` | 서버 SSH 접속 계정 | `ubuntu` |
| `DEPLOY_SSH_PRIVATE_KEY` | 서버 접속용 SSH 개인 키 전체 | `-----BEGIN OPENSSH PRIVATE KEY----- …` |

`DEPLOY_SSH_PRIVATE_KEY`에는 개인 키의 시작 줄부터 끝 줄까지 넣습니다. 개인 키를 다른 사람에게 공유하지 마세요.

### Variables

| 이름 | 설명 | 예시값 |
| --- | --- | --- |
| `DEPLOY_PATH` | 서버에서 앱 파일을 둘 절대 경로. Server 저장소와 같은 경로를 권장합니다. | `/home/ubuntu/apps` |
| `DEPLOY_PLATFORM` | 서버 CPU 아키텍처. 생략하면 `linux/amd64`를 사용합니다. | `linux/arm64` |
| `VITE_API_BASE_URL` | 방문자 브라우저가 접속할 백엔드 주소 | `https://api.example.com` |

## Server 저장소 (`web-template-server`)

### Secrets

| 이름 | 설명 | 예시값 |
| --- | --- | --- |
| `DEPLOY_HOST` | 배포 서버의 공인 IP 또는 호스트명 | `203.0.113.10` |
| `DEPLOY_USER` | 서버 SSH 접속 계정 | `ubuntu` |
| `DEPLOY_SSH_PRIVATE_KEY` | 서버 접속용 SSH 개인 키 전체 | `-----BEGIN OPENSSH PRIVATE KEY----- …` |
| `DB_HOST` | 배포 서버에서 접속 가능한 DB 주소 | `10.0.0.20` |
| `DB_PORT` | DB 포트 | `3306` |
| `DB_NAME` | 사용할 데이터베이스 이름 | `web_template` |
| `DB_USERNAME` | DB 계정 | `web_template_user` |
| `DB_PASSWORD` | DB 비밀번호 | `your-database-password` |

### Variables

| 이름 | 설명 | 예시값 |
| --- | --- | --- |
| `DEPLOY_PATH` | 서버에서 앱 파일을 둘 절대 경로. Front 저장소와 같은 경로를 권장합니다. | `/home/ubuntu/apps` |
| `DEPLOY_PLATFORM` | 서버 CPU 아키텍처. 생략하면 `linux/amd64`를 사용합니다. | `linux/arm64` |
| `FRONTEND_ORIGIN` | 방문자가 접속하는 프론트엔드 주소의 origin | `https://example.com` |
| `SESSION_COOKIE_SECURE` | HTTPS를 사용할 때 보안 쿠키 사용 여부 | `true` |

## 서버와 배포 동작

- 두 workflow는 해당 저장소의 `main` 브랜치에 푸시할 때 자동 실행됩니다. GitHub Actions에서 `workflow_dispatch`로 수동 실행할 수도 있습니다.
- 서버는 Linux와 Docker를 사용해야 합니다. SSH 계정에 Docker 실행 권한과 `DEPLOY_PATH` 쓰기 권한이 필요합니다.
- Front 컨테이너는 호스트 포트 `8080`, Server 컨테이너는 호스트 포트 `8100`을 사용합니다.
- DB는 배포 서버에서 접근 가능한 주소여야 합니다. 컨테이너 안의 `localhost`는 배포 서버가 아니라 컨테이너 자신을 가리킵니다.
- `VITE_API_BASE_URL`은 브라우저에서 직접 접속할 수 있는 주소여야 합니다. `FRONTEND_ORIGIN`은 그 프론트엔드 주소와 일치해야 합니다.
- HTTPS를 사용하는 운영 환경은 도메인과 HTTPS 리버스 프록시를 설정하고 `SESSION_COOKIE_SECURE=true`를 사용하세요.

## 배포 후 접속 주소

- Front: `http://<DEPLOY_HOST>:8080`
- Server health check: `http://<DEPLOY_HOST>:8100/api/health`
- Server Swagger: `http://<DEPLOY_HOST>:8100/swagger-ui/index.html`

운영에서는 HTTPS 도메인으로 연결하도록 리버스 프록시를 설정하는 것을 권장합니다.
