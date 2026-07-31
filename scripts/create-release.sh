#!/usr/bin/env bash
# Interactive helper to cut a new figmacompare release.
#
# A GitHub Release publish (via .github/workflows/publish.yml) is what actually
# triggers publishing io.eot:figmacompare to GitHub Packages - see the "Publishing a
# release" section in README.md. This script just prompts for the info gh needs and
# runs `gh release create`; it does no publishing itself.
set -euo pipefail

REPO="anandbagmar/figmacompare"

if ! command -v gh &> /dev/null; then
    echo "Error: gh CLI is not installed. See https://cli.github.com/" >&2
    exit 1
fi

if ! gh auth status &> /dev/null; then
    echo "Error: gh CLI is not authenticated. Run 'gh auth login' first." >&2
    exit 1
fi

read -rp "Version to release (e.g. 1.2.0, no 'v' prefix): " VERSION
if [[ -z "$VERSION" ]]; then
    echo "Error: version is required." >&2
    exit 1
fi
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Error: version must be in x.y.z format (got '$VERSION')." >&2
    exit 1
fi

TAG="v${VERSION}"

if gh release view "$TAG" --repo "$REPO" &> /dev/null; then
    echo "Error: release '$TAG' already exists in $REPO." >&2
    exit 1
fi

read -rp "Release title [default: $TAG]: " TITLE
TITLE="${TITLE:-$TAG}"

echo "Release notes (what changed in this version). End with an empty line:"
NOTES=""
while IFS= read -r line; do
    [[ -z "$line" ]] && break
    NOTES="${NOTES}${line}"$'\n'
done
if [[ -z "$NOTES" ]]; then
    echo "Error: release notes are required." >&2
    exit 1
fi

echo
echo "About to create a release:"
echo "  Repo:  $REPO"
echo "  Tag:   $TAG"
echo "  Title: $TITLE"
echo "  Notes:"
echo "$NOTES" | sed 's/^/    /'
read -rp "Proceed? [y/N]: " CONFIRM
if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
    echo "Aborted - no release created."
    exit 1
fi

gh release create "$TAG" --repo "$REPO" --title "$TITLE" --notes "$NOTES"

echo
echo "Release $TAG created. This triggers the 'Publish to GitHub Packages' workflow - watch it with:"
echo "  gh run list --repo $REPO --workflow \"Publish to GitHub Packages\" --limit 1"
