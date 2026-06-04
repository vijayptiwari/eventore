import re
from pathlib import Path

bootstrap_src = (Path(__file__).resolve().parent.parent / "js" / "guide-bootstrap.js").read_text(
    encoding="utf-8"
)
# Drop block comment; keep one inline script for trailing-slash URLs.
body = re.sub(r"/\*\*[\s\S]*?\*/\s*", "", bootstrap_src).strip()
INLINE = "  <script>\n" + body + "\n  </script>\n"

HEAD_LOADER = re.compile(
    r'  <script src="\.\./js/(?:base-path|guide-init|guide-bootstrap)\.js"></script>\n'
)

root = Path(__file__).resolve().parent.parent / "guide"
for f in sorted(root.glob("*.html")):
    if f.name == "inspection.html":
        continue
    t = f.read_text(encoding="utf-8")
    if "loadScript('js/base-path.js'" not in t:
        if HEAD_LOADER.search(t):
            t = HEAD_LOADER.sub("", t)
        t = t.replace("  <!-- /eventore-seo -->\n", "  <!-- /eventore-seo -->\n" + INLINE, 1)
    t = t.replace('  <link rel="stylesheet" href="../css/site.css"/>\n', "")
    t = t.replace('  <script src="../js/site.js"></script>\n', "")
    f.write_text(t, encoding="utf-8")
    print("patched", f.name)
