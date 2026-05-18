# Phase 1J: End-to-End Truth Validation Instrumentation

## Implementation Plan

### Current State Analysis

**Already Instrumented** ✅:
1. **MonitoringEngine** - Has TruthMarkers.TRUTH logging for:
   - Engine startup/lifecycle
   - Watch roots configuration
   - Recursive registration
   - Directory registration counts
   - Watch loop started
   - Registration failures

2. **DetectionEngine** - Has TruthMarkers.TRUTH logging for:
   - Rules registered
   - Engine started with worker/queue info
   - Detection event received
   - Detection enqueue status
   - Queue overflow
   - Worker started/crashed
   - Rule evaluation started/outcome
   - Detection emitted
   - Detection suppressed

3. **AlertEngine** - Has TruthMarkers.TRUTH logging for:
   - Engine started with worker/queue info
   - Worker started
   - Detection received
   - Correlation decision (match/new)
   - Incident created
   - Incident updated with escalation info
   - Detection suppressed
   - Alert queue full
   - Alert processing failure

4. **EventBus** - Basic structure exists but needs truth instrumentation

**Missing Instrumentation** ❌:
1. **MonitoringEngine** - Missing detailed watch event logging
2. **EventBus** - Missing subscriber registration/dispatch logging
3. **IncidentPersistenceSubscriber** - Missing all persistence logging
4. **InvestigationQueryService** - Missing query logging
5. **InvestigationWorkspaceController** - Missing UI refresh logging
6. **System Readiness Signal** - Missing coordinated readiness marker
7. **Validation Mode Reset** - Partial (engines have it, need coordination)

---

## Implementation Tasks

### Task 1: Complete MonitoringEngine Instrumentation
**File**: `src/main/java/com/filex/engine/MonitoringEngine.java`

Add missing logs in `processWatchEvent()`:
- WatchKey received with directory
- Raw event kind and context
- Resolved absolute path
- Normalized event type published

Add in `publishNormalizedEvent()`:
- Event published confirmation with type and path

### Task 2: Add EventBus Truth Instrumentation
**File**: `src/main/java/com/filex/event/EventBus.java`

Add logs for:
- Subscriber registration (type, handler class)
- Event publish (sync/async, type, subscriber count)
- Subscriber dispatch (type, subscriber, success/failure)
- Subscriber execution failure

### Task 3: Instrument IncidentPersistenceSubscriber
**File**: `src/main/java/com/filex/alert/IncidentPersistenceSubscriber.java`

Add logs for:
- Subscriber started with DB path
- Incident write attempt/success/failure
- Evidence write attempt/success/failure
- Timeline write attempt/success/failure
- Transaction begin/commit/rollback

### Task 4: Instrument InvestigationQueryService
**File**: `src/main/java/com/filex/investigation/InvestigationQueryService.java`

Add logs for:
- Query invocation with criteria
- Pagination parameters
- Returned incident count
- Query duration
- Detail fetch operations
- Evidence fetch operations
- Timeline fetch operations

### Task 5: Instrument InvestigationWorkspaceController
**File**: `src/main/java/com/filex/controller/InvestigationWorkspaceController.java`

Add logs for:
- Workspace initialized
- Incident list refresh triggered
- Refresh trigger source
- Incident count loaded
- New incident subscription callback
- UI list update complete
- Incident selection event

### Task 6: Create System Readiness Coordinator
**New File**: `src/main/java/com/filex/validation/SystemReadinessCoordinator.java`

Responsibilities:
- Track component readiness states
- Emit single "FILEX TRUTH VALIDATION READY" log
- Only when ALL components are ready:
  - MonitoringEngine RUNNING
  - DetectionEngine RUNNING
  - AlertEngine RUNNING
  - Persistence subscriptions active
  - EventBus operational

### Task 7: Create Validation Mode Manager
**New File**: `src/main/java/com/filex/validation/ValidationModeManager.java`

Responsibilities:
- Coordinate validation mode reset across all components
- Reset sequence:
  1. Stop all engines
  2. Clear/reset validation database
  3. Reset detection state (suppression, history, queues)
  4. Reset alert state (incidents, correlation, suppression)
  5. Reset UI state
  6. Restart engines
