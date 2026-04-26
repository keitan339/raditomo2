#!/usr/bin/env bash
# mock-oauth2-server をローカル dev container で起動する。
#
# - 使い方:
#     ./scripts/dev/start-mock-oauth.sh           # フォアグラウンド起動
#     ./scripts/dev/start-mock-oauth.sh --bg      # バックグラウンド起動
#     ./scripts/dev/start-mock-oauth.sh --stop    # 停止
#     ./scripts/dev/start-mock-oauth.sh --logs    # ログ tail
#
# - listen: 0.0.0.0:18080（VS Code の port forward 経由でホスト側 localhost:18080 に到達）
# - issuer: http://localhost:18080/default（id_token の iss はこの値）
# - mvn の test classpath（mock-oauth2-server をテスト依存に持つ）から起動

set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
PID_FILE="/tmp/raditomo-mock-oauth.pid"
LOG_FILE="/tmp/raditomo-mock-oauth.log"
CP_FILE="/tmp/raditomo-mock-cp.txt"
PORT=18080
MAIN_CLASS="no.nav.security.mock.oauth2.StandaloneMockOAuth2ServerKt"
M2_REPO="${M2_REPO:-/tmp/m2-repo}"

cmd="${1:-fg}"
case "$cmd" in
  --stop|stop)
    if [[ -f "$PID_FILE" ]]; then
      pid=$(cat "$PID_FILE")
      kill "$pid" 2>/dev/null && echo "Stopped mock-oauth (pid=$pid)" || true
      rm -f "$PID_FILE"
    fi
    pids=$(lsof -ti:${PORT} 2>/dev/null || true)
    [[ -n "$pids" ]] && kill $pids 2>/dev/null && echo "Killed processes: $pids" || true
    exit 0
    ;;
  --logs|logs)
    tail -f "$LOG_FILE"
    exit 0
    ;;
  --bg|bg)
    BG=1
    ;;
  *)
    BG=0
    ;;
esac

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "Already running (pid=$(cat "$PID_FILE")). Restarting..."
  kill "$(cat "$PID_FILE")" 2>/dev/null || true
  sleep 1
fi

# Maven test スコープのクラスパスを取得
cd "$REPO_ROOT/apps/backend"
echo "Resolving classpath via Maven (-DincludeScope=test)..."
mvn -B -q -Dmaven.repo.local="$M2_REPO" \
  dependency:build-classpath -DincludeScope=test "-Dmdep.outputFile=$CP_FILE" 1>/dev/null
CP=$(cat "$CP_FILE")
if [[ -z "$CP" ]]; then
  echo "Empty classpath. Run 'mvn -Dmaven.repo.local=$M2_REPO test-compile' first." >&2
  exit 1
fi

export SERVER_PORT="$PORT"
export JSON_CONFIG='{"interactiveLogin":true,"loginPagePath":null,"tokenCallbacks":[]}'

echo "Starting mock-oauth2-server on :${PORT} (issuer=http://localhost:${PORT}/default)"

if [[ "$BG" == "1" ]]; then
  nohup java -cp "$CP" "$MAIN_CLASS" > "$LOG_FILE" 2>&1 &
  echo $! > "$PID_FILE"
  for _ in {1..40}; do
    if curl -fsS "http://localhost:${PORT}/default/.well-known/openid-configuration" >/dev/null 2>&1; then
      echo "  ready: http://localhost:${PORT}/default"
      echo "  log:   $LOG_FILE   (tail -f or --logs)"
      exit 0
    fi
    sleep 0.5
  done
  echo "Failed to confirm startup. Check $LOG_FILE" >&2
  exit 1
else
  exec java -cp "$CP" "$MAIN_CLASS"
fi
