package io.harness.delegate.task.azure.appservice.webapp.ng.response;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class AzureWebAppNGRollbackResponse implements AzureWebAppRequestResponse {
  private List<AzureAppDeploymentData> azureAppDeploymentData;
  private AzureAppServicePreDeploymentData preDeploymentData;
  private String deploymentProgressMarker;
}
