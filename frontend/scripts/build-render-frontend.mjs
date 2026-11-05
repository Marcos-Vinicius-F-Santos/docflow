import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const apiBaseUrl = process.env.DOCFLOW_API_BASE_URL?.trim().replace(/\/+$/, '');
const authBaseUrl = process.env.NEON_AUTH_BASE_URL?.trim().replace(/\/+$/, '');

if (!apiBaseUrl) {
  throw new Error('DOCFLOW_API_BASE_URL must be configured for the Render frontend build');
}
if (!authBaseUrl) {
  throw new Error('NEON_AUTH_BASE_URL must be configured for the Render frontend build');
}

const config = `window.__DOCFLOW_CONFIG__ = ${JSON.stringify({ apiBaseUrl, authBaseUrl })};\n`;
writeFileSync(resolve('frontend/public/config.js'), config, 'utf8');
