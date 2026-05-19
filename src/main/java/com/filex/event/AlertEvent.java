package com.filex.event;

/** Base class for alert events. */
public abstract class AlertEvent extends AppEvent {

  protected AlertEvent(String source) {
    super(source);
  }

  protected AlertEvent(String source, String correlationId) {
    super(source, correlationId);
  }
}
