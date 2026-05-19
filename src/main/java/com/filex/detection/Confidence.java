package com.filex.detection;

/**
 * Confidence levels for detection results.
 *
 * <p>Confidence indicates how certain the detection engine is that the detected behavior is truly
 * suspicious.
 */
public enum Confidence {

  /** Low confidence - behavior may be suspicious but has high false-positive risk. */
  LOW(1),

  /** Medium confidence - behavior is likely suspicious. */
  MEDIUM(2),

  /** High confidence - behavior is very likely suspicious. */
  HIGH(3);

  private final int level;

  Confidence(int level) {
    this.level = level;
  }

  /** Returns the numeric confidence level for comparison. */
  public int getLevel() {
    return level;
  }

  /** Returns the higher of two confidence levels. */
  public static Confidence max(Confidence a, Confidence b) {
    return a.level >= b.level ? a : b;
  }
}
