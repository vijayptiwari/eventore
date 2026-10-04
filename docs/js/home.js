(function () {
  var examples = {
    kafka: { topic: 'orders.created', key: 'ord_10284', meta: 'Partition 02 · Offset 84,291', payload: { event: 'order.created', order_id: 'ord_10284', customer: '[REDACTED]', total: 128, currency: 'USD' } },
    rabbitmq: { topic: 'fulfillment.orders', key: 'job_02481', meta: 'Queue fulfillment.orders · Delivery mode 2', payload: { event: 'fulfillment.requested', job_id: 'job_02481', order_id: 'ord_10284', warehouse: 'eu-west', priority: 'standard' } },
    kinesis: { topic: 'device.telemetry', key: 'sensor_0082', meta: 'Shard 000000000001 · Partition key sensor_0082', payload: { event: 'sensor.reading', device_id: 'sensor_0082', temperature: 21.4, unit: 'celsius', region: 'eu-west-1' } }
  };
  document.querySelectorAll('[data-broker]').forEach(function (button) {
    button.addEventListener('click', function () {
      document.querySelectorAll('[data-broker]').forEach(function (other) { other.setAttribute('aria-pressed', String(other === button)); });
      var example = examples[button.dataset.broker];
      document.getElementById('demo-topic').textContent = example.topic;
      document.getElementById('demo-key').textContent = example.key + ' ↗';
      document.getElementById('demo-meta').textContent = example.meta;
      document.getElementById('demo-payload').textContent = JSON.stringify(example.payload, null, 2);
      document.querySelectorAll('.message-row span:last-child:not(#demo-key)').forEach(function (key, index) { key.textContent = example.key.slice(0, -2) + String(80 - index); });
      if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        var workspace = document.querySelector('.console-work');
        if (workspace.animate) {
          workspace.getAnimations().forEach(function (animation) { animation.cancel(); });
          workspace.animate([{ opacity: 0.5, transform: 'translateY(3px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: 280, easing: 'cubic-bezier(.22,1,.36,1)' });
        }
      }
    });
  });
  var copy = document.querySelector('.copy-command');
  copy.addEventListener('click', async function () {
    try {
      await navigator.clipboard.writeText(document.getElementById('install-command').textContent);
      copy.textContent = 'Copied';
      setTimeout(function () { copy.textContent = 'Copy'; }, 2000);
    } catch (error) {
      copy.textContent = 'Select command';
      var range = document.createRange();
      range.selectNodeContents(document.getElementById('install-command'));
      window.getSelection().removeAllRanges();
      window.getSelection().addRange(range);
    }
  });
})();
