package io.harness.cdng.ecs;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExceptionPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.serverless.ServerlessStepCommonHelper;
import io.harness.cdng.serverless.beans.ServerlessExecutionPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessGitFetchFailurePassThroughData;
import io.harness.cdng.serverless.beans.ServerlessStepExceptionPassThroughData;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.exception.ServerlessNGException;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.request.EcsRollingDeployRequest;
import io.harness.delegate.task.ecs.response.EcsRollingDeployResponse;
import io.harness.delegate.task.serverless.response.ServerlessDeployResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class EcsRollingDeployStep extends TaskChainExecutableWithRollbackAndRbac implements EcsStepExecutor {

  public static final StepType STEP_TYPE = StepType.newBuilder()
          .setType(ExecutionNodeType.ECS_ROLLING_DEPLOY.getYamlType())
          .setStepCategory(StepCategory.STEP)
          .build();

  private final String ECS_ROLLING_DEPLOY_COMMAND_NAME = "EcsRollingDeploy";

  @Inject
  private EcsStepCommonHelper ecsStepCommonHelper;
  @Inject private EcsStepHelperImpl ecsStepHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // nothing
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    log.info("Calling executeNextLink");
    return ecsStepCommonHelper.executeNextLink(
            this, ambiance, stepParameters, passThroughData, responseSupplier, ecsStepHelper);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof EcsGitFetchFailurePassThroughData) {
      return ecsStepCommonHelper.handleGitTaskFailure(
              (EcsGitFetchFailurePassThroughData) passThroughData);
    } else if (passThroughData instanceof EcsStepExceptionPassThroughData) {
      return ecsStepCommonHelper.handleStepExceptionFailure(
              (EcsStepExceptionPassThroughData) passThroughData);
    }

    log.info("Finalizing execution with passThroughData: " + passThroughData.getClass().getName());
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
            (EcsExecutionPassThroughData) passThroughData;
    InfrastructureOutcome infrastructureOutcome = ecsExecutionPassThroughData.getInfrastructure();
    EcsRollingDeployResponse ecsRollingDeployResponse;
    try {
      ecsRollingDeployResponse = (EcsRollingDeployResponse) responseDataSupplier.get();
    } catch (Exception e) {
      EcsNGException ecsNGException = ExceptionUtils.cause(EcsNGException.class, e);
      if (ecsNGException == null) {
        log.error("Error while processing ecs task response: {}", e.getMessage(), e);
        return ecsStepCommonHelper.handleTaskException(ambiance, ecsExecutionPassThroughData, e);
      }
      log.error("Error while processing ecs task response: {}", e.getMessage(), e);
      return ecsStepCommonHelper.handleTaskException(ambiance, ecsExecutionPassThroughData, e);
    }
    StepResponse.StepResponseBuilder stepResponseBuilder =
            StepResponse.builder().unitProgressList(ecsRollingDeployResponse.getUnitProgressData().getUnitProgresses());
    if (ecsRollingDeployResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return EcsStepCommonHelper.getFailureResponseBuilder(ecsRollingDeployResponse, stepResponseBuilder)
              .build();
    }

//    List<ServerInstanceInfo> functionInstanceInfos = serverlessStepCommonHelper.getFunctionInstanceInfo(
//            ecsRollingDeployResponse, serverlessAwsLambdaStepHelper, infrastructureOutcome.getInfrastructureKey());
//    StepResponse.StepOutcome stepOutcome =
//            instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, functionInstanceInfos);
    // todo
    StepResponse.StepOutcome stepOutcome = StepResponse.StepOutcome.builder()
            .name(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME)
            .build();
    return stepResponseBuilder.status(Status.SUCCEEDED).stepOutcome(stepOutcome).build();
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return ecsStepCommonHelper.startChainLink(ambiance, stepParameters, ecsStepHelper);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return null;
  }

  @Override
  public TaskChainResponse executeEcsTask(Ambiance ambiance, StepElementParameters stepElementParameters, EcsExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData, EcsStepExecutorParams ecsStepExecutorParams) {
    InfrastructureOutcome infrastructureOutcome = executionPassThroughData.getInfrastructure();
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    EcsRollingDeployRequest ecsRollingDeployRequest = EcsRollingDeployRequest.builder()
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_ROLLING_DEPLOY)
            .commandName(ECS_ROLLING_DEPLOY_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .ecsTaskDefinitionManifestContent(ecsStepExecutorParams.getEcsTaskDefinitionManifestContent())
            .ecsServiceDefinitionManifestContent(ecsStepExecutorParams.getEcsServiceDefinitionManifestContent())
            .ecsScalableTargetManifestContentList(ecsStepExecutorParams.getEcsScalableTargetManifestContentList())
            .ecsScalingPolicyManifestContentList(ecsStepExecutorParams.getEcsScalingPolicyManifestContentList())
            .build();

    return ecsStepCommonHelper.queueEcsTask(
            stepElementParameters, ecsRollingDeployRequest, ambiance, executionPassThroughData, true);
  }
}
