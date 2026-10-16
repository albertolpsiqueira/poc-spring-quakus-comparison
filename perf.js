import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const TEST = __ENV.TEST || 'read';   // read | write | list
const SEED = 1000;
const headers = { headers: { 'Content-Type': 'application/json' } };

export const options = {
  scenarios: {
    warmup:  { executor: 'constant-vus', vus: 20, duration: '60s',
               tags: { phase: 'warmup' } },
    measure: { executor: 'constant-vus', vus: 50, duration: '120s',
               startTime: '65s', tags: { phase: 'measure' } },
  },
  thresholds: {
    'http_req_duration{phase:measure}': ['p(95)<5000'],
    'http_req_failed{phase:measure}': ['rate<0.01'],
    'http_reqs{phase:measure}': ['count>0'],
  },
};

export function setup() {
  const ids = [];
  const n = TEST === 'list' ? 100 : SEED;
  for (let i = 0; i < n; i++) {
    const r = http.post(`${BASE}/users`,
      JSON.stringify({ name: `seed${i}`, email: `seed${i}@x.com` }), headers);
    ids.push(r.json('id'));
  }
  return { ids };
}

export default function (data) {
  if (TEST === 'read') {
    const id = data.ids[Math.floor(Math.random() * data.ids.length)];
    const r = http.get(`${BASE}/users/${id}`);
    check(r, { 'status 200': (x) => x.status === 200 });
  } else if (TEST === 'write') {
    const email = `u${__VU}-${__ITER}-${Date.now()}@x.com`;
    const r = http.post(`${BASE}/users`,
      JSON.stringify({ name: 'Load', email }), headers);
    check(r, { 'status 201': (x) => x.status === 201 });
  } else {
    const r = http.get(`${BASE}/users`);
    check(r, { 'status 200': (x) => x.status === 200 });
  }
}
