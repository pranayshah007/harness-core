package io.harness.delegate.task.pcf.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfSwapRoutesRequestNG implements CfCommandRequestNG {
  String accountId;
  CfCommandTypeNG cfCommandTypeNG;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  Integer timeoutIntervalInMin;
  TasInfraConfig tasInfraConfig;
  String newApplicationName;
  List<CfAppSetupTimeDetails> existingApplicationDetails;
  List<String> existingApplicationNames;
  List<String> tempRoutes;
  List<String> finalRoutes;
  boolean downsizeOldApplication;
  boolean isMapRoutesOperation;
  boolean upSizeInActiveApp;
  CfAppSetupTimeDetails existingInActiveApplicationDetails;
  CfAppSetupTimeDetails newApplicationDetails;
  String cfAppNamePrefix;
}
