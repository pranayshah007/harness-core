package io.harness.delegate.task.pcf.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.pcf.model.CfCliVersion;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfRollbackCommandRequestNG implements CfCommandRequestNG{
    String accountId;
    CfCommandTypeNG pcfCommandType;
    String commandName;
    CommandUnitsProgress commandUnitsProgress;
    Integer timeoutIntervalInMin;
    TasInfraConfig tasInfraConfig;
    List<CfServiceData> instanceData;
    List<String> routeMaps;
    List<String> tempRouteMaps;
    ResizeStrategy resizeStrategy;
    List<CfAppSetupTimeDetails> appsToBeDownSized;
    CfAppSetupTimeDetails newApplicationDetails;
    boolean isStandardBlueGreenWorkflow;
    boolean versioningChanged;
    boolean nonVersioning;
    String cfAppNamePrefix;
    CfAppSetupTimeDetails existingInActiveApplicationDetails;
    Integer activeAppRevision;
    boolean useCfCLI;
    CfCliVersion cfCliVersion;
    boolean useAppAutoscalar;
}
