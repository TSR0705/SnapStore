# 📊 Observability, Logging, & Telemetry Diagnostics

FileX implements high-fidelity, structured logging built on **SLF4J** and **Logback** to support deep forensic analysis and operational auditing.

---

## 1. Structured Logging Standard

All logs output by the FileX engines follow a strict key-value tagging format to allow easy indexing by SIEM platforms (Splunk, Datadog, ELK):

```
timestamp [thread] LEVEL logger - component=Engine event=action key=value ...
```

### Example Logs:
* **Ingestion Event:**
  ```log
  2026-05-18 21:51:28.847 [filex-watch-loop] INFO com.filex.engine.MonitoringEngine - component=MonitoringEngine event=recursive_registration_completed root=C:\Users\ACER\Documents dirs=1
  ```
* **Alert Trigger:**
  ```log
  2026-05-18 21:49:07.842 [filex-detection-evaluator] WARN com.filex.detection.DetectionEngine - component=DetectionEngine event=threat_alert_triggered rule=RapidModificationRule path=C:\Users\ACER\Downloads\decoy_1.txt
  ```

---

## 2. Dynamic MDC Diagnostics

During asynchronous dispatch across the `EventBus`, the engine automatically propagates ThreadLocal MDC (Mapped Diagnostic Context) metadata:
* `correlationId`: Links multiple disjoint filesystem events to a single correlated alert pattern.
* `incidentId`: Links related evidence items to the main incident ticket in the SQLite database.

---

## 3. Logback Configuration

FileX rolling log files are written to the `./logs` workspace directory by default, utilizing a rolling appender with a maximum 10MB file limit and a 7-day retention sweep.
