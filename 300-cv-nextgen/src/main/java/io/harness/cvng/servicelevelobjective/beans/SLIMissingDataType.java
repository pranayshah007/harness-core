package io.harness.cvng.servicelevelobjective.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

public enum SLIMissingDataType {
  @JsonProperty("Good") GOOD,
  @JsonProperty("Bad") BAD,
  @JsonProperty("Ignore") IGNORE;

  public double calculateSLIValue(long goodCount, long badCount, long totalMinutes) {
    Preconditions.checkState(totalMinutes != 0);
    long missingDataCount = totalMinutes - (goodCount + badCount);
    switch (this) {
      case GOOD:
        return ((goodCount + missingDataCount) * 100.0) / totalMinutes;
      case BAD:
        return (goodCount * 100.0) / totalMinutes;
      case IGNORE:
        if (goodCount + badCount == 0) {
          return 100;
        }
        return (goodCount * 100.0) / (goodCount + badCount);
      default:
        throw new IllegalStateException("Unhanded SLIMissingDataType " + this);
    }
  }
}
