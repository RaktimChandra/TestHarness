"""Builds the published site around the dashboard: a landing page and the blog rendered to HTML.

usage: build_site.py <repo root> <site dir> <runs in history window>

The dashboard (site/dashboard) and Allure report (site/allure) are already in place; this adds
site/index.html and site/blog/<post>.html. Relative links in the blog are rewritten to the
repository on GitHub so they keep working off-repo.
"""
import datetime
import html
import os
import re
import sys

import markdown

root, site, runs = sys.argv[1], sys.argv[2], sys.argv[3]
repo = os.environ.get("GITHUB_REPOSITORY", "RaktimChandra/TestHarness")
sha = os.environ.get("GITHUB_SHA", "")[:7]
run_id = os.environ.get("GITHUB_RUN_ID", "")
blob = f"https://github.com/{repo}/blob/main/"

STYLE = """
:root{--bg:#fcfcfb;--fg:#1a1a19;--muted:#5c5c58;--line:#e4e4e0;--card:#ffffff;--accent:#2a78d6}
@media (prefers-color-scheme:dark){:root{--bg:#1a1a19;--fg:#ededea;--muted:#a6a6a0;--line:#3a3a37;--card:#232322;--accent:#3987e5}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);
font:16px/1.6 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:760px;margin:0 auto;padding:40px 16px 64px}a{color:var(--accent)}
h1{font-size:1.9rem;line-height:1.25;margin:0 0 8px}h2{margin-top:2em}.muted{color:var(--muted)}
.cards{display:grid;gap:12px;margin:28px 0}.card{display:block;padding:16px 18px;border:1px solid var(--line);
border-radius:10px;background:var(--card);text-decoration:none;color:inherit}.card:hover{border-color:var(--accent)}
.card b{display:block;color:var(--accent)}table{border-collapse:collapse;width:100%;display:block;overflow-x:auto}
th,td{border-bottom:1px solid var(--line);padding:6px 8px;text-align:left;vertical-align:top}
code{font-size:.92em}pre{overflow-x:auto;padding:12px;border:1px solid var(--line);border-radius:8px}
footer{margin-top:40px;font-size:.9rem;color:var(--muted)}
"""


def page(title, body):
    return (f"<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
            f"<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>{html.escape(title)}</title>"
            f"<style>{STYLE}</style></head><body><main>{body}</main></body></html>")


now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
posts = []
blog_dir = os.path.join(root, "docs", "blog")
os.makedirs(os.path.join(site, "blog"), exist_ok=True)
for name in sorted(os.listdir(blog_dir)):
    if not name.endswith(".md"):
        continue
    text = open(os.path.join(blog_dir, name), encoding="utf-8").read()
    # links relative to docs/blog/ point into the repository
    text = re.sub(r"\]\((?!https?:|#|mailto:)(?:\.\./\.\./)?([^)]+)\)", lambda m: "](" + blob +
                  os.path.normpath(os.path.join("docs/blog", m.group(1))).replace(os.sep, "/") + ")"
                  if not m.group(0).startswith("](../../") else "](" + blob + m.group(1) + ")", text)
    title = next((l[2:].strip() for l in text.splitlines() if l.startswith("# ")), name)
    body = markdown.markdown(text, extensions=["tables", "fenced_code"])
    out = name[:-3] + ".html"
    footer = f"<footer><a href=\"../\">← TestHarness quality site</a> · source: <a href=\"{blob}docs/blog/{name}\">{name}</a></footer>"
    open(os.path.join(site, "blog", out), "w", encoding="utf-8").write(page(title, body + footer))
    posts.append((title, "blog/" + out))

cards = [("Quality dashboard", "dashboard/", f"Pass rates, flakiness, coverage, mutation, accessibility and experiments, "
          f"built from the last {runs} pipeline run(s) on main.")]
if os.path.isfile(os.path.join(site, "allure", "index.html")):
    cards.append(("Allure report", "allure/", "Every test of the latest pipeline with steps, evidence and history."))
cards += [(t, href, "Blog post (draft).") for t, href in posts]
cards.append(("Source and results", f"https://github.com/{repo}", "README, RESULTS.md (every number with its run), DESIGN.md."))
body = ("<h1>TestHarness</h1><p class=\"muted\">Published by CI from <code>main</code> after every pipeline. "
        "Nothing here is edited by hand.</p><div class=\"cards\">"
        + "".join(f"<a class=\"card\" href=\"{h}\"><b>{html.escape(t)}</b>{html.escape(d)}</a>" for t, h, d in cards)
        + f"</div><footer>Built {now} from commit <code>{sha}</code>, run "
        f"<a href=\"https://github.com/{repo}/actions/runs/{run_id}\">{run_id}</a>.</footer>")
open(os.path.join(site, "index.html"), "w", encoding="utf-8").write(page("TestHarness quality site", body))
print(f"site: index + {len(posts)} post(s)")
