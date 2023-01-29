/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.elastigroup.beans.ElastigroupExecutionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExecutorParams;
import io.harness.cdng.elastigroup.beans.ElastigroupSwapRouteDataOutcome;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.elastigroup.ElastigroupSwapRouteResult;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.elastigroup.request.ElastigroupSwapRouteCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupSwapRouteResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ElastigroupSwapRouteStepTest extends CDNGTestBase {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final String ELASTIGROUP_SWAP_ROUTE_COMMAND_NAME = "ElastigroupSwapRoute";
  public static final int DEFAULT_CURRENT_RUNNING_INSTANCE_COUNT = 2;

  @Mock private ElastigroupStepCommonHelper elastigroupStepCommonHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private ConnectorInfoDTO connectorInfoDTO;

  @InjectMocks private ElastigroupSwapRouteStep elastigroupSetupStep;

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void executeElastigroupTaskTest() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                            .build();
    String elastigroupJson = "elastigroupJson";
    ElastigroupFixedInstances elastigroupFixedInstances =
        ElastigroupFixedInstances.builder()
            .desired(ParameterField.<Integer>builder().value(1).build())
            .min(ParameterField.<Integer>builder().value(1).build())
            .max(ParameterField.<Integer>builder().value(1).build())
            .build();
    ElastigroupInstances elastigroupInstances =
        ElastigroupInstances.builder().spec(elastigroupFixedInstances).type(ElastigroupInstancesType.FIXED).build();
    String elastigroupNamePrefix = "elastigroupNamePrefix";
    String startupScript = "startupScript";
    ElastigroupSwapRouteStepParameters elastigroupSwapRouteStepParameters =
        ElastigroupSwapRouteStepParameters.infoBuilder()
            .downsizeOldElastigroup(ParameterField.<Boolean>builder().value(Boolean.FALSE).build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(elastigroupSwapRouteStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();
    String ELASTIGROUP_SETUP_COMMAND_NAME = "ElastigroupSetup";
    ElastigroupStepExecutorParams elastigroupStepExecutorParams = ElastigroupStepExecutorParams.builder()
                                                                      .elastigroupConfiguration(elastigroupJson)
                                                                      .startupScript(startupScript)
                                                                      .build();
    ElastigroupInfrastructureOutcome elastigroupInfrastructureOutcome =
        ElastigroupInfrastructureOutcome.builder().build();
    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData =
        ElastigroupExecutionPassThroughData.builder().infrastructure(elastigroupInfrastructureOutcome).build();
    SpotInstConfig spotInstConfig = SpotInstConfig.builder().build();
    doReturn(spotInstConfig)
        .when(elastigroupStepCommonHelper)
        .getSpotInstConfig(elastigroupInfrastructureOutcome, ambiance);

    ElastiGroup elastiGroup = ElastiGroup.builder()
                                  .name(elastigroupNamePrefix)
                                  .id("123")
                                  .capacity(ElastiGroupCapacity.builder().maximum(1).minimum(1).target(1).build())
                                  .build();
    String connectorRef = "connectorRef";
    String region = "region";
    LoadBalancerDetailsForBGDeployment loadBalancer = LoadBalancerDetailsForBGDeployment.builder().build();
    ElastigroupSetupDataOutcome elastigroupSetupDataOutcome =
        ElastigroupSetupDataOutcome.builder()
            .elastigroupNamePrefix(elastigroupNamePrefix)
            .useCurrentRunningInstanceCount(false)
            .currentRunningInstanceCount(1)
            .maxInstanceCount(1)
            .isBlueGreen(true)
            .oldElastigroupOriginalConfig(elastiGroup)
            .newElastigroupOriginalConfig(elastiGroup)
            .awsConnectorRef(connectorRef)
            .awsRegion(region)
            .loadBalancerDetailsForBGDeployments(Arrays.asList(loadBalancer))
            .build();
    OptionalSweepingOutput optionalElastigroupSetupOutput =
        OptionalSweepingOutput.builder().output(elastigroupSetupDataOutcome).build();
    doReturn(optionalElastigroupSetupOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(
            ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.ELASTIGROUP_SETUP_OUTCOME));

    doReturn(connectorRef).when(elastigroupStepCommonHelper).renderExpression(ambiance, connectorRef);
    doReturn(connectorInfoDTO).when(elastigroupStepCommonHelper).getConnector(connectorRef, ambiance);

    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList();
    doReturn(encryptedDataDetails).when(elastigroupStepCommonHelper).getEncryptedDataDetail(connectorInfoDTO, ambiance);

    ElastigroupSwapRouteCommandRequest elastigroupSwapRouteCommandRequest =
        ElastigroupSwapRouteCommandRequest.builder()
            .blueGreen(elastigroupSetupDataOutcome.isBlueGreen())
            //            .awsEncryptedDetails(Collections.emptyList())
            //            .connectorInfoDTO(ConnectorInfoDTO.builder().build())
            .newElastigroup(elastigroupSetupDataOutcome.getNewElastigroupOriginalConfig())
            .oldElastigroup(elastigroupSetupDataOutcome.getOldElastigroupOriginalConfig())
            .elastigroupNamePrefix(elastigroupSetupDataOutcome.getElastigroupNamePrefix())
            .accountId("test-account")
            .downsizeOldElastigroup(
                String.valueOf(elastigroupSwapRouteStepParameters.getDownsizeOldElastigroup().getValue()))
            .resizeStrategy(elastigroupSetupDataOutcome.getResizeStrategy())
            //            .awsRegion(region)
            .spotInstConfig(spotInstConfig)
            //            .connectorInfoDTO(connectorInfoDTO)
            .commandName(ELASTIGROUP_SWAP_ROUTE_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(null))
            .timeoutIntervalInMin(10)
            //            .awsEncryptedDetails(encryptedDataDetails)
            //            .lBdetailsForBGDeploymentList(elastigroupSetupDataOutcome.getLoadBalancerDetailsForBGDeployments())
            .build();
    TaskChainResponse taskChainResponse = TaskChainResponse.builder()
                                              .chainEnd(false)
                                              .taskRequest(TaskRequest.newBuilder().build())
                                              .passThroughData(elastigroupExecutionPassThroughData)
                                              .build();
    doReturn(taskChainResponse)
        .when(elastigroupStepCommonHelper)
        .queueElastigroupTask(stepElementParameters, elastigroupSwapRouteCommandRequest, ambiance,
            elastigroupExecutionPassThroughData, true, TaskType.ELASTIGROUP_SWAP_ROUTE_COMMAND_TASK_NG);
    elastigroupSetupStep.executeElastigroupTask(
        ambiance, stepElementParameters, elastigroupExecutionPassThroughData, null);
  }

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextTest() throws Exception {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                            .build();
    ElastigroupSwapRouteStepParameters elastigroupSwapRouteStepParameters =
        ElastigroupSwapRouteStepParameters.infoBuilder()
            .downsizeOldElastigroup(ParameterField.<Boolean>builder().value(Boolean.FALSE).build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(elastigroupSwapRouteStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();
    String newElastiGroupId = "newElastiGroupId";
    ElastigroupSwapRouteResult elastigroupSwapRouteResult =
        ElastigroupSwapRouteResult.builder().newElastiGroupId(newElastiGroupId).build();
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    ElastigroupSwapRouteResponse elastigroupSwapRouteResponse =
        ElastigroupSwapRouteResponse.builder()
            .elastigroupSwapRouteResult(elastigroupSwapRouteResult)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .unitProgressData(unitProgressData)
            .build();
    ElastigroupInfrastructureOutcome elastigroupInfrastructureOutcome =
        ElastigroupInfrastructureOutcome.builder().build();
    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData =
        ElastigroupExecutionPassThroughData.builder().infrastructure(elastigroupInfrastructureOutcome).build();
    ResponseData responseData = elastigroupSwapRouteResponse;
    ThrowingSupplier<ResponseData> responseSupplier = () -> responseData;
    StepResponse stepResponse = elastigroupSetupStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, elastigroupExecutionPassThroughData, responseSupplier);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(1);
    assertThat(
        ((ElastigroupSwapRouteDataOutcome) stepResponse.getStepOutcomes().stream().findFirst().get().getOutcome())
            .getNewElastigroupId())
        .isEqualTo(newElastiGroupId);
  }
}
