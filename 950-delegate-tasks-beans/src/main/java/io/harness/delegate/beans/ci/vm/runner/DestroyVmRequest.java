package io.harness.delegate.beans.ci.vm.runner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DestroyVmRequest {
  @JsonProperty("correlation_id") String correlationID;
  @JsonProperty("pool_id") String poolID;
  @JsonProperty("id") String id;
}