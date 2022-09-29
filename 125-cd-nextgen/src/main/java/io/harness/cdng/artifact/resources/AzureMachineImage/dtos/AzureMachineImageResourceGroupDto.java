package io.harness.cdng.artifact.resources.AzureMachineImage.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureMachineImageResourceGroupDto {
  String name;
  String subscriptionId;
}
