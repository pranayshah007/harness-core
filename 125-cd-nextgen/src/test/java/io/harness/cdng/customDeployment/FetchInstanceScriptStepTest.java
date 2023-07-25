/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customDeployment;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.SOURABH;

import static software.wings.beans.TaskType.FETCH_INSTANCE_SCRIPT_TASK_NG;

import static java.time.Duration.ofMinutes;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.instance.outcome.InstancesOutcome;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.CustomDeploymentServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.customdeployment.FetchInstanceScriptTaskNGRequest;
import io.harness.delegate.task.customdeployment.FetchInstanceScriptTaskNGResponse;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.environment.EnvironmentOutcome;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class FetchInstanceScriptStepTest extends CDNGTestBase {
  @Mock private CDStepHelper cdStepHelper;
  @Mock private CDExpressionResolver cdExpressionResolver;
  @Mock private StepHelper stepHelper;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Captor private ArgumentCaptor<List<ServerInstanceInfo>> serverInstanceInfoListCaptor;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @InjectMocks private FetchInstanceScriptStep fetchInstanceScriptStep;

  private FetchInstanceScriptStepParameters parameters =
      FetchInstanceScriptStepParameters.infoBuilder()
          .delegateSelectors(ParameterField.createValueField(List.of(new TaskSelectorYaml("selector-1"))))
          .build();
  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().spec(parameters).timeout(ParameterField.createValueField("10m")).build();

  private final StepInputPackage stepInputPackage = StepInputPackage.builder().build();
  private final CustomDeploymentInfrastructureOutcome infrastructure =
      CustomDeploymentInfrastructureOutcome.builder()
          .instancesListPath("hosts")
          .instanceAttributes(Map.of("instancename", "host", "artifactBuildNo", "artifactBuildNo"))
          .instanceFetchScript(
              "{\"hosts\":[{ \"host\": 1, \"artifactBuildNo\": \"2021.07.10_app_2.war\" }, { \"host\": 2, \"artifactBuildNo\": \"2021.07.10_app_1.war\" } ] }")
          .build();
  private final Ambiance ambiance = getAmbiance();
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @Before
  public void setup() {
    ILogStreamingStepClient logStreamingStepClient;
    logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
    doReturn(infrastructure).when(cdStepHelper).getInfrastructureOutcome(ambiance);
    doReturn(infrastructure.getInstanceFetchScript())
        .when(cdExpressionResolver)
        .updateExpressions(ambiance, infrastructure.getInstanceFetchScript());
    CustomDeploymentInfrastructureOutcome customDeploymentInfrastructureOutcome =
        CustomDeploymentInfrastructureOutcome.builder()
            .infrastructureKey("1234")
            .environment(EnvironmentOutcome.builder().identifier("environmentId").build())
            .instanceFetchScript(infrastructure.getInstanceFetchScript())
            .instanceAttributes(infrastructure.getInstanceAttributes())
            .instancesListPath(infrastructure.getInstancesListPath())
            .variables(new HashMap<>())
            .build();

    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);
    doReturn(customDeploymentInfrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbac() {
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    Mockito.mockStatic(TaskRequestsUtils.class);
    when(TaskRequestsUtils.prepareCDTaskRequest(
             any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    TaskRequest taskRequest =
        fetchInstanceScriptStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();

    FetchInstanceScriptTaskNGRequest requestParameters =
        (FetchInstanceScriptTaskNGRequest) taskDataArgumentCaptor.getValue().getParameters()[0];

    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(FETCH_INSTANCE_SCRIPT_TASK_NG.toString());
    assertThat(requestParameters.getScriptBody()).isEqualTo(infrastructure.getInstanceFetchScript());
    assertThat(requestParameters.getOutputPathKey()).isEqualTo(FetchInstanceScriptStep.OUTPUT_PATH_KEY);
    assertThat(requestParameters.getAccountId()).isEqualTo("account");
    assertThat(requestParameters.getTimeoutInMillis()).isEqualTo(ofMinutes(10).toMillis());
  }
  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacForAllScopesSecret() {
    String script = "echo <+infra.variables.sec1>\n"
        + "echo <+infra.variables.sec2>\n"
        + "echo <+infra.variables.sec3>\n"
        + "\n"
        + "echo '{\"a\":[{\"ip\":\"1.1\"}, {\"ip\":\"2.2\"}]}' > $INSTANCE_OUTPUT_PATH";

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "account");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");

    Ambiance ambiance1 = Ambiance.newBuilder()
                             .putAllSetupAbstractions(setupAbstractions)
                             .setStageExecutionId("stageExecutionId")
                             .build();

    SecretRefData accountLevelSecret = SecretRefData.builder()
                                           .scope(Scope.ACCOUNT)
                                           .identifier("secret1")
                                           .decryptedValue(generateUuid().toCharArray())
                                           .build();

    SecretRefData orgLevelSecret = SecretRefData.builder()
                                       .scope(Scope.ORG)
                                       .identifier("secret2")
                                       .decryptedValue(generateUuid().toCharArray())
                                       .build();

    SecretRefData projectLevelSecret = SecretRefData.builder()
                                           .scope(Scope.PROJECT)
                                           .identifier("secret3")
                                           .decryptedValue(generateUuid().toCharArray())
                                           .build();

    Map<String, Object> variables = new HashMap<>();
    variables.put("sec1", accountLevelSecret);
    variables.put("sec2", orgLevelSecret);
    variables.put("sec3", projectLevelSecret);

    CustomDeploymentInfrastructureOutcome infrastructureOutcome =
        CustomDeploymentInfrastructureOutcome.builder().variables(variables).instanceFetchScript(script).build();

    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance1);

    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    Mockito.mockStatic(TaskRequestsUtils.class);
    when(TaskRequestsUtils.prepareCDTaskRequest(
             any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    TaskRequest taskRequest =
        fetchInstanceScriptStep.obtainTaskAfterRbac(ambiance1, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();

    FetchInstanceScriptTaskNGRequest requestParameters =
        (FetchInstanceScriptTaskNGRequest) taskDataArgumentCaptor.getValue().getParameters()[0];

    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(FETCH_INSTANCE_SCRIPT_TASK_NG.toString());
    assertThat(requestParameters.getOutputPathKey()).isEqualTo(FetchInstanceScriptStep.OUTPUT_PATH_KEY);
    assertThat(requestParameters.getAccountId()).isEqualTo("account");
    assertThat(requestParameters.getTimeoutInMillis()).isEqualTo(ofMinutes(10).toMillis());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateResourcesFFEnabled() {
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.NG_SVC_ENV_REDESIGN));
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().type("FetchInstanceScript").spec(parameters).build();
    fetchInstanceScriptStep.validateResources(ambiance, stepElementParameters);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateResourcesFFDisabled() {
    doReturn(false).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.NG_SVC_ENV_REDESIGN));
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().type("FetchInstanceScript").spec(parameters).build();
    assertThatThrownBy(() -> fetchInstanceScriptStep.validateResources(ambiance, stepElementParameters))
        .hasMessage("NG_SVC_ENV_REDESIGN FF is not enabled for this account. Please contact harness customer care.");
  }

  private Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "account");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");

    return Ambiance.newBuilder()
        .putAllSetupAbstractions(setupAbstractions)
        .setStageExecutionId("stageExecutionId")
        .build();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(fetchInstanceScriptStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleResponseWithSecurityContext() throws Exception {
    List<UnitProgress> unitProgresses = singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    FetchInstanceScriptTaskNGResponse fetchInstanceScriptTaskNGResponse =
        FetchInstanceScriptTaskNGResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .unitProgressData(unitProgressData)
            .output(
                "{\"hosts\":[{ \"host\": 1, \"artifactBuildNo\": \"artifact1\" }, { \"host\": \"instance2\", \"artifactBuildNo\": \"artifact2\" } ] }")
            .build();
    doReturn("").when(executionSweepingOutputService).consume(any(), any(), any(), any());
    StepResponse stepResponse = fetchInstanceScriptStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> fetchInstanceScriptTaskNGResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    verify(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(eq(ambiance), serverInstanceInfoListCaptor.capture());
    List<ServerInstanceInfo> serverInstanceInfoList = serverInstanceInfoListCaptor.getValue();
    assertThat(serverInstanceInfoList).hasSize(2);
    assertThat(((CustomDeploymentServerInstanceInfo) serverInstanceInfoList.get(0)).getInstanceFetchScript())
        .isEqualTo(infrastructure.getInstanceFetchScript());
    assertThat(((CustomDeploymentServerInstanceInfo) serverInstanceInfoList.get(0)).getInfrastructureKey())
        .isEqualTo("1234");
    assertThat(((CustomDeploymentServerInstanceInfo) serverInstanceInfoList.get(0)).getInstanceName()).isEqualTo("1");
    assertThat(((CustomDeploymentServerInstanceInfo) serverInstanceInfoList.get(0)).getProperties())
        .isEqualTo(Map.of("instancename", 1, "artifactBuildNo", "artifact1"));
    assertThat(((CustomDeploymentServerInstanceInfo) serverInstanceInfoList.get(1)).getInstanceFetchScript())
        .isEqualTo(infrastructure.getInstanceFetchScript());
    assertThat(((CustomDeploymentServerInstanceInfo) serverInstanceInfoList.get(1)).getInfrastructureKey())
        .isEqualTo("1234");
    assertThat(((CustomDeploymentServerInstanceInfo) serverInstanceInfoList.get(1)).getInstanceName())
        .isEqualTo("instance2");
    assertThat(((CustomDeploymentServerInstanceInfo) serverInstanceInfoList.get(1)).getProperties())
        .isEqualTo(Map.of("instancename", "instance2", "artifactBuildNo", "artifact2"));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleResponseWithSecurityContextFailure() throws Exception {
    List<UnitProgress> unitProgresses = singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    FetchInstanceScriptTaskNGResponse shellScriptTaskResponseNG =
        FetchInstanceScriptTaskNGResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .unitProgressData(unitProgressData)
            .errorMessage("Shell Script execution failed. Please check execution logs.")
            .build();

    StepResponse stepResponse = fetchInstanceScriptStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> shellScriptTaskResponseNG);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    assertThat(stepResponse.getFailureInfo().getErrorMessage())
        .isEqualTo("Shell Script execution failed. Please check execution logs.");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testHandleResponseWithSecurityContextForInstanceOutcome() throws Exception {
    List<UnitProgress> unitProgresses = singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    FetchInstanceScriptTaskNGResponse fetchInstanceScriptTaskNGResponse =
        FetchInstanceScriptTaskNGResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .unitProgressData(unitProgressData)
            .output(
                "{\"hosts\":[{ \"host\": \"instance1\", \"artifactBuildNo\": \"artifact1\" }, { \"host\": \"instance2\", \"artifactBuildNo\": \"artifact2\" } ] }")
            .build();
    doReturn("").when(executionSweepingOutputService).consume(any(), any(), any(), any());
    StepResponse stepResponse = fetchInstanceScriptStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> fetchInstanceScriptTaskNGResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    verify(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(eq(ambiance), serverInstanceInfoListCaptor.capture());
    ArgumentCaptor<ExecutionSweepingOutput> instancesOutcomeCaptor =
        ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    verify(executionSweepingOutputService, times(2)).consume(any(), any(), instancesOutcomeCaptor.capture(), any());
    assertThat(((InstancesOutcome) instancesOutcomeCaptor.getAllValues().get(0)).getInstances().get(0).getHostName())
        .isEqualTo("instance1");
    assertThat(((InstancesOutcome) instancesOutcomeCaptor.getAllValues().get(0)).getInstances().get(1).getHostName())
        .isEqualTo("instance2");
  }
}
