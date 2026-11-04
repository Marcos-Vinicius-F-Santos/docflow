#!/usr/bin/env bash

set -euo pipefail

: "${DOCFLOW_BASE_URL:?DOCFLOW_BASE_URL must point to the staging backend}"

base_url="${DOCFLOW_BASE_URL%/}"
payload_file="$(mktemp)"
download_file="$(mktemp)"

cleanup() {
  rm -f "$payload_file" "$download_file"
}
trap cleanup EXIT

printf 'DocFlow staging smoke test %s\n' "${GITHUB_SHA:-local}" > "$payload_file"

health_response="$(curl --fail-with-body --silent --show-error "$base_url/actuator/health")"
printf '%s' "$health_response" | jq --exit-status --arg status UP '.status == $status' > /dev/null
echo "health: UP"

registration_response="$(curl --fail-with-body --silent --show-error \
  --request POST \
  --form "file=@${payload_file};type=text/plain" \
  "$base_url/documents")"
document_id="$(printf '%s' "$registration_response" | jq --exit-status --raw-output '.id')"
echo "registered document: ${document_id}"

completed=false
for attempt in $(seq 1 30); do
  document_response="$(curl --fail-with-body --silent --show-error \
    "$base_url/documents/${document_id}")"
  status="$(printf '%s' "$document_response" | jq --raw-output '.status')"
  echo "document status (${attempt}/30): ${status}"
  if [[ "$status" == COMPLETED ]]; then
    completed=true
    break
  fi
  if [[ "$status" == FAILED ]]; then
    echo "document processing failed" >&2
    exit 1
  fi
  sleep 2
done

if [[ "$completed" != true ]]; then
  echo "document did not reach COMPLETED within the smoke-test window" >&2
  exit 1
fi

curl --fail-with-body --silent --show-error \
  "$base_url/documents/${document_id}/content" > "$download_file"
cmp --silent "$payload_file" "$download_file"
echo "download: verified"

delete_status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  --request DELETE "$base_url/documents/${document_id}")"
if [[ "$delete_status" != 204 ]]; then
  echo "expected 204 from deletion, received ${delete_status}" >&2
  exit 1
fi
echo "delete: 204"

missing_status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  "$base_url/documents/${document_id}")"
if [[ "$missing_status" != 404 ]]; then
  echo "expected 404 after deletion, received ${missing_status}" >&2
  exit 1
fi
echo "smoke test: passed"
