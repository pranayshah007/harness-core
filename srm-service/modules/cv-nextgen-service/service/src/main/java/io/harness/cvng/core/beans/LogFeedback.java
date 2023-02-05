package io.harness.cvng.core.beans;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LogFeedback {
  public enum FeedbackScore { NO_RISK_IGNORE_FREQUENCY, NO_RISK_CONSIDER_FREQUENCY, MEDIUM_RISK, HIGH_RISK, DEFAULT }
  String sampleMessage;
  FeedbackScore feedbackScore;
  String feedbackId;
  String description;
  String serviceIdentifier;
  String environmentIdentifier;
  String clusterId;
  String verificationJobInstanceId;
  long createdAt;
  long lastUpdatedAt;
  String createdBy;
  String lastUpdatedBy;
}
