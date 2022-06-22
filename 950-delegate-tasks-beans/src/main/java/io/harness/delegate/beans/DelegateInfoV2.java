package io.harness.delegate.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class DelegateInfoV2 {
  private String id;
  @JsonProperty("instance_id") private String instanceId;
  @JsonProperty("token") private String callbackToken;
}
