/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.elastigroup.beans.ElastigroupExecutionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExceptionPassThroughData;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.elastigroup.request.AwsConnectedCloudProvider;
import io.harness.delegate.task.elastigroup.request.AwsLoadBalancerConfig;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupSetupResponse;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ElastigroupBGStageSetupStep
    extends TaskChainExecutableWithRollbackAndRbac implements ElastigroupStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ELASTIGROUP_BG_STAGE_SETUP.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  static final String ELASTIGROUP_BG_STAGE_SETUP_COMMAND_NAME = "ElastigroupBGStageSetup";
  public static final int DEFAULT_CURRENT_RUNNING_INSTANCE_COUNT = 2;

  @Inject private ElastigroupStepCommonHelper elastigroupStepCommonHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  public TaskChainResponse executeElastigroupTask(Ambiance ambiance, StepBaseParameters stepParameters,
      ElastigroupExecutionPassThroughData passThroughData, UnitProgressData unitProgressData) {
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    ElastigroupBGStageSetupStepParameters elastigroupBGStageSetupStepParameters =
        (ElastigroupBGStageSetupStepParameters) stepParameters.getSpec();

    ElastiGroup generatedElastigroupConfig = elastigroupStepCommonHelper.generateOriginalConfigFromJson(
        passThroughData.getElastigroupConfiguration(), elastigroupBGStageSetupStepParameters.getInstances(), ambiance);

    List<LoadBalancerDetailsForBGDeployment> loadBalancerDetailsForBGDeployments =
        elastigroupStepCommonHelper.addLoadBalancerConfigAfterExpressionEvaluation(
            elastigroupBGStageSetupStepParameters.getLoadBalancers()
                .stream()
                .map(loadBalancer -> (AwsLoadBalancerConfigYaml) loadBalancer.getSpec())
                .collect(Collectors.toList()),
            ambiance);

    String awsConnectorRef = elastigroupStepCommonHelper.renderExpression(ambiance,
        ((AwsCloudProviderBasicConfig) elastigroupBGStageSetupStepParameters.getConnectedCloudProvider().getSpec())
            .getConnectorRef()
            .getValue());
    ConnectorInfoDTO connectorInfoDTO = elastigroupStepCommonHelper.getConnector(awsConnectorRef, ambiance);

    String awsRegion = elastigroupStepCommonHelper.renderExpression(ambiance,
        ((AwsCloudProviderBasicConfig) elastigroupBGStageSetupStepParameters.getConnectedCloudProvider().getSpec())
            .getRegion()
            .getValue());

    AwsLoadBalancerConfig loadBalancerConfig =
        AwsLoadBalancerConfig.builder().loadBalancerDetails(loadBalancerDetailsForBGDeployments).build();
    AwsConnectedCloudProvider connectedCloudProvider =
        AwsConnectedCloudProvider.builder()
            .connectorRef(awsConnectorRef)
            .connectorInfoDTO(connectorInfoDTO)
            .region(awsRegion)
            .encryptionDetails(elastigroupStepCommonHelper.getEncryptedDataDetail(connectorInfoDTO, ambiance))
            .build();

    ElastigroupSetupCommandRequest elastigroupSetupCommandRequest =
        ElastigroupSetupCommandRequest.builder()
            .blueGreen(true)
            .elastigroupNamePrefix(passThroughData.getElastigroupNamePrefix())
            .accountId(accountId)
            .spotInstConfig(passThroughData.getSpotInstConfig())
            .elastigroupConfiguration(passThroughData.getElastigroupConfiguration())
            .startupScript(passThroughData.getBase64EncodedStartupScript())
            .commandName(ELASTIGROUP_BG_STAGE_SETUP_COMMAND_NAME)
            .image(passThroughData.getImage())
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(getSteadyStateTimeout(stepParameters))
            .maxInstanceCount(generatedElastigroupConfig.getCapacity().getMaximum())
            .useCurrentRunningInstanceCount(ElastigroupInstancesType.CURRENT_RUNNING.equals(
                elastigroupBGStageSetupStepParameters.getInstances().getType()))
            .connectedCloudProvider(connectedCloudProvider)
            .loadBalancerConfig(loadBalancerConfig)
            .generatedElastigroupConfig(generatedElastigroupConfig)
            .resizeStrategy(passThroughData.getResizeStrategy())
            .build();

    passThroughData.setGeneratedElastigroupConfig(generatedElastigroupConfig);
    passThroughData.setLoadBalancerConfig(loadBalancerConfig);
    passThroughData.setConnectedCloudProvider(connectedCloudProvider);

    return elastigroupStepCommonHelper.queueElastigroupTask(stepParameters, elastigroupSetupCommandRequest, ambiance,
        passThroughData, true, TaskType.ELASTIGROUP_BG_STAGE_SETUP_COMMAND_TASK_NG);
  }

  private int getSteadyStateTimeout(StepBaseParameters stepParameters) {
    return CDStepHelper.getTimeoutInMin(stepParameters);
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
    // nothing
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    log.info("Calling executeNextLink");
    return elastigroupStepCommonHelper.executeNextLink(
        this, ambiance, stepParameters, passThroughData, responseSupplier);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof ElastigroupStepExceptionPassThroughData) {
      return elastigroupStepCommonHelper.handleStepExceptionFailure(
          (ElastigroupStepExceptionPassThroughData) passThroughData);
    }

    log.info("Finalizing execution with passThroughData: " + passThroughData.getClass().getName());
    ElastigroupExecutionPassThroughData executionPassThroughData =
        (ElastigroupExecutionPassThroughData) passThroughData;

    ElastigroupSetupDataOutcome elastigroupSetupDataOutcome = ElastigroupSetupDataOutcome.builder().build();
    elastigroupSetupDataOutcome.setResizeStrategy(executionPassThroughData.getResizeStrategy());
    elastigroupSetupDataOutcome.setElastigroupNamePrefix(executionPassThroughData.getElastigroupNamePrefix());
    elastigroupSetupDataOutcome.setBlueGreen(executionPassThroughData.isBlueGreen());
    elastigroupSetupDataOutcome.setAwsConnectorRef(
        executionPassThroughData.getConnectedCloudProvider().getConnectorRef());
    elastigroupSetupDataOutcome.setAwsRegion(
        ((AwsConnectedCloudProvider) (executionPassThroughData.getConnectedCloudProvider())).getRegion());

    ElastigroupSetupResponse elastigroupSetupResponse;
    try {
      elastigroupSetupResponse = (ElastigroupSetupResponse) responseDataSupplier.get();
    } catch (Exception e) {
      elastigroupSetupDataOutcome.setSuccessful(false);
      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ELASTIGROUP_SETUP_OUTCOME,
          elastigroupSetupDataOutcome, StepOutcomeGroup.STAGE.name());
      log.error("Error while processing elastigroup task response: {}", e.getMessage(), e);
      return elastigroupStepCommonHelper.handleTaskException(ambiance, executionPassThroughData, e);
    }

    ElastigroupSetupResult elastigroupSetupResult = elastigroupSetupResponse.getElastigroupSetupResult();
    ElastiGroup oldElastiGroup = elastigroupStepCommonHelper.fetchOldElasticGroup(elastigroupSetupResult);

    elastigroupSetupDataOutcome.setUseCurrentRunningInstanceCount(
        elastigroupSetupResult.isUseCurrentRunningInstanceCount());
    elastigroupSetupDataOutcome.setMaxInstanceCount(elastigroupSetupResult.getMaxInstanceCount());
    elastigroupSetupDataOutcome.setOldElastigroupOriginalConfig(oldElastiGroup);
    elastigroupSetupDataOutcome.setNewElastigroupOriginalConfig(elastigroupSetupResult.getNewElastigroup());
    elastigroupSetupDataOutcome.setLoadBalancerDetailsForBGDeployments(
        elastigroupSetupResult.getLoadBalancerDetailsForBGDeployments());
    elastigroupSetupDataOutcome.setSuccessful(
        elastigroupSetupResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS);

    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(elastigroupSetupResponse.getUnitProgressData().getUnitProgresses());
    if (oldElastiGroup != null && oldElastiGroup.getCapacity() != null) {
      elastigroupSetupDataOutcome.setCurrentRunningInstanceCount(oldElastiGroup.getCapacity().getTarget());
    } else {
      elastigroupSetupDataOutcome.setCurrentRunningInstanceCount(DEFAULT_CURRENT_RUNNING_INSTANCE_COUNT);
    }

    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ELASTIGROUP_SETUP_OUTCOME,
        elastigroupSetupDataOutcome, StepOutcomeGroup.STAGE.name());

    if (elastigroupSetupResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return ElastigroupStepCommonHelper.getFailureResponseBuilder(elastigroupSetupResponse, stepResponseBuilder)
          .build();
    }

    elastigroupStepCommonHelper.saveSpotServerInstanceInfosToSweepingOutput(
        elastigroupSetupResult.getEc2InstanceIdsAdded(), elastigroupSetupResult.getEc2InstanceIdsExisting(), ambiance);

    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .outcome(elastigroupSetupDataOutcome)
                         .build())
        .build();
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    ElastigroupExecutionPassThroughData passThroughData =
        ElastigroupExecutionPassThroughData.builder().blueGreen(true).build();
    return elastigroupStepCommonHelper.startChainLink(ambiance, stepParameters, passThroughData);
  }
}
