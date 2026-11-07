import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const baseUrl = (__ENV.DOCFLOW_BASE_URL || '').replace(/\/+$/, '');
const accessToken = __ENV.DOCFLOW_ACCESS_TOKEN || '';
const fixturePath = __ENV.DOCFLOW_FIXTURE || './fixtures/k6-smoke.txt';
const fixtureFilename = __ENV.DOCFLOW_FILENAME || 'k6-fixture.txt';
const fixtureContentType = __ENV.DOCFLOW_CONTENT_TYPE || 'text/plain';
const cleanup = (__ENV.K6_CLEANUP || 'false').toLowerCase() === 'true';
const requireAuth = (__ENV.K6_REQUIRE_AUTH || 'true').toLowerCase() !== 'false';

if (__ENV.K6_APPROVED !== 'YES') {
  throw new Error('Set K6_APPROVED=YES only after approving the staging load parameters.');
}
if (!baseUrl) {
  throw new Error('DOCFLOW_BASE_URL must point to an isolated staging backend.');
}
if (requireAuth && !accessToken) {
  throw new Error('DOCFLOW_ACCESS_TOKEN is required for upload load tests.');
}

const uploadFailures = new Counter('docflow_upload_failures');
const uploadDuration = new Trend('docflow_upload_duration', true);

export const options = {
  scenarios: {
    uploads: {
      executor: 'constant-vus',
      vus: Number(__ENV.K6_VUS || 1),
      duration: __ENV.K6_DURATION || '30s',
      gracefulStop: __ENV.K6_GRACEFUL_STOP || '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<3000', 'p(99)<8000'],
    docflow_upload_failures: ['count==0'],
  },
};

const fixture = open(fixturePath, 'b');

function params() {
  const headers = { Accept: 'application/json' };
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  return {
    headers,
    tags: { component: 'api', phase: '5', test_type: 'upload' },
  };
}

function cleanupDocument(documentId) {
  for (let attempt = 0; attempt < 10; attempt += 1) {
    const current = http.get(`${baseUrl}/documents/${encodeURIComponent(documentId)}`, params());
    const status = current.status === 200 ? current.json('status') : '';
    if (status === 'COMPLETED') {
      const deleted = http.del(`${baseUrl}/documents/${encodeURIComponent(documentId)}`, null, params());
      check(deleted, { 'test document cleanup responds 204': (result) => result.status === 204 });
      return;
    }
    if (status === 'FAILED') {
      return;
    }
    sleep(1);
  }
}

export default function () {
  const startedAt = Date.now();
  const response = http.post(
    `${baseUrl}/documents`,
    {
      file: http.file(
        fixture,
        `k6-${__VU}-${__ITER}-${fixtureFilename}`,
        fixtureContentType,
      ),
    },
    params(),
  );
  uploadDuration.add(Date.now() - startedAt);

  const accepted = response.status === 201;
  check(response, {
    'upload responds 201': () => accepted,
    'upload returns document id': () => accepted && Boolean(response.json('id')),
  });

  if (!accepted) {
    uploadFailures.add(1);
    return;
  }

  if (cleanup) {
    const documentId = response.json('id');
    cleanupDocument(documentId);
  }
}
