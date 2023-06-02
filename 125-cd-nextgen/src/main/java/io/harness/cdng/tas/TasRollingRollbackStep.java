/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.execution.tas.TasStageExecutionDetails;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.tas.outcome.TasRollingDeployOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.TasServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.artifact.TasArtifactConfig;
import io.harness.delegate.task.pcf.request.CfRollingRollbackRequestNG;
import io.harness.delegate.task.pcf.request.TasManifestsPackage;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfRollingRollbackResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.pcf.CfCommandUnitConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasRollingRollbackStep extends CdTaskExecutable<CfCommandResponseNG> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TAS_ROLLING_ROLLBACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private TasEntityHelper tasEntityHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private TasStepHelper tasStepHelper;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private EngineExpressionService engineExpressionService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_TAS_NG)) {
      throw new AccessDeniedException(
          "CDS_TAS_NG FF is not enabled for this account. Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
  }
  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    TasRollingRollbackStepParameters tasRollingRollbackStepParameters =
        (TasRollingRollbackStepParameters) stepParameters.getSpec();

    String accountId = AmbianceUtils.getAccountId(ambiance);
    TasInfraConfig tasInfraConfig = getTasInfraConfig(ambiance);

    OptionalSweepingOutput tasRollingDeployOutcomeOptional = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(tasRollingRollbackStepParameters.getTasRollingDeployFqn() + "."
            + OutcomeExpressionConstants.TAS_ROLLING_DEPLOY_OUTCOME));

    if (!tasRollingDeployOutcomeOptional.isFound()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder().setMessage(" Outcome Not Found For Deploy Step, so skipping it ").build())
          .build();
    }

    TasRollingDeployOutcome tasRollingDeployOutcome =
        (TasRollingDeployOutcome) tasRollingDeployOutcomeOptional.getOutput();

    TasStageExecutionDetails tasStageExecutionDetails = tasRollingDeployOutcome.getTasStageExecutionDetails();

    List<ArtifactOutcome> artifactOutcomes =
        tasStageExecutionDetails == null ? Collections.emptyList() : tasStageExecutionDetails.getArtifactsOutcome();
    TasArtifactConfig tasArtifactConfig =
        artifactOutcomes.isEmpty() ? null : tasStepHelper.getPrimaryArtifactConfig(ambiance, artifactOutcomes.get(0));

    TasManifestsPackage resolvedTasManifestsPackage =
        tasStageExecutionDetails == null ? null : TasManifestsPackage.builder().build();
    TasManifestsPackage unresolvedTasManifestsPackage =
        tasStageExecutionDetails == null ? null : tasStageExecutionDetails.getTasManifestsPackage();

    CDExpressionResolveFunctor cdExpressionResolveFunctor =
        new CDExpressionResolveFunctor(engineExpressionService, ambiance);
    if (unresolvedTasManifestsPackage != null
        && EmptyPredicate.isNotEmpty(unresolvedTasManifestsPackage.getManifestYml())) {
      resolvedTasManifestsPackage.setManifestYml((String) ExpressionEvaluatorUtils.updateExpressions(
          unresolvedTasManifestsPackage.getManifestYml(), cdExpressionResolveFunctor));
    }

    if (unresolvedTasManifestsPackage != null
        && EmptyPredicate.isNotEmpty(unresolvedTasManifestsPackage.getAutoscalarManifestYml())) {
      resolvedTasManifestsPackage.setAutoscalarManifestYml((String) ExpressionEvaluatorUtils.updateExpressions(
          unresolvedTasManifestsPackage.getAutoscalarManifestYml(), cdExpressionResolveFunctor));
    }

    List<String> varsYamlList = new ArrayList<>();
    if (unresolvedTasManifestsPackage != null && unresolvedTasManifestsPackage.getVariableYmls() != null) {
      for (String varsYaml : unresolvedTasManifestsPackage.getVariableYmls()) {
        varsYamlList.add((String) ExpressionEvaluatorUtils.updateExpressions(varsYaml, cdExpressionResolveFunctor));
      }
      resolvedTasManifestsPackage.setVariableYmls(varsYamlList);
    }

    CfRollingRollbackRequestNG cfRollingRollbackRequestNG =
        CfRollingRollbackRequestNG.builder()
            .applicationName(tasRollingDeployOutcome.getAppName())
            .accountId(accountId)
            .useCfCLI(true)
            .commandName(CfCommandTypeNG.TAS_ROLLING_ROLLBACK.name())
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .tasInfraConfig(tasInfraConfig)
            .tasArtifactConfig(tasArtifactConfig)
            .cfCommandTypeNG(CfCommandTypeNG.TAS_ROLLING_ROLLBACK)
            .tasManifestsPackage(resolvedTasManifestsPackage)
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .isFirstDeployment(tasStageExecutionDetails == null)
            .useAppAutoScalar(tasStageExecutionDetails != null && tasStageExecutionDetails.getIsAutoscalarEnabled())
            .desiredCount(tasStageExecutionDetails == null ? 0 : tasStageExecutionDetails.getDesiredCount())
            .routeMaps(tasStageExecutionDetails == null ? null : tasStageExecutionDetails.getRouteMaps())
            .cfCliVersion(tasStepHelper.cfCliVersionNGMapper(tasRollingDeployOutcome.getCfCliVersion()))
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                                  .taskType(TaskType.TAS_ROLLING_ROLLBACK.name())
                                  .parameters(new Object[] {cfRollingRollbackRequestNG})
                                  .build();
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        Arrays.asList(CfCommandUnitConstants.Rollback, CfCommandUnitConstants.Wrapup),
        TaskType.TAS_ROLLING_ROLLBACK.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(tasRollingRollbackStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
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

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<CfCommandResponseNG> responseDataSupplier)
      throws Exception {
    StepResponseBuilder builder = StepResponse.builder();

    CfRollingRollbackResponseNG response;
    try {
      response = (CfRollingRollbackResponseNG) responseDataSupplier.get();
    } catch (Exception ex) {
      log.error("Error while processing Tas response: {}", ExceptionUtils.getMessage(ex), ex);
      throw ex;
    }
    if (!response.getCommandExecutionStatus().equals(CommandExecutionStatus.SUCCESS)) {
      return StepResponse.builder()
          .status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder().setErrorMessage(response.getErrorMessage()).build())
          .unitProgressList(
              tasStepHelper
                  .completeUnitProgressData(response.getUnitProgressData(), ambiance, response.getErrorMessage())
                  .getUnitProgresses())
          .build();
    }
    List<ServerInstanceInfo> serverInstanceInfoList = getServerInstanceInfoList(response, ambiance);
    StepResponse.StepOutcome stepOutcome =
        instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfoList);
    builder.stepOutcome(stepOutcome);
    builder.unitProgressList(response.getUnitProgressData().getUnitProgresses());
    builder.status(Status.SUCCEEDED);
    return builder.build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(CfRollingRollbackResponseNG response, Ambiance ambiance) {
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        (TanzuApplicationServiceInfrastructureOutcome) outcomeService.resolve(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    if (response == null) {
      log.error("Could not generate server instance info for rolling deploy step");
      return Collections.emptyList();
    }
    List<CfInternalInstanceElement> instances = response.getNewAppInstances();
    if (!isNull(instances)) {
      return instances.stream()
          .map(instance -> getServerInstance(instance, infrastructureOutcome))
          .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  private ServerInstanceInfo getServerInstance(
      CfInternalInstanceElement instance, TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome) {
    return TasServerInstanceInfo.builder()
        .id(instance.getApplicationId() + ":" + instance.getInstanceIndex())
        .instanceIndex(instance.getInstanceIndex())
        .tasApplicationName(instance.getDisplayName())
        .tasApplicationGuid(instance.getApplicationId())
        .organization(infrastructureOutcome.getOrganization())
        .space(infrastructureOutcome.getSpace())
        .build();
  }
}
