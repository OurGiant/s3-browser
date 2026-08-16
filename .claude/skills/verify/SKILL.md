---
name: verify
description: How to build, launch, and drive S3 Browser to verify a Swing UI change actually works on this dev setup. Use before trusting the generic verify-java-swing skill's screenshot step; read that skill too for the underlying techniques (modal-dialog deadlock, synthetic MouseEvent dispatch, process safety).
---

# Verifying S3 Browser

This is the project-specific companion to the generic `verify-java-swing`
skill (techniques) and `java-swing-project-setup` (build/structure
standard this project follows). Read those first — this file is what to
actually type for *this* project.

## Build here, run there

Maven only exists in the Docker container, not on the host:

```bash
docker exec festive_bardeen bash -c "cd /projects/OHI/s3-browser && mvn -q package -DskipTests"
```

If `festive_bardeen` doesn't respond, find the current container:
`docker ps -a --format '{{.Names}} {{.Status}} {{.Image}}'` and
`docker start <name>` if stopped — the name can drift across sessions.
Note the in-container path is `/projects/OHI/s3-browser`, not
`/projects/s3-browser` — the bind-mount root is `~/projects` and OHI is a
subdirectory of it (this tripped up lambda-inspector's scaffolding pass
too, see its own verify skill).

`/projects` is bind-mounted from the host's `~/projects`, so the jar lands
at `target/s3-browser-all.jar`, visible on the host. The container is
headless (no `DISPLAY`) — run the jar on the **host**, not inside the
container, or it dies at `JFrame` construction with `HeadlessException`.

```bash
java -jar target/s3-browser-all.jar
```

Main class: `com.ourgiant.s3.browser.Main`.

## First-run state

AWS profile/region and the last-notified update version are stored via
`java.util.prefs` at `/com/ourgiant/s3/browser/gui` (see `AppPreferences`).
Tests never touch `~/.aws` directly — `AwsProfiles` reads from the
directory named by the `s3.browser.awsConfigDir` system property when
set (surefire sets it to `target/test-aws-config`; see `pom.xml`).

## Component lookup gotcha: editable JComboBox contains its own JTextField

Same gotcha lambda-inspector's verify skill documents, since this
project's connection dialog is the same shape: the profile field is an
editable `JComboBox<String>` whose live editor component is itself a
`JTextField`, added as a child of the combo box. A naive "find the Nth
`JTextField` in the dialog" reflection walk will find the combo's
internal editor before the standalone region `JTextField` sibling,
silently writing into the wrong field. Skip recursing into `JComboBox`
internals when searching for any other component type.

## Nothing else confirmed yet

No project-specific screenshot/rendering gotchas have been confirmed on
this dev host for *this* project yet — lambda-inspector confirmed
`Robot.createScreenCapture(...)` works cleanly on this host's DISPLAY
`:1` (a real X11 session, not a Wayland sandbox), which is a reasonable
first thing to try here too, but re-check rather than assuming — record
what's actually confirmed here once it's been checked.
