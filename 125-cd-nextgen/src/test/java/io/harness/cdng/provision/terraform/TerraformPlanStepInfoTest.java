/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.BitbucketStore;
import io.harness.cdng.manifest.yaml.GitLabStore;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.TrimmerFunctor;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.core.variables.StringNGVariable;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class TerraformPlanStepInfoTest extends CategoryTest {
  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testValidateParams() {
    TerraformPlanStepInfo terraformPlanStepInfo = new TerraformPlanStepInfo();

    TerraformPlanExecutionData terraformPlanExecutionData = TerraformPlanExecutionData.builder().build();
    terraformPlanStepInfo.setTerraformPlanExecutionData(terraformPlanExecutionData);
    Assertions.assertThatThrownBy(terraformPlanStepInfo::validateSpecParams)
        .hasMessageContaining("Config files are null");

    TerraformConfigFilesWrapper terraformConfigFilesWrapper = new TerraformConfigFilesWrapper();
    terraformPlanExecutionData.setTerraformConfigFilesWrapper(terraformConfigFilesWrapper);
    Assertions.assertThatThrownBy(terraformPlanStepInfo::validateSpecParams)
        .hasMessageContaining("Store cannot be null in Config Files");

    StoreConfigWrapper storeConfigWrapper =
        StoreConfigWrapper.builder().type(StoreConfigType.BITBUCKET).spec(BitbucketStore.builder().build()).build();
    terraformConfigFilesWrapper.setStore(storeConfigWrapper);
    Assertions.assertThatThrownBy(terraformPlanStepInfo::validateSpecParams)
        .hasMessageContaining("Terraform Plan command is null");

    terraformPlanExecutionData.setCommand(TerraformPlanCommand.APPLY);
    Assertions.assertThatThrownBy(terraformPlanStepInfo::validateSpecParams)
        .hasMessageContaining("Secret Manager Ref for Tf plan is null");

    // should validate successfully
    terraformPlanExecutionData.setSecretManagerRef(ParameterField.createValueField("KMS"));
    terraformPlanStepInfo.validateSpecParams();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testExtractConnectorRefs() {
    TerraformPlanStepInfo terraformPlanStepInfo = new TerraformPlanStepInfo();
    TerraformPlanExecutionData terraformPlanExecutionData = TerraformPlanExecutionData.builder().build();
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
    terraformPlanExecutionData.setTerraformConfigFilesWrapper(configFilesWrapper);
    terraformPlanExecutionData.setCommand(TerraformPlanCommand.APPLY);
    terraformPlanExecutionData.setSecretManagerRef(ParameterField.createValueField("secret"));
    TerraformVarFileWrapper terraformVarFileWrapper = new TerraformVarFileWrapper();
    terraformVarFileWrapper.setVarFile(
        TerraformVarFile.builder().identifier("var-file-1").type("Remote").spec(remoteTerraformVarFileSpec).build());
    List<TerraformVarFileWrapper> varFiles = new LinkedList<>();
    varFiles.add(terraformVarFileWrapper);
    terraformPlanExecutionData.setTerraformVarFiles(varFiles);
    terraformPlanStepInfo.setTerraformPlanExecutionData(terraformPlanExecutionData);
    Map<String, ParameterField<String>> response = terraformPlanStepInfo.extractConnectorRefs();
    assertThat(response.size()).isEqualTo(3);
    assertThat(response.get("configuration.varFiles.var-file-1.spec.store.spec.connectorRef").getValue())
        .isEqualTo("terraform");
    assertThat(response.get("configuration.configFiles.store.spec.connectorRef").getValue()).isEqualTo("terraform");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    TerraformPlanStepInfo terraformPlanStepInfo = new TerraformPlanStepInfo();
    TerraformPlanExecutionData terraformPlanExecutionData = TerraformPlanExecutionData.builder().build();
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
    terraformPlanExecutionData.setTerraformConfigFilesWrapper(configFilesWrapper);
    TerraformVarFileWrapper terraformVarFileWrapper = new TerraformVarFileWrapper();
    terraformVarFileWrapper.setVarFile(
        TerraformVarFile.builder().identifier("var-file-1").type("Remote").spec(remoteTerraformVarFileSpec).build());
    List<TerraformVarFileWrapper> varFiles = new LinkedList<>();
    varFiles.add(terraformVarFileWrapper);
    terraformPlanExecutionData.setTerraformVarFiles(varFiles);
    terraformPlanExecutionData.setCommand(TerraformPlanCommand.APPLY);
    terraformPlanExecutionData.setSecretManagerRef(ParameterField.createValueField("secret"));
    terraformPlanStepInfo.setTerraformPlanExecutionData(terraformPlanExecutionData);
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("sel1");
    terraformPlanStepInfo.setDelegateSelectors(ParameterField.createValueField(Arrays.asList(taskSelectorYaml)));
    SpecParameters specParameters = terraformPlanStepInfo.getSpecParameters();
    TerraformPlanStepParameters terraformPlanStepParameters = (TerraformPlanStepParameters) specParameters;
    assertThat(specParameters).isNotNull();
    assertThat(terraformPlanStepParameters.configuration.command).isEqualTo(TerraformPlanCommand.APPLY);
    assertThat(terraformPlanStepParameters.configuration.configFiles.store.getType()).isEqualTo(StoreConfigType.GITHUB);
    assertThat(terraformPlanStepParameters.configuration.varFiles.get("var-file-1").type).isEqualTo("Remote");
    assertThat(terraformPlanStepParameters.configuration.varFiles.get("var-file-1").identifier).isEqualTo("var-file-1");
    assertThat(terraformPlanStepParameters.delegateSelectors.getValue().get(0).getDelegateSelectors())
        .isEqualTo("sel1");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetSpecParametersAndTFCloudCli() {
    TerraformPlanStepInfo terraformPlanStepInfo = new TerraformPlanStepInfo();
    TerraformCloudCliPlanExecutionData terraformPlanExecutionData =
        TerraformCloudCliPlanExecutionData.builder().build();
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
    terraformPlanExecutionData.setTerraformConfigFilesWrapper(configFilesWrapper);
    TerraformVarFileWrapper terraformVarFileWrapper = new TerraformVarFileWrapper();
    terraformVarFileWrapper.setVarFile(
        TerraformVarFile.builder().identifier("var-file-1").type("Remote").spec(remoteTerraformVarFileSpec).build());
    List<TerraformVarFileWrapper> varFiles = new LinkedList<>();
    varFiles.add(terraformVarFileWrapper);
    terraformPlanExecutionData.setTerraformVarFiles(varFiles);
    terraformPlanExecutionData.setCommand(TerraformPlanCommand.APPLY);
    terraformPlanStepInfo.setTerraformCloudCliPlanExecutionData(terraformPlanExecutionData);
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("sel1");
    terraformPlanStepInfo.setDelegateSelectors(ParameterField.createValueField(Arrays.asList(taskSelectorYaml)));
    SpecParameters specParameters = terraformPlanStepInfo.getSpecParameters();
    TerraformPlanStepParameters terraformPlanStepParameters = (TerraformPlanStepParameters) specParameters;
    assertThat(specParameters).isNotNull();
    assertThat(terraformPlanStepParameters.configuration.command).isEqualTo(TerraformPlanCommand.APPLY);
    assertThat(terraformPlanStepParameters.configuration.configFiles.store.getType()).isEqualTo(StoreConfigType.GITHUB);
    assertThat(terraformPlanStepParameters.configuration.varFiles.get("var-file-1").type).isEqualTo("Remote");
    assertThat(terraformPlanStepParameters.configuration.varFiles.get("var-file-1").identifier).isEqualTo("var-file-1");
    assertThat(terraformPlanStepParameters.delegateSelectors.getValue().get(0).getDelegateSelectors())
        .isEqualTo("sel1");
    assertThat(terraformPlanStepParameters.getConfiguration().getIsTerraformCloudCli().getValue()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    TerraformPlanStepInfo terraformPlanStepInfo = new TerraformPlanStepInfo();
    String response = terraformPlanStepInfo.getFacilitatorType();
    assertThat(response).isEqualTo("TASK");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetStepType() {
    TerraformPlanStepInfo terraformPlanStepInfo = new TerraformPlanStepInfo();
    StepType response = terraformPlanStepInfo.getStepType();
    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo("TerraformPlan");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testTrimmerFunctor() {
    TerraformPlanExecutionData terraformPlanExecutionData =
        TerraformPlanExecutionData.builder()
            .targets(ParameterField.createValueField(Arrays.asList(" t1", "t2 ")))
            .environmentVariables(Arrays.asList(
                StringNGVariable.builder().name(" name").value(ParameterField.createValueField(" value")).build()))
            .workspace(ParameterField.createExpressionField(true, "<+workspace>  ", null, true))
            .build();

    TaskSelectorYaml taskSelector1 = new TaskSelectorYaml(" del1 ");
    TaskSelectorYaml taskSelector2 = new TaskSelectorYaml("del2  ");

    TerraformPlanStepInfo terraformPlanStepInfo =
        TerraformPlanStepInfo.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("  abc  "))
            .delegateSelectors(ParameterField.createValueField(Arrays.asList(taskSelector1, taskSelector2)))
            .terraformPlanExecutionData(terraformPlanExecutionData)
            .build();

    terraformPlanStepInfo =
        (TerraformPlanStepInfo) ExpressionEvaluatorUtils.updateExpressions(terraformPlanStepInfo, new TrimmerFunctor());

    assertThat(terraformPlanStepInfo.getProvisionerIdentifier().getValue()).isEqualTo("abc");
    assertThat(terraformPlanStepInfo.getDelegateSelectors().getValue())
        .isEqualTo(Arrays.asList(taskSelector1, taskSelector2));
    assertThat(terraformPlanStepInfo.getTerraformPlanExecutionData().getTargets().getValue())
        .isEqualTo(Arrays.asList("t1", "t2"));
    assertThat(terraformPlanStepInfo.getTerraformPlanExecutionData().getEnvironmentVariables().get(0).getName())
        .isEqualTo("name");
    assertThat(terraformPlanStepInfo.getTerraformPlanExecutionData()
                   .getEnvironmentVariables()
                   .get(0)
                   .getCurrentValue()
                   .getValue())
        .isEqualTo("value");
    assertThat(terraformPlanStepInfo.getTerraformPlanExecutionData().getWorkspace().getExpressionValue())
        .isEqualTo("<+workspace>");
  }
}
