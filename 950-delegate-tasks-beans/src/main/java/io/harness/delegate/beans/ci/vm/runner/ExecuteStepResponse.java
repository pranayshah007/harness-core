package io.harness.delegate.beans.ci.vm.runner;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExecuteStepResponse {
  @JsonProperty("exit_code") int exitCode;
  @JsonProperty("exited") boolean exited;
  @JsonProperty("error") String error;
  @JsonProperty("oom_killed") boolean oomKilled;
  @JsonProperty("outputs") Map<String, String> outputs;
}