package io.harness.delegate.task.azure.appservice.webapp.ng.response;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class AzureWebAppTrafficShiftResponse implements AzureWebAppRequestResponse {
  private String deploymentProgressMarker;
}
