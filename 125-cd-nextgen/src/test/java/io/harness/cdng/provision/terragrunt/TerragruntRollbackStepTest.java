/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terragrunt;

import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

import io.harness.CategoryTest;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.manifest.yaml.GitStoreDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.InlineFileConfig;
import io.harness.delegate.beans.storeconfig.InlineStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.terragrunt.request.TerragruntApplyTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntDestroyTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntRunConfiguration;
import io.harness.delegate.beans.terragrunt.request.TerragruntTaskRunType;
import io.harness.delegate.beans.terragrunt.response.TerragruntApplyTaskResponse;
import io.harness.delegate.beans.terragrunt.response.TerragruntDestroyTaskResponse;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.persistence.HIterator;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.telemetry.TelemetryReporter;

import java.util.ArrayList;
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
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({StepUtils.class})
@OwnedBy(HarnessTeam.CDP)
public class TerragruntRollbackStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private KryoSerializer kryoSerializer;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private TerragruntStepHelper terragruntStepHelper;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private StepHelper stepHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private AccountService accountService;
  @Mock private TelemetryReporter telemetryReporter;
  @Mock private TerragruntConfigDAL terragruntConfigDAL;
  @InjectMocks private TerragruntRollbackStep terragruntRollbackStep = new TerragruntRollbackStep();

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testObtainTaskDestroyScenario() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId("executionId")
                            .putSetupAbstractions("accountId", "test-account")
                            .build();
    TerragruntRollbackStepParameters rollbackSpec =
        TerragruntRollbackStepParameters.builder().provisionerIdentifier("id").build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();

    doReturn("fullId").when(terragruntStepHelper).generateFullIdentifier("id", ambiance);

    HIterator<TerragruntConfig> iterator = mock(HIterator.class);
    doReturn(iterator).when(terragruntConfigDAL).getIterator(ambiance, "fullId");
    when(iterator.hasNext()).thenReturn(true, true, false);

    TerragruntConfig terragruntConfig = TerragruntConfig.builder()
                                            .pipelineExecutionId("executionId")
                                            .configFiles(GitStoreDTO.builder().build())
                                            .workspace("test-workspace")
                                            .targets(new ArrayList<>() {
                                              { add("test-target"); }
                                            })
                                            .runConfiguration(TerragruntRunConfiguration.builder()
                                                                  .runType(TerragruntTaskRunType.RUN_MODULE)
                                                                  .path("test-path")
                                                                  .build())
                                            .build();
    doReturn(terragruntConfig).when(iterator).next();

    doReturn(null).when(executionSweepingOutputService).consume(any(), any(), any(), any());
    doReturn("fileId").when(terragruntStepHelper).getLatestFileId(any());
    when(terragruntStepHelper.toStoreDelegateVarFilesFromTgConfig(any(), any()))
        .thenReturn(Collections.singletonList(
            InlineStoreDelegateConfig.builder()
                .identifier("test-var1-id")
                .files(Collections.singletonList(InlineFileConfig.builder().content("test-var1-content").build()))
                .build()));
    when(terragruntStepHelper.getGitFetchFilesConfig(any(), any(), any()))
        .thenReturn(TerragruntTestStepUtils.createGitStoreDelegateConfig());
    when(terragruntStepHelper.getBackendConfigFromTgConfig(any(), any()))
        .thenReturn(InlineStoreDelegateConfig.builder().identifier("test-backend-id").build());
    when(terragruntStepHelper.getEnvironmentVariablesMap(any())).thenReturn(new HashMap<>());
    when(terragruntStepHelper.getLatestFileId(any())).thenReturn(null);
    Mockito.mockStatic(StepUtils.class);
    when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    TaskRequest taskRequest = terragruntRollbackStep.obtainTask(ambiance, stepElementParameters, null);
    assertThat(taskRequest).isNotNull();
    verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();

    TerragruntDestroyTaskParameters params =
        (TerragruntDestroyTaskParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(params.getAccountId()).isEqualTo("test-account");
    assertThat(params.getWorkspace()).isEqualTo("test-workspace");
    assertThat(params.getRunConfiguration().getRunType()).isEqualTo(TerragruntTaskRunType.RUN_MODULE);
    assertThat(params.getRunConfiguration().getPath()).isEqualTo("test-path");
    assertThat(params.getTargets().get(0)).isEqualTo("test-target");
    List<StoreDelegateConfig> inlineVar = params.getVarFiles();
    assertThat(((InlineStoreDelegateConfig) inlineVar.get(0)).getIdentifier()).isEqualTo("test-var1-id");
    assertThat(((InlineStoreDelegateConfig) params.getBackendFilesStore()).getIdentifier())
        .isEqualTo("test-backend-id");
    assertThat(((GitStoreDelegateConfig) params.getConfigFilesStore()).getConnectorName()).isEqualTo("terragrunt");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testObtainTaskApplyScenario() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId("executionId")
                            .putSetupAbstractions("accountId", "test-account")
                            .build();
    TerragruntRollbackStepParameters rollbackSpec =
        TerragruntRollbackStepParameters.builder().provisionerIdentifier("id").build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();

    doReturn("fullId").when(terragruntStepHelper).generateFullIdentifier("id", ambiance);

    HIterator<TerragruntConfig> iterator = mock(HIterator.class);
    doReturn(iterator).when(terragruntConfigDAL).getIterator(ambiance, "fullId");
    when(iterator.hasNext()).thenReturn(true, true, false);

    TerragruntConfig terragruntConfig = TerragruntConfig.builder()
                                            .pipelineExecutionId("PreviousExecutionId")
                                            .configFiles(GitStoreDTO.builder().build())
                                            .workspace("test-workspace")
                                            .targets(new ArrayList<>() {
                                              { add("test-target"); }
                                            })
                                            .runConfiguration(TerragruntRunConfiguration.builder()
                                                                  .runType(TerragruntTaskRunType.RUN_MODULE)
                                                                  .path("test-path")
                                                                  .build())
                                            .build();
    doReturn(terragruntConfig).when(iterator).next();

    doReturn(null).when(executionSweepingOutputService).consume(any(), any(), any(), any());
    doReturn("fileId").when(terragruntStepHelper).getLatestFileId(any());
    when(terragruntStepHelper.toStoreDelegateVarFilesFromTgConfig(any(), any()))
        .thenReturn(Collections.singletonList(
            InlineStoreDelegateConfig.builder()
                .identifier("test-var1-id")
                .files(Collections.singletonList(InlineFileConfig.builder().content("test-var1-content").build()))
                .build()));
    when(terragruntStepHelper.getGitFetchFilesConfig(any(), any(), any()))
        .thenReturn(TerragruntTestStepUtils.createGitStoreDelegateConfig());
    when(terragruntStepHelper.getBackendConfigFromTgConfig(any(), any()))
        .thenReturn(InlineStoreDelegateConfig.builder().identifier("test-backend-id").build());
    when(terragruntStepHelper.getEnvironmentVariablesMap(any())).thenReturn(new HashMap<>());
    when(terragruntStepHelper.getLatestFileId(any())).thenReturn(null);
    Mockito.mockStatic(StepUtils.class);
    when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    TaskRequest taskRequest = terragruntRollbackStep.obtainTask(ambiance, stepElementParameters, null);
    assertThat(taskRequest).isNotNull();
    verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();

    TerragruntApplyTaskParameters params =
        (TerragruntApplyTaskParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(params.getAccountId()).isEqualTo("test-account");
    assertThat(params.getWorkspace()).isEqualTo("test-workspace");
    assertThat(params.getRunConfiguration().getRunType()).isEqualTo(TerragruntTaskRunType.RUN_MODULE);
    assertThat(params.getRunConfiguration().getPath()).isEqualTo("test-path");
    assertThat(params.getTargets().get(0)).isEqualTo("test-target");
    List<StoreDelegateConfig> inlineVar = params.getVarFiles();
    assertThat(((InlineStoreDelegateConfig) inlineVar.get(0)).getIdentifier()).isEqualTo("test-var1-id");
    assertThat(((InlineStoreDelegateConfig) params.getBackendFilesStore()).getIdentifier())
        .isEqualTo("test-backend-id");
    assertThat(((GitStoreDelegateConfig) params.getConfigFilesStore()).getConnectorName()).isEqualTo("terragrunt");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testHandleTaskRequestForApplyWithSuccessTaskResponse() throws Exception {
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId("executionId").putSetupAbstractions("accountId", "accId").build();
    TerragruntRollbackStepParameters rollbackSpec =
        TerragruntRollbackStepParameters.builder().provisionerIdentifier("id").build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    TerragruntApplyTaskResponse applyTaskResponse =
        TerragruntApplyTaskResponse.builder().unitProgressData(unitProgressData).stateFileId("test-stateId").build();

    TerragruntConfig terragruntConfig = TerragruntConfig.builder().build();
    TerragruntConfigSweepingOutput terragruntConfigSweepingOutput =
        TerragruntConfigSweepingOutput.builder()
            .terragruntConfig(terragruntConfig)
            .rollbackTaskType(TerragruntRollbackTaskType.APPLY)
            .build();

    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(terragruntConfigSweepingOutput).build();
    doReturn(optionalSweepingOutput).when(executionSweepingOutputService).resolveOptional(any(), any());
    doNothing().when(terragruntStepHelper).saveTerragruntConfig(terragruntConfig, ambiance);
    AccountDTO accountDTO = AccountDTO.builder().name("TestAccountName").build();
    doReturn(accountDTO).when(accountService).getAccount(any());

    StepResponse stepResponse =
        terragruntRollbackStep.handleTaskResult(ambiance, stepElementParameters, () -> applyTaskResponse);

    assertThat(applyTaskResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(unitProgresses);
    verify(terragruntStepHelper, times(1)).updateParentEntityIdAndVersion(any(), any());
    verify(terragruntStepHelper, times(1)).saveTerragruntConfig(terragruntConfig, ambiance);
    verify(stepHelper, times(1)).sendRollbackTelemetryEvent(any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testHandleTaskRequestForDestroyWithSuccessTaskResponse() throws Exception {
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId("executionId").putSetupAbstractions("accountId", "accId").build();
    TerragruntRollbackStepParameters rollbackSpec =
        TerragruntRollbackStepParameters.builder().provisionerIdentifier("id").build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    TerragruntDestroyTaskResponse applyTaskResponse =
        TerragruntDestroyTaskResponse.builder().unitProgressData(unitProgressData).stateFileId("test-stateId").build();

    TerragruntConfig terragruntConfig = TerragruntConfig.builder().build();
    TerragruntConfigSweepingOutput terragruntConfigSweepingOutput =
        TerragruntConfigSweepingOutput.builder()
            .terragruntConfig(terragruntConfig)
            .rollbackTaskType(TerragruntRollbackTaskType.DESTROY)
            .build();

    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(terragruntConfigSweepingOutput).build();
    doReturn(optionalSweepingOutput).when(executionSweepingOutputService).resolveOptional(any(), any());
    doNothing().when(terragruntStepHelper).saveTerragruntConfig(terragruntConfig, ambiance);
    AccountDTO accountDTO = AccountDTO.builder().name("TestAccountName").build();
    doReturn(accountDTO).when(accountService).getAccount(any());

    StepResponse stepResponse =
        terragruntRollbackStep.handleTaskResult(ambiance, stepElementParameters, () -> applyTaskResponse);

    assertThat(applyTaskResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(unitProgresses);
    verify(terragruntStepHelper, times(1)).updateParentEntityIdAndVersion(any(), any());
    verify(terragruntConfigDAL, times(1)).clearTerragruntConfig(any(), any());
    verify(stepHelper, times(1)).sendRollbackTelemetryEvent(any(), any(), any());
  }
}
