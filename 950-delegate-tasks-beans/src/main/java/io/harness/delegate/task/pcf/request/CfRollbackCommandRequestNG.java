package io.harness.delegate.task.pcf.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.pcf.model.CfCliVersion;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfRollbackCommandRequestNG implements CfCommandRequestNG{
    String accountId;
    CfCommandTypeNG cfCommandTypeNG;
    String commandName;
    CommandUnitsProgress commandUnitsProgress;
    Integer timeoutIntervalInMin;
    TasInfraConfig tasInfraConfig;
    List<CfServiceData> instanceData;
    List<String> routeMaps;
    List<String> tempRouteMaps;
    CfAppSetupTimeDetails oldApplicationDetails;
    CfAppSetupTimeDetails newApplicationDetails;
    String cfAppNamePrefix;
    Integer activeAppRevision;
    CfAppSetupTimeDetails existingInActiveApplicationDetails;
    CfCliVersion cfCliVersion;
    boolean enforceSslValidation;
    boolean useAppAutoscalar;
    boolean swapRouteOccured;
    boolean limitPcfThreads;
    String existingAppNamingStrategy;
    boolean upsizeInActiveApp;
    boolean downsizeOldApps;
}
