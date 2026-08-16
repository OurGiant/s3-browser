---
name: ship-issue
description: The standard workflow for shipping a bug fix or feature to S3 Browser — file a GitHub issue, branch off main, implement, verify, bump the patch version, and open a PR. Use whenever picking up a bug fix or feature for this repo.
---

# Shipping a change to S3 Browser

Follow `java-swing-ship-issue` (the generic workflow shared across the
Java Swing project family) with these S3 Browser specifics:

- **Project path**: `/projects/OHI/s3-browser` inside the build container.
- **Verify**: use this repo's own `.claude/skills/verify/SKILL.md` for
  build/launch mechanics.
- **AWS-call surfaces need extra care**: any change to `core/` that adds
  a new AWS API call (S3, STS) should note the new IAM permission it
  requires in README's IAM Permissions section (see dynamodb-client's or
  lambda-inspector's for the pattern) — not just implement it silently.
- **Destructive/mutating actions get a confirmation dialog**: this
  project's only mutating AWS action is upload (`PutObject`), and even
  that always checks the destination with `HeadObject` first and
  requires explicit confirmation before overwriting an existing object
  (see `gui/UploadDialog.java`) — mirroring dynamodb-client's
  delete-confirmation caution. Any *further* mutating S3 action (delete,
  batch/folder upload, changing a bucket policy) is a scope question to
  raise, not a routine feature add — see README Scope.
- No repo-specific branch-naming or extra PR-checklist step beyond the
  generic workflow has been established here yet; follow
  `java-swing-ship-issue` as-is until one is.
