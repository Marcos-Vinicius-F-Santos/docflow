import { defineConfig } from 'vitest/config';

export default defineConfig({
  // Angular's unit-test builder emits init-testbed.js outside the source tree.
  // Keep package resolution anchored to the workspace node_modules so Vite can
  // resolve Angular's package exports from that generated entry point.
  resolve: {
    preserveSymlinks: true,
  },
  test: {
    fileParallelism: false,
    maxWorkers: 1,
    minWorkers: 1,
    pool: 'forks',
  },
});
