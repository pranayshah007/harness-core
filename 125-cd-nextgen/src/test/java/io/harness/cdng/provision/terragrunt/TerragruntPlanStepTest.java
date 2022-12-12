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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.provision.terraform.TerraformStepDataGenerator;
import io.harness.cdng.provision.terragrunt.outcome.TerragruntPlanOutcome;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.InlineFileConfig;
import io.harness.delegate.beans.storeconfig.InlineStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.terragrunt.request.TerragruntCommandType;
import io.harness.delegate.beans.terragrunt.request.TerragruntPlanTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntTaskRunType;
import io.harness.delegate.beans.terragrunt.response.TerragruntPlanTaskResponse;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({StepUtils.class})
@OwnedBy(HarnessTeam.CDP)
public class TerragruntPlanStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private KryoSerializer kryoSerializer;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private TerragruntStepHelper terragruntStepHelper;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private StepHelper stepHelper;
  @InjectMocks private TerragruntPlanStep terragruntPlanStep = new TerragruntPlanStep();
  @Captor ArgumentCaptor<List<EntityDetail>> captor;

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "test-account")
        .putSetupAbstractions("projectIdentifier", "test-project")
        .putSetupAbstractions("orgIdentifier", "test-org")
        .build();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testValidateResourcesWithGithubStore() {
    TerragruntConfigFilesWrapper configFilesWrapper = createConfigFilesWrapper();
    LinkedHashMap<String, TerragruntVarFile> varFilesMap = createVarFilesRemote();
    TerragruntBackendConfig terragruntBackendConfig = createRemoteBackendConfig();

    TerragruntPlanStepParameters parameters =
        TerragruntPlanStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("test-provisionerId"))
            .configuration(TerragruntPlanExecutionDataParameters.builder()
                               .configFiles(configFilesWrapper)
                               .backendConfig(terragruntBackendConfig)
                               .varFiles(varFilesMap)
                               .secretManagerRef(ParameterField.createValueField("test-secretManager"))
                               .build())
            .build();

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();
    terragruntPlanStep.validateResources(getAmbiance(), stepElementParameters);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(getAmbiance()), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(4);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("terragrunt-configFiles");
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo("terragrunt-varFiles");
    assertThat(entityDetails.get(2).getEntityRef().getIdentifier()).isEqualTo("terragrunt-backendFile");
    assertThat(entityDetails.get(3).getEntityRef().getIdentifier()).isEqualTo("test-secretManager");
  }

  private TerragruntBackendConfig createInlineBackendConfig() {
    InlineTerragruntBackendConfigSpec inlineTerragruntBackendConfigSpec = new InlineTerragruntBackendConfigSpec();
    inlineTerragruntBackendConfigSpec.setContent(ParameterField.createValueField("back-content"));
    return TerragruntBackendConfig.builder()
        .type(TerragruntBackendFileTypes.Inline)
        .spec(inlineTerragruntBackendConfigSpec)
        .build();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testobtainTaskAfterRbac() {
    TerragruntConfigFilesWrapper configFilesWrapper = createConfigFilesWrapper();
    LinkedHashMap<String, TerragruntVarFile> varFilesMap = createVarFilesInline();
    TerragruntBackendConfig backendConfig = createInlineBackendConfig();

    Map<String, Object> envVars = new HashMap<>() {
      { put("envKey", "envVal"); }
    };

    TerragruntPlanStepParameters parameters =
        TerragruntPlanStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("test-provisionerId"))
            .configuration(TerragruntPlanExecutionDataParameters.builder()
                               .command(TerragruntPlanCommand.APPLY)
                               .configFiles(configFilesWrapper)
                               .backendConfig(backendConfig)
                               .varFiles(varFilesMap)
                               .terragruntModuleConfig(createTerragruntModuleConfig())
                               .exportTerragruntPlanJson(ParameterField.createValueField(true))
                               .targets(ParameterField.createValueField(Collections.singletonList("test-target")))
                               .environmentVariables(envVars)
                               .workspace(ParameterField.createValueField("test-workspace"))
                               .secretManagerRef(ParameterField.createValueField("test-secretManager"))
                               .build())
            .build();

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();

    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    when(terragruntStepHelper.generateFullIdentifier(any(), any())).thenReturn("testEntityId");
    when(terragruntStepHelper.getGitFetchFilesConfig(any(), any(), any())).thenReturn(createGitStoreDelegateConfig());
    when(terragruntStepHelper.toStoreDelegateVarFiles(anyMap(), any()))
        .thenReturn(Collections.singletonList(
            InlineStoreDelegateConfig.builder()
                .identifier("test-var1-id")
                .files(Collections.singletonList(InlineFileConfig.builder().content("test-var1-content").build()))
                .build()));
    when(terragruntStepHelper.getBackendConfig(any(), any()))
        .thenReturn(InlineStoreDelegateConfig.builder().identifier("test-backend-id").build());
    when(terragruntStepHelper.getEnvironmentVariablesMap(any())).thenReturn(new HashMap<>());
    when(terragruntStepHelper.getLatestFileId(any())).thenReturn(null);
    Mockito.mockStatic(StepUtils.class);

    terragruntPlanStep.obtainTaskAfterRbac(getAmbiance(), stepElementParameters, StepInputPackage.builder().build());
    PowerMockito.verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerragruntPlanTaskParameters params =
        (TerragruntPlanTaskParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(params.getCommandType()).isEqualTo(TerragruntCommandType.APPLY);
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
  public void testhandleTaskResultWithSecurityContext() throws Exception {
    Ambiance ambiance = getAmbiance();

    List<UnitProgress> unitProgresses = new ArrayList<>();
    unitProgresses.add(UnitProgress.newBuilder().setStatus(UnitStatus.SUCCESS).build());

    TerragruntPlanTaskResponse terraformTaskNGResponse =
        TerragruntPlanTaskResponse.builder()
            .unitProgressData(UnitProgressData.builder().unitProgresses(unitProgresses).build())
            .stateFileId("test-stateFileId")
            .build();

    TerragruntPlanStepParameters parameters =
        TerragruntPlanStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("test-provisionerId"))
            .configuration(TerragruntPlanExecutionDataParameters.builder()
                               .command(TerragruntPlanCommand.APPLY)
                               .configFiles(createConfigFilesWrapper())
                               .backendConfig(createInlineBackendConfig())
                               .varFiles(createVarFilesInline())
                               .terragruntModuleConfig(createTerragruntModuleConfig())
                               .exportTerragruntPlanJson(ParameterField.createValueField(true))
                               .targets(ParameterField.createValueField(Collections.singletonList("test-target")))
                               .environmentVariables(new HashMap<>())
                               .workspace(ParameterField.createValueField("test-workspace"))
                               .secretManagerRef(ParameterField.createValueField("test-secretManager"))
                               .build())

            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();

    StepResponse stepResponse = terragruntPlanStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> terraformTaskNGResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    StepResponse.StepOutcome stepOutcome = ((List<StepResponse.StepOutcome>) stepResponse.getStepOutcomes()).get(0);
    assertThat(stepOutcome.getOutcome()).isInstanceOf(TerragruntPlanOutcome.class);
    verify(terragruntStepHelper, times(1)).saveTerragruntInheritOutput(any(), any(), any());
    verify(terragruntStepHelper, times(1)).updateParentEntityIdAndVersion(any(), any());
    verify(terragruntStepHelper, times(1)).generateFullIdentifier(any(), any());
    verify(terragruntStepHelper, times(1)).saveTerragruntPlanExecutionDetails(any(), any(), any(), any());
    verify(terragruntStepHelper, times(1)).saveTerraformPlanJsonOutput(any(), any(), any());
  }

  private TerragruntConfigFilesWrapper createConfigFilesWrapper() {
    TerragruntConfigFilesWrapper configFilesWrapper = new TerragruntConfigFilesWrapper();
    StoreConfig storeConfigFiles;

    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terragrunt-configFiles"))
            .build();

    storeConfigFiles =
        GithubStore.builder()
            .branch(ParameterField.createValueField(gitStoreConfigFiles.getBranch()))
            .gitFetchType(gitStoreConfigFiles.getFetchType())
            .folderPath(ParameterField.createValueField(gitStoreConfigFiles.getFolderPath().getValue()))
            .connectorRef(ParameterField.createValueField(gitStoreConfigFiles.getConnectoref().getValue()))
            .build();
    configFilesWrapper.setStore(
        StoreConfigWrapper.builder().spec(storeConfigFiles).type(StoreConfigType.GITHUB).build());

    return configFilesWrapper;
  }

  private LinkedHashMap<String, TerragruntVarFile> createVarFilesRemote() {
    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("varFiles/"))
            .connectoref(ParameterField.createValueField("terragrunt-varFiles"))
            .build();

    StoreConfig storeVarFiles;
    RemoteTerragruntVarFileSpec remoteTerragruntVarFileSpec = new RemoteTerragruntVarFileSpec();

    storeVarFiles = GithubStore.builder()
                        .branch(ParameterField.createValueField(gitStoreVarFiles.getBranch()))
                        .gitFetchType(gitStoreVarFiles.getFetchType())
                        .folderPath(ParameterField.createValueField(gitStoreVarFiles.getFolderPath().getValue()))
                        .connectorRef(ParameterField.createValueField(gitStoreVarFiles.getConnectoref().getValue()))
                        .build();

    remoteTerragruntVarFileSpec.setStore(
        StoreConfigWrapper.builder().spec(storeVarFiles).type(StoreConfigType.GITHUB).build());

    LinkedHashMap<String, TerragruntVarFile> varFilesMap = new LinkedHashMap<>();
    varFilesMap.put("var-file-01",
        TerragruntVarFile.builder().identifier("var-file-01").type("Remote").spec(remoteTerragruntVarFileSpec).build());
    return varFilesMap;
  }

  private TerragruntBackendConfig createRemoteBackendConfig() {
    TerraformStepDataGenerator.GitStoreConfig gitStoreBackend =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("backend/"))
            .connectoref(ParameterField.createValueField("terragrunt-backendFile"))
            .build();

    StoreConfig storeBackend;
    storeBackend = GithubStore.builder()
                       .branch(ParameterField.createValueField(gitStoreBackend.getBranch()))
                       .gitFetchType(gitStoreBackend.getFetchType())
                       .folderPath(ParameterField.createValueField(gitStoreBackend.getFolderPath().getValue()))
                       .connectorRef(ParameterField.createValueField(gitStoreBackend.getConnectoref().getValue()))
                       .build();

    RemoteTerragruntBackendConfigSpec remoteTerragruntBackendConfigSpec = new RemoteTerragruntBackendConfigSpec();
    remoteTerragruntBackendConfigSpec.setStore(
        StoreConfigWrapper.builder().spec(storeBackend).type(StoreConfigType.GITHUB).build());

    TerragruntBackendConfig terragruntBackendConfig = new TerragruntBackendConfig();
    terragruntBackendConfig.setType("Remote");
    terragruntBackendConfig.setTerragruntBackendConfigSpec(remoteTerragruntBackendConfigSpec);
    return terragruntBackendConfig;
  }

  private GitStoreDelegateConfig createGitStoreDelegateConfig() {
    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .delegateSelectors(Collections.singleton("delegateName"))
                                    .url("https://github.com/wings-software")
                                    .branchName("master")
                                    .build();
    return GitStoreDelegateConfig.builder()
        .branch("master")
        .connectorName("terragrunt")
        .gitConfigDTO(gitConfigDTO)
        .build();
  }

  private LinkedHashMap<String, TerragruntVarFile> createVarFilesInline() {
    InlineTerragruntVarFileSpec inlineTerragruntVarFileSpec = new InlineTerragruntVarFileSpec();
    inlineTerragruntVarFileSpec.setContent(ParameterField.createValueField("test-backendContent"));
    LinkedHashMap<String, TerragruntVarFile> varFilesMap = new LinkedHashMap<>();
    varFilesMap.put("var-file-01",
        TerragruntVarFile.builder().identifier("var-file-01").type("Inline").spec(inlineTerragruntVarFileSpec).build());
    return varFilesMap;
  }

  private TerragruntModuleConfig createTerragruntModuleConfig() {
    TerragruntModuleConfig terragruntModuleConfig = new TerragruntModuleConfig();
    terragruntModuleConfig.terragruntRunType = TerragruntRunType.RUN_MODULE;
    terragruntModuleConfig.path = ParameterField.createValueField("test-path");
    return terragruntModuleConfig;
  }
}