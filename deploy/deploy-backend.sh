#!/usr/bin/env bash
# 내 컴퓨터에서 백엔드 jar 를 만들어 서버에 올리고 다시 켜요.
#   deploy/deploy-backend.sh azureuser@서버주소
# SSH 키가 ~/.ssh/yyt_deploy 가 아니면 SSH_KEY=키경로 를 앞에 붙여요. JDK 21 로 빌드해요.
set -euo pipefail

HOST="${1:?사용법: deploy/deploy-backend.sh azureuser@서버주소}"
KEY="${SSH_KEY:-$HOME/.ssh/yyt_deploy}"
SSH=(ssh -i "$KEY" -o StrictHostKeyChecking=accept-new "$HOST")

cd "$(dirname "$0")/../backend"

# 서버의 DEPLOYED 에 남길 기록. 커밋하지 않은 백엔드 수정이 있으면 +수정 을 붙여요
VERSION="local $(git rev-parse --abbrev-ref HEAD) $(git rev-parse --short HEAD)"
[[ -n "$(git status --porcelain -- .)" ]] && VERSION="$VERSION+수정"

echo "== jar 만들기 ($VERSION)"
./gradlew bootJar -q
JAR="$(ls build/libs/*.jar | grep -v -- '-plain' | head -n 1)"
echo "$JAR"

echo "== 서버에 올리기 (전 jar 는 app.jar.prev 로 남겨요)"
scp -i "$KEY" -o StrictHostKeyChecking=accept-new "$JAR" "$HOST:/tmp/app.jar"
"${SSH[@]}" 'D=/opt/local-demand-map
    if [ -f $D/app.jar ]; then sudo cp -p $D/app.jar $D/app.jar.prev; fi
    if [ -f $D/DEPLOYED ]; then sudo cp -p $D/DEPLOYED $D/DEPLOYED.prev; fi
    sudo install -o app -g app -m 644 /tmp/app.jar $D/app.jar && rm -f /tmp/app.jar && sudo systemctl restart local-demand-map'

echo "== 켜질 때까지 기다리기 (최대 90초)"
for _ in $(seq 1 45); do
    if "${SSH[@]}" 'curl -fsS http://127.0.0.1:8080/api/v1/health' >/dev/null 2>&1; then
        "${SSH[@]}" "echo '$VERSION ($(date '+%F %T'))' | sudo tee /opt/local-demand-map/DEPLOYED >/dev/null"
        echo "켜졌어요: $("${SSH[@]}" 'curl -fsS http://127.0.0.1:8080/api/v1/health')"
        exit 0
    fi
    sleep 2
done

echo "!! 90초 안에 안 켜졌어요. 전 jar 로 되돌리려면: ssh ... 'sudo /opt/local-demand-map/update-backend.sh --rollback'" >&2
echo "   서버 로그를 봐요:" >&2
echo "   ssh -i $KEY $HOST 'sudo journalctl -u local-demand-map -n 80 --no-pager'" >&2
exit 1
