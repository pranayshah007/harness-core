package io.harness.delegate.beans.azure.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.AzureImageVersion;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@OwnedBy(HarnessTeam.CDC)
public class AzureMachineImageBuildResponse extends AzureDelegateTaskResponse {
  List<AzureImageVersion> azureImageVersions;
}
