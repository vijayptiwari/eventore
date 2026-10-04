# Maintaining product discoverability

## Search ownership and indexing

Use the Google Search Console URL-prefix property `https://vijayptiwari.github.io/eventore/`. Preserve any existing ownership verification. If HTML-tag verification is needed, add the exact public `google-site-verification` name/content pair to `siteVerification` in `docs/seo/site.json`, then regenerate and deploy the SEO metadata.

Submit `https://vijayptiwari.github.io/eventore/sitemap.xml` in that property's Sitemaps section. Inspect the homepage, product tour, and Kafka-to-RabbitMQ tutorial; request indexing after the published pages are reachable. Submission does not guarantee indexing or ranking.

The project is hosted under `/eventore/`. Its nested `robots.txt` does not control the host: crawlers look for `https://vijayptiwari.github.io/robots.txt`. Submit the sitemap directly instead of assuming the project's robots file is effective. Do not change the user-site repository or migrate domains without a separate migration plan.

References: [Google sitemap guidance](https://developers.google.com/search/docs/crawling-indexing/sitemaps/build-sitemap), [robots.txt location and scope](https://developers.google.com/search/docs/crawling-indexing/robots/create-robots-txt).

## Measurement

Record a baseline when Search Console access is available: last 28 days of clicks, impressions, click-through rate, top queries, and indexed pages. Review the same measures after the next 28 days. Separate branded searches for EventOre from problem-oriented searches. Search Console data is unavailable until ownership/access is confirmed; do not report fabricated baselines.

For adoption, review GitHub traffic/referrers and release asset downloads alongside visits to quickstart and lab pages. GitHub asset downloads are not equivalent to successful installations and exclude container pulls. Website analytics is not enabled by this change: connect the owner's chosen property before reporting website conversion rates.

## Content and distribution

Current entry points:

- [Product tour](https://vijayptiwari.github.io/eventore/guide/product-tour.html)
- [Kafka to RabbitMQ lab](https://vijayptiwari.github.io/eventore/guide/kafka-rabbitmq-tutorial.html)
- [MQTT lab](https://vijayptiwari.github.io/eventore/guide/examples.html)
- [Release notes](https://vijayptiwari.github.io/eventore/releases.html)

When publishing a new guide, register it in `docs/seo/pages.json`, link it from an existing relevant page, and regenerate SEO metadata. Use a distinct, descriptive title and a complete example. The cross-broker lab has a CI workflow that validates the published-image configuration and real message delivery.

Draft community introduction (review and adapt to each community's posting rules; not posted automatically):

> I build EventOre, a self-hosted console for Kafka, RabbitMQ, and six other messaging protocols. I wrote a local walkthrough that follows an order from a Kafka topic into a RabbitMQ queue, including filtering, transformed headers, delivery checks, and failure diagnosis. It uses versioned container images. The guide also explains the current process-local bridge state and delivery limitations. I'd welcome feedback on whether the walkthrough makes this workflow easier to evaluate.
>
> https://vijayptiwari.github.io/eventore/guide/kafka-rabbitmq-tutorial.html

A recorded demo can follow the four steps in the product tour. Record the actual local lab and show the received message; do not present sample UI data as a live deployment.
