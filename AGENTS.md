# CMC Helper

- Application ID: `com.haha5039.cmc`. Change only the CMC role, never the system device category.
- No root, Shizuku, network, phone, SMS, contact, or persistent accessibility permission.
- The sole optional app permission is `WRITE_SECURE_SETTINGS`. Prefer transient ADB grant/revoke through `tools/device.py`; preserve an existing user grant.
- Native preparation must remain a finite ADB instrumentation operation using only temporarily delegated `MANAGE_GAME_ACTIVITY`, released in `finally`.
- Never press Samsung's test-mode OFF, Apply and Reset, or call ResetService: they reset CMC and can reboot.
- Exception disable requires idle CMC and `pd`. Its unverified positive-switch route is verified only on CMC 9.1.00.15 (910015100); fail closed for other builds.
- Role changes require idle CMC. Save the prior role (including an absent key) before writing; verify, restore on failure, and preserve external changes.
- All APKs, including test/debug builds, must use SHA-256 `fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c`.
- Use the external local signing helper signing helper or `CMC_SIGNING_HELPER`; do not copy its private config, print credentials, or generate a fallback key. Verify the output certificate.
- Distinguish role state, native registration, IMS registration, and actual call/SMS receipt. Do not present one as proof of another.
- Keep private account data, raw device logs, original APKs, decompiled code, and signing material out of deliverables.

