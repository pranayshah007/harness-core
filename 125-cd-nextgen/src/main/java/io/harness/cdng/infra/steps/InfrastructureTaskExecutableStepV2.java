package io.harness.cdng.infra.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.helper.ExecutionInfoKeyMapper;
import io.harness.cdng.execution.helper.StageExecutionHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.steps.executable.TaskExecutableWithRbac;
import io.harness.steps.shellscript.K8sInfraDelegateConfigOutput;
import io.harness.supplier.ThrowingSupplier;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class InfrastructureTaskExecutableStepV2 extends AbstractInfrastructureTaskExecutableStep
    implements TaskExecutableWithRbac<InfrastructureTaskExecutableStepV2Params, DelegateResponseData> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.INFRASTRUCTURE_TASKSTEP_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  public static final String DEFAULT_TIMEOUT = "10m";

  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject private InfrastructureStepHelper infrastructureStepHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private StageExecutionHelper stageExecutionHelper;
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;

  @Override
  public Class<InfrastructureTaskExecutableStepV2Params> getStepParametersClass() {
    return null;
  }

  @Override
  public void validateResources(Ambiance ambiance, InfrastructureTaskExecutableStepV2Params stepParameters) {
    final InfrastructureConfig infrastructureConfig = fetchInfraEntityFromDB(ambiance, stepParameters);
    final Infrastructure infraSpec = infrastructureConfig.getInfrastructureDefinitionConfig().getSpec();

    publishInfraOutput(ambiance, infraSpec);

    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    String principal = executionPrincipalInfo.getPrincipal();
    if (isEmpty(principal)) {
      return;
    }
    Set<EntityDetailProtoDTO> entityDetails =
        entityReferenceExtractorUtils.extractReferredEntities(ambiance, infraSpec);
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails);
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, InfrastructureTaskExecutableStepV2Params stepParameters, StepInputPackage inputPackage) {
    final InfrastructureConfig infrastructureConfig = fetchInfraEntityFromDB(ambiance, stepParameters);
    final Infrastructure infraSpec = infrastructureConfig.getInfrastructureDefinitionConfig().getSpec();

    // Create delegate task for infra if needed
    if (isTaskStep(infraSpec.getKind())) {
      return super.obtainTaskInternal(ambiance, infraSpec, inputPackage).get();
    }

    // If delegate task is not needed, just validate the infra spec
    executeSync(ambiance, infraSpec);
    return null;
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      InfrastructureTaskExecutableStepV2Params stepParameters,
      ThrowingSupplier<DelegateResponseData> responseDataSupplier) throws Exception {
    final InfrastructureOutcome infraOutput = fetchInfraOutputOrThrow(ambiance);

    // handle response from delegate if task was created
    if (isTaskStep(infraOutput.getKind())) {
      return super.handleTaskResultWithSecurityContext(ambiance, infraOutput, responseDataSupplier);
    }

    // just produce step response. Sync flow
    return produceStepResponse(ambiance, infraOutput);
  }

  private StepResponse produceStepResponse(Ambiance ambiance, InfrastructureOutcome infraOutput) {
    final long startTime = System.currentTimeMillis();

    NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance, true);
    saveExecutionLog(logCallback, "Starting infrastructure step...");

    final OutcomeSet outcomeSet = fetchRequiredOutcomes(ambiance);
    final EnvironmentOutcome environmentOutcome = outcomeSet.getEnvironmentOutcome();
    final ServiceStepOutcome serviceOutcome = outcomeSet.getServiceStepOutcome();

    if (environmentOutcome != null) {
      if (isNotEmpty(environmentOutcome.getName())) {
        saveExecutionLog(logCallback, color(format("Environment Name: %s", environmentOutcome.getName()), Yellow));
      }

      if (environmentOutcome.getType() != null && isNotEmpty(environmentOutcome.getType().name())) {
        saveExecutionLog(
            logCallback, color(format("Environment Type: %s", environmentOutcome.getType().name()), Yellow));
      }
    }

    if (infraOutput != null && isNotEmpty(infraOutput.getKind())) {
      saveExecutionLog(logCallback, color(format("Infrastructure Definition Type: %s", infraOutput.getKind()), Yellow));
    }

    saveExecutionLog(logCallback, color("Environment information fetched", Green));

    publishInfraDelegateConfigOutput(serviceOutcome, infraOutput, ambiance);

    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    String infrastructureKind = infraOutput.getKind();
    if (stageExecutionHelper.shouldSaveStageExecutionInfo(infrastructureKind)) {
      ExecutionInfoKey executionInfoKey = ExecutionInfoKeyMapper.getExecutionInfoKey(
          ambiance, infrastructureKind, environmentOutcome, serviceOutcome, infraOutput);
      stageExecutionHelper.saveStageExecutionInfoAndPublishExecutionInfoKey(
          ambiance, executionInfoKey, infrastructureKind);
      if (stageExecutionHelper.isRollbackArtifactRequiredPerInfrastructure(infrastructureKind)) {
        stageExecutionHelper.addRollbackArtifactToStageOutcomeIfPresent(
            ambiance, stepResponseBuilder, executionInfoKey, infrastructureKind);
      }
    }

    if (logCallback != null) {
      logCallback.saveExecutionLog(
          color("Completed infrastructure step", Green), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    }

    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .outcome(infraOutput)
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .group(OutcomeExpressionConstants.INFRASTRUCTURE_GROUP)
                         .build())
        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setUnitName("Execute")
                                                        .setStatus(UnitStatus.SUCCESS)
                                                        .setStartTime(startTime)
                                                        .setEndTime(System.currentTimeMillis())
                                                        .build()))
        .build();
  }

  private void executeSync(Ambiance ambiance, Infrastructure infrastructure) {
    NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance, true);
    validateConnector(infrastructure, ambiance);
    saveExecutionLog(logCallback, "Fetching environment information...");
    validateInfrastructure(infrastructure, ambiance);
  }

  private boolean isTaskStep(String infraKind) {
    return InfrastructureKind.SSH_WINRM_AZURE.equals(infraKind) || InfrastructureKind.SSH_WINRM_AWS.equals(infraKind);
  }

  private InfrastructureConfig fetchInfraEntityFromDB(
      Ambiance ambiance, InfrastructureTaskExecutableStepV2Params stepParameters) {
    Optional<InfrastructureEntity> infrastructureEntity =
        infrastructureEntityService.get(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
            AmbianceUtils.getProjectIdentifier(ambiance), stepParameters.getEnvRef().getValue(),
            stepParameters.getInfraRef().getValue());
    if (infrastructureEntity.isEmpty()) {
      // todo: (yogesh) improve this
      throw new InvalidRequestException("infra not found");
    }

    return InfrastructureEntityConfigMapper.toInfrastructureConfig(infrastructureEntity.get());
  }

  private void publishInfraDelegateConfigOutput(
      ServiceStepOutcome serviceOutcome, InfrastructureOutcome infrastructureOutcome, Ambiance ambiance) {
    if (ServiceSpecType.SSH.equals(serviceOutcome.getType())) {
      publishSshInfraDelegateConfigOutput(infrastructureOutcome, ambiance);
      return;
    }

    if (ServiceSpecType.WINRM.equals(serviceOutcome.getType())) {
      publishWinRmInfraDelegateConfigOutput(infrastructureOutcome, ambiance);
      return;
    }

    if (infrastructureOutcome instanceof K8sGcpInfrastructureOutcome
        || infrastructureOutcome instanceof K8sDirectInfrastructureOutcome
        || infrastructureOutcome instanceof K8sAzureInfrastructureOutcome) {
      K8sInfraDelegateConfig k8sInfraDelegateConfig =
          cdStepHelper.getK8sInfraDelegateConfig(infrastructureOutcome, ambiance);

      K8sInfraDelegateConfigOutput k8sInfraDelegateConfigOutput =
          K8sInfraDelegateConfigOutput.builder().k8sInfraDelegateConfig(k8sInfraDelegateConfig).build();
      executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.K8S_INFRA_DELEGATE_CONFIG_OUTPUT_NAME,
          k8sInfraDelegateConfigOutput, StepCategory.STAGE.name());
    }
  }
}
