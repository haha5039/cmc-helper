# CMC Helper

- Application ID: `com.haha5039.cmc`. Change only the CMC role, never the system device category.
- Use Shizuku for the PC-free path. No su calls, network, phone, SMS, contact, or persistent accessibility permission.
- Shizuku API permission is the primary authorization. WRITE_SECURE_SETTINGS remains optional for legacy ADB; the Shizuku role writer must not grant it to the app. Preserve original role journaling.
- Reuse MainActivity asynchronously with a deadline; never use startActivitySync to await a reused live Activity. Native preparation stays finite and fixed-target. Shizuku starts self-instrumentation with --no-restart and performs hidden game-session launch calls in its shell/root UserService; never silently fall back to a restarting instrumentation path. Keep legacy ADB permission delegation cleanup in finally.
- Never press Samsung's test-mode OFF, Apply and Reset, or call ResetService: they reset CMC and can reboot.
- Exception disable requires idle CMC and `pd`. Its unverified positive-switch route is verified only on CMC 9.1.00.15 (910015100); fail closed for other builds.
- Actual role writes require idle CMC. A confirmed app transition may use native call/message switches to reach idle; never forge activation flags. Save the prior role before writing, verify and preserve external changes. Same-role and cancelled requests must never deactivate CMC.
- Run journal/access/role preflight before native preparation. Carry its expected role into the final write and reject drift. The Shizuku engine must independently recheck usage before put/delete.
- All APKs, including test/debug builds, must use SHA-256 `fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c`.
- Use the external local signing helper signing helper or `CMC_SIGNING_HELPER`; do not copy its private config, print credentials, or generate a fallback key. Verify the output certificate.
- Distinguish role state, native registration, IMS registration, and actual call/SMS receipt. Do not present one as proof of another. Host-only Shizuku checks are not device execution evidence. Preserve v1.0 evidence as historical.
- Keep private account data, raw device logs, original APKs, decompiled code, and signing material out of deliverables.