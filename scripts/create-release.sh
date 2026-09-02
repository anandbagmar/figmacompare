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

LATEST_TAG=$(gh release view --repo "$REPO" --json tagName -q .tagName 2>/dev/null || true)

if [[ -z "$LATEST_TAG" ]]; then
    echo "No existing releases found in $REPO - this will be the first one."
    SUGGESTED_VERSION="0.1.0"
elif [[ "${LATEST_TAG#v}" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "Latest release: $LATEST_TAG"
    SUGGESTED_VERSION="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.$((BASH_REMATCH[3] + 1))"
else
    echo "Latest release: $LATEST_TAG (not x.y.z, can't suggest the next version)"
    SUGGESTED_VERSION=""
fi

if [[ -n "$SUGGESTED_VERSION" ]]; then
    read -rp "Version to release [default: $SUGGESTED_VERSION, next patch]: " VERSION
    VERSION="${VERSION:-$SUGGESTED_VERSION}"
else
    read -rp "Version to release (e.g. 1.2.0, no 'v' prefix): " VERSION
fi
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

echo "Release notes (what changed in this version) - multi-paragraph/markdown is fine,"
echo "blank lines included. Paste the full text, then end input with Ctrl-D on its own line:"
NOTES="$(cat)"
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
