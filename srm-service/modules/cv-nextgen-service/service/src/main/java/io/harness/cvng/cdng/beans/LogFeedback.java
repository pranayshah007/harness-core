package io.harness.cvng.cdng.beans;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LogFeedback {
  public enum FeedbackScore { NO_RISK_IGNORE_FREQUENCY, NO_RISK_CONSIDER_FREQUENCY, MEDIUM_RISK, HIGH_RISK }
  String sampleMessage;
  FeedbackScore feedbackScore;
  String feedbackId;
  String description;
}
