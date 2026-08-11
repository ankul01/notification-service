---
name: ship-pr
description: >-
  Commit current work on a new feature branch and open a pull request into main.
  Use when the user runs /ship-pr or asks to commit via a feature branch and raise
  a PR to main.
disable-model-invocation: true
---

# Ship PR

Commit local changes on a **new** feature branch and open a PR targeting `main`.

## Preconditions

- Abort if there is nothing to commit (clean tree and no untracked files worth shipping).
- Never update git config.
- Never force-push to `main` / `master`.
- Never use `--no-verify` or interactive git flags (`-i`).
- Do not commit secrets (`.env`, credentials, keys, `application-*-local/secret/prod` files). Warn and skip them.
- Prefer `gh` for GitHub operations; configure `gh auth setup-git` if HTTPS push fails auth.

## Workflow

Run from the git repository root that owns the changes (detect with `git rev-parse --show-toplevel`).

### 1. Inspect state (parallel)

```bash
git status -sb
git diff && git diff --cached
git log --oneline -8
git branch -vv
git remote -v
git fetch origin main
```

Note current branch. If already on a dirty feature branch that should not be reused, still create a **new** branch from updated `main` (see step 2).

### 2. New feature branch from main

```bash
# Preserve WIP
git stash push -u -m "ship-pr-wip"

git checkout main
git pull origin main

# Branch name: feature/<short-kebab-from-diff>
# Prefer user-provided name; otherwise derive from the primary change
# (e.g. feature/maven-security-hardening, feature/t0.3-request-errors).
git checkout -b feature/<name>

git stash pop
```

If stash pop conflicts, stop and ask the user how to resolve.

If the repo was already based on up-to-date `main` and the user is on `main` with only local WIP, creating `feature/<name>` directly from `main` without stash is fine.

### 3. Stage and commit

1. Stage relevant files only (`git add` paths explicitly when possible).
2. Draft a concise commit message (1–2 sentences, why over what) matching recent `git log` style.
3. Commit with a HEREDOC (use `git -c user.name=… -c user.email=…` only if commit identity is missing — never write git config):

```bash
git commit -m "$(cat <<'EOF'
Commit title.

Optional body clarifying why.
EOF
)"
```

4. Run `git status` after commit. If a hook fails, fix and create a **new** commit (do not amend unless the user explicitly asks and amend rules allow it).

### 4. Push and open PR

```bash
git push -u origin HEAD

gh pr create --base main --title "<title>" --body "$(cat <<'EOF'
## Summary
- <1-3 bullets of what changed and why>

## Test plan
- [ ] <commands / checks reviewers should run>

EOF
)"
```

### 5. Report back

Reply with:
- Feature branch name
- Commit subject / short SHA
- PR URL (`gh pr view --json url -q .url`)

## Branch naming

| Situation | Example |
| --- | --- |
| Task id known | `feature/t0.3-request-error-contracts` |
| Theme known | `feature/maven-spring-boot-security` |
| User gave a name | Use it verbatim (kebab-case it if needed) |

## Titles

- PR title: imperative, specific (`Add Maven SHA pin and CI CVE gates`)
- Commit subject: same voice; keep under ~72 chars when practical
