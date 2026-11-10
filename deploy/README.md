# 배포 (Azure VM 한 대)

시연용 서버 한 대에 화면·API·DB를 같이 올려요. 주소 하나로 다 나가서 CORS·mixed content 걱정이 없어요.

```
브라우저 ── http(s)://주소 ──▶ nginx (80·443)
                                ├─ /           → 프론트 빌드 파일 (/var/www/local-demand-map)
                                ├─ /api/       → Spring Boot (127.0.0.1:8080, 밖에서는 못 들어와요)
                                └─ Swagger     → Spring Boot
                                                   └─ MySQL 8.4 (docker, 127.0.0.1:3306)
```

| 파일 | 하는 일 |
|---|---|
| `setup-server.sh` | 새 서버를 한 번 준비(Java 21·nginx·Docker 설치, 비밀 값 만들기, MySQL 띄우기). 여러 번 돌려도 돼요 |
| `app.env.example` | 서버 환경변수 목록. 서버에서는 `/etc/local-demand-map/app.env`(root만 읽음) |
| `compose.prod.yaml` | 서버의 MySQL |
| `local-demand-map.service` | 백엔드를 systemd로 켜고, 꺼지면 다시 켜요 |
| `nginx.conf` | 화면과 `/api/`를 한 주소로 |
| `update-backend.sh` | **서버에서** GitHub 코드를 받아 빌드하고 다시 켜기. 안 켜지면 전 jar 로 되돌려요 |
| `deploy-backend.sh` | **내 컴퓨터에서** jar 만들어 올리고 다시 켜기 |
| `deploy-frontend.sh` | 프론트 빌드 폴더(`dist`) 올리기 |

## 0. 준비물

- Azure 학생 구독(Azure for Students, 학교 메일로 인증, 카드 없음)
- 배포용 SSH 키. 없으면 만들어요(개인키 `yyt_deploy`는 어디에도 올리지 않아요)

```bash
ssh-keygen -t ed25519 -f ~/.ssh/yyt_deploy -C yyt-deploy
```

## 1. VM 만들기 (Azure Portal → Virtual machines → Create)

| 칸 | 값 |
|---|---|
| Resource group | 새로 만들기 `yyt-rg` (끝나면 이것만 지우면 다 지워져요) |
| Virtual machine name | `yyt-app` |
| Region | Korea Central. 목록에 없으면 Japan East 등 가까운 곳(학생 구독은 쓸 수 있는 지역만 보여요) |
| Image | Ubuntu Server 24.04 LTS (x64) |
| Size | 메모리 4GB짜리(예: B2s, 2 vCPU·4GB). 1GB짜리는 Spring Boot·MySQL이 같이 못 버텨요 |
| Authentication | SSH public key, Username `azureuser`, Key source "Use existing public key"에 `cat ~/.ssh/yyt_deploy.pub` 한 줄 |
| Inbound ports | HTTP(80), HTTPS(443), SSH(22) |
| OS disk | Standard SSD, 기본 30GB |

만든 뒤에 두 가지를 더 해요.

1. **SSH는 내 IP만**: VM → Networking → SSH(22) 규칙 → Source를 "My IP address"로. 다른 곳에서 접속하면 그 IP를 더해요.
2. **주소 이름 붙이기**: VM → Overview → DNS name → Configure → DNS name label에 `yyt-demand` 같은 이름. 주소가 `yyt-demand.<지역>.cloudapp.azure.com`이 돼요. https와 카카오 지도 키에 이 주소를 써요.

VM → Auto-shutdown이 꺼져 있는지도 봐요. 켜져 있으면 정해진 시각에 서버가 꺼져요.

아래에서 `주소`는 이 DNS 이름(또는 공인 IP)이에요.

## 2. 서버 준비 (한 번)

레포 루트에서:

```bash
scp -i ~/.ssh/yyt_deploy -r deploy azureuser@주소:~/
ssh -i ~/.ssh/yyt_deploy azureuser@주소 'sudo bash ~/deploy/setup-server.sh'
```

- 비어 있는 `DB_PASSWORD`·`DB_ROOT_PASSWORD`·`JWT_SECRET`은 서버가 직접 만들어 `/etc/local-demand-map/app.env`에 넣어요. 값을 채팅이나 내 컴퓨터로 옮길 필요가 없어요.
- AI를 실제로 쓰려면 서버에서 `sudo nano /etc/local-demand-map/app.env`로 `AI_PROVIDER=openai`, `AI_API_KEY=...`를 넣고 `sudo systemctl restart local-demand-map`.
- 확인: 브라우저에서 `http://주소` → "서버 준비 완료"

