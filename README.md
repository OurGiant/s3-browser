# S3 Browser

[![Build](https://github.com/OurGiant/s3-browser/actions/workflows/build.yml/badge.svg)](https://github.com/OurGiant/s3-browser/actions/workflows/build.yml)
[![Latest Release](https://img.shields.io/github/v/release/OurGiant/s3-browser?label=Release)](https://github.com/OurGiant/s3-browser/releases/latest)
[![License: MIT](https://img.shields.io/github/license/OurGiant/s3-browser)](LICENSE)
[![Java 24](https://img.shields.io/badge/Java-24-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Platforms](https://img.shields.io/badge/platform-Linux%20%7C%20macOS%20%7C%20Windows-blue)](#build)

A Java Swing desktop application for browsing AWS S3 buckets and objects. Connects using local AWS profiles, in the same style as [dynamodb-client](https://github.com/OurGiant/dynamodb-client) and [lambda-inspector](https://github.com/OurGiant/lambda-inspector): an active-profile-aware connection dialog rather than a config file to hand-edit.

## Features

- **Active-profile-aware connection dialog**: Select an AWS profile and region, with a Test Connection check against STS showing Active/Inactive status and the resolved account ID
- **Bucket list**: Loads the connected profile's entire bucket list in the background (paging internally, not user-facing pagination), with a live search field that filters by substring across every loaded bucket — not just a visible page — plus a Refresh action. Double-click (or select + Open) to browse
- **Prefix/folder-style object browsing**: Drills into a bucket like a file manager — folders are S3 "common prefixes," not real objects, so navigating never lists more of the bucket than the current level. A clickable breadcrumb (bucket root plus every path segment) jumps back to any ancestor in one click; paginated via Load More for buckets with more than 1000 keys at a level
- **Object metadata detail**: Double-click an object (or select + View Details) for size/storage class/last-modified (already known from the listing) plus content-type, encryption (including the KMS key ID for SSE-KMS objects), version ID, and user metadata — loaded in the background via a separate `HeadObject` call, since `ListObjectsV2` doesn't carry those
- **Upload**: Pick a local file and upload it into the currently browsed bucket/prefix, with the destination key defaulted to the current prefix + filename (editable). Since S3 has no versioning by default, uploading to an existing key would silently overwrite it — so before ever calling `PutObject`, the destination is checked with `HeadObject` and an explicit overwrite confirmation is required if something's already there. A simple indeterminate progress indicator shows during the upload, not a byte-level progress bar
- **Copy ARN / Copy S3 URL**: One-click clipboard copy for both buckets (from the object browser's top bar) and individual objects (from the detail view) — the ARN (`arn:aws:s3:::bucket[/key]`) and the `s3://bucket/key` URI, for pasting into IaC, CLI commands, or other tooling
- **Persistent settings**: Remembers the last-used AWS profile and region between sessions
- **FlatLaf theming**: Switchable Light/Dark/IntelliJ themes via the View menu
- **Help > About**: App version, copyright, and a manual/silent (non-blocking) check for newer GitHub releases

## Scope

This is primarily a **browse** tool, mirroring dynamodb-client's and lambda-inspector's "browse, don't mutate destructively" ethos. The one mutating action is uploading a file (see Features above), which always confirms before overwriting an existing object. No delete, no bucket mutation, no ACL/policy changes, and no batch/folder upload — single file at a time. If scope grows beyond that, it should get the same explicit confirmation-dialog treatment the upload-overwrite check already has, not be added silently.

## Prerequisites

- Java 24 or higher
- AWS credentials configured at `~/.aws/credentials`
- Network access to AWS S3

## IAM Permissions

The connected AWS profile needs at least the following actions:

- `sts:GetCallerIdentity` — verifying a profile's credentials are active and showing its account ID in the window title
- `s3:ListAllMyBuckets` — the bucket list
- `s3:ListBucket` — object/prefix browsing within a bucket
- `s3:GetObject` — technically covers `HeadObject` too, but `HeadObject` is what's actually called for the object metadata detail view and the upload dialog's overwrite check; a policy scoped to `s3:GetObject` covers it
- `s3:PutObject` — upload; this is the app's only mutating AWS action (see [Scope](#scope))

This list will grow if the [Scope](#scope) below ever changes; each addition is documented here alongside the code, not just implemented silently (see `.claude/skills/ship-issue/SKILL.md`).

## Build

```bash
mvn clean package
```

Produces `target/s3-browser-all.jar`.

## Run

```bash
java -jar target/s3-browser-all.jar
```

On launch, a connection dialog prompts for an AWS profile and region. These values are saved for subsequent runs.

## Project Structure

```
src/main/java/com/ourgiant/s3/browser/
├── Main.java               # Entry point
├── AppPreferences.java     # java.util.prefs wrapper (last profile/region, update-notified version)
├── ThemeManager.java       # FlatLaf theme selection
├── model/                  # Plain data types (ProfileActivity, BucketSummary, S3Entry, ObjectDetail)
├── core/                   # Swing-free domain logic (AWS profile/region resolution, connection
│                           # messages, ListBuckets/ListObjectsV2/HeadObject/PutObject request
│                           # building and response mapping, byte-size formatting, default
│                           # upload-key derivation) - no javax.swing.* dependency
├── gui/                    # MainWindow, AboutDialog, BucketListPanel, ObjectBrowserPanel,
│                           # ObjectDetailDialog, UploadDialog, and all Swing wiring - depends
│                           # one-way on core/model
└── util/                   # Shared helpers with no business meaning of their own
                            # (AppVersion, UpdateChecker, HttpClientFactory, NetworkFetchException)
```

## Dependencies

- **AWS SDK for Java v2**: S3 client, authentication, region resolution
- **FlatLaf** (+ intellij-themes, extras): application theming
- **SLF4J + Logback**: logging
- **Jackson (jackson-databind)**: parsing the GitHub releases API response for the About dialog's update check
- **JUnit 5 + Mockito**: testing

## License

See LICENSE file for details.
