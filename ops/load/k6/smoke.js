import http from 'k6/http';
import { check } from 'k6';

const baseUrl = (__ENV.DOCFLOW_BASE_URL || '').replace(/\/+$/, '');
const accessToken = __ENV.DOCFLOW_ACCESS_TOKEN || '';
const mode = (__ENV.K6_MODE || 'protected').toLowerCase();

if (!baseUrl) {
  throw new Error('DOCFLOW_BASE_URL must point to an isolated staging backend.');
}

if (!['health', 'health-load'].includes(mode) && !accessToken) {
  throw new Error('DOCFLOW_ACCESS_TOKEN is required for protected smoke checks.');
}

export const options = {
  scenarios: {
    ...(mode === 'health-load'
      ? {
          health_load: {
            executor: 'constant-vus',
            vus: Number(__ENV.K6_VUS || 2),
            duration: __ENV.K6_DURATION || '1m',
          },
        }
      : {
          smoke: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: __ENV.K6_MAX_DURATION || '30s',
          },
        }),
  },
  thresholds: {
    http_req_failed: ['rate==0'],
    http_req_duration: ['p(95)<2000'],
  },
};

function authParams() {
  return {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: 'application/json',
    },
    tags: { component: 'api', phase: '5', test_type: 'smoke' },
  };
}

export default function () {
  const health = http.get(`${baseUrl}/actuator/health`, {
    tags: { component: 'api', phase: '5', test_type: 'smoke', endpoint: 'health' },
  });
  check(health, {
    'health responds 200': (response) => response.status === 200,
    'health reports UP': (response) => response.json('status') === 'UP',
  });

  if (mode === 'health' || mode === 'health-load') {
    return;
  }

  const documents = http.get(`${baseUrl}/documents`, authParams());
  check(documents, {
    'documents responds 200': (response) => response.status === 200,
    'documents returns JSON': (response) =>
      String(response.headers['Content-Type'] || '').includes('json'),
  });
}
