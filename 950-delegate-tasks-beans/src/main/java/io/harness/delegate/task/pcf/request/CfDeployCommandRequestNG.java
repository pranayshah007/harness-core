package io.harness.delegate.task.pcf.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.PcfManifestsPackage;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.pcf.model.CfCliVersion;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfDeployCommandRequestNG implements CfCommandRequestNG {
    String accountId;
    CfCommandTypeNG pcfCommandType;
    String commandName;
    CommandUnitsProgress commandUnitsProgress;
    Integer timeoutIntervalInMin;
    @NotNull TasInfraConfig tasInfraConfig;
    String newReleaseName;
    List<String> routeMaps;

    /**
     * This is not desired count but update count, means upsize new app by currentCount + 5,
     * delegating calculating actual desiredInstanceCount to PCFCommandTask
     * (delegate), makes sure in all deploy state, we calculate based on most current data.
     *
     */
    Integer updateCount;
    Integer downSizeCount;
    Integer totalPreviousInstanceCount;
    CfAppSetupTimeDetails downsizeAppDetail;
    Integer maxCount;
    PcfManifestsPackage pcfManifestsPackage;
    /**
     * This will be empty for deploy_state, so deploy will figureOut old versions and scale them down by 5
     * This will be set by Rollback, Rollback will use same request and PCFCommand.DEPLOY,
     * and looking at this list, we will know its coming from deploy state or rollback state
     */
    List<CfServiceData> instanceData;
    ResizeStrategy resizeStrategy;
    boolean isStandardBlueGreen;
    boolean useCfCLI;
    CfCliVersion cfCliVersion;
    boolean useAppAutoscalar;

}
