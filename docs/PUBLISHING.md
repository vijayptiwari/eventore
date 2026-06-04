# Publish to GitHub and host the docs site

The repo is ready for GitHub Pages from the **`/docs`** folder (`docs/.nojekyll` is included).

## 1. Create the GitHub repository

1. Open [https://github.com/new](https://github.com/new)
2. Repository name: **`eventore`** (or your choice — the Pages URL uses this name)
3. **Do not** add a README, `.gitignore`, or license (this repo already has them)
4. Create the repository

## 2. Push from your machine

Replace `YOUR_GITHUB_USERNAME` with your GitHub username:

```powershell
cd "C:\Users\Vijay Prakash Tiwari\OneDrive\Documents\Codebase\eventore"
git remote add origin https://github.com/YOUR_GITHUB_USERNAME/eventore.git
git push -u origin main
```

Use SSH if you prefer:

```powershell
git remote add origin git@github.com:YOUR_GITHUB_USERNAME/eventore.git
git push -u origin main
```

If `origin` already exists, update the URL:

```powershell
git remote set-url origin https://github.com/YOUR_GITHUB_USERNAME/eventore.git
git push -u origin main
```

## 3. Enable GitHub Pages

1. On GitHub: **Repository → Settings → Pages**
2. **Build and deployment → Source:** Deploy from a branch
3. **Branch:** `main`
4. **Folder:** `/docs`
5. Click **Save**

After one or two minutes, the site is live at:

```text
https://YOUR_GITHUB_USERNAME.github.io/eventore/
```

(If the repo name is not `eventore`, use that name in the URL instead.)

## 4. Verify

- Home: `https://YOUR_GITHUB_USERNAME.github.io/eventore/`
- Guide: `https://YOUR_GITHUB_USERNAME.github.io/eventore/guide/`
- Deployment: `https://YOUR_GITHUB_USERNAME.github.io/eventore/guide/deployment.html`

## Optional: custom domain

Under **Pages → Custom domain**, add your domain and configure DNS per GitHub’s instructions.

## Updates

After doc changes:

```powershell
git add docs/
git commit -m "Update documentation"
git push
```

Pages redeploys automatically on push to `main`.
