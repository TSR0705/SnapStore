# ⚠️ Known Limitations & Hardening Roadmap

As a production-grade local security agent, **FileX** maintains high fidelity inside monitored workspaces, but there are specific system-level boundaries that operators must recognize:

---

## 1. Operating System Watches
* **Watch Limit Exhaustion (Linux):** On Linux platforms, `java.nio.file.WatchService` maps directly to `inotify`. If the directory tree being watched exceeds the system's `fs.inotify.max_user_watches` threshold, the agent will throw an `IOException` at startup. Operators must manually scale system watch settings.
* **Symbolic Link Watches:** The watcher loop does not resolve nested directories through symbolic links to prevent circular folder parsing deadlocks. Only actual directories are monitored recursively.

---

## 2. In-Memory Backpressure
* **Event Storms:** During heavy filesystem writes (e.g. major OS updates or bulk compilation runs), the raw event queue can flood with thousands of modify events per second. The present evaluation queue operates on an unbound memory buffer. Extreme volume write storms can increase JVM heap consumption and potentially trigger Out Of Memory (`OOM`) crashes.
* **Hardening Path:** Implement fixed-capacity circular buffers with drop-tail fallback policies for non-security paths in upcoming releases.

---

## 3. Database Locks under WAL Concurrency
* **Write contention:** SQLite operates in WAL mode allowing simultaneous read sessions. However, during high-velocity threat surges where thousands of events try to log parallel evidence logs in real time, minor transactional write-wait latency can occur.
