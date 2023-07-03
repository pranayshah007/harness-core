/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.BitbucketStore;
import io.harness.cdng.manifest.yaml.GitLabStore;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class TerraformApplyStepInfoTest extends CategoryTest {
  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testValidateParams() {
    TerraformApplyStepInfo terraformApplyStepInfo = new TerraformApplyStepInfo();

    TerraformStepConfiguration terraformStepConfiguration = new TerraformStepConfiguration();
    terraformApplyStepInfo.setTerraformStepConfiguration(terraformStepConfiguration);
    Assertions.assertThatThrownBy(terraformApplyStepInfo::validateSpecParams)
        .hasMessageContaining("Step Configuration Type is null");

    terraformStepConfiguration.setTerraformStepConfigurationType(TerraformStepConfigurationType.INHERIT_FROM_APPLY);
    terraformApplyStepInfo.validateSpecParams();

    terraformStepConfiguration.setTerraformStepConfigurationType(TerraformStepConfigurationType.INLINE);
    Assertions.assertThatThrownBy(terraformApplyStepInfo::validateSpecParams)
        .hasMessageContaining("Spec inside Configuration cannot be null");

    TerraformExecutionData terraformExecutionData = new TerraformExecutionData();
    terraformStepConfiguration.setTerraformExecutionData(terraformExecutionData);
    Assertions.assertThatThrownBy(terraformApplyStepInfo::validateSpecParams)
        .hasMessageContaining("Config files are null");

    TerraformConfigFilesWrapper terraformConfigFilesWrapper = new TerraformConfigFilesWrapper();
    terraformExecutionData.setTerraformConfigFilesWrapper(terraformConfigFilesWrapper);
    Assertions.assertThatThrownBy(terraformApplyStepInfo::validateSpecParams)
        .hasMessageContaining("Store cannot be null in Config Files");

    StoreConfigWrapper storeConfigWrapper =
        StoreConfigWrapper.builder().type(StoreConfigType.BITBUCKET).spec(BitbucketStore.builder().build()).build();
    terraformConfigFilesWrapper.setStore(storeConfigWrapper);
    terraformApplyStepInfo.validateSpecParams();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testExtractConnectorRefs() {
    TerraformApplyStepInfo terraformApplyStepInfo = new TerraformApplyStepInfo();
    TerraformExecutionData terraformExecutionData = new TerraformExecutionData();
    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();
    configFilesWrapper.setStore(StoreConfigWrapper.builder()
                                    .spec(GithubStore.builder()
                                              .branch(ParameterField.createValueField("master"))
                                              .gitFetchType(FetchType.BRANCH)
                                              .connectorRef(ParameterField.createValueField("terraform"))
                                              .folderPath(ParameterField.createValueField("Config/"))
                                              .build())
                                    .type(StoreConfigType.GITHUB)
                                    .build());
    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();
    remoteTerraformVarFileSpec.setStore(StoreConfigWrapper.builder()
                                            .spec(GitLabStore.builder()
                                                      .branch(ParameterField.createValueField("master"))
                                                      .gitFetchType(FetchType.BRANCH)
                                                      .connectorRef(ParameterField.createValueField("terraform"))
                                                      .folderPath(ParameterField.createValueField("VarFiles/"))
                                                      .build())
                                            .type(StoreConfigType.GITLAB)
                                            .build());
    terraformExecutionData.setTerraformConfigFilesWrapper(configFilesWrapper);
    TerraformVarFileWrapper terraformVarFileWrapper = new TerraformVarFileWrapper();
    terraformVarFileWrapper.setVarFile(
        TerraformVarFile.builder().identifier("var-file-1").type("Remote").spec(remoteTerraformVarFileSpec).build());
    List<TerraformVarFileWrapper> varFiles = new LinkedList<>();
    varFiles.add(terraformVarFileWrapper);
    terraformExecutionData.setTerraformVarFiles(varFiles);
    TerraformStepConfiguration terraformStepConfiguration = new TerraformStepConfiguration();
    terraformStepConfiguration.setTerraformExecutionData(terraformExecutionData);
    terraformStepConfiguration.setTerraformStepConfigurationType(TerraformStepConfigurationType.INLINE);
    terraformApplyStepInfo.setTerraformStepConfiguration(terraformStepConfiguration);
    Map<String, ParameterField<String>> response = terraformApplyStepInfo.extractConnectorRefs();
    assertThat(response.size()).isEqualTo(2);
    assertThat(response.get("configuration.spec.varFiles.var-file-1.spec.store.spec.connectorRef").getValue())
        .isEqualTo("terraform");
    assertThat(response.get("configuration.spec.configFiles.store.spec.connectorRef").getValue())
        .isEqualTo("terraform");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    TerraformApplyStepInfo terraformApplyStepInfo = new TerraformApplyStepInfo();
    TerraformExecutionData terraformExecutionData = new TerraformExecutionData();
    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();
    configFilesWrapper.setStore(StoreConfigWrapper.builder()
                                    .spec(GithubStore.builder()
                                              .branch(ParameterField.createValueField("master"))
                                              .gitFetchType(FetchType.BRANCH)
                                              .connectorRef(ParameterField.createValueField("terraform"))
                                              .folderPath(ParameterField.createValueField("Config/"))
                                              .build())
                                    .type(StoreConfigType.GITHUB)
                                    .build());
    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();
    remoteTerraformVarFileSpec.setStore(StoreConfigWrapper.builder()
                                            .spec(GitLabStore.builder()
                                                      .branch(ParameterField.createValueField("master"))
                                                      .gitFetchType(FetchType.BRANCH)
                                                      .connectorRef(ParameterField.createValueField("terraform"))
                                                      .folderPath(ParameterField.createValueField("VarFiles/"))
                                                      .build())
                                            .type(StoreConfigType.GITLAB)
                                            .build());
    terraformExecutionData.setTerraformConfigFilesWrapper(configFilesWrapper);
    TerraformVarFileWrapper terraformVarFileWrapper = new TerraformVarFileWrapper();
    terraformVarFileWrapper.setVarFile(
        TerraformVarFile.builder().identifier("var-file-1").type("Remote").spec(remoteTerraformVarFileSpec).build());
    List<TerraformVarFileWrapper> varFiles = new LinkedList<>();
    varFiles.add(terraformVarFileWrapper);
    terraformExecutionData.setTerraformVarFiles(varFiles);
    TerraformStepConfiguration terraformStepConfiguration = new TerraformStepConfiguration();
    terraformStepConfiguration.setTerraformExecutionData(terraformExecutionData);
    terraformStepConfiguration.setTerraformStepConfigurationType(TerraformStepConfigurationType.INLINE);
    terraformApplyStepInfo.setTerraformStepConfiguration(terraformStepConfiguration);
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("sel1");
    terraformApplyStepInfo.setDelegateSelectors(ParameterField.createValueField(Arrays.asList(taskSelectorYaml)));

    SpecParameters specParameters = terraformApplyStepInfo.getSpecParameters();
    TerraformApplyStepParameters terraformApplyStepParameters = (TerraformApplyStepParameters) specParameters;
    assertThat(specParameters).isNotNull();
    assertThat(terraformApplyStepParameters.configuration.getType()).isEqualTo(TerraformStepConfigurationType.INLINE);
    assertThat(terraformApplyStepParameters.configuration.getSpec().configFiles.store.getType())
        .isEqualTo(StoreConfigType.GITHUB);
    assertThat(terraformApplyStepParameters.configuration.getSpec().varFiles.get("var-file-1").type)
        .isEqualTo("Remote");
    assertThat(terraformApplyStepParameters.configuration.getSpec().varFiles.get("var-file-1").identifier)
        .isEqualTo("var-file-1");
    assertThat(terraformApplyStepParameters.delegateSelectors.getValue().get(0).getDelegateSelectors())
        .isEqualTo("sel1");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetSpecParametersAndCloudCli() {
    TerraformApplyStepInfo terraformApplyStepInfo = new TerraformApplyStepInfo();
    TerraformCloudCliExecutionData terraformExecutionData = new TerraformCloudCliExecutionData();
    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();
    configFilesWrapper.setStore(StoreConfigWrapper.builder()
                                    .spec(GithubStore.builder()
                                              .branch(ParameterField.createValueField("master"))
                                              .gitFetchType(FetchType.BRANCH)
                                              .connectorRef(ParameterField.createValueField("terraform"))
                                              .folderPath(ParameterField.createValueField("Config/"))
                                              .build())
                                    .type(StoreConfigType.GITHUB)
                                    .build());
    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();
    remoteTerraformVarFileSpec.setStore(StoreConfigWrapper.builder()
                                            .spec(GitLabStore.builder()
                                                      .branch(ParameterField.createValueField("master"))
                                                      .gitFetchType(FetchType.BRANCH)
                                                      .connectorRef(ParameterField.createValueField("terraform"))
                                                      .folderPath(ParameterField.createValueField("VarFiles/"))
                                                      .build())
                                            .type(StoreConfigType.GITLAB)
                                            .build());
    terraformExecutionData.setTerraformConfigFilesWrapper(configFilesWrapper);
    TerraformVarFileWrapper terraformVarFileWrapper = new TerraformVarFileWrapper();
    terraformVarFileWrapper.setVarFile(
        TerraformVarFile.builder().identifier("var-file-1").type("Remote").spec(remoteTerraformVarFileSpec).build());
    List<TerraformVarFileWrapper> varFiles = new LinkedList<>();
    varFiles.add(terraformVarFileWrapper);
    terraformExecutionData.setTerraformVarFiles(varFiles);
    TerraformCloudCliStepConfiguration terraformStepConfiguration = new TerraformCloudCliStepConfiguration();
    terraformStepConfiguration.setTerraformCloudCliExecutionData(terraformExecutionData);
    terraformApplyStepInfo.setTerraformCloudCliStepConfiguration(terraformStepConfiguration);
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("sel1");
    terraformApplyStepInfo.setDelegateSelectors(ParameterField.createValueField(Arrays.asList(taskSelectorYaml)));

    SpecParameters specParameters = terraformApplyStepInfo.getSpecParameters();
    TerraformApplyStepParameters terraformApplyStepParameters = (TerraformApplyStepParameters) specParameters;
    assertThat(specParameters).isNotNull();
    assertThat(terraformApplyStepParameters.configuration.getSpec().configFiles.store.getType())
        .isEqualTo(StoreConfigType.GITHUB);
    assertThat(terraformApplyStepParameters.configuration.getSpec().varFiles.get("var-file-1").type)
        .isEqualTo("Remote");
    assertThat(terraformApplyStepParameters.configuration.getSpec().varFiles.get("var-file-1").identifier)
        .isEqualTo("var-file-1");
    assertThat(terraformApplyStepParameters.delegateSelectors.getValue().get(0).getDelegateSelectors())
        .isEqualTo("sel1");
    assertThat(terraformApplyStepParameters.getConfiguration().getSpec().getIsTerraformCloudCli().getValue()).isTrue();
    assertThat(terraformApplyStepParameters.getConfiguration().getType().getDisplayName()).isEqualTo("Inline");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    TerraformApplyStepInfo terraformApplyStepInfo = new TerraformApplyStepInfo();
    String response = terraformApplyStepInfo.getFacilitatorType();
    assertThat(response).isEqualTo("TASK_CHAIN");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetStepType() {
    TerraformApplyStepInfo terraformApplyStepInfo = new TerraformApplyStepInfo();
    StepType response = terraformApplyStepInfo.getStepType();
    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo("TERRAFORM_APPLY_V2");
  }
}
