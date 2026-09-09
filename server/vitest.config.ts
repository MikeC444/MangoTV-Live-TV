import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    setupFiles: ["./tests/setup.ts"],
    // All test files share one real Postgres database and truncate it
    // between tests (see tests/helpers/db.ts) — running files in
    // parallel would let one file's cleanup race another file's
    // in-flight assertions. Tests within a single file already run
    // sequentially by default; this just extends that across files too.
    fileParallelism: false,
  },
});
