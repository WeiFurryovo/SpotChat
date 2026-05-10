# SpotChat debug signing

`spotchat-debug.keystore` is a fixed debug-only signing key for local builds and GitHub Actions artifacts.

It is intentionally committed so APKs from different workflow runs keep the same Android package signature. That lets a watch install a newer Action APK over the previous Action APK without uninstalling first.

Do not use this key for Play Store or production release builds.
