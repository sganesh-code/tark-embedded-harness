# Publishing to Maven Central

This project publishes under the namespace `io.github.sganesh-code`, which is verified
automatically by Sonatype through the `sganesh-code` GitHub account — no domain ownership
needed. The Java package (`com.tark.harness`) is unrelated to the Maven coordinates and doesn't
need to change.

Publishing is done with the [vanniktech `maven-publish` plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/),
configured in `build.gradle.kts`. It handles the sources/javadoc jars, POM metadata, GPG signing,
and uploading to the [Central Portal](https://central.sonatype.com/) publisher API.

## One-time setup

### 1. Central Portal account token

1. Log in to https://central.sonatype.com/ with the account that owns the `io.github.sganesh-code`
   namespace.
2. Go to **Account → Generate User Token**. This gives you a username/password pair (not your
   login credentials — a scoped token).

### 2. GPG signing key

Maven Central requires every published artifact to be GPG-signed. If you don't already have a key
dedicated to this:

```bash
gpg --full-generate-key                      # RSA, 4096-bit, no expiry (or your preference)
gpg --list-secret-keys --keyid-format SHORT  # note the short (8 hex char) key ID
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>   # publish so Central can verify signatures
gpg --export-secret-keys --armor <KEY_ID> > private-key.asc
```

You'll need the ASCII-armored private key contents, the key ID, and the key's passphrase.

> The Gradle signing plugin's in-memory signer wants the **short 8-character key ID** (e.g.
> `435D9E2E`), not the long fingerprint — a long-form ID fails with "The key ID must be in a
> valid form" even though it looks more precise.

## Publishing locally

Add to `~/.gradle/gradle.properties` (**not** to the project — never commit these):

```properties
mavenCentralUsername=<token username>
mavenCentralPassword=<token password>

signing.keyId=<last 8 chars of the key ID>
signing.password=<key passphrase>
signing.secretKeyRingFile=/absolute/path/to/private-key.asc
```

Then, with the version in `build.gradle.kts` set to a release version (no `-SNAPSHOT`):

```bash
./gradlew publishToMavenCentral        # uploads the release, does not publish it
./gradlew publishAndReleaseToMavenCentral   # uploads AND publishes it — irreversible
```

After `publishToMavenCentral`, go to the [Central Portal deployments page](https://central.sonatype.com/publishing/deployments)
to inspect and manually press **Publish**. Releases are immutable once published, so this manual
checkpoint is intentional.

## Publishing via CI

`.github/workflows/publish.yml` runs on any tag matching `v*.*.*` (e.g. `v0.1.0`). It builds,
tests, and uploads to the Central Portal — it stops short of pressing "Publish" for the same
reason as above.

Add these as **repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal token username |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal token password |
| `GPG_SIGNING_KEY` | Full ASCII-armored private key (`private-key.asc` contents) |
| `GPG_SIGNING_KEY_ID` | The key ID |
| `GPG_SIGNING_KEY_PASSWORD` | The key passphrase |

You can set them from the CLI instead of the UI, e.g.:

```bash
gh secret set MAVEN_CENTRAL_USERNAME
gh secret set MAVEN_CENTRAL_PASSWORD
gh secret set GPG_SIGNING_KEY < private-key.asc
gh secret set GPG_SIGNING_KEY_ID
gh secret set GPG_SIGNING_KEY_PASSWORD
```

### Cutting a release

1. Bump `version` in `build.gradle.kts` to a release version (drop `-SNAPSHOT`).
2. Commit, then tag: `git tag v0.1.0 && git push origin v0.1.0`.
3. Watch the `Publish to Maven Central` workflow run.
4. Go to the Central Portal deployments page and press **Publish**.
5. Bump `version` back to the next `-SNAPSHOT` for continued development.

If you'd rather CI fully auto-release with no manual step, swap the workflow's final command from
`publishToMavenCentral` to `publishAndReleaseToMavenCentral` — just be aware there's no undo.
