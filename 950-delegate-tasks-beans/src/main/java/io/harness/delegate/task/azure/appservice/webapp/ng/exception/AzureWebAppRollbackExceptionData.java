package io.harness.delegate.task.azure.appservice.webapp.ng.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.DataException;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@OwnedBy(CDP)
@EqualsAndHashCode(callSuper = false)
public class AzureWebAppRollbackExceptionData extends DataException {
  String deploymentProgressMarker;

  public AzureWebAppRollbackExceptionData(String deploymentProgressMarker, Throwable cause) {
    super(cause);
    this.deploymentProgressMarker = deploymentProgressMarker;
  }
}
