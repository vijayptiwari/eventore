import { readFileSync, unlinkSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execSync } from 'node:child_process';

const frontendRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const schemaPath = join(frontendRoot, 'src', 'api', 'generated', 'schema.ts');
const tmpSchemaPath = join(frontendRoot, 'src', 'api', 'generated', 'schema.tmp.ts');

console.log('Running OpenAPI bundle and drift check...');

try {
  // Step 1: Bundle OpenAPI
  execSync('npm run bundle:openapi', { cwd: frontendRoot, stdio: 'inherit' });

  // Step 2: Generate schema to temporary file
  execSync(
    'npx openapi-typescript ../backend/openapi/eventore-api-bundled.yaml -o src/api/generated/schema.tmp.ts',
    { cwd: frontendRoot, stdio: 'inherit' }
  );

  if (!existsSync(schemaPath)) {
    console.error('ERROR: src/api/generated/schema.ts does not exist! Run "npm run generate:api"');
    process.exit(1);
  }

  const currentContent = readFileSync(schemaPath, 'utf8').replace(/\r\n/g, '\n').trim();
  const generatedContent = readFileSync(tmpSchemaPath, 'utf8').replace(/\r\n/g, '\n').trim();

  if (currentContent !== generatedContent) {
    console.error(
      '\n❌ OpenAPI contract drift detected!\n' +
      'Backend OpenAPI specs have changed but frontend generated schema is out of date.\n' +
      'Run "npm run generate:api" and commit src/api/generated/schema.ts.\n'
    );
    process.exit(1);
  }

  console.log('✅ OpenAPI contracts and frontend generated schema are in sync.');
} finally {
  if (existsSync(tmpSchemaPath)) {
    try {
      unlinkSync(tmpSchemaPath);
    } catch {
      // ignore cleanup errors
    }
  }
}
