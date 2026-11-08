#!/usr/bin/env bash
# 서버에서 GitHub 코드를 받아 백엔드를 빌드하고 다시 켜요. 90초 안에 안 켜지면 전 jar 로 되돌려요.
#   sudo /opt/local-demand-map/update-backend.sh               # main
#   sudo /opt/local-demand-map/update-backend.sh be/브랜치이름  # 다른 브랜치 시험
#   sudo /opt/local-demand-map/update-backend.sh --rollback    # 바로 전 jar 로 되돌리기
# 지금 무엇이 떠 있는지: cat /opt/local-demand-map/DEPLOYED
# 처음 한 번은 Gradle·라이브러리를 받느라 몇 분 걸려요. 빌드는 app 계정으로 해요(root 로 빌드하지 않아요).
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
    echo "sudo 로 실행해요: sudo $0 $*" >&2
    exit 1
fi

REPO_URL="https://github.com/yongyongton-team/local-demand-map.git"
APP_DIR=/opt/local-demand-map
SRC_DIR="$APP_DIR/src"
JAR="$APP_DIR/app.jar"
MARK="$APP_DIR/DEPLOYED"

restart_and_wait() {
    systemctl restart local-demand-map
    for _ in $(seq 1 45); do
        if curl -fsS http://127.0.0.1:8080/api/v1/health >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done
    return 1
}

# 전 jar·전 기록으로 되돌리고 다시 켜요
rollback() {
    if [[ ! -f "$JAR.prev" ]]; then
        echo "!! 되돌릴 전 jar 가 없어요." >&2
        return 1
    fi
    install -o app -g app -m 644 "$JAR.prev" "$JAR"
    if [[ -f "$MARK.prev" ]]; then
        cp -p "$MARK.prev" "$MARK"
    else
        echo "전 jar (기록 없음, $(date '+%F %T') 에 되돌림)" > "$MARK"
    fi
    if restart_and_wait; then
        echo "전 jar 로 되돌렸어요: $(cat "$MARK" 2>/dev/null || echo '기록 없음')"
    else
        echo "!! 전 jar 로도 안 켜져요. 로그: sudo journalctl -u local-demand-map -n 100 --no-pager" >&2
        return 1
    fi
}

if [[ "${1:-}" == "--rollback" ]]; then
    rollback
    exit $?
fi

BRANCH="${1:-main}"

echo "== GitHub 에서 $BRANCH 받기"
if [[ ! -d "$SRC_DIR/.git" ]]; then
    sudo -u app -H git clone --quiet --depth 20 --branch "$BRANCH" "$REPO_URL" "$SRC_DIR"
else
    sudo -u app -H git -C "$SRC_DIR" fetch --quiet --depth 20 origin "$BRANCH"
    sudo -u app -H git -C "$SRC_DIR" reset --quiet --hard FETCH_HEAD
fi
COMMIT="$(sudo -u app -H git -C "$SRC_DIR" log -1 --format='%h %s')"
echo "$COMMIT"

echo "== 빌드"
JAVA_HOME_21="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
cd "$SRC_DIR/backend"
sudo -u app -H env JAVA_HOME="$JAVA_HOME_21" ./gradlew bootJar -q --no-daemon
NEW="$(ls build/libs/*.jar | grep -v -- '-plain' | head -n 1)"

echo "== 바꿔 끼우고 다시 켜기"
[[ -f "$JAR" ]] && cp -p "$JAR" "$JAR.prev"
[[ -f "$MARK" ]] && cp -p "$MARK" "$MARK.prev"
install -o app -g app -m 644 "$NEW" "$JAR"
if restart_and_wait; then
    echo "github $BRANCH $COMMIT ($(date '+%F %T'))" > "$MARK"
    echo "켜졌어요: $(cat "$MARK")"
else
    echo "!! 90초 안에 안 켜져서 전 jar 로 되돌려요." >&2
    rollback || true
    echo "   새 버전 로그: sudo journalctl -u local-demand-map -n 100 --no-pager" >&2
    exit 1
fi
