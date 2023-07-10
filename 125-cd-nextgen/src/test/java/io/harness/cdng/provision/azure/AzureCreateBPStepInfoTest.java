/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class AzureCreateBPStepInfoTest extends CategoryTest {
  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParams() {
    AzureCreateBPStepInfo azureCreateStepInfo = new AzureCreateBPStepInfo();
    AzureTemplateFile templateFile = new AzureTemplateFile();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>()))
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .build())
            .build();

    templateFile.setStore(templateStore);
    azureCreateStepInfo.setCreateStepBPConfiguration(AzureCreateBPStepConfiguration.builder()
                                                         .connectorRef(ParameterField.createValueField("connectorRef"))
                                                         .template(templateFile)
                                                         .scope(AzureBPScopes.SUBSCRIPTION)
                                                         .build());
    azureCreateStepInfo.validateSpecParameters();
    assertThat(azureCreateStepInfo.getCreateStepBPConfiguration().getConnectorRef().getValue())
        .isEqualTo("connectorRef");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoDeploymentInfo() {
    AzureCreateBPStepInfo azureCreateStepInfo = new AzureCreateBPStepInfo();
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters)
        .hasMessageContaining("AzureCreateBPResource Step configuration is null");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoConfiguration() {
    AzureCreateBPStepInfo azureCreateStepInfo = new AzureCreateBPStepInfo();
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters)
        .hasMessageContaining("AzureCreateBPResource Step configuration is null");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoConnectorRef() {
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>()))
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .build())
            .build();

    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(templateStore);
    AzureCreateBPStepInfo azureCreateStepInfo = new AzureCreateBPStepInfo();
    azureCreateStepInfo.setCreateStepBPConfiguration(
        AzureCreateBPStepConfiguration.builder().template(templateFile).scope(AzureBPScopes.SUBSCRIPTION).build());
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters)
        .hasMessageContaining("Connector ref can't be empty");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoTemplateFiles() {
    AzureCreateBPStepInfo azureCreateStepInfo = new AzureCreateBPStepInfo();
    azureCreateStepInfo.setCreateStepBPConfiguration(AzureCreateBPStepConfiguration.builder()
                                                         .connectorRef(ParameterField.createValueField("connectorRef"))
                                                         .scope(AzureBPScopes.SUBSCRIPTION)
                                                         .build());
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters)
        .hasMessageContaining("Template file can't be empty");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractConnectorRefForBP() {
    AzureCreateBPStepInfo azureCreateStepInfo = new AzureCreateBPStepInfo();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>()))
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .build())
            .build();

    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(templateStore);
    azureCreateStepInfo.setCreateStepBPConfiguration(
        AzureCreateBPStepConfiguration.builder()
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("azConnectorRef"))
            .build());
    Map<String, ParameterField<String>> parameterFieldMap = azureCreateStepInfo.extractConnectorRefs();
    assertThat(parameterFieldMap.size()).isEqualTo(2);
    assertThat(parameterFieldMap.get("configuration.spec.connectorRef").getValue()).isEqualTo("azConnectorRef");
    assertThat(parameterFieldMap.get("configuration.spec.templateFile.store.spec.connectorRef").getValue())
        .isEqualTo("template-connector-ref");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    AzureCreateBPStepInfo azureCreateStepInfo = new AzureCreateBPStepInfo();
    String response = azureCreateStepInfo.getFacilitatorType();
    assertThat(response).isEqualTo("TASK_CHAIN");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetStepType() {
    AzureCreateBPStepInfo azureCreateStepInfo = new AzureCreateBPStepInfo();
    StepType response = azureCreateStepInfo.getStepType();
    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo("AzureCreateBPResource");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExtractRefs() {
    AzureCreateBPStepInfo azureCreateBPStepInfo = new AzureCreateBPStepInfo();
    AzureTemplateFile templateFile = new AzureTemplateFile();
    StoreConfigWrapper configStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(HarnessStore.builder().files(ParameterField.createValueField(List.of("/script.sh"))).build())
            .build();
    templateFile.setStore(configStoreConfigWrapper);
    azureCreateBPStepInfo.setCreateStepBPConfiguration(
        AzureCreateBPStepConfiguration.builder()
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("azConnectorRef"))
            .build());
    Map<String, ParameterField<List<String>>> fileMap;
    fileMap = azureCreateBPStepInfo.extractFileRefs();
    assertThat(fileMap.get("configuration.template.store.spec.files").getValue().size()).isEqualTo(1);
    assertThat(fileMap.get("configuration.template.store.spec.files").getValue().get(0)).isEqualTo("/script.sh");
  }
}
