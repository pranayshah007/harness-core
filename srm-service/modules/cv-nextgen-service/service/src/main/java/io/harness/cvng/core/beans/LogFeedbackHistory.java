package io.harness.cvng.core.beans;

import io.harness.cvng.core.beans.LogFeedback;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LogFeedbackHistory {
  LogFeedback logFeedback;
  private String createdBy;
  private String updatedBy;
}
