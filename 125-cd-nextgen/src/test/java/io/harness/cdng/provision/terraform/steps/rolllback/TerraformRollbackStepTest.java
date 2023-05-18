/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform.steps.rolllback;

import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.manifest.yaml.ArtifactoryStorageConfigDTO;
import io.harness.cdng.manifest.yaml.GitStoreDTO;
import io.harness.cdng.manifest.yaml.TerraformCommandFlagType;
import io.harness.cdng.provision.terraform.TerraformCliOptionFlag;
import io.harness.cdng.provision.terraform.TerraformConfig;
import io.harness.cdng.provision.terraform.TerraformConfigDAL;
import io.harness.cdng.provision.terraform.TerraformConfigHelper;
import io.harness.cdng.provision.terraform.TerraformStepHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.persistence.HIterator;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.telemetry.TelemetryReporter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({StepUtils.class})
public class TerraformRollbackStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private TerraformStepHelper terraformStepHelper;
  @Mock private TerraformConfigDAL terraformConfigDAL;
  @Mock private TerraformConfigHelper terraformConfigHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private StepHelper stepHelper;
  @Mock private AccountService accountService;
  @Mock private TelemetryReporter telemetryReporter;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  @InjectMocks private TerraformRollbackStep terraformRollbackStep;

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testObtainTaskSkippedRollback() {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "test-account").build();
    TerraformRollbackStepParameters rollbackSpec =
        TerraformRollbackStepParameters.builder().provisionerIdentifier(ParameterField.createValueField("id")).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();

    doReturn("fullId").when(terraformStepHelper).generateFullIdentifier("id", ambiance);

    HIterator<TerraformConfig> iterator = mock(HIterator.class);
    doReturn(iterator).when(terraformConfigHelper).getIterator(ambiance, "fullId");
    doReturn(false).when(iterator).hasNext();

    TaskRequest taskRequest = terraformRollbackStep.obtainTask(ambiance, stepElementParameters, null);

    assertThat(taskRequest.getSkipTaskRequest()).isNotNull();
    assertThat(taskRequest.getSkipTaskRequest().getMessage())
        .isEqualTo("No successful Provisioning found with provisionerIdentifier: [id]. Skipping rollback.");
    verify(stepHelper, times(0)).sendRollbackTelemetryEvent(any(), any(), any());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testObtainTaskDestroyScenario() {
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId("executionId").putSetupAbstractions("accountId", "accId").build();
    TerraformRollbackStepParameters rollbackSpec = TerraformRollbackStepParameters.builder()
                                                       .provisionerIdentifier(ParameterField.createValueField("id"))
                                                       .skipRefreshCommand(ParameterField.createValueField(true))
                                                       .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();

    doReturn("fullId").when(terraformStepHelper).generateFullIdentifier("id", ambiance);
    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);

    HIterator<TerraformConfig> iterator = mock(HIterator.class);
    doReturn(iterator).when(terraformConfigHelper).getIterator(ambiance, "fullId");
    when(iterator.hasNext()).thenReturn(true, true, false);

    TerraformConfig terraformConfig =
        TerraformConfig.builder().pipelineExecutionId("executionId").configFiles(GitStoreDTO.builder().build()).build();
    doReturn(terraformConfig).when(iterator).next();

    doReturn(null).when(executionSweepingOutputService).consume(any(), any(), any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId("fullId");
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder().build();
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(null).when(terraformStepHelper).prepareTerraformVarFileInfo(any(), any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    TaskRequest taskRequest = terraformRollbackStep.obtainTask(ambiance, stepElementParameters, null);

    assertThat(taskRequest).isNotNull();

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformTaskNGParameters taskParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.DESTROY);
    verify(stepHelper, times(0)).sendRollbackTelemetryEvent(any(), any(), any());
    assertThat(taskParameters.isSkipTerraformRefresh()).isTrue();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testObtainTaskApplyScenario() {
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId("executionId").putSetupAbstractions("accountId", "accId").build();
    TerraformRollbackStepParameters rollbackSpec =
        TerraformRollbackStepParameters.builder()
            .provisionerIdentifier(ParameterField.createValueField("id"))
            .skipRefreshCommand(ParameterField.createValueField(true))
            .commandFlags(List.of(TerraformCliOptionFlag.builder()
                                      .commandType(TerraformCommandFlagType.APPLY)
                                      .flag(ParameterField.createValueField("-lock-timeout=0s"))
                                      .build()))
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();

    doReturn("fullId").when(terraformStepHelper).generateFullIdentifier("id", ambiance);
    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);

    HIterator<TerraformConfig> iterator = mock(HIterator.class);
    doReturn(iterator).when(terraformConfigHelper).getIterator(ambiance, "fullId");
    when(iterator.hasNext()).thenReturn(true, true, false);

    TerraformConfig terraformConfig = TerraformConfig.builder()
                                          .pipelineExecutionId("oldExecutionId")
                                          .configFiles(GitStoreDTO.builder().build())
                                          .build();
    doReturn(terraformConfig).when(iterator).next();

    doReturn(null).when(executionSweepingOutputService).consume(any(), any(), any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId("fullId");
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder().build();
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(null).when(terraformStepHelper).prepareTerraformVarFileInfo(any(), any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("APPLY", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    TaskRequest taskRequest = terraformRollbackStep.obtainTask(ambiance, stepElementParameters, null);

    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformTaskNGParameters taskParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
    verify(stepHelper, times(0)).sendRollbackTelemetryEvent(any(), any(), any());
    assertThat(taskParameters.isSkipTerraformRefresh()).isTrue();
    assertThat(taskParameters.getTerraformCommandFlags().get("APPLY")).isEqualTo("-lock-timeout=0s");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testObtainTaskApplyScenarioFFEnabled() {
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId("executionId").putSetupAbstractions("accountId", "accId").build();
    TerraformRollbackStepParameters rollbackSpec =
        TerraformRollbackStepParameters.builder().provisionerIdentifier(ParameterField.createValueField("id")).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();

    doReturn("fullId").when(terraformStepHelper).generateFullIdentifier("id", ambiance);
    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);
    HIterator<TerraformConfig> iterator = mock(HIterator.class);
    doReturn(iterator).when(terraformConfigHelper).getIterator(ambiance, "fullId");
    when(iterator.hasNext()).thenReturn(true, true, false);

    TerraformConfig terraformConfig = TerraformConfig.builder()
                                          .pipelineExecutionId("oldExecutionId")
                                          .useConnectorCredentials(true)
                                          .configFiles(GitStoreDTO.builder().build())
                                          .build();
    doReturn(terraformConfig).when(iterator).next();

    doReturn(null).when(executionSweepingOutputService).consume(any(), any(), any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId("fullId");
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder().build();
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(null).when(terraformStepHelper).prepareTerraformVarFileInfo(any(), any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    TaskRequest taskRequest = terraformRollbackStep.obtainTask(ambiance, stepElementParameters, null);

    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformTaskNGParameters taskParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
    assertThat(taskParameters.isTfModuleSourceInheritSSH()).isTrue();
    verify(stepHelper, times(0)).sendRollbackTelemetryEvent(any(), any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskApplyScenarioArtifactoryStore() {
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId("executionId").putSetupAbstractions("accountId", "accId").build();
    TerraformRollbackStepParameters rollbackSpec =
        TerraformRollbackStepParameters.builder().provisionerIdentifier(ParameterField.createValueField("id")).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();

    doReturn("fullId").when(terraformStepHelper).generateFullIdentifier("id", ambiance);
    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);

    HIterator<TerraformConfig> iterator = mock(HIterator.class);
    doReturn(iterator).when(terraformConfigHelper).getIterator(ambiance, "fullId");
    when(iterator.hasNext()).thenReturn(true, true, false);

    TerraformConfig terraformConfig =
        TerraformConfig.builder()
            .pipelineExecutionId("oldExecutionId")
            .fileStoreConfig(ArtifactoryStorageConfigDTO.builder().artifactPaths(asList("artifactPath")).build())
            .build();
    doReturn(terraformConfig).when(iterator).next();

    doReturn(null).when(executionSweepingOutputService).consume(any(), any(), any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId("fullId");
    ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig = ArtifactoryStoreDelegateConfig.builder().build();
    doReturn(artifactoryStoreDelegateConfig).when(terraformStepHelper).prepareTerraformConfigFileInfo(any(), any());
    doReturn(null).when(terraformStepHelper).prepareTerraformVarFileInfo(any(), any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    TaskRequest taskRequest = terraformRollbackStep.obtainTask(ambiance, stepElementParameters, null);

    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformTaskNGParameters taskParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
    verify(stepHelper, times(0)).sendRollbackTelemetryEvent(any(), any(), any());
    assertThat(taskParameters.getConfigFile()).isNull();
    assertThat(taskParameters.getFileStoreConfigFiles()).isEqualTo(artifactoryStoreDelegateConfig);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testHandleTaskRequestForApplyWithSuccessTaskResponse() throws Exception {
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId("executionId").putSetupAbstractions("accountId", "accId").build();
    TerraformRollbackStepParameters rollbackSpec =
        TerraformRollbackStepParameters.builder().provisionerIdentifier(ParameterField.createValueField("id")).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    TerraformConfig terraformConfig = TerraformConfig.builder().build();
    TerraformConfigSweepingOutput terraformConfigSweepingOutput =
        TerraformConfigSweepingOutput.builder().terraformConfig(terraformConfig).tfTaskType(TFTaskType.APPLY).build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(terraformConfigSweepingOutput).build();
    doReturn(optionalSweepingOutput).when(executionSweepingOutputService).resolveOptional(any(), any());
    doNothing().when(terraformStepHelper).saveTerraformConfig(terraformConfig, ambiance);

    AccountDTO accountDTO = AccountDTO.builder().name("TestAccountName").build();
    doReturn(accountDTO).when(accountService).getAccount(any());

    StepResponse stepResponse =
        terraformRollbackStep.handleTaskResult(ambiance, stepElementParameters, () -> terraformTaskNGResponse);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(unitProgresses);
    verify(terraformStepHelper, times(1)).saveTerraformConfig(terraformConfig, ambiance);
    verify(stepHelper, times(1)).sendRollbackTelemetryEvent(any(), any(), any());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testHandleTaskRequestForDestroyWithSuccessTaskResponse() throws Exception {
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId("executionId").putSetupAbstractions("accountId", "accId").build();
    TerraformRollbackStepParameters rollbackSpec =
        TerraformRollbackStepParameters.builder().provisionerIdentifier(ParameterField.createValueField("id")).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    TerraformConfig terraformConfig = TerraformConfig.builder().entityId("entityId").build();
    TerraformConfigSweepingOutput terraformConfigSweepingOutput =
        TerraformConfigSweepingOutput.builder().terraformConfig(terraformConfig).tfTaskType(TFTaskType.DESTROY).build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(terraformConfigSweepingOutput).build();
    doReturn(optionalSweepingOutput).when(executionSweepingOutputService).resolveOptional(any(), any());
    doNothing().when(terraformConfigDAL).clearTerraformConfig(ambiance, "entityId");

    AccountDTO accountDTO = AccountDTO.builder().name("TestAccountName").build();
    doReturn(accountDTO).when(accountService).getAccount(any());

    StepResponse stepResponse =
        terraformRollbackStep.handleTaskResult(ambiance, stepElementParameters, () -> terraformTaskNGResponse);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(unitProgresses);
    verify(terraformConfigDAL, times(1)).clearTerraformConfig(ambiance, "entityId");
    verify(stepHelper, times(1)).sendRollbackTelemetryEvent(any(), any(), any());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testHandleTaskRequestForFailedTaskResponse() throws Exception {
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId("executionId").putSetupAbstractions("accountId", "accId").build();
    TerraformRollbackStepParameters rollbackSpec =
        TerraformRollbackStepParameters.builder().provisionerIdentifier(ParameterField.createValueField("id")).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    TerraformConfig terraformConfig = TerraformConfig.builder().entityId("entityId").build();
    TerraformConfigSweepingOutput terraformConfigSweepingOutput =
        TerraformConfigSweepingOutput.builder().terraformConfig(terraformConfig).tfTaskType(TFTaskType.DESTROY).build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(terraformConfigSweepingOutput).build();
    doReturn(optionalSweepingOutput).when(executionSweepingOutputService).resolveOptional(any(), any());

    AccountDTO accountDTO = AccountDTO.builder().name("TestAccountName").build();
    doReturn(accountDTO).when(accountService).getAccount(any());

    StepResponse stepResponse =
        terraformRollbackStep.handleTaskResult(ambiance, stepElementParameters, () -> terraformTaskNGResponse);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getUnitProgressList()).isNullOrEmpty();
    verify(stepHelper, times(1)).sendRollbackTelemetryEvent(any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(terraformRollbackStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetSpecParametersWithDelegateSelectors() {
    TerraformRollbackStepInfo terraformRollbackStepInfo = new TerraformRollbackStepInfo();
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("sel1");
    terraformRollbackStepInfo.setDelegateSelectors(ParameterField.createValueField(asList(taskSelectorYaml)));

    SpecParameters specParameters = terraformRollbackStepInfo.getSpecParameters();
    TerraformRollbackStepParameters terraformRollbackStepParameters = (TerraformRollbackStepParameters) specParameters;
    assertThat(specParameters).isNotNull();
    assertThat(terraformRollbackStepParameters.delegateSelectors.getValue().get(0).getDelegateSelectors())
        .isEqualTo("sel1");
  }
}
