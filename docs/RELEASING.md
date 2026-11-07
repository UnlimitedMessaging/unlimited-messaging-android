# Releasing

The app is distributed as a signed APK on GitHub Releases (and linked from
the website), not on Google Play: Play only allows `SEND_SMS` and
`RECEIVE_SMS` for default SMS apps and a short list of exceptions.

## How a release happens

**Every push to `main` is a release.** `.github/workflows/release.yml`:

1. runs lint and the unit tests;
2. builds the release APK (minified with R8), signed with the release key,
   with `versionCode` = the workflow run number and `versionName` =
   `0.1.<run number>`, so each release installs over the previous one;
3. checks the signature with `apksigner`;
4. publishes a GitHub release tagged `v0.1.<run number>`, with three assets:
   - `unlimited-messaging-v0.1.<n>.apk`, the versioned APK;
   - its `.sha256`;
   - `unlimited-messaging.apk`, the same file under a stable name, so
     `releases/latest/download/unlimited-messaging.apk` always points at the
     newest version (the website and the README link there);
5. posts the result to Discord, and posts there too if anything fails.

Release notes are generated from the merged pull requests and commits since
the previous release: write commit messages and PR titles for users
(Conventional Commits, see CONTRIBUTING.md).

Pull requests and other branches run `.github/workflows/ci.yml` (lint, tests,
debug build, the debug APK as a workflow artifact). Only `main` releases.

To publish a version without a code change (for example after rotating a
secret), run the Release workflow by hand from the Actions tab.

The version base (`0.1`) lives in `release.yml` (`VERSION_BASE`). Raise it for
a milestone; the run number keeps `versionCode` increasing either way.

## Repository secrets

| Secret | Content |
| --- | --- |
| `UM_ANDROID_KEYSTORE_BASE64` | The release keystore, base64 (`base64 -i um-release.jks`). |
| `UM_ANDROID_KEYSTORE_PASSWORD` | Keystore password. |
| `UM_ANDROID_KEY_ALIAS` | `um-release` |
| `UM_ANDROID_KEY_PASSWORD` | Key password (the same as the keystore's, PKCS12). |
| `DISCORD_WEBHOOK` | Discord webhook URL for release and failure notifications. Optional: without it, notifications are skipped. |

## The signing key

One key signs every release, forever: Android refuses to update an app
signed with a different key, so losing it means every user has to uninstall
and reinstall. The maintainers keep the keystore and its password in a
password manager plus an offline copy; it is never committed (`*.jks` is
git-ignored).

Its certificate fingerprint is published in the README so users can verify
what they install. If the key ever has to change, say so loudly in the
release notes: users must uninstall the old app first.

For reference, it was created with:

```sh
keytool -genkeypair -v \
  -keystore um-release.jks -storetype PKCS12 \
  -alias um-release \
  -keyalg RSA -keysize 4096 -validity 10000
```

## Building a signed release locally

Create `keystore.properties` at the repository root (git-ignored):

```properties
storeFile=/absolute/path/to/um-release.jks
storePassword=...
keyAlias=um-release
keyPassword=...
```

Then:

```sh
./gradlew assembleRelease -Pum.versionCode=1000 -Pum.versionName=0.1.0-local
# app/build/outputs/apk/release/app-release.apk
```

Without `keystore.properties` (or the `UM_ANDROID_*` environment variables
CI sets), the release build still runs and produces an unsigned APK, which
Android will not install.

## Before merging to main

The release build talks to `https://api.unlimitedmessaging.app`. Check that
production runs an API version implementing the [contract](API_CONTRACT.md)
this version of the app expects.
