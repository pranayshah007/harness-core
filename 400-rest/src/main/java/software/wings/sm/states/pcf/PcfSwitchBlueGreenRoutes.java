/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.CF_APP_NON_VERSIONING_INACTIVE_ROLLBACK;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.cf.apprenaming.AppNamingStrategy;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfRouteUpdateCommandResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.pcf.PcfRouteUpdateStateExecutionData;
import software.wings.api.pcf.SetupSweepingOutputPcf;
import software.wings.api.pcf.SwapRouteRollbackSweepingOutputPcf;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.PcfConfig;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.command.PcfDummyCommandUnit;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.Attributes;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class PcfSwitchBlueGreenRoutes extends State {
  @Inject private transient AppService appService;
  @Inject private transient InfrastructureMappingService infrastructureMappingService;
  @Inject private transient SecretManager secretManager;
  @Inject private transient SettingsService settingsService;
  @Inject private transient ActivityService activityService;
  @Inject private transient PcfStateHelper pcfStateHelper;
  @Inject private transient SweepingOutputService sweepingOutputService;
  @Inject protected transient FeatureFlagService featureFlagService;
  @Inject private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @Getter @Setter private List<String> tags;

  public static final String PCF_BG_SWAP_ROUTE_COMMAND = "PCF BG Swap Route";
  public static final String RESTORE_ACTIVE_IN_ACTIVE_APPS_COMMAND = "Restore Active and In-Active Apps";
  static final String PCF_BG_SKIP_SWAP_ROUTE_MESG = "Skipping route swapping";

  @Attributes(title = "Downsize Old Applications") private boolean downsizeOldApps;
  @Getter @Setter @Attributes(title = "Up size InActive app") private boolean upSizeInActiveApp;

  public boolean isDownsizeOldApps() {
    return downsizeOldApps;
  }

  public void setDownsizeOldApps(boolean downsizeOldApps) {
    this.downsizeOldApps = downsizeOldApps;
  }

  /**
   * Instantiates a new state.
   *
   * @param name      the name
   */
  public PcfSwitchBlueGreenRoutes(String name) {
    super(name, StateType.PCF_BG_MAP_ROUTE.name());
  }

  public PcfSwitchBlueGreenRoutes(String name, String stateType) {
    super(name, stateType);
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      return executeInternal(context);
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public Integer getTimeoutMillis(ExecutionContext context) {
    return pcfStateHelper.getStateTimeoutMillis(context, 5, isRollback());
  }

  protected ExecutionResponse executeInternal(ExecutionContext context) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    Application app = appService.get(context.getAppId());
    Environment env = workflowStandardParamsExtensionService.getEnv(workflowStandardParams);
    notNullCheck("Environment does not exist", env, USER);

    PcfInfrastructureMapping pcfInfrastructureMapping =
        (PcfInfrastructureMapping) infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());

    SetupSweepingOutputPcf setupSweepingOutputPcf = pcfStateHelper.findSetupSweepingOutputPcf(context, isRollback());
    pcfStateHelper.populatePcfVariables(context, setupSweepingOutputPcf);
    SettingAttribute settingAttribute = settingsService.get(pcfInfrastructureMapping.getComputeProviderSettingId());
    PcfConfig pcfConfig = (PcfConfig) settingAttribute.getValue();
    CfRouteUpdateRequestConfigData requestConfigData =
        getPcfRouteUpdateRequestConfigData(setupSweepingOutputPcf, pcfConfig);
    Activity activity = createActivity(context, pcfConfig);

    if (isRollback()) {
      if (pcfStateHelper.isRollBackNotNeeded(setupSweepingOutputPcf)) {
        return pcfStateHelper.handleRollbackSkipped(
            context.getAppId(), activity.getUuid(), PCF_BG_SWAP_ROUTE_COMMAND, PCF_BG_SKIP_SWAP_ROUTE_MESG);
      }
      SweepingOutputInstance sweepingOutputInstance =
          sweepingOutputService.find(context.prepareSweepingOutputInquiryBuilder()
                                         .name(pcfStateHelper.obtainSwapRouteSweepingOutputName(context, true))
                                         .build());
      if (sweepingOutputInstance != null) {
        SwapRouteRollbackSweepingOutputPcf swapRouteRollbackSweepingOutputPcf =
            (SwapRouteRollbackSweepingOutputPcf) sweepingOutputInstance.getValue();

        if (isEmpty(tags) && isNotEmpty(swapRouteRollbackSweepingOutputPcf.getTags())) {
          tags = swapRouteRollbackSweepingOutputPcf.getTags();
        }

        // it means no update route happened.
        if (swapRouteRollbackSweepingOutputPcf.getPcfRouteUpdateRequestConfigData() != null) {
          downsizeOldApps =
              swapRouteRollbackSweepingOutputPcf.getPcfRouteUpdateRequestConfigData().isDownsizeOldApplication();
        }
      }
      requestConfigData.setSkipRollback(sweepingOutputInstance == null);
    }

    List<EncryptedDataDetail> encryptedDataDetails = secretManager.getEncryptionDetails(
        (EncryptableSetting) pcfConfig, context.getAppId(), context.getWorkflowExecutionId());

    // update value as for rollback, we need to readt it from SweepingOutput
    requestConfigData.setDownsizeOldApplication(downsizeOldApps);

    List<String> renderedTags = pcfStateHelper.getRenderedTags(context, tags);

    return pcfStateHelper.queueDelegateTaskForRouteUpdate(
        PcfRouteUpdateQueueRequestData.builder()
            .pcfConfig(pcfConfig)
            .app(app)
            .pcfInfrastructureMapping(pcfInfrastructureMapping)
            .activityId(activity.getUuid())
            .envId(env.getUuid())
            .environmentType(env.getEnvironmentType())
            .timeoutIntervalInMinutes(firstNonNull(setupSweepingOutputPcf.getTimeoutIntervalInMinutes(), 5))
            .commandName(
                shouldUpSizeInActiveApp(pcfConfig) ? RESTORE_ACTIVE_IN_ACTIVE_APPS_COMMAND : PCF_BG_SWAP_ROUTE_COMMAND)
            .requestConfigData(requestConfigData)
            .encryptedDataDetails(encryptedDataDetails)
            .downsizeOldApps(downsizeOldApps)
            .useCfCli(true)
            .build(),
        setupSweepingOutputPcf, context.getStateExecutionInstanceId(), isSelectionLogsTrackingForTasksEnabled(),
        renderedTags);
  }

  private CfRouteUpdateRequestConfigData getPcfRouteUpdateRequestConfigData(
      SetupSweepingOutputPcf setupSweepingOutputPcf, PcfConfig pcfConfig) {
    List<String> existingAppNames;

    if (setupSweepingOutputPcf != null && isNotEmpty(setupSweepingOutputPcf.getAppDetailsToBeDownsized())) {
      existingAppNames = setupSweepingOutputPcf.getAppDetailsToBeDownsized()
                             .stream()
                             .map(CfAppSetupTimeDetails::getApplicationName)
                             .collect(toList());
    } else {
      existingAppNames = emptyList();
    }

    return CfRouteUpdateRequestConfigData.builder()
        .newApplicationName(getNewApplicationName(setupSweepingOutputPcf))
        .existingApplicationDetails(
            setupSweepingOutputPcf != null ? setupSweepingOutputPcf.getAppDetailsToBeDownsized() : null)
        .existingApplicationNames(existingAppNames)
        .tempRoutes(setupSweepingOutputPcf != null ? setupSweepingOutputPcf.getTempRouteMap() : emptyList())
        .finalRoutes(setupSweepingOutputPcf != null ? setupSweepingOutputPcf.getRouteMaps() : emptyList())
        .isRollback(isRollback())
        .isStandardBlueGreen(true)
        .downsizeOldApplication(downsizeOldApps)
        .upSizeInActiveApp(shouldUpSizeInActiveApp(pcfConfig))
        .versioningChanged(setupSweepingOutputPcf != null && setupSweepingOutputPcf.isVersioningChanged())
        .nonVersioning(setupSweepingOutputPcf != null && setupSweepingOutputPcf.isNonVersioning())
        .existingAppNamingStrategy(getExistingAppNamingStrategy(setupSweepingOutputPcf))
        .existingInActiveApplicationDetails(
            setupSweepingOutputPcf != null ? setupSweepingOutputPcf.getMostRecentInactiveAppVersionDetails() : null)
        .newApplicationDetails(
            setupSweepingOutputPcf != null ? setupSweepingOutputPcf.getNewPcfApplicationDetails() : null)
        .cfAppNamePrefix(setupSweepingOutputPcf != null ? setupSweepingOutputPcf.getCfAppNamePrefix() : null)
        .build();
  }

  private String getExistingAppNamingStrategy(SetupSweepingOutputPcf setupSweepingOutputPcf) {
    if (setupSweepingOutputPcf == null || isEmpty(setupSweepingOutputPcf.getExistingAppNamingStrategy())) {
      return AppNamingStrategy.VERSIONING.name();
    }
    return setupSweepingOutputPcf.getExistingAppNamingStrategy();
  }

  private boolean shouldUpSizeInActiveApp(PcfConfig pcfConfig) {
    return isRollback()
        && featureFlagService.isEnabled(CF_APP_NON_VERSIONING_INACTIVE_ROLLBACK, pcfConfig.getAccountId())
        && upSizeInActiveApp;
  }

  @VisibleForTesting
  String getNewApplicationName(SetupSweepingOutputPcf setupSweepingOutputPcf) {
    String name = EMPTY;
    if (setupSweepingOutputPcf != null && setupSweepingOutputPcf.getNewPcfApplicationDetails() != null) {
      name = setupSweepingOutputPcf.getNewPcfApplicationDetails().getApplicationName();
    }

    return name;
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      String activityId = response.keySet().iterator().next();
      CfCommandExecutionResponse executionResponse = (CfCommandExecutionResponse) response.values().iterator().next();
      ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
          ? ExecutionStatus.SUCCESS
          : ExecutionStatus.FAILED;
      activityService.updateStatus(activityId, context.getAppId(), executionStatus);

      // update PcfDeployStateExecutionData,
      PcfRouteUpdateStateExecutionData stateExecutionData =
          (PcfRouteUpdateStateExecutionData) context.getStateExecutionData();
      stateExecutionData.setStatus(executionStatus);
      stateExecutionData.setErrorMsg(executionResponse.getErrorMessage());
      CfRouteUpdateCommandResponse cfRouteUpdateCommandResponse =
          (CfRouteUpdateCommandResponse) executionResponse.getPcfCommandResponse();
      stateExecutionData.setFinalAppDetails(cfRouteUpdateCommandResponse.getUpdatedValues());
      if (executionStatus == ExecutionStatus.SUCCESS) {
        pcfStateHelper.updateInfoVariables(context, stateExecutionData, executionResponse, isRollback());
      }

      if (!isRollback()) {
        sweepingOutputService.save(
            context.prepareSweepingOutputBuilder(Scope.WORKFLOW)
                .name(pcfStateHelper.obtainSwapRouteSweepingOutputName(context, false))
                .value(SwapRouteRollbackSweepingOutputPcf.builder()
                           .pcfRouteUpdateRequestConfigData(stateExecutionData.getPcfRouteUpdateRequestConfigData())
                           .tags(stateExecutionData.getTags())
                           .build())
                .build());
      }

      return ExecutionResponse.builder()
          .executionStatus(executionStatus)
          .errorMessage(executionResponse.getErrorMessage())
          .stateExecutionData(stateExecutionData)
          .build();

    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  protected Activity createActivity(ExecutionContext executionContext, PcfConfig pcfConfig) {
    boolean shouldUpSizeInActiveApp = shouldUpSizeInActiveApp(pcfConfig);
    return pcfStateHelper.createActivity(executionContext, getPcfBgSwapRouteCommand(shouldUpSizeInActiveApp),
        getStateType(), CommandUnitType.PCF_BG_SWAP_ROUTE, activityService,
        getCommandUnitList(shouldUpSizeInActiveApp));
  }

  List<CommandUnit> getCommandUnitList(boolean shouldUpSizeInActiveApp) {
    List<CommandUnit> commandUnits = new ArrayList<>();
    commandUnits.add(new PcfDummyCommandUnit(
        shouldUpSizeInActiveApp ? RESTORE_ACTIVE_IN_ACTIVE_APPS_COMMAND : PCF_BG_SWAP_ROUTE_COMMAND));
    return commandUnits;
  }

  @NotNull
  private String getPcfBgSwapRouteCommand(boolean upSizeInActiveApp) {
    return upSizeInActiveApp ? RESTORE_ACTIVE_IN_ACTIVE_APPS_COMMAND : PCF_BG_SWAP_ROUTE_COMMAND;
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
