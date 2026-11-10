#!/usr/bin/env bash
# 프론트(local-fe 레포)에서 만든 빌드 폴더를 서버에 올려요. nginx 는 다시 켤 필요 없어요.
#   (local-fe 에서) pnpm build
#   deploy/deploy-frontend.sh azureuser@서버주소 ../local-fe/dist
# 프론트는 API 를 같은 주소의 /api/v1/... 로 불러요(서버 주소를 따로 넣지 않아도 돼요).
set -euo pipefail

HOST="${1:?사용법: deploy/deploy-frontend.sh azureuser@서버주소 프론트dist경로}"
DIST="${2:?프론트 빌드 폴더(dist) 경로를 넣어요}"
KEY="${SSH_KEY:-$HOME/.ssh/yyt_deploy}"

if [[ ! -f "$DIST/index.html" ]]; then
    echo "!! $DIST/index.html 이 없어요. 프론트에서 pnpm build 를 먼저 해요." >&2
    exit 1
fi

echo "== 올리기"
rsync -az --delete -e "ssh -i $KEY -o StrictHostKeyChecking=accept-new" "$DIST/" "$HOST:/tmp/fe-dist/"
ssh -i "$KEY" "$HOST" 'sudo rsync -a --delete --chown=www-data:www-data /tmp/fe-dist/ /var/www/local-demand-map/ && rm -rf /tmp/fe-dist'
echo "끝. 브라우저에서 새로고침해 봐요."
