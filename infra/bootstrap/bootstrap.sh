#!/bin/sh

set -eu

: "${POSTGRES_HOST:?POSTGRES_HOST must be set}"
: "${POSTGRES_PORT:?POSTGRES_PORT must be set}"
: "${POSTGRES_DB:?POSTGRES_DB must be set}"
: "${POSTGRES_USER:?POSTGRES_USER must be set}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set}"
: "${RABBITMQ_HOST:?RABBITMQ_HOST must be set}"
: "${RABBITMQ_MANAGEMENT_PORT:?RABBITMQ_MANAGEMENT_PORT must be set}"
: "${RABBITMQ_USER:?RABBITMQ_USER must be set}"
: "${RABBITMQ_PASSWORD:?RABBITMQ_PASSWORD must be set}"
: "${MINIO_ENDPOINT:?MINIO_ENDPOINT must be set}"
: "${MINIO_ROOT_USER:?MINIO_ROOT_USER must be set}"
: "${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD must be set}"
: "${MINIO_ACCESS_KEY:?MINIO_ACCESS_KEY must be set}"
: "${MINIO_SECRET_KEY:?MINIO_SECRET_KEY must be set}"
: "${MINIO_STAGING_BUCKET:?MINIO_STAGING_BUCKET must be set}"
: "${MINIO_BUCKET:?MINIO_BUCKET must be set}"

MAX_ATTEMPTS="${BOOTSTRAP_MAX_ATTEMPTS:-60}"
RETRY_DELAY_SECONDS="${BOOTSTRAP_RETRY_DELAY_SECONDS:-2}"

fail() {
    echo "[docflow-bootstrap] ERROR: $*" >&2
    exit 1
}

wait_for_postgres() {
    attempt=1
    while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
        output=$(PGPASSWORD="$POSTGRES_PASSWORD" psql \
            --host="$POSTGRES_HOST" \
            --port="$POSTGRES_PORT" \
            --username="$POSTGRES_USER" \
            --dbname="$POSTGRES_DB" \
            --command='SELECT 1' 2>&1) && return 0

        case "$output" in
            *"password authentication failed"*|*"role \"$POSTGRES_USER\" does not exist"*)
                fail "PostgreSQL credentials are invalid for user '$POSTGRES_USER' or database '$POSTGRES_DB'."
                ;;
        esac

        echo "[docflow-bootstrap] Waiting for PostgreSQL ($attempt/$MAX_ATTEMPTS)..." >&2
        attempt=$((attempt + 1))
        sleep "$RETRY_DELAY_SECONDS"
    done

    fail "PostgreSQL did not become available or authenticated within the configured wait period."
}

wait_for_rabbitmq() {
    attempt=1
    rabbit_url="http://${RABBITMQ_HOST}:${RABBITMQ_MANAGEMENT_PORT}/api/overview"
    while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
        if curl --fail --silent --show-error \
            --user "$RABBITMQ_USER:$RABBITMQ_PASSWORD" \
            "$rabbit_url" >/dev/null 2>&1; then
            return 0
        fi

        echo "[docflow-bootstrap] Waiting for RabbitMQ and validating credentials ($attempt/$MAX_ATTEMPTS)..." >&2
        attempt=$((attempt + 1))
        sleep "$RETRY_DELAY_SECONDS"
    done

    fail "RabbitMQ did not become available or rejected the configured credentials."
}

wait_for_minio() {
    attempt=1
    while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
        if mc alias set docflow "$MINIO_ENDPOINT" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; then
            return 0
        fi

        echo "[docflow-bootstrap] Waiting for MinIO and validating root credentials ($attempt/$MAX_ATTEMPTS)..." >&2
        attempt=$((attempt + 1))
        sleep "$RETRY_DELAY_SECONDS"
    done

    fail "MinIO did not become available or rejected MINIO_ROOT_USER/MINIO_ROOT_PASSWORD."
}

echo "[docflow-bootstrap] Validating PostgreSQL, RabbitMQ and MinIO..."
wait_for_postgres
wait_for_rabbitmq
wait_for_minio

mc mb --ignore-existing "docflow/$MINIO_STAGING_BUCKET" \
    || fail "Could not create or validate MinIO bucket '$MINIO_STAGING_BUCKET'."
mc mb --ignore-existing "docflow/$MINIO_BUCKET" \
    || fail "Could not create or validate MinIO bucket '$MINIO_BUCKET'."

policy_file=/tmp/docflow-app-user-policy.json
sed \
    -e "s|__MINIO_STAGING_BUCKET__|$MINIO_STAGING_BUCKET|g" \
    -e "s|__MINIO_BUCKET__|$MINIO_BUCKET|g" \
    /usr/local/share/docflow/app-user-policy.json > "$policy_file"

if ! mc admin user info docflow "$MINIO_ACCESS_KEY" >/dev/null 2>&1; then
    mc admin user add docflow "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" \
        || fail "Could not create MinIO application user '$MINIO_ACCESS_KEY'."
fi

if ! mc admin policy info docflow docflow-application >/dev/null 2>&1; then
    mc admin policy create docflow docflow-application "$policy_file" \
        || fail "Could not create MinIO application policy."
fi

mc admin policy attach docflow docflow-application --user "$MINIO_ACCESS_KEY" \
    || fail "Could not attach the MinIO application policy to '$MINIO_ACCESS_KEY'."

if ! mc alias set docflow-app "$MINIO_ENDPOINT" "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null 2>&1; then
    fail "MinIO application credentials were rejected for '$MINIO_ACCESS_KEY'."
fi
mc ls "docflow-app/$MINIO_STAGING_BUCKET" >/dev/null \
    || fail "MinIO application credentials cannot access '$MINIO_STAGING_BUCKET'."
mc ls "docflow-app/$MINIO_BUCKET" >/dev/null \
    || fail "MinIO application credentials cannot access '$MINIO_BUCKET'."

echo "[docflow-bootstrap] Bootstrap completed. Buckets '$MINIO_STAGING_BUCKET' and '$MINIO_BUCKET' are ready."
