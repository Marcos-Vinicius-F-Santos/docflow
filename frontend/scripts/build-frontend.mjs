import { spawnSync } from 'node:child_process';

const result = spawnSync('ng', ['build', ...process.argv.slice(2)], {
  env: {
    ...process.env,
    NG_BUILD_SASS_EMBEDDED: process.env.NG_BUILD_SASS_EMBEDDED ?? '0',
  },
  shell: process.platform === 'win32',
  stdio: 'inherit',
});

if (result.error) {
  console.error(result.error.message);
}
process.exit(result.status ?? 1);
