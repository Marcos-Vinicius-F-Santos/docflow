interface DocFlowRuntimeConfig {
  apiBaseUrl?: string;
  authBaseUrl?: string;
}

declare global {
  interface Window {
    __DOCFLOW_CONFIG__?: DocFlowRuntimeConfig;
  }
}

const runtimeConfig = typeof window !== 'undefined' ? window.__DOCFLOW_CONFIG__ : undefined;
const apiBaseUrl = (runtimeConfig?.apiBaseUrl ?? '').replace(/\/+$/, '');
const neonAuthBaseUrl = (runtimeConfig?.authBaseUrl ?? '').replace(/\/+$/, '');

export const environment = {
  production: true,
  apiBaseUrl,
  neonAuthBaseUrl,
  documentApiBaseUrl: `${apiBaseUrl}/documents`,
  applicationLogsApiUrl: `${apiBaseUrl}/logs`,
  notificationWebSocketPath: '/ws/notifications',
} as const;
