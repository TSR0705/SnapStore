# 🛡️ Security Policy & Vulnerability Disclosure

We take the security of **FileX** seriously. As an endpoint security monitoring agent, maintaining absolute trust and data integrity is our highest priority.

---

## 1. Supported Versions

We actively maintain and provide security patches for the following versions:

| Version | Supported |
| :--- | :--- |
| **`v1.x.x` (Active Branch)** | 🟢 Yes |
| `< v1.0.0` (Prototype Phases) | 🔴 No |

---

## 2. Reporting a Vulnerability

**DO NOT file public GitHub issues for security vulnerabilities.**

If you discover a security issue or vulnerability (e.g. privilege escalation, code injection, SQLite file traversal locks), please report it to us confidentially:

1. **Email:** Send a detailed report to **[security@filex.io](mailto:security@filex.io)**.
2. **Encrypted Communication:** If necessary, request our GPG public key via email to encrypt your payload findings.
3. **Details to Include:**
   * Step-by-step reproduction guide.
   * Host OS configuration (Windows/Linux/macOS).
   * Proof of Concept (PoC) code.
   * Potential impact assessment.

We will acknowledge receipt of your report within **24 hours** and provide a resolution timeframe within **7 days**.

---

## 3. Threat Model & Boundaries

* **Database File:** The database `filex.db` runs inside user directories. Access control relies on OS file system privileges. Ensure your operational deployment locks down write access to this database file.
* **Watcher Loop Permissions:** FileX monitors paths recursively. It requires appropriate OS read permissions on target folders (e.g. configuration or AppData directories). Running as Administrator is required to capture system configuration write attempts.
