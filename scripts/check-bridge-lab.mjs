// End-to-end validation of the public tutorial against published container images.
import assert from 'node:assert/strict';
const api = 'http://127.0.0.1:8080/api/v1';
const rabbit = 'http://127.0.0.1:15672/api/queues/%2F/warehouse.orders';
const rabbitHeaders = { Authorization: 'Basic ' + Buffer.from('eventore:eventore').toString('base64') };
async function request(url, method = 'GET', data, headers = {}) {
  const response = await fetch(url, { method, headers: { 'Content-Type': 'application/json', ...headers }, body: data === undefined ? undefined : JSON.stringify(data), signal: AbortSignal.timeout(15000) });
  assert.ok(response.ok, `${method} ${url}: ${response.status} ${!response.ok ? await response.text() : ''}`);
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
await request(rabbit, 'PUT', { durable: true }, rabbitHeaders);
const source = await request(api + '/connections', 'POST', { name: 'lab-kafka', protocol: 'KAFKA', brokerUrl: 'kafka:9092' });
const target = await request(api + '/connections', 'POST', { name: 'lab-rabbitmq', protocol: 'RABBITMQ', brokerUrl: 'amqp://rabbitmq:5672', credentials: { username: 'env:LAB_RABBITMQ_USER', password: 'env:LAB_RABBITMQ_PASSWORD' } });
for (const [status, expected] of [['CONFIRMED', true], ['PENDING', false]]) {
  const result = await request(api + '/bridges/test', 'POST', { payload: JSON.stringify({ status }), headers: {}, payloadFilter: 'CONFIRMED', headerTransform: { 'x-origin': 'eventore-lab' }, loopPrevention: true });
  assert.equal(result.passedFilter, expected);
  assert.equal(result.transformedHeaders['x-origin'], 'eventore-lab');
}
const bridge = await request(api + '/bridges', 'POST', { name: 'confirmed-orders', sourceConnectionId: source.id, sourceDestination: 'orders', targetConnectionId: target.id, targetDestination: 'warehouse.orders', payloadFilter: 'CONFIRMED', headerTransform: { 'x-origin': 'eventore-lab' }, loopPrevention: true, autoStart: true });
assert.equal(bridge.stats.state, 'RUNNING');
try {
  let delivered = false;
  // Fresh messages also cover asynchronous Kafka consumer initialization.
  for (let attempt = 0; attempt < 20 && !delivered; attempt++) {
    await request(`${api}/connections/${source.id}/publish`, 'POST', { destination: 'orders', payload: JSON.stringify({ orderId: 'demo-1001', status: 'CONFIRMED' }) });
    await new Promise(resolve => setTimeout(resolve, 1500));
    const messages = await request(rabbit + '/get', 'POST', { count: 5, ackmode: 'ack_requeue_false', encoding: 'auto', truncate: 50000 }, rabbitHeaders);
    delivered = messages.some(message => JSON.parse(message.payload).orderId === 'demo-1001' && message.properties.headers['x-origin'] === 'eventore-lab');
  }
  assert.ok(delivered, 'Expected a confirmed order in RabbitMQ with the transformed header');
  const current = await request(api + '/bridges/' + bridge.id);
  assert.ok(current.stats.totalReplicated > 0);
  assert.equal(current.stats.errorsCount, 0);
  console.log('Published-image tutorial verified: filter dry run, Kafka source, RabbitMQ delivery, headers, and bridge counters.');
} finally {
  await request(`${api}/bridges/${bridge.id}/stop`, 'POST');
  await request(`${api}/bridges/${bridge.id}`, 'DELETE');
}
