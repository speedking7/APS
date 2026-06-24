#!/usr/bin/env bash

set -euo pipefail

ACTION="${1:-}"
shift || true

APP_PORT="${APP_PORT:-}"
SKIP_BUILD=0
KEEP_MYSQL=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)
      APP_PORT="${2:?missing port value}"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --keep-mysql)
      KEEP_MYSQL=1
      shift
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$ACTION" ]]; then
  echo "Usage: scripts/aps-local.sh <start|stop|status> [--port N] [--skip-build] [--keep-mysql]" >&2
  exit 2
fi

SCRIPT_DIR="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
APP_DIR="$REPO_ROOT/aps-system"
RUN_DIR="$APP_DIR/.local-run"

APP_JAR="$APP_DIR/target/aps-system-1.0.0.jar"
APP_PID_FILE="$RUN_DIR/app.pid"
APP_PORT_FILE="$RUN_DIR/app.port"
APP_LOG="$RUN_DIR/app.log"
APP_ERR_LOG="$RUN_DIR/app.err.log"

MYSQL_HOME="${MYSQL_HOME:-$HOME/aps-mysql}"
MYSQL_DATA="$MYSQL_HOME/data"
MYSQL_RUN="$MYSQL_HOME/run"
MYSQL_LOG_DIR="$MYSQL_HOME/log"
MYSQL_PORT="${MYSQL_PORT:-3307}"
MYSQL_BIND_ADDRESS="${MYSQL_BIND_ADDRESS:-127.0.0.1}"
MYSQL_SOCKET="$MYSQL_RUN/mysql.sock"
MYSQL_PID_FILE="$MYSQL_RUN/mysqld.pid"
MYSQL_LOG_FILE="$MYSQL_LOG_DIR/mysql.log"
MYSQL_INIT_LOG="$MYSQL_LOG_DIR/init.log"
MYSQL_ROOT_MARKER="$MYSQL_HOME/root-configured"

DB_NAME="${DB_NAME:-aps_db}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-root}"

log() {
  printf '%s\n' "$*"
}

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

