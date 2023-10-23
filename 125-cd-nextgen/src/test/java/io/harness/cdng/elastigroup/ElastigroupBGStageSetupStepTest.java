/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.elastigroup.beans.ElastigroupExecutionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExecutorParams;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.elastigroup.request.AwsConnectedCloudProvider;
import io.harness.delegate.task.elastigroup.request.AwsLoadBalancerConfig;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupPreFetchResponse;
import io.harness.delegate.task.elastigroup.response.ElastigroupSetupResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
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

public class ElastigroupBGStageSetupStepTest extends CDNGTestBase {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  public static final int DEFAULT_CURRENT_RUNNING_INSTANCE_COUNT = 2;

  @Mock private ElastigroupStepCommonHelper elastigroupStepCommonHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  @InjectMocks private ElastigroupBGStageSetupStep elastigroupSetupStep;

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void executeElastigroupTaskTest() {
    Ambiance ambiance = anAmbiance();
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
    String connectorRef = "connectorRef";
    String region = "region";
    AwsLoadBalancerConfigYaml awsLoadBalancerConfigYaml = AwsLoadBalancerConfigYaml.builder().build();
    LoadBalancer loadBalancer =
        LoadBalancer.builder().type(LoadBalancerType.AWS_LOAD_BALANCER_CONFIG).spec(awsLoadBalancerConfigYaml).build();
    AwsCloudProviderBasicConfig awsCloudProviderBasicConfig =
        AwsCloudProviderBasicConfig.builder()
            .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
            .region(ParameterField.<String>builder().value(region).build())
            .build();
    CloudProvider cloudProvider =
        CloudProvider.builder().type(CloudProviderType.AWS).spec(awsCloudProviderBasicConfig).build();
    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder().build();
    ConnectorConfigDTO connectorConfigDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(connectorConfigDTO).connectorType(ConnectorType.AWS).build();
    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList();
    AwsConnectedCloudProvider awsConnectedCloudProvider = AwsConnectedCloudProvider.builder()
                                                              .region(region)
                                                              .connectorInfoDTO(connectorInfoDTO)
                                                              .encryptionDetails(encryptedDataDetails)
                                                              .build();
    ElastigroupBGStageSetupStepParameters elastigroupBGStageSetupStepParameters =
        ElastigroupBGStageSetupStepParameters.infoBuilder()
            .name(ParameterField.<String>builder().value(elastigroupNamePrefix).build())
            .instances(elastigroupInstances)
            .connectedCloudProvider(cloudProvider)
            .loadBalancers(Arrays.asList(loadBalancer))
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(elastigroupBGStageSetupStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();
    String ELASTIGROUP_SETUP_COMMAND_NAME = "ElastigroupBGStageSetup";
    ElastigroupStepExecutorParams elastigroupStepExecutorParams = ElastigroupStepExecutorParams.builder()
                                                                      .elastigroupConfiguration(elastigroupJson)
                                                                      .startupScript(startupScript)
                                                                      .build();
    ElastigroupInfrastructureOutcome elastigroupInfrastructureOutcome =
        ElastigroupInfrastructureOutcome.builder().build();

    SpotInstConfig spotInstConfig = SpotInstConfig.builder().build();
    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData =
        ElastigroupExecutionPassThroughData.builder()
            .infrastructure(elastigroupInfrastructureOutcome)
            .connectedCloudProvider(awsConnectedCloudProvider)
            .elastigroupNamePrefix(elastigroupNamePrefix)
            .elastigroupConfiguration(elastigroupJson)
            .base64EncodedStartupScript(startupScript)
            .spotInstConfig(spotInstConfig)
            .build();
    doReturn(spotInstConfig)
        .when(elastigroupStepCommonHelper)
        .getSpotInstConfig(elastigroupInfrastructureOutcome, ambiance);
    ElastiGroup elastiGroup =
        ElastiGroup.builder().capacity(ElastiGroupCapacity.builder().maximum(1).minimum(1).target(1).build()).build();
    doReturn(elastiGroup).when(elastigroupStepCommonHelper).generateConfigFromJson(elastigroupJson);
    doReturn(startupScript).when(elastigroupStepCommonHelper).getBase64EncodedStartupScript(ambiance, startupScript);
    ElastigroupSetupCommandRequest elastigroupSetupCommandRequest =
        ElastigroupSetupCommandRequest.builder()
            .blueGreen(true)
            .elastigroupNamePrefix(elastigroupNamePrefix)
            .accountId("test-account")
            .spotInstConfig(spotInstConfig)
            .elastigroupConfiguration(elastigroupStepExecutorParams.getElastigroupConfiguration())
            .startupScript(startupScript)
            .commandName(ELASTIGROUP_SETUP_COMMAND_NAME)
            .image(elastigroupStepExecutorParams.getImage())
            .commandUnitsProgress(null)
            .timeoutIntervalInMin(10)
            .maxInstanceCount(1)
            .useCurrentRunningInstanceCount(false)
            .generatedElastigroupConfig(elastiGroup)
            .loadBalancerConfig(AwsLoadBalancerConfig.builder().build())
            .connectedCloudProvider(awsConnectedCloudProvider)
            .build();
    doReturn(connectorInfoDTO).when(elastigroupStepCommonHelper).getConnector(null, ambiance);
    doReturn(region).when(elastigroupStepCommonHelper).renderExpression(ambiance, region);
    TaskChainResponse taskChainResponse = TaskChainResponse.builder()
                                              .chainEnd(false)
                                              .taskRequest(TaskRequest.newBuilder().build())
                                              .passThroughData(elastigroupExecutionPassThroughData)
                                              .build();
    doReturn(taskChainResponse)
        .when(elastigroupStepCommonHelper)
        .queueElastigroupTask(stepElementParameters, elastigroupSetupCommandRequest, ambiance,
            elastigroupExecutionPassThroughData, true, TaskType.ELASTIGROUP_BG_STAGE_SETUP_COMMAND_TASK_NG);
    doReturn(
        ElastiGroup.builder().capacity(ElastiGroupCapacity.builder().maximum(1).minimum(1).target(1).build()).build())
        .when(elastigroupStepCommonHelper)
        .generateOriginalConfigFromJson(
            elastigroupJson, elastigroupBGStageSetupStepParameters.getInstances(), ambiance);
    elastigroupSetupStep.executeElastigroupTask(
        ambiance, stepElementParameters, elastigroupExecutionPassThroughData, null);
    verify(elastigroupStepCommonHelper)
        .queueElastigroupTask(eq(stepElementParameters), any(), eq(ambiance),
            any(ElastigroupExecutionPassThroughData.class), eq(true),
            eq(TaskType.ELASTIGROUP_BG_STAGE_SETUP_COMMAND_TASK_NG));
  }

  @Test
  @Owner(developers = {PIYUSH_BHUWALKA})
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextTest() throws Exception {
    Ambiance ambiance = anAmbiance();
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
    String connectorRef = "connectorRef";
    String region = "region";
    AwsLoadBalancerConfigYaml awsLoadBalancerConfigYaml = AwsLoadBalancerConfigYaml.builder().build();
    LoadBalancer loadBalancer =
        LoadBalancer.builder().type(LoadBalancerType.AWS_LOAD_BALANCER_CONFIG).spec(awsLoadBalancerConfigYaml).build();
    AwsCloudProviderBasicConfig awsCloudProviderBasicConfig =
        AwsCloudProviderBasicConfig.builder()
            .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
            .region(ParameterField.<String>builder().value(region).build())
            .build();
    CloudProvider cloudProvider =
        CloudProvider.builder().type(CloudProviderType.AWS).spec(awsCloudProviderBasicConfig).build();
    ElastigroupBGStageSetupStepParameters elastigroupBGStageSetupStepParameters =
        ElastigroupBGStageSetupStepParameters.infoBuilder()
            .name(ParameterField.<String>builder().value(elastigroupNamePrefix).build())
            .instances(elastigroupInstances)
            .connectedCloudProvider(cloudProvider)
            .loadBalancers(Arrays.asList(loadBalancer))
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(elastigroupBGStageSetupStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();
    ElastiGroup elastiGroup = ElastiGroup.builder()
                                  .name(elastigroupNamePrefix)
                                  .id("123")
                                  .capacity(ElastiGroupCapacity.builder().maximum(1).minimum(1).target(1).build())
                                  .build();
    ElastigroupSetupResult elastigroupSetupResult = ElastigroupSetupResult.builder()
                                                        .elastigroupNamePrefix(elastigroupNamePrefix)
                                                        .useCurrentRunningInstanceCount(false)
                                                        .newElastigroup(elastiGroup)
                                                        .elastigroupOriginalConfig(elastiGroup)
                                                        .maxInstanceCount(1)
                                                        .groupToBeDownsized(Arrays.asList(elastiGroup))
                                                        .build();
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    ElastigroupSetupResponse elastigroupSetupResponse = ElastigroupSetupResponse.builder()
                                                            .elastigroupSetupResult(elastigroupSetupResult)
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .unitProgressData(unitProgressData)
                                                            .build();
    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder().build();
    ConnectorConfigDTO connectorConfigDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(connectorConfigDTO).connectorType(ConnectorType.AWS).build();
    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList();
    ElastigroupInfrastructureOutcome elastigroupInfrastructureOutcome =
        ElastigroupInfrastructureOutcome.builder().build();
    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData =
        ElastigroupExecutionPassThroughData.builder()
            .infrastructure(elastigroupInfrastructureOutcome)
            .connectedCloudProvider(AwsConnectedCloudProvider.builder()
                                        .region(region)
                                        .connectorInfoDTO(connectorInfoDTO)
                                        .encryptionDetails(encryptedDataDetails)
                                        .build())
            .elastigroupNamePrefix(elastigroupNamePrefix)
            .build();
    ResponseData responseData = elastigroupSetupResponse;
    ThrowingSupplier<ResponseData> responseSupplier = () -> responseData;
    doReturn(elastiGroup).when(elastigroupStepCommonHelper).fetchOldElasticGroup(elastigroupSetupResult);
    doReturn(
        ElastiGroup.builder().capacity(ElastiGroupCapacity.builder().maximum(1).minimum(1).target(1).build()).build())
        .when(elastigroupStepCommonHelper)
        .generateOriginalConfigFromJson(
            elastigroupJson, elastigroupBGStageSetupStepParameters.getInstances(), ambiance);
    StepResponse stepResponse = elastigroupSetupStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, elastigroupExecutionPassThroughData, responseSupplier);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(1);
    assertThat(((ElastigroupSetupDataOutcome) stepResponse.getStepOutcomes().stream().findFirst().get().getOutcome())
                   .getElastigroupNamePrefix())
        .isEqualTo(elastigroupNamePrefix);
  }

  @Test
  @Owner(developers = {FILIP})
  @Category(UnitTests.class)
  public void executeElastigroupTaskShouldQueueElastigroupTaskTest() throws Exception {
    // Given
    StepElementParameters stepParameters =
        StepElementParameters.builder()
            .spec(ElastigroupBGStageSetupStepParameters.infoBuilder()
                      .instances(ElastigroupInstances.builder().type(ElastigroupInstancesType.FIXED).build())
                      .loadBalancers(singletonList(
                          LoadBalancer.builder().spec(AwsLoadBalancerConfigYaml.builder().build()).build()))
                      .connectedCloudProvider(
                          CloudProvider.builder()
                              .spec(AwsCloudProviderBasicConfig.builder()
                                        .connectorRef(ParameterField.createValueField("aws-ConnectorDisconnectHandler-ref"))
                                        .region(ParameterField.createValueField("us-east-1"))
                                        .build())
                              .build())
                      .build())
            .build();

    when(elastigroupStepCommonHelper.generateOriginalConfigFromJson(any(), any(), any()))
        .thenReturn(ElastiGroup.builder()
                        .id("elastiid")
                        .name("nameelast")
                        .capacity(ElastiGroupCapacity.builder().minimum(10).target(20).maximum(30).build())
                        .build());

    ElastigroupExecutionPassThroughData passThroughData = ElastigroupExecutionPassThroughData.builder()
                                                              .elastigroupNamePrefix("name-prefix")
                                                              .base64EncodedStartupScript("startupscript")
                                                              .image("image")
                                                              .build();

    // When
    elastigroupSetupStep.executeElastigroupTask(anAmbiance(), stepParameters, passThroughData, null);

    // Then
    verify(elastigroupStepCommonHelper)
        .queueElastigroupTask(eq(stepParameters), any(), eq(anAmbiance()), eq(passThroughData), eq(true),
            eq(TaskType.ELASTIGROUP_BG_STAGE_SETUP_COMMAND_TASK_NG));
  }

  @Test
  @Owner(developers = {FILIP})
  @Category(UnitTests.class)
  public void getStepParametersClassTest() {
    assertThat(elastigroupSetupStep.getStepParametersClass()).isEqualTo(StepBaseParameters.class);
  }

  @Test
  @Owner(developers = {FILIP})
  @Category(UnitTests.class)
  public void executeNextLinkWithSecurityContextTest() throws Exception {
    // Given
    StepElementParameters stepElementParameters = StepElementParameters.builder().build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    ElastigroupExecutionPassThroughData passThroughData = ElastigroupExecutionPassThroughData.builder().build();
    ThrowingSupplier<ResponseData> responseDataThrowingSupplier = () -> ElastigroupPreFetchResponse.builder().build();

    // When
    elastigroupSetupStep.executeNextLinkWithSecurityContext(
        anAmbiance(), stepElementParameters, stepInputPackage, passThroughData, responseDataThrowingSupplier);

    // Then
    verify(elastigroupStepCommonHelper)
        .executeNextLink(eq(elastigroupSetupStep), eq(anAmbiance()), eq(stepElementParameters), eq(passThroughData),
            eq(responseDataThrowingSupplier));
  }

  @Test
  @Owner(developers = {FILIP})
  @Category(UnitTests.class)
  public void startChainLinkAfterRbacTest() {
    // Given
    StepElementParameters stepElementParameters = StepElementParameters.builder().build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();

    // When
    elastigroupSetupStep.startChainLinkAfterRbac(anAmbiance(), stepElementParameters, stepInputPackage);

    // Then
    ElastigroupExecutionPassThroughData passThroughData =
        ElastigroupExecutionPassThroughData.builder().blueGreen(true).build();

    verify(elastigroupStepCommonHelper)
        .startChainLink(eq(anAmbiance()), eq(stepElementParameters), eq(passThroughData));
  }

  @Test
  @Owner(developers = {FILIP})
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextPositiveTest() throws Exception {
    // Given
    StepElementParameters stepParameters = StepElementParameters.builder().build();
    ElastigroupExecutionPassThroughData passThroughData =
        ElastigroupExecutionPassThroughData.builder()
            .connectedCloudProvider(AwsConnectedCloudProvider.builder().build())
            .build();
    ThrowingSupplier<ResponseData> throwingSupplier = ()
        -> ElastigroupSetupResponse.builder()
               .elastigroupSetupResult(ElastigroupSetupResult.builder().build())
               .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
               .unitProgressData(UnitProgressData.builder().build())
               .build();

    // When
    final StepResponse response = elastigroupSetupStep.finalizeExecutionWithSecurityContext(
        anAmbiance(), stepParameters, passThroughData, throwingSupplier);

    // Then
    assertThat(response).isNotNull().extracting(StepResponse::getStatus).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = {FILIP})
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextNegativeTest() throws Exception {
    // Given
    StepElementParameters stepParameters = StepElementParameters.builder().build();
    ElastigroupExecutionPassThroughData passThroughData =
        ElastigroupExecutionPassThroughData.builder()
            .connectedCloudProvider(AwsConnectedCloudProvider.builder().build())
            .build();
    ThrowingSupplier<ResponseData> throwingSupplier = () -> {
      throw new RuntimeException();
    };

    // When
    elastigroupSetupStep.finalizeExecutionWithSecurityContext(
        anAmbiance(), stepParameters, passThroughData, throwingSupplier);

    // Then
    verify(elastigroupStepCommonHelper).handleTaskException(eq(anAmbiance()), eq(passThroughData), any());
  }

  private Ambiance anAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
        .build();
  }
}
