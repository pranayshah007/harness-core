package io.harness.delegate.beans.ci.vm.runner;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SetupVmResponse {
  @JsonProperty("instance_id") String instanceID;
  @JsonProperty("ip_address") String ipAddress;
}