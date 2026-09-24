# Build Notes

GitHub Actions builds the iOS application on a macOS runner, archives it with code signing disabled, packages `Payload/ATTA-Boy.app` into an IPA, and uploads the IPA as an artifact for SideStore testing.

Distribution signing and App Store provisioning are intentionally separate from this development pipeline.
