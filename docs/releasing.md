# Releases and JetBrains Marketplace

Repository: [thirtyeighttwentysix/volan-idea](https://github.com/thirtyeighttwentysix/volan-idea).
Plugin ID: `io.github.thirtyeighttwentysix.volan.idea`.

## Configured automation

- **CI** tests and builds on Windows and Linux. A separate job runs Plugin Verifier.
- **Release** runs on `v*` tags, checks version/changelog consistency, runs tests and
  compatibility checks, signs the ZIP, verifies the signature, and creates a GitHub
  Release with both distributions and `SHA256SUMS`. Pre-release tags are marked accordingly.
- **Publish to JetBrains Marketplace** downloads the signed GitHub Release asset,
  checks its checksum, descriptor and signature, and uploads that exact archive.
  It can be invoked manually or called after a successful Release.
- Dependabot tracks Gradle and GitHub Actions dependencies weekly. Actions are pinned
  to reviewed commit hashes. Fork pull requests do not receive publishing credentials.

Repository Actions secret `PRIVATE_KEY` contains the release signing key.
`PRIVATE_KEY_PASSWORD` is optional for an encrypted PEM. The public certificate is
committed in `certificates/volan.pem`; the private key must never be committed.
The initial certificate is valid for five years. Back up the private key separately
and keep the same signing identity for future releases. Before certificate expiry,
follow JetBrains' signing documentation and verify the replacement with ZIP Signer.

## One-time Marketplace setup

JetBrains requires the **first upload of a new plugin through the Marketplace UI**.
The Gradle publishing API supports updates to a plugin that has already been uploaded.
See [Publishing a Plugin](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)
and [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html).

1. Download `volan-idea-0.2.0-signed.zip` from the
   [v0.2.0 GitHub Release](https://github.com/thirtyeighttwentysix/volan-idea/releases/tag/v0.2.0).
   Use the signed ZIP directly; do not extract/repackage it.
2. Sign into [JetBrains Marketplace](https://plugins.jetbrains.com/) using the account
   that will own the plugin. Choose your profile → **Add new plugin** and upload the ZIP.
   Name: **Volan Schema**. License: **Apache-2.0**. Source:
   `https://github.com/thirtyeighttwentysix/volan-idea`. Issues: the repository's Issues page.
   The description, icons, supported IDE range and change notes are inside the archive.
   Attach a screenshot from IDEA if the submission form requests one.
3. Complete JetBrains review and associate the plugin with the desired Marketplace vendor.
   GitHub organization ownership and Marketplace vendor ownership are separate settings.
4. Create a Marketplace personal access token in your JetBrains profile → **My Tokens**.
   Add it as **`PUBLISH_TOKEN`** in GitHub → Settings → Environments → **marketplace** →
   Environment secrets. Never paste the token into an issue, source file or chat.
5. Set repository Actions variable **`MARKETPLACE_PUBLISHING_ENABLED=true`** only after
   the listing exists and the token is configured. Until then GitHub Releases still work;
   automatic Marketplace publishing is deliberately skipped.

CLI alternative for step 4 (reads token from stdin):

```shell
gh secret set PUBLISH_TOKEN --repo thirtyeighttwentysix/volan-idea --env marketplace
gh variable set MARKETPLACE_PUBLISHING_ENABLED --body true --repo thirtyeighttwentysix/volan-idea
```

Do not publish `0.2.0` again after the initial upload. Marketplace requires a unique
version for every update, and an accepted upload can still await JetBrains review.

## Publishing a new version

1. Update `pluginVersion` in `gradle.properties`, the release entry in `CHANGELOG.md`,
   and the matching heading/content in `CHANGE_NOTES.html`.
2. Run `./gradlew test buildPlugin verifyPluginStructure verifyPlugin` and merge
   the changes after CI passes. On Windows use `gradlew.bat`.
3. Tag that commit and push the tag:

   ```shell
   git tag -a v0.3.0 -m "Volan Schema 0.3.0"
   git push origin v0.3.0
   ```

4. Check the Release workflow. When Marketplace publishing is enabled, its final job
   calls the publishing workflow for the **default** channel. Tags such as
   `v0.3.0-beta.1`, `v0.3.0-alpha.1` and `v0.3.0-rc.1` use the **beta** channel.

To retry a failed Marketplace upload without rebuilding, run **Publish to JetBrains
Marketplace** from Actions, selecting the existing release tag and channel. Pre-release
versions cannot be sent to the default channel. Do not retry an already accepted version.
If signing/verification fails, the Release workflow stops before creating a release.
If upload fails, the GitHub Release remains available for investigation or retry.

Local signing requires `PRIVATE_KEY` in the process environment, then:

```shell
./gradlew signPlugin verifyPluginSignature --no-configuration-cache
```

The release certificate is public and can be inspected with OpenSSL:

```shell
openssl x509 -in certificates/volan.pem -noout -subject -dates -fingerprint -sha256
```

## Repository settings

The repository uses squash merge, automatically deletes merged branches, enables
Discussions and private vulnerability reporting. CI checks protect `main`; organization
administrators retain a bypass for maintenance. No manual review gate is imposed on
the Marketplace environment. Release tags should only point to reviewed commits from
`main`; do not move or delete tags that already have published assets.
