#!/usr/bin/env bash
# 새 Ubuntu 24.04 서버를 한 번 준비해요. 여러 번 돌려도 괜찮아요.
#   scp -i ~/.ssh/yyt_deploy -r deploy azureuser@서버주소:~/
#   ssh -i ~/.ssh/yyt_deploy azureuser@서버주소 'sudo bash ~/deploy/setup-server.sh'
# 하는 일: 시간대, 스왑, Java 21(JDK)·git·nginx·Docker·certbot 설치, app 계정·폴더, 비밀 값 만들기,
#          systemd·nginx 설정과 GitHub 배포 스크립트(update-backend.sh) 넣기, MySQL 띄우기
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
    echo "sudo 로 실행해요: sudo bash $0" >&2
    exit 1
fi

HERE="$(cd "$(dirname "$0")" && pwd)"
APP_DIR=/opt/local-demand-map
ENV_DIR=/etc/local-demand-map
WEB_DIR=/var/www/local-demand-map

echo "== 시간대"
timedatectl set-timezone Asia/Seoul

echo "== 스왑 2GB (메모리가 모자랄 때 서버가 바로 죽지 않게)"
if ! swapon --show | grep -q /swapfile; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "== 프로그램 설치"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
# JDK·git 은 서버에서 GitHub 코드를 받아 빌드할 때(update-backend.sh) 써요
apt-get install -y -q openjdk-21-jdk-headless git nginx docker.io docker-compose-v2 certbot python3-certbot-nginx rsync curl
systemctl enable --now docker

echo "== app 계정과 폴더"
id app >/dev/null 2>&1 || useradd --system --home-dir "$APP_DIR" --shell /usr/sbin/nologin app
install -d -o app -g app -m 755 "$APP_DIR"
install -d -o root -g root -m 700 "$ENV_DIR"
install -d -o www-data -g www-data -m 755 "$WEB_DIR"
install -o root -g root -m 644 "$HERE/compose.prod.yaml" "$APP_DIR/compose.prod.yaml"
install -o root -g root -m 755 "$HERE/update-backend.sh" "$APP_DIR/update-backend.sh"

# 프론트를 올리기 전에도 nginx가 잘 뜨는지 볼 수 있게 임시 화면을 둬요
if [[ ! -f "$WEB_DIR/index.html" ]]; then
    cat > "$WEB_DIR/index.html" <<'HTML'
<!doctype html><meta charset="utf-8"><title>생기면 갈게요</title>
<p>서버 준비 완료. 프론트를 올리면 이 화면이 바뀌어요.</p>
HTML
    chown www-data:www-data "$WEB_DIR/index.html"
fi

echo "== 환경변수 파일"
if [[ ! -f "$ENV_DIR/app.env" ]]; then
    install -o root -g root -m 600 "$HERE/app.env.example" "$ENV_DIR/app.env"
    echo "   $ENV_DIR/app.env 를 만들었어요(root 만 읽어요)."
fi

# 비어 있는 비밀 값만 서버에서 새로 만들어요. 이미 있는 값은 그대로 둬요(DB 비밀번호가 바뀌면 안 돼서)
fill_secret() {
    local key="$1" value="$2"
    if grep -qE "^${key}=$" "$ENV_DIR/app.env"; then
        sed -i "s|^${key}=$|${key}=${value}|" "$ENV_DIR/app.env"
        echo "   $key 를 새로 만들었어요."
    fi
}
fill_secret DB_PASSWORD "$(openssl rand -hex 24)"
fill_secret DB_ROOT_PASSWORD "$(openssl rand -hex 24)"
fill_secret JWT_SECRET "$(openssl rand -base64 32)"

echo "== systemd"
install -o root -g root -m 644 "$HERE/local-demand-map.service" /etc/systemd/system/local-demand-map.service
systemctl daemon-reload
systemctl enable local-demand-map >/dev/null

echo "== nginx"
install -o root -g root -m 644 "$HERE/nginx.conf" /etc/nginx/sites-available/local-demand-map
ln -sf /etc/nginx/sites-available/local-demand-map /etc/nginx/sites-enabled/local-demand-map
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

echo "== MySQL"
docker compose -f "$APP_DIR/compose.prod.yaml" --env-file "$ENV_DIR/app.env" up -d

echo
echo "준비 끝. 백엔드는 둘 중 하나로 올려요."
echo "  서버에서 GitHub main 받아 빌드: sudo $APP_DIR/update-backend.sh"
echo "  내 컴퓨터에서 빌드해 올리기:    deploy/deploy-backend.sh azureuser@서버주소"
