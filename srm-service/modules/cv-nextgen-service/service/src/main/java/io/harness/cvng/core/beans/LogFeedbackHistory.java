package io.harness.cvng.core.beans;

import lombok.Builder;
import lombok.Value;
import org.codehaus.jackson.annotate.JsonProperty;

@Value
@Builder
public class LogFeedbackHistory {
  LogFeedback logFeedback;
  @JsonProperty("createdBy") String createdBy;
  @JsonProperty("updatedBy") String updatedBy;
}
