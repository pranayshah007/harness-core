package io.harness.delegate.task.azure.appservice.webapp.ng.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppRequestType;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@OwnedBy(CDP)
@EqualsAndHashCode(callSuper = true)
public class AzureWebAppTrafficShiftRequest extends AbstractWebAppTaskRequest {
  private double trafficPercentage;

  @Builder
  public AzureWebAppTrafficShiftRequest(CommandUnitsProgress commandUnitsProgress,
      AzureWebAppInfraDelegateConfig infrastructure, double trafficPercentage) {
    super(commandUnitsProgress, infrastructure);
    this.trafficPercentage = trafficPercentage;
  }

  @Override
  public AzureWebAppRequestType getRequestType() {
    return AzureWebAppRequestType.SLOT_TRAFFIC_SHIFT;
  }
}
