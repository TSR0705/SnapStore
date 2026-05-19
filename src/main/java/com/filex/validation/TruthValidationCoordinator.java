package com.filex.validation;

/** Declares common constants for MDC logging correlation. */
public final class TruthValidationCoordinator {

  /** Standard MDC key for tracing threat pipeline correlation. */
  public static final String MDC_VALIDATION_RUN_ID = "validationRunId";

  private TruthValidationCoordinator() {
    // Prevent instantiation
  }
}
