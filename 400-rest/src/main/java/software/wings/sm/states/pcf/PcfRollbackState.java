/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.LIMIT_PCF_THREADS;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.CfCommandRequest.PcfCommandType;
import io.harness.delegate.task.pcf.request.CfCommandRollbackRequest;

import software.wings.api.pcf.DeploySweepingOutputPcf;
import software.wings.api.pcf.PcfDeployStateExecutionData;
import software.wings.api.pcf.SetupSweepingOutputPcf;
import software.wings.beans.Application;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CDP)
public class PcfRollbackState extends PcfDeployState {
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private PcfStateHelper pcfStateHelper;
  /**
   * Instantiates a new state.
   *
   * @param name the name
   */
  public PcfRollbackState(String name) {
    super(name, StateType.PCF_ROLLBACK.name());
  }

  @Override
  public Integer getTimeoutMillis(ExecutionContext context) {
    return pcfStateHelper.getStateTimeoutMillis(context, 5, isRollback());
  }

  @Override
  public CfCommandRequest getPcfCommandRequest(ExecutionContext context, Application application, String activityId,
      SetupSweepingOutputPcf setupSweepingOutputPcf, CfInternalConfig pcfConfig, Integer updateCount,
      Integer downsizeUpdateCount, PcfDeployStateExecutionData stateExecutionData,
      PcfInfrastructureMapping infrastructureMapping) {
    DeploySweepingOutputPcf deploySweepingOutputPcf =
        sweepingOutputService.findSweepingOutput(context.prepareSweepingOutputInquiryBuilder()
                                                     .name(pcfStateHelper.obtainDeploySweepingOutputName(context, true))
                                                     .build());

    // Just revert previousCount and desiredCount values for Rollback
    // Deploy sends emptyInstanceData and PcfCommandTask figured out which apps to be resized,
    // in case of rollback, we send InstanceData mentioning apps and their reset counts
    StringBuilder updateDetails = new StringBuilder();
    List<CfServiceData> instanceData = new ArrayList<>();
    if (deploySweepingOutputPcf != null && deploySweepingOutputPcf.getInstanceData() != null) {
      deploySweepingOutputPcf.getInstanceData().forEach(cfServiceData -> {
        Integer temp = cfServiceData.getDesiredCount();
        cfServiceData.setDesiredCount(cfServiceData.getPreviousCount());
        cfServiceData.setPreviousCount(temp);
        updateDetails.append(new StringBuilder()
                                 .append("App Name: ")
                                 .append(cfServiceData.getName())
                                 .append(", DesiredCount: ")
                                 .append(cfServiceData.getDesiredCount())
                                 .append("}\n")
                                 .toString());

        instanceData.add(cfServiceData);
      });
    }

    stateExecutionData.setUpdateDetails(updateDetails.toString());
    stateExecutionData.setActivityId(activityId);

    return CfCommandRollbackRequest.builder()
        .activityId(activityId)
        .commandName(PCF_RESIZE_COMMAND)
        .workflowExecutionId(context.getWorkflowExecutionId())
        .organization(getOrgFromContext(setupSweepingOutputPcf))
        .space(fetchSpaceFromContext(setupSweepingOutputPcf))
        .resizeStrategy(setupSweepingOutputPcf.getResizeStrategy())
        .routeMaps(infrastructureMapping.getRouteMaps())
        .tempRouteMaps(infrastructureMapping.getTempRouteMap())
        .pcfConfig(pcfConfig)
        .pcfCommandType(PcfCommandType.ROLLBACK)
        .instanceData(instanceData)
        .appId(application.getUuid())
        .accountId(application.getAccountId())
        .timeoutIntervalInMin(firstNonNull(setupSweepingOutputPcf.getTimeoutIntervalInMinutes(), Integer.valueOf(5)))
        .appsToBeDownSized(setupSweepingOutputPcf.getAppDetailsToBeDownsized())
        .newApplicationDetails(setupSweepingOutputPcf.getNewPcfApplicationDetails())
        .isStandardBlueGreenWorkflow(setupSweepingOutputPcf.isStandardBlueGreenWorkflow())
        .enforceSslValidation(setupSweepingOutputPcf.isEnforceSslValidation())
        .useAppAutoscalar(setupSweepingOutputPcf.isUseAppAutoscalar())
        .useCfCLI(true)
        .limitPcfThreads(featureFlagService.isEnabled(LIMIT_PCF_THREADS, pcfConfig.getAccountId()))
        .cfCliVersion(
            pcfStateHelper.getCfCliVersionOrDefault(application.getAppId(), setupSweepingOutputPcf.getServiceId()))
        .versioningChanged(setupSweepingOutputPcf.isVersioningChanged())
        .nonVersioning(setupSweepingOutputPcf.isNonVersioning())
        .cfAppNamePrefix(setupSweepingOutputPcf.getCfAppNamePrefix())
        .existingInActiveApplicationDetails(setupSweepingOutputPcf.getMostRecentInactiveAppVersionDetails())
        .activeAppRevision(setupSweepingOutputPcf.getActiveAppRevision())
        .build();
  }

  private String fetchSpaceFromContext(SetupSweepingOutputPcf setupSweepingOutputPcf) {
    if (setupSweepingOutputPcf == null || setupSweepingOutputPcf.getPcfCommandRequest() == null) {
      return StringUtils.EMPTY;
    }

    return setupSweepingOutputPcf.getPcfCommandRequest().getSpace();
  }

  private String getOrgFromContext(SetupSweepingOutputPcf setupSweepingOutputPcf) {
    if (setupSweepingOutputPcf == null || setupSweepingOutputPcf.getPcfCommandRequest() == null) {
      return StringUtils.EMPTY;
    }

    return setupSweepingOutputPcf.getPcfCommandRequest().getOrganization();
  }

  @Override
  public Integer getInstanceCount() {
    return 0;
  }

  @Override
  public InstanceUnitType getInstanceUnitType() {
    return null;
  }

  @Override
  public Integer getDownsizeInstanceCount() {
    return null;
  }

  @Override
  public InstanceUnitType getDownsizeInstanceUnitType() {
    return null;
  }

  @Override
  public Integer getUpsizeUpdateCount(SetupSweepingOutputPcf setupSweepingOutputPcf, CfInternalConfig pcfConfig) {
    return -1;
  }

  @Override
  public Integer getDownsizeUpdateCount(SetupSweepingOutputPcf setupSweepingOutputPcf, CfInternalConfig pcfConfig) {
    return -1;
  }

  @Override
  public Map<String, String> validateFields() {
    return emptyMap();
  }

  @Override
  @SchemaIgnore
  public boolean isUseAppResizeV2() {
    return super.isUseAppResizeV2();
  }
}
