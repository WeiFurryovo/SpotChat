# SpotChat signing

This directory contains documentation only. No keystore, private key, certificate password, or other signing secret may be committed to the repository.

Debug builds use the Android Gradle Plugin's default debug signing configuration and the separate application ID `com.weifurry.spotchat.debug`. A GitHub-hosted runner normally generates a fresh debug key, so APKs from different workflow runs cannot be expected to upgrade one another. The CI artifact is test-only; uninstall an older debug build first if Android reports a signature mismatch.

Production releases must be signed with a private release key or Play App Signing. Supply release signing material through a secure local configuration or CI secrets, and never store it in source control.

The retired repository debug key remains exposed in Git history and must be treated as compromised. Uninstall any earlier `com.weifurry.spotchat` build signed with that key before installing a trusted production release; deleting the current file cannot revoke an Android signing key already distributed publicly.
