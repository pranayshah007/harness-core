/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform.steps.rolllback;

import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.VLICA;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
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
import io.harness.cdng.provision.terraform.TerraformPassThroughData;
import io.harness.cdng.provision.terraform.TerraformStepHelper;
import io.harness.cdng.provision.terraform.outcome.TerraformGitRevisionOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreTFDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.delegate.task.terraform.InlineTerraformVarFileInfo;
import io.harness.delegate.task.terraform.RemoteTerraformVarFileInfo;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.delegate.task.terraform.TerraformVarFileInfo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
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
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
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
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({StepUtils.class})
public class TerraformRollbackStepV2Test extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private TerraformStepHelper terraformStepHelper;
  @Mock private TerraformConfigDAL terraformConfigDAL;
  @Mock private TerraformConfigHelper terraformConfigHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private StepHelper stepHelper;
  @Mock private AccountService accountService;
  @Mock private TelemetryReporter telemetryReporter;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  @InjectMocks private TerraformRollbackStepV2 terraformRollbackStepV2;

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbac() {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "test-account").build();
    TerraformRollbackStepParameters rollbackSpec =
        TerraformRollbackStepParameters.builder().provisionerIdentifier(ParameterField.createValueField("id")).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();

    doReturn("fullId").when(terraformStepHelper).generateFullIdentifier("id", ambiance);

    HIterator<TerraformConfig> iterator = mock(HIterator.class);
    doReturn(iterator).when(terraformConfigHelper).getIterator(ambiance, "fullId");
    doReturn(false).when(iterator).hasNext();

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    TaskChainResponse taskChainResponse =
        terraformRollbackStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, null);

    assertThat(taskChainResponse.isChainEnd()).isTrue();
    assertThat(((TerraformPassThroughData) taskChainResponse.getPassThroughData()).skipTerraformRollback()).isTrue();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbacForDestroy() {
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

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent("var-file-inline").build());
    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());

    doReturn(null).when(executionSweepingOutputService).consume(any(), any(), any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId("fullId");
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder().build();
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).prepareTerraformVarFileInfo(any(), any(), anyBoolean());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformRollbackStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, null);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.DESTROY);
    assertThat(taskParameters.isSkipTerraformRefresh()).isTrue();

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles()).isFalse();
    assertThat(terraformPassThroughData.hasS3Files()).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(0)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbacForDestroyWhenRemoteVarFile() {
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

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(RemoteTerraformVarFileInfo.builder()
                        .filestoreFetchFilesConfig(S3StoreTFDelegateConfig.builder()
                                                       .bucketName("test-bucket")
                                                       .region("test-region")
                                                       .path("test-path")
                                                       .connectorDTO(ConnectorInfoDTO.builder().build())
                                                       .build())
                        .build());

    doReturn(null).when(executionSweepingOutputService).consume(any(), any(), any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId("fullId");
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder().build();
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).prepareTerraformVarFileInfo(any(), any(), anyBoolean());

    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());
    doReturn(true).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());
    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformRollbackStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, null);

    verify(terraformStepHelper)
        .fetchRemoteVarFiles(tfPassThroughDataArgumentCaptor.capture(), any(), any(), any(), any(), any());

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles()).isTrue();
    assertThat(terraformPassThroughData.hasS3Files()).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(1)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
    verify(terraformStepHelper, times(0)).executeTerraformTask(any(), any(), any(), any(), any(), any());

    TerraformTaskNGParameters taskParameters = terraformPassThroughData.getTerraformTaskNGParametersBuilder().build();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.DESTROY);
    assertThat(taskParameters.isSkipTerraformRefresh()).isTrue();
    assertThat(taskParameters.getConfigFile()).isNotNull();
    assertThat(((RemoteTerraformVarFileInfo) taskParameters.getVarFileInfos().get(0)).getFilestoreFetchFilesConfig())
        .isNotNull();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbacApplyScenario() {
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

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent("var-file-inline").build());
    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());

    HIterator<TerraformConfig> iterator = mock(HIterator.class);
    doReturn(iterator).when(terraformConfigHelper).getIterator(ambiance, "fullId");
    when(iterator.hasNext()).thenReturn(true, true, false);

    TerraformConfig terraformConfig = TerraformConfig.builder()
                                          .pipelineExecutionId("oldExecutionId")
                                          .configFiles(GitStoreDTO.builder().build())
                                          .useConnectorCredentials(true)
                                          .build();
    doReturn(terraformConfig).when(iterator).next();

    doReturn(null).when(executionSweepingOutputService).consume(any(), any(), any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId("fullId");
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder().build();
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).prepareTerraformVarFileInfo(any(), any(), anyBoolean());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("APPLY", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformRollbackStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, null);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
    assertThat(taskParameters.isSkipTerraformRefresh()).isTrue();
    assertThat(taskParameters.isTfModuleSourceInheritSSH()).isTrue();
    assertThat(taskParameters.getTerraformCommandFlags().get("APPLY")).isEqualTo("-lock-timeout=0s");

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles()).isFalse();
    assertThat(terraformPassThroughData.hasS3Files()).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(0)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbacApplyScenarioArtifactoryStore() {
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
    doReturn(null).when(terraformStepHelper).prepareTerraformVarFileInfo(any(), any(), anyBoolean());
    doReturn(new ArrayList<>()).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformRollbackStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, null);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);

    assertThat(taskParameters.getConfigFile()).isNull();
    assertThat(taskParameters.getFileStoreConfigFiles()).isEqualTo(artifactoryStoreDelegateConfig);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeApplyWithSuccessTaskResponse() throws Exception {
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

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasS3Files(false).hasGitFiles(false).build();

    StepResponse stepResponse = terraformRollbackStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);

    StepResponse.StepOutcome stepOutcome = ((List<StepResponse.StepOutcome>) stepResponse.getStepOutcomes()).get(0);

    assertThat(stepOutcome.getName()).isEqualTo(TerraformGitRevisionOutcome.OUTCOME_NAME);
    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(unitProgresses);
    verify(terraformStepHelper, times(1)).saveTerraformConfig(terraformConfig, ambiance);
    verify(stepHelper, times(1)).sendRollbackTelemetryEvent(any(), any(), any());
    verify(terraformStepHelper, times(1)).getRevisionsMap(any(TerraformPassThroughData.class), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeApplyWithSuccessTaskResponseWithMultipleCommandUnits() throws Exception {
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId("executionId").putSetupAbstractions("accountId", "accId").build();
    TerraformRollbackStepParameters rollbackSpec =
        TerraformRollbackStepParameters.builder().provisionerIdentifier(ParameterField.createValueField("id")).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();
    TerraformConfig terraformConfig = TerraformConfig.builder().build();
    TerraformConfigSweepingOutput terraformConfigSweepingOutput =
        TerraformConfigSweepingOutput.builder().terraformConfig(terraformConfig).tfTaskType(TFTaskType.APPLY).build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(terraformConfigSweepingOutput).build();
    doReturn(optionalSweepingOutput).when(executionSweepingOutputService).resolveOptional(any(), any());
    doNothing().when(terraformStepHelper).saveTerraformConfig(terraformConfig, ambiance);

    AccountDTO accountDTO = AccountDTO.builder().name("TestAccountName").build();
    doReturn(accountDTO).when(accountService).getAccount(any());

    List<UnitProgress> unitProgressesPlan = new ArrayList<>();
    UnitProgress planUP = UnitProgress.newBuilder().setUnitName("Plan").setStatus(UnitStatus.SUCCESS).build();
    unitProgressesPlan.add(planUP);

    List<UnitProgress> unitProgressesFetch = new ArrayList<>();
    UnitProgress fetchFilesUP =
        UnitProgress.newBuilder().setUnitName("Fetch Files").setStatus(UnitStatus.SUCCESS).build();
    unitProgressesFetch.add(fetchFilesUP);

    UnitProgressData unitProgressDataPlan = UnitProgressData.builder().unitProgresses(unitProgressesPlan).build();

    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressDataPlan)
                                                          .build();

    TerraformPassThroughData terraformPassThroughData = TerraformPassThroughData.builder()
                                                            .hasGitFiles(true)
                                                            .hasS3Files(true)
                                                            .unitProgresses(unitProgressesFetch)
                                                            .build();

    StepResponse stepResponse = terraformRollbackStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);

    StepResponse.StepOutcome stepOutcome = ((List<StepResponse.StepOutcome>) stepResponse.getStepOutcomes()).get(0);

    assertThat(stepOutcome.getName()).isEqualTo(TerraformGitRevisionOutcome.OUTCOME_NAME);
    assertThat(stepResponse.getUnitProgressList().size()).isEqualTo(2);
    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(terraformStepHelper, times(1)).saveTerraformConfig(terraformConfig, ambiance);
    verify(stepHelper, times(1)).sendRollbackTelemetryEvent(any(), any(), any());
    verify(terraformStepHelper, times(1)).getRevisionsMap(any(TerraformPassThroughData.class), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeWhenRollbackIsSkipped() throws Exception {
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId("executionId").putSetupAbstractions("accountId", "accId").build();

    TerraformRollbackStepParameters rollbackSpec =
        TerraformRollbackStepParameters.builder().provisionerIdentifier(ParameterField.createValueField("id")).build();

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();
    AccountDTO accountDTO = AccountDTO.builder().name("TestAccountName").build();
    doReturn(accountDTO).when(accountService).getAccount(any());

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasS3Files(false).hasGitFiles(false).skipTerraformRollback(true).build();

    StepResponse stepResponse = terraformRollbackStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> null);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
    assertThat(stepResponse.getFailureInfo().getErrorMessage())
        .isEqualTo("No successful Provisioning found with provisionerIdentifier: [id]. Skipping rollback.");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContext() throws Exception {
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId("executionId").putSetupAbstractions("accountId", "accId").build();
    TerraformRollbackStepParameters rollbackSpec =
        TerraformRollbackStepParameters.builder().provisionerIdentifier(ParameterField.createValueField("id")).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());

    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
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

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasS3Files(false).hasGitFiles(false).build();

    StepResponse stepResponse = terraformRollbackStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);

    StepResponse.StepOutcome stepOutcome = ((List<StepResponse.StepOutcome>) stepResponse.getStepOutcomes()).get(0);

    assertThat(stepOutcome.getName()).isEqualTo(TerraformGitRevisionOutcome.OUTCOME_NAME);
    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    verify(stepHelper, times(1)).sendRollbackTelemetryEvent(any(), any(), any());
    verify(terraformStepHelper, times(1)).getRevisionsMap(any(TerraformPassThroughData.class), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(terraformRollbackStepV2.getStepParametersClass()).isEqualTo(StepElementParameters.class);
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

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContext() throws Exception {
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId("executionId").putSetupAbstractions("accountId", "accId").build();
    TerraformRollbackStepParameters rollbackSpec =
        TerraformRollbackStepParameters.builder().provisionerIdentifier(ParameterField.createValueField("id")).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(rollbackSpec).build();

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder()
            .hasS3Files(true)
            .terraformTaskNGParametersBuilder(TerraformTaskNGParameters.builder())
            .build();

    doReturn(TaskChainResponse.builder().taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeNextLink(eq(ambiance), any(), eq(terraformPassThroughData), any(), eq(stepElementParameters), any());

    GitFetchResponse gitFetchResponse = GitFetchResponse.builder().build();

    terraformRollbackStepV2.executeNextLinkWithSecurityContext(ambiance, stepElementParameters,
        StepInputPackage.builder().build(), terraformPassThroughData, () -> gitFetchResponse);

    verify(terraformStepHelper, times(1)).executeNextLink(any(), any(), any(), any(), any(), any());
  }
}
