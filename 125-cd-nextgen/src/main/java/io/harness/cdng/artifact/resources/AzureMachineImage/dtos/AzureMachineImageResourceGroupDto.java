package io.harness.cdng.artifact.resources.AzureMachineImage.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureMachineImageResourceGroupDto {
  private String name;
  private String subscriptionId;
}
