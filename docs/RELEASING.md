# Versioned releases

`VERSION` is the single product version. Run `node scripts/sync-version.mjs` after changing it, then `node scripts/sync-version.mjs --check`. This keeps Maven parent/module versions, npm manifests and lockfiles, chart versions, API metadata, MCP identity, and website preparation metadata aligned.

## Release procedure

1. Update VERSION, synchronize manifests, and update `docs/product/RELEASE-NOTES.md`.
2. Commit and push to main. Resolve every required quality-gate failure.
3. Create an annotated `v<VERSION>` tag at the verified commit and push that tag. Never move an already published release tag or reuse a release version for different contents.
4. The tag pipeline runs unit, broker integration, mocked/live browser, MCP, and Helm checks, and verifies the provider libraries and administration delegates in all ten packaged backend bundles. Backend images use `<version>-<provider>` tags, preventing matrix variants from overwriting each other. Frontend and MCP use `<version>`.
5. Only after all image and chart publishing succeeds does the pipeline create the GitHub Release with chart archives and checksums. Failed pipelines do not mark the website ready for adoption. Before retrying a failed release, inspect partially published artifacts; do not silently replace a completed release.
6. The documentation workflow follows successful artifact workflows. It retrieves the latest non-draft, non-prerelease GitHub Release, checks its chart/checksum assets, and rebuilds the website with that version and release date. If no release exists, the site retains preparation status.

The website sync can also be run locally with `node docs/scripts/sync-release.mjs`, followed by `build-product.mjs` and `inject-seo.mjs`. Commit the resulting verified metadata when updating the repository's static copy. `GH_TOKEN` is optional for public repository reads.

Normal main pushes validate but do not publish artifacts. Manual workflow dispatch can publish development images under Git-SHA tags and charts under `<VERSION>-dev.<run-number>`. Development charts point to matching SHA images. They never overwrite the stable chart version.

Helm defaults derive image tags from Chart.appVersion; explicit image overrides remain available. Use one backend replica unless the selected connection store and subscription routing have been designed for multiple replicas. A release is not an assertion of feature parity or production suitability for every broker environment.
