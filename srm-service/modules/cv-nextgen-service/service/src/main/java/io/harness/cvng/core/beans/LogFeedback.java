package io.harness.cvng.core.beans;

import lombok.Builder;
import lombok.Value;
import org.codehaus.jackson.annotate.JsonIgnore;

@Value
@Builder
public class LogFeedback {
  public enum FeedbackScore { NO_RISK_IGNORE_FREQUENCY, NO_RISK_CONSIDER_FREQUENCY, MEDIUM_RISK, HIGH_RISK }
  String sampleMessage;
  FeedbackScore feedbackScore;
  String feedbackId;
  String description;
  String serviceIdentifier;
  String environmentIdentifier;
  String clusterId;
  String verificationJobInstanceId;
}
