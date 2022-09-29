package io.harness.artifacts.azuremachineimage.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AzureResourceGroup {
  private String name;
  private String subscriptionId;
}