## 3. 백엔드 올리기

둘 중 하나로 올려요. 둘 다 서버의 `app.jar`를 바꾸고 다시 켜는 거라 섞어 써도 되고, 마지막에 올린 것이 떠 있어요.

### 평소: 서버에서 GitHub main 받기 (추천)

main에 머지된 코드만 올라가요. 서버에 들어갈 수 있는 사람이면 누구든 할 수 있어요.

```bash
ssh -i ~/.ssh/yyt_deploy azureuser@주소
sudo /opt/local-demand-map/update-backend.sh               # main
sudo /opt/local-demand-map/update-backend.sh be/브랜치이름  # 머지 전 브랜치를 잠깐 시험
```

- 처음 한 번은 Gradle·라이브러리를 받느라 몇 분 걸려요. 다음부터는 1분 안팎이에요.
- 90초 안에 안 켜지면 스스로 전 jar 로 되돌려요.

### 급할 때: 내 컴퓨터에서 빌드해 올리기

아직 GitHub에 올리지 않은 코드도 올라가니 조심해요. JDK 21로 빌드해요(macOS: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`).

```bash
deploy/deploy-backend.sh azureuser@주소
```

### 공통

- 지금 무엇이 떠 있는지: `cat /opt/local-demand-map/DEPLOYED` (예: `github main 3f2a1bc ... (2026-11-08 20:10:00)`, 내 컴퓨터에서 올렸으면 `local ...`)
- 바로 전 버전으로 되돌리기: `sudo /opt/local-demand-map/update-backend.sh --rollback`
- 확인: `http://주소/api/v1/health` → `{"status":"UP"}`, Swagger는 `http://주소/swagger-ui.html`
- 서버를 다시 켤 때마다 시연 데이터가 처음 상태(구성동 과일 29명)로 돌아가요(`DEMO_SEED_ENABLED=true`).

## 4. 프론트 올리기

프론트(`local-fe` 레포)는 API를 같은 주소의 `/api/v1/...`로 불러요.

```bash
# local-fe 에서
pnpm build
# 이 레포 루트에서 (경로는 local-fe 위치에 맞게)
deploy/deploy-frontend.sh azureuser@주소 ../local-fe/dist
```

프론트 개발자가 자기 컴퓨터(`localhost:5173`)에서 이 서버 API를 부르는 것도 열어 뒀어요(`CORS_ALLOWED_ORIGINS`).

## 5. https 붙이기 (권장)

DNS 이름을 붙였으면 무료 인증서를 받을 수 있어요. 메일은 인증서 만료 알림을 받을 주소예요.

```bash
ssh -i ~/.ssh/yyt_deploy azureuser@주소 'sudo certbot --nginx -d 주소 --redirect -m 내메일 --agree-tos -n'
```

이후 http로 들어와도 https로 넘어가요. 인증서는 서버가 알아서 갱신해요.

## 6. 카카오 지도 키

카카오 개발자 콘솔 → 내 애플리케이션 → 플랫폼 → Web → 사이트 도메인에 아래를 넣어요.

- `http://localhost:5173` (개발)
- `https://주소` (배포)

지도 JavaScript 키는 브라우저에 그대로 보이는 키라서 도메인 제한이 꼭 있어야 해요.

## 운영하다 막히면

```bash
ssh -i ~/.ssh/yyt_deploy azureuser@주소
sudo journalctl -u local-demand-map -n 100 --no-pager   # 백엔드 로그
sudo systemctl restart local-demand-map                  # 백엔드 다시 켜기(시연 데이터 처음 상태로)
sudo docker compose -f /opt/local-demand-map/compose.prod.yaml --env-file /etc/local-demand-map/app.env ps
sudo tail -n 50 /var/log/nginx/error.log
```

- 8080·3306은 서버 안에서만 열려요. Azure 방화벽(NSG)에는 22(내 IP)·80·443만 열어요.
- `app.env`는 root만 읽어요. 키·비밀번호를 단톡방·커밋·캡처에 올리지 않아요.

## 끝나면 지우기

결선(11/15)이 끝나면 Azure Portal → Resource groups → `yyt-rg` → Delete resource group. VM·디스크·공인 IP가 같이 지워져 크레딧이 더 나가지 않아요.