- Provide explicit activation mechanism
- Thread-safe coordination

### Task 8: Add Validation Metadata Support
**Enhancement**: Add correlation timestamps to all events

Modify event base classes to include:
- Filesystem action timestamp (from WatchService)
- Detection timestamp (when rule fired)
- Incident timestamp (when incident created/updated)
- Persistence timestamp (when written to DB)
- UI display timestamp (when shown in workspace)

This enables forensic traceability across the entire pipeline.

---

## Architecture Rationale

### Why This Approach?

1. **Minimal Intrusion**: Add logging without changing core logic
2. **Thread-Safe**: All logging is thread-safe (SLF4J guarantees)
3. **Performance**: Structured logging has minimal overhead
4. **Grep-able**: key=value format enables easy log analysis
5. **Isolated**: TruthMarkers.TRUTH routes to separate log file
6. **Production-Ready**: No test-only hacks or shortcuts

### Design Principles

1. **Structured Logging**: Always use key=value format
2. **Sanitization**: Always sanitize values (no newlines/control chars)
3. **Correlation IDs**: Track events across pipeline with IDs
4. **Timestamps**: Capture timing at each stage
5. **Failure Visibility**: Log all failure paths explicitly
6. **No Blocking**: Never block critical paths for logging

---

## Implementation Order

1. ✅ MonitoringEngine (enhance existing)
2. ✅ EventBus (add new)
3. ✅ IncidentPersistenceSubscriber (add new)
4. ✅ InvestigationQueryService (add new)
5. ✅ InvestigationWorkspaceController (add new)
6. ✅ SystemReadinessCoordinator (create new)
7. ✅ ValidationModeManager (create new)
8. ✅ Validation metadata (enhance events)

---

## Testing Strategy

After implementation:
1. Start FileX in validation mode
2. Create test file in monitored directory
3. Grep logs for TRUTH marker
4. Verify complete pipeline trace:
   - MonitoringEngine detects file
   - EventBus dispatches to DetectionEngine
   - DetectionEngine evaluates rules
   - DetectionEngine emits detection
   - EventBus dispatches to AlertEngine
   - AlertEngine creates incident
   - EventBus dispatches to IncidentPersistenceSubscriber
   - Persistence writes to DB
   - InvestigationQueryService reads from DB
   - InvestigationWorkspaceController displays in UI

---

## Success Criteria

✅ Every filesystem mutation is traceable end-to-end
✅ No silent failures (all errors logged)
✅ Correlation IDs link events across pipeline
✅ Timestamps enable latency analysis
✅ System readiness signal is authoritative
✅ Validation mode reset is deterministic
✅ No architecture changes
✅ No thread-safety regressions
✅ Production-quality code

---

## Risks & Mitigations

**Risk**: Excessive logging impacts performance
**Mitigation**: Use async logging, structured format, separate appender

**Risk**: Log files grow too large
**Mitigation**: Configure log rotation in logback.xml

**Risk**: Sanitization breaks correlation
**Mitigation**: Preserve event IDs, use consistent sanitization

**Risk**: Validation reset corrupts production data
**Mitigation**: Explicit activation, separate validation DB, safety checks

**Risk**: Race conditions in readiness detection
**Mitigation**: Use atomic state tracking, synchronized coordination

---

## Files to Modify

1. `src/main/java/com/filex/engine/MonitoringEngine.java` - enhance
2. `src/main/java/com/filex/event/EventBus.java` - enhance
3. `src/main/java/com/filex/alert/IncidentPersistenceSubscriber.java` - enhance
4. `src/main/java/com/filex/investigation/InvestigationQueryService.java` - enhance
5. `src/main/java/com/filex/controller/InvestigationWorkspaceController.java` - enhance

## Files to Create

1. `src/main/java/com/filex/validation/SystemReadinessCoordinator.java` - new
2. `src/main/java/com/filex/validation/ValidationModeManager.java` - new

---

**Status**: Ready for implementation
**Estimated LOC**: ~800 lines (mostly logging statements)
**Estimated Time**: 2-3 hours
**Risk Level**: LOW (additive changes only)
