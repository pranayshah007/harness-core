/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static software.wings.beans.TaskType.CF_COMMAND_TASK_NG;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.tas.beans.TasSetupDataOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.request.CfSwapRoutesRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.ng.core.BaseNGAccess;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasSwapRoutesStep extends TaskExecutableWithRollbackAndRbac<CfCommandResponseNG> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TAS_SWAP_ROUTES.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private TasEntityHelper tasEntityHelper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  public static final String TAS_SWAP_ROUTES = "TasSwapRoutes";
  public static final String COMMAND_UNIT = "Tas Swap Routes";
  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_TAS_NG)) {
      throw new AccessDeniedException(
          "CDS_TAS_NG FF is not enabled for this account. Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
  }
  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<CfCommandResponseNG> responseDataSupplier) throws Exception {
    StepResponse.StepResponseBuilder builder = StepResponse.builder();

    CfCommandResponseNG response;
    try {
      response = responseDataSupplier.get();
    } catch (Exception ex) {
      log.error("Error while processing Tas response: {}", ExceptionUtils.getMessage(ex), ex);
      throw ex;
    }
    builder.unitProgressList(response.getUnitProgressData().getUnitProgresses());
    builder.status(Status.SUCCEEDED);
    return builder.build();
  }
  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    TasSwapRoutesStepParameters tasSwapRoutesStepParameters = (TasSwapRoutesStepParameters) stepParameters.getSpec();
    OptionalSweepingOutput tasSetupDataOptional = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(
            tasSwapRoutesStepParameters.getTasSetupFqn() + "." + OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME));

    if (!tasSetupDataOptional.isFound()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder().setMessage("Tas App resize Step was not executed. Skipping .").build())
          .build();
    }
    TasSetupDataOutcome tasSetupDataOutcome = (TasSetupDataOutcome) tasSetupDataOptional.getOutput();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    TasInfraConfig tasInfraConfig = getTasInfraConfig(ambiance);
    List<String> existingAppNames = tasSetupDataOutcome.getAppDetailsToBeDownsized()
                                        .stream()
                                        .map(CfAppSetupTimeDetails::getApplicationName)
                                        .collect(toList());

    boolean downSizeOldApplication = false;
    CfSwapRoutesRequestNG cfSwapRoutesRequestNG =
        CfSwapRoutesRequestNG.builder()
            .finalRoutes(tasSetupDataOutcome.getRouteMaps())
            .downsizeOldApplication(downSizeOldApplication)
            .existingApplicationNames(existingAppNames)
            .accountId(accountId)
            .newApplicationDetails(tasSetupDataOutcome.getNewApplicationDetails())
            .cfAppNamePrefix(tasSetupDataOutcome.getCfAppNamePrefix())
            .commandName(TAS_SWAP_ROUTES)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .existingApplicationDetails(tasSetupDataOutcome.getAppDetailsToBeDownsized())
            .tempRoutes(tasSetupDataOutcome.getTempRouteMap())
            .existingInActiveApplicationDetails(tasSetupDataOutcome.getOldApplicationDetails())
            .newApplicationName(getNewAppicationName(tasSetupDataOutcome))
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROUTES)
            .tasInfraConfig(tasInfraConfig)
            .timeoutIntervalInMin(10)
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                                  .taskType(CF_COMMAND_TASK_NG.name())
                                  .parameters(new Object[] {cfSwapRoutesRequestNG})
                                  .build();
    return StepUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        Collections.singletonList(TasAppResizeStep.COMMAND_UNIT), CF_COMMAND_TASK_NG.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(tasSwapRoutesStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  private String getNewAppicationName(TasSetupDataOutcome tasSetupDataOutcome) {
    if (tasSetupDataOutcome.getNewApplicationDetails() != null) {
      return tasSetupDataOutcome.getNewApplicationDetails().getApplicationName();
    }
    return null;
  }

  private TasInfraConfig getTasInfraConfig(Ambiance ambiance) {
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        (TanzuApplicationServiceInfrastructureOutcome) outcomeService.resolve(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    BaseNGAccess baseNGAccess = tasEntityHelper.getBaseNGAccess(accountId, orgId, projectId);
    ConnectorInfoDTO connectorInfoDTO =
        tasEntityHelper.getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), accountId, orgId, projectId);
    return TasInfraConfig.builder()
        .organization(infrastructureOutcome.getOrganization())
        .space(infrastructureOutcome.getSpace())
        .encryptionDataDetails(tasEntityHelper.getEncryptionDataDetails(connectorInfoDTO, baseNGAccess))
        .tasConnectorDTO((TasConnectorDTO) connectorInfoDTO.getConnectorConfig())
        .build();
  }
}
