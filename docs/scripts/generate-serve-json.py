"""Regenerate docs/serve.json redirects from docs/seo/pages.json."""
import json
from pathlib import Path

root = Path(__file__).resolve().parent.parent
pages = json.loads((root / "seo" / "pages.json").read_text(encoding="utf-8"))["pages"]

redirects = [{"source": "/guide", "destination": "/guide/index.html", "type": 301}]

for p in pages:
    path = p["path"]
    if not path.endswith(".html"):
        continue
    if path.startswith("guide/"):
        slug = path[len("guide/") : -len(".html")]
        if slug == "index":
            continue
        redirects.append({"source": "/guide/" + slug, "destination": "/guide/" + slug + ".html", "type": 301})
        redirects.append({"source": "/guide/" + slug + "/", "destination": "/guide/" + slug + ".html", "type": 301})
    elif path == "about.html":
        redirects.append({"source": "/about", "destination": "/about.html", "type": 301})
        redirects.append({"source": "/about/", "destination": "/about.html", "type": 301})

redirects.append(
    {"source": "/guide/developers", "destination": "/about.html#developer", "type": 301}
)
redirects.append(
    {"source": "/guide/developers/", "destination": "/about.html#developer", "type": 301}
)
redirects.append(
    {"source": "/guide/contributing", "destination": "/about.html#contribute", "type": 301}
)
redirects.append(
    {"source": "/guide/contributing/", "destination": "/about.html#contribute", "type": 301}
)
redirects.append(
    {"source": "/guide/inspection", "destination": "/guide/streaming.html", "type": 301}
)
redirects.append(
    {"source": "/guide/inspection/", "destination": "/guide/streaming.html", "type": 301}
)

for slug in [
    "kafka",
    "pulsar",
    "rabbitmq",
    "mqtt",
    "jms",
    "kinesis",
    "gcp-pubsub",
    "azure-service-bus",
]:
    redirects.append({"source": "/" + slug, "destination": "/guide/" + slug + ".html", "type": 301})

serve = {
    "cleanUrls": False,
    "directoryListing": False,
    "rewrites": [{"source": "/", "destination": "/index.html"}],
    "redirects": redirects,
}
(root / "serve.json").write_text(json.dumps(serve, indent=2) + "\n", encoding="utf-8")
print(len(redirects), "redirects written")
