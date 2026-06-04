# Publish to GitHub and host the docs site

The repo is ready for GitHub Pages from the **`/docs`** folder (`docs/.nojekyll` is included).

## 1. Create the GitHub repository

1. Open [https://github.com/new](https://github.com/new)
2. Repository name: **`eventore`** (or your choice — the Pages URL uses this name)
3. **Do not** add a README, `.gitignore`, or license (this repo already has them)
4. Create the repository

## 2. Push from your machine

Repository: **https://github.com/vijayptiwari/eventore**

```powershell
cd "C:\Users\Vijay Prakash Tiwari\OneDrive\Documents\Codebase\eventore"
git remote add origin https://github.com/vijayptiwari/eventore.git
git push -u origin main
```

## 3. Enable GitHub Pages

**Option A — GitHub Actions (recommended, already in repo)**

1. **Settings → Pages → Build and deployment → Source:** **GitHub Actions**
2. Push to `main` runs `.github/workflows/docs-pages.yml` automatically

**Option B — Deploy from branch**

1. **Settings → Pages → Source:** Deploy from a branch
2. **Branch:** `main`, **Folder:** `/docs`

Live site (after deploy):

```text
https://vijayptiwari.github.io/eventore/
```

## 4. Verify

- Home: `https://YOUR_GITHUB_USERNAME.github.io/eventore/`
- Guide: `https://YOUR_GITHUB_USERNAME.github.io/eventore/guide/`
- Deployment: `https://YOUR_GITHUB_USERNAME.github.io/eventore/guide/deployment.html`

## Optional: custom domain

Under **Pages → Custom domain**, add your domain and configure DNS per GitHub’s instructions.

## Updates

After doc changes:

```powershell
# Optional locally — CI also runs this before deploy
node docs/scripts/inject-seo.mjs

git add docs/
git commit -m "Update documentation"
git push
```

Pages redeploys automatically on push to `main`.

## SEO checklist (one-time)

1. [Google Search Console](https://search.google.com/search-console) → add `https://vijayptiwari.github.io/eventore/`
2. Submit sitemap: `https://vijayptiwari.github.io/eventore/sitemap.xml`
3. Test a URL with [LinkedIn Post Inspector](https://www.linkedin.com/post-inspector/) after deploy
