import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
const version = fs.readFileSync('VERSION', 'utf8').trim();
const matrix = JSON.parse(fs.readFileSync('deploy/ci-backend-images.json', 'utf8'));
const bundle = matrix.find(item => item.tag === process.argv[2]);
if (!bundle) throw new Error('Specify a published backend bundle');
const providers = matrix.filter(item => item.tag !== 'all' && item.tag !== 'kafka-kinesis').map(item => item.tag);
const expected = bundle.tag === 'all' ? providers : bundle.tag === 'kafka-kinesis' ? ['kafka', 'kinesis'] : [bundle.tag];
const entries = execFileSync('jar', ['tf', `backend/eventore-server/target/eventore-server-${version}.jar`], { encoding: 'utf8' }).split(/\r?\n/);
for (const provider of providers) {
  const included = entries.includes(`BOOT-INF/lib/eventore-provider-${provider}-${version}.jar`);
  if (included !== expected.includes(provider)) throw new Error(`${bundle.tag}: incorrect provider ${provider}`);
}
for (const [provider, delegate] of [['kafka', 'KafkaAdminApiDelegateImpl'], ['kinesis', 'KinesisAdminApiDelegateImpl']]) {
  const included = entries.includes(`BOOT-INF/classes/com/eventore/api/delegate/${delegate}.class`);
  if (included !== expected.includes(provider)) throw new Error(`${bundle.tag}: incorrect administration delegate ${delegate}`);
}
console.log(`Verified ${bundle.tag}: ${expected.join(', ')} and matching administration delegates`);