is_pid_running() {
  local pid="${1:-}"
  [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null
}

rotate_file() {
  local file="$1"
  if [[ -f "$file" ]]; then
    mv "$file" "$file.$(date +%Y%m%d%H%M%S)"
  fi
}

wait_for_mysql() {
  local retries="${1:-30}"
  local i
  for ((i = 1; i <= retries; i++)); do
    if mysqladmin --protocol=tcp -h 127.0.0.1 -P "$MYSQL_PORT" -u "$DB_USER" "-p$DB_PASSWORD" ping >/dev/null 2>&1; then
      return 0
    fi
    if mysqladmin --socket="$MYSQL_SOCKET" -u root ping >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_for_http() {
  local port="$1"
  local retries="${2:-60}"
  local i
  for ((i = 1; i <= retries; i++)); do
    if curl -fsS "http://127.0.0.1:${port}/api/forecast" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

ensure_dirs() {
  mkdir -p "$RUN_DIR" "$MYSQL_DATA" "$MYSQL_RUN" "$MYSQL_LOG_DIR"
}

ensure_mysql_initialized() {
  if [[ -d "$MYSQL_DATA/mysql" ]]; then
    return 0
  fi

  log "Initializing local MySQL data directory under $MYSQL_DATA"
  rm -rf "$MYSQL_DATA"/*
  mysqld --initialize-insecure \
    --datadir="$MYSQL_DATA" \
    --basedir=/usr \
    --log-error="$MYSQL_INIT_LOG"
}

start_mysql() {
  ensure_mysql_initialized

  if mysqladmin --protocol=tcp -h 127.0.0.1 -P "$MYSQL_PORT" -u "$DB_USER" "-p$DB_PASSWORD" ping >/dev/null 2>&1; then
    return 0
  fi

  if [[ -f "$MYSQL_PID_FILE" ]]; then
    local old_pid
    old_pid="$(tr -d '[:space:]' < "$MYSQL_PID_FILE")"
    if ! is_pid_running "$old_pid"; then
      rm -f "$MYSQL_PID_FILE"
    fi
  fi

  log "Starting local MySQL on 127.0.0.1:$MYSQL_PORT"
  nohup mysqld \
    --datadir="$MYSQL_DATA" \
    --socket="$MYSQL_SOCKET" \
    --port="$MYSQL_PORT" \
    --bind-address="$MYSQL_BIND_ADDRESS" \
    --pid-file="$MYSQL_PID_FILE" \
    --log-error="$MYSQL_LOG_FILE" \
    --mysqlx=0 \
    >"$RUN_DIR/mysql.stdout.log" 2>&1 &

  wait_for_mysql 30 || fail "MySQL did not become ready. Check $MYSQL_LOG_FILE"

  if [[ ! -f "$MYSQL_ROOT_MARKER" ]]; then
    log "Configuring local MySQL root credentials"
    mysql --protocol=socket -S "$MYSQL_SOCKET" -u root <<SQL
ALTER USER 'root'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;
FLUSH PRIVILEGES;
SQL
    touch "$MYSQL_ROOT_MARKER"
  fi

  mysql --protocol=tcp -h 127.0.0.1 -P "$MYSQL_PORT" -u "$DB_USER" "-p$DB_PASSWORD" \
    -e "CREATE DATABASE IF NOT EXISTS ${DB_NAME} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
}

stop_app() {
  if [[ -f "$APP_PID_FILE" ]]; then
    local pid
    pid="$(tr -d '[:space:]' < "$APP_PID_FILE")"
    if is_pid_running "$pid"; then
      log "Stopping APS app process $pid"
      kill "$pid"
      local i
      for ((i = 0; i < 20; i++)); do
        if ! is_pid_running "$pid"; then
          break
        fi
        sleep 1
      done
      if is_pid_running "$pid"; then
        kill -9 "$pid" 2>/dev/null || true
      fi
    fi
    rm -f "$APP_PID_FILE" "$APP_PORT_FILE"
  fi
}

stop_mysql() {
  if [[ ! -f "$MYSQL_PID_FILE" ]]; then
    return 0
  fi

  local pid
  pid="$(tr -d '[:space:]' < "$MYSQL_PID_FILE")"
  if is_pid_running "$pid"; then
    log "Stopping local MySQL process $pid"
    kill "$pid"
    local i
    for ((i = 0; i < 20; i++)); do
      if ! is_pid_running "$pid"; then
        break
      fi
      sleep 1
    done
    if is_pid_running "$pid"; then
      kill -9 "$pid" 2>/dev/null || true
    fi
  fi
  rm -f "$MYSQL_PID_FILE"
}

build_app() {
  log "Building APS jar with Maven"
  rm -f "$APP_DIR/target/aps-system-1.0.0.jar" "$APP_DIR/target/aps-system-1.0.0.jar.original"
  (
    cd "$APP_DIR"
    mvn -q clean package -DskipTests
  )

  [[ -f "$APP_JAR" ]] || fail "Build completed without producing $APP_JAR"
}

start_app() {
  local port="$1"
  rotate_file "$APP_LOG"
  rotate_file "$APP_ERR_LOG"

  log "Starting APS app on port $port"
  (
    cd "$APP_DIR"
    nohup java -jar "$APP_JAR" \
      --server.port="$port" \
      --spring.datasource.url="jdbc:mysql://127.0.0.1:${MYSQL_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false" \
      --spring.datasource.username="$DB_USER" \
      --spring.datasource.password="$DB_PASSWORD" \
      --spring.sql.init.mode=never \
      >"$APP_LOG" 2>"$APP_ERR_LOG" < /dev/null &
    echo $! > "$APP_PID_FILE"
  )
  echo "$port" > "$APP_PORT_FILE"

  wait_for_http "$port" 60 || fail "APS app did not become ready. Check $APP_LOG and $APP_ERR_LOG"
}

status() {
  local app_pid=""
  local port=""
  local wsl_ip=""

  if [[ -f "$APP_PID_FILE" ]]; then
    app_pid="$(tr -d '[:space:]' < "$APP_PID_FILE")"
  fi
  if [[ -f "$APP_PORT_FILE" ]]; then
    port="$(tr -d '[:space:]' < "$APP_PORT_FILE")"
  fi
  wsl_ip="$(hostname -I | awk '{print $1}')"

  if [[ -n "$app_pid" ]] && is_pid_running "$app_pid"; then
    log "APP_STATUS=running"
    log "APP_PID=$app_pid"
    log "APP_PORT=${port:-unknown}"
    log "APP_URL=http://${wsl_ip}:${port:-unknown}"
    if [[ -n "$port" ]] && curl -fsS "http://127.0.0.1:${port}/api/forecast" >/dev/null 2>&1; then
      log "APP_HEALTH=ok"
    else
      log "APP_HEALTH=unreachable"
    fi
  else
    log "APP_STATUS=stopped"
  fi

  if [[ -f "$MYSQL_PID_FILE" ]]; then
    local mysql_pid
    mysql_pid="$(tr -d '[:space:]' < "$MYSQL_PID_FILE")"
    if is_pid_running "$mysql_pid"; then
      log "MYSQL_STATUS=running"
      log "MYSQL_PID=$mysql_pid"
      log "MYSQL_PORT=$MYSQL_PORT"
      return 0
    fi
  fi

  if mysqladmin --protocol=tcp -h 127.0.0.1 -P "$MYSQL_PORT" -u "$DB_USER" "-p$DB_PASSWORD" ping >/dev/null 2>&1; then
    log "MYSQL_STATUS=running"
    log "MYSQL_PORT=$MYSQL_PORT"
  else
    log "MYSQL_STATUS=stopped"
  fi
}

start() {
  local port="${1:-8081}"

  require_cmd java
  require_cmd mvn
  require_cmd mysql
  require_cmd mysqladmin
  require_cmd mysqld
  require_cmd curl

  ensure_dirs
  stop_app
  start_mysql

  if [[ "$SKIP_BUILD" -eq 0 ]]; then
    build_app
  else
    [[ -f "$APP_JAR" ]] || fail "--skip-build was used but $APP_JAR does not exist"
  fi

  start_app "$port"
  status
}

case "$ACTION" in
  start)
    start "${APP_PORT:-8081}"
    ;;
  stop)
    stop_app
    if [[ "$KEEP_MYSQL" -eq 0 ]]; then
      stop_mysql
    fi
    status
    ;;
  status)
    status
    ;;
  *)
    fail "Unsupported action: $ACTION"
    ;;
esac
