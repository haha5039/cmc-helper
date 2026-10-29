# CMC Helper

- Application ID: `com.haha5039.cmc`. Change only the CMC role, never the system device category.
- Use Shizuku for the PC-free path. No su calls, network, phone, SMS, contact, or persistent accessibility permission.
- Shizuku API permission is the primary authorization. WRITE_SECURE_SETTINGS remains optional for legacy ADB; the Shizuku role writer must not grant it to the app. Preserve original role journaling.
- Reuse MainActivity asynchronously with a deadline; never use startActivitySync to await a reused live Activity. Native preparation stays finite and fixed-target. Shizuku starts self-instrumentation with --no-restart and performs hidden game-session launch calls in its shell/root UserService; never silently fall back to a restarting instrumentation path. Keep legacy ADB permission delegation cleanup in finally.
- Ordinary role changes must not reset CMC or reboot. A separate native Apply/Reset flow requires explicit in-app confirmation, authenticated entry, a durable request record, and post-reboot reconciliation. Never replay a reset request automatically.
- Exception disable requires idle CMC and `pd`. Its unverified positive-switch route is verified only on CMC 9.1.00.15 (910015100); fail closed for other builds.
- Direct role writes require idle CMC. A confirmed ordinary app transition may use native call/message switches to reach idle; never forge activation flags. The separately confirmed native Reset route delegates its reset sequence to Samsung CMC. Save the prior role before writing, verify and preserve external changes. Same-role and cancelled requests must never deactivate CMC.
- Run journal/access/role preflight before native preparation. Carry its expected role into the final write and reject drift. The Shizuku engine must independently recheck usage before put/delete.
- All APKs, including test/debug builds, must use SHA-256 `fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c`.
- Use `CMC_SIGNING_HELPER` to select a private local signing helper; do not copy its private config, print credentials, or generate a fallback key. Verify the output certificate.
- Distinguish role state, native registration, IMS registration, and actual call/SMS receipt. Do not present one as proof of another. Host-only Shizuku checks are not device execution evidence. Keep all runtime evidence local and outside version control.
- Keep private account data, raw device logs, original APKs, decompiled code, and signing material out of deliverables.

- Developer shortcut must enter through About authentication and leave the real DeveloperActivity open without pressing its controls. Respect the native initial-setup prerequisite.
- Close only CMC-owned settings tasks after a successful idle role change. Never force-stop CMC or clear its data for UI refresh.
- Manual KEY calculator is local-only, does not require Shizuku or alter CMC, and must reuse CmcOtp. Do not store KEY/code history or log user input. Recompute on minute change and before copying; clear stale results when KEY changes.
- Publish ordinary GitHub Releases with prerelease=false and Latest enabled unless the user requests otherwise. Keep verification limits explicit in the release body.
- Host Android/Shizuku adapters and fake settings CLI are test-only. Keep them out of APK builds and distinguish their evidence from real device/Binder execution.
- Use resource IDs for UI automation, visible labels/current values for accessibility. Give scroll/input views stable IDs; do not restore stale calculated codes or status text from hierarchy state.
- Run the separate UI test APK only on an explicitly selected isolated emulator without CMC/Shizuku. Sign it with the same required certificate; never include it in the product APK.

- Never commit or publish runtime records, device identifiers, logs, crash reports, screenshots, private configuration, or personal workstation paths. Keep guide content procedural only.
- Use environment variables and generic/relative examples for build configuration. Never copy private signing instructions into this repository.
- Release packages contain the APK, sanitized source archive and checksums only. Keep verification payloads local.
- Run the privacy gate before committing or publishing. If sensitive history exists, stop publication and notify the user; do not rewrite history or force-push without explicit authorization.
