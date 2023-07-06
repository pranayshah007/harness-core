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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class AzureCreateARMResourceStepInfoTest extends CategoryTest {
  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParams() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .build())
            .build();

    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(templateStore);

    StoreConfigWrapper fileStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("parameters-connector-ref"))
                      .build())
            .build();
    AzureCreateARMResourceParameterFile parametersFileBuilder = new AzureCreateARMResourceParameterFile();
    parametersFileBuilder.setStore(fileStoreConfigWrapper);

    azureCreateStepInfo.setCreateStepConfiguration(
        AzureCreateARMResourceStepConfiguration.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .template(templateFile)
            .parameters(parametersFileBuilder)
            .scope(AzureCreateARMResourceStepScope.builder()
                       .spec(AzureResourceGroupSpec.builder()
                                 .subscription(ParameterField.createValueField("foobar"))
                                 .resourceGroup(ParameterField.createValueField("bar"))
                                 .mode(AzureARMResourceDeploymentMode.COMPLETE)
                                 .build())
                       .build())
            .build());
    azureCreateStepInfo.validateSpecParameters();
    assertThat(azureCreateStepInfo.getCreateStepConfiguration().getConnectorRef().getValue()).isEqualTo("connectorRef");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoDeploymentInfo() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters)
        .hasMessageContaining("AzureCreateARMResource Step configuration is null");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoConfiguration() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters)
        .hasMessageContaining("AzureCreateARMResource Step configuration is null");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoConnectorRef() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .build())
            .build();

    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(templateStore);

    StoreConfigWrapper fileStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("parameters-connector-ref"))
                      .build())
            .build();
    AzureCreateARMResourceParameterFile parametersFileBuilder = new AzureCreateARMResourceParameterFile();
    parametersFileBuilder.setStore(fileStoreConfigWrapper);
    azureCreateStepInfo.setCreateStepConfiguration(
        AzureCreateARMResourceStepConfiguration.builder()
            .template(templateFile)
            .parameters(parametersFileBuilder)
            .scope(AzureCreateARMResourceStepScope.builder()
                       .spec(AzureResourceGroupSpec.builder()
                                 .subscription(ParameterField.createValueField("foobar"))
                                 .resourceGroup(ParameterField.createValueField("bar"))
                                 .mode(AzureARMResourceDeploymentMode.COMPLETE)
                                 .build())
                       .build())
            .build());
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters)
        .hasMessageContaining("Connector ref can't be empty");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoTemplateFiles() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    StoreConfigWrapper fileStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("parameters-connector-ref"))
                      .build())
            .build();
    AzureCreateARMResourceParameterFile parametersFileBuilder = new AzureCreateARMResourceParameterFile();
    parametersFileBuilder.setStore(fileStoreConfigWrapper);
    azureCreateStepInfo.setCreateStepConfiguration(
        AzureCreateARMResourceStepConfiguration.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .parameters(parametersFileBuilder)
            .scope(AzureCreateARMResourceStepScope.builder()
                       .spec(AzureResourceGroupSpec.builder()
                                 .subscription(ParameterField.createValueField("foobar"))
                                 .resourceGroup(ParameterField.createValueField("bar"))
                                 .mode(AzureARMResourceDeploymentMode.COMPLETE)
                                 .build())
                       .build())
            .build());
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters)
        .hasMessageContaining("Template file can't be empty");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoScope() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .build())
            .build();

    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(templateStore);
    azureCreateStepInfo.setCreateStepConfiguration(AzureCreateARMResourceStepConfiguration.builder()
                                                       .template(templateFile)
                                                       .connectorRef(ParameterField.createValueField("connectorRef"))
                                                       .build());
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters).hasMessageContaining("Scope can't be empty");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoSubscriptionAtRGLevel() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .build())
            .build();
    AzureCreateARMResourceParameterFile parametersFileBuilder = new AzureCreateARMResourceParameterFile();
    parametersFileBuilder.setStore(templateStore);
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(templateStore);
    azureCreateStepInfo.setCreateStepConfiguration(
        AzureCreateARMResourceStepConfiguration.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .template(templateFile)
            .parameters(parametersFileBuilder)
            .scope(AzureCreateARMResourceStepScope.builder()
                       .spec(AzureResourceGroupSpec.builder()
                                 .resourceGroup(ParameterField.createValueField("bar"))
                                 .mode(AzureARMResourceDeploymentMode.COMPLETE)
                                 .build())
                       .build())
            .build());
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters).hasMessageContaining("subscription can't be null");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoResourceGroupAtRGLEvel() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .build())
            .build();
    AzureCreateARMResourceParameterFile parametersFileBuilder = new AzureCreateARMResourceParameterFile();
    parametersFileBuilder.setStore(templateStore);
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(templateStore);
    azureCreateStepInfo.setCreateStepConfiguration(
        AzureCreateARMResourceStepConfiguration.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .template(templateFile)
            .parameters(parametersFileBuilder)
            .scope(AzureCreateARMResourceStepScope.builder()
                       .spec(AzureResourceGroupSpec.builder()
                                 .subscription(ParameterField.createValueField("foobar"))
                                 .mode(AzureARMResourceDeploymentMode.COMPLETE)
                                 .build())
                       .build())
            .build());
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters).hasMessageContaining("resourceGroup can't be null");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoModeAtRGLevel() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .build())
            .build();
    AzureCreateARMResourceParameterFile parametersFileBuilder = new AzureCreateARMResourceParameterFile();
    parametersFileBuilder.setStore(templateStore);
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(templateStore);
    azureCreateStepInfo.setCreateStepConfiguration(
        AzureCreateARMResourceStepConfiguration.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .template(templateFile)
            .parameters(parametersFileBuilder)
            .scope(AzureCreateARMResourceStepScope.builder()
                       .spec(AzureResourceGroupSpec.builder()
                                 .subscription(ParameterField.createValueField("foobar"))
                                 .resourceGroup(ParameterField.createValueField("bar"))
                                 .build())
                       .build())
            .build());
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters).hasMessageContaining("mode can't be null");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoSubscriptionAtSubLevel() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .build())
            .build();
    AzureCreateARMResourceParameterFile parametersFileBuilder = new AzureCreateARMResourceParameterFile();
    parametersFileBuilder.setStore(templateStore);
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(templateStore);
    azureCreateStepInfo.setCreateStepConfiguration(
        AzureCreateARMResourceStepConfiguration.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .template(templateFile)
            .parameters(parametersFileBuilder)
            .scope(
                AzureCreateARMResourceStepScope.builder()
                    .spec(AzureSubscriptionSpec.builder().location(ParameterField.createValueField("foobar")).build())
                    .build())
            .build());
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters).hasMessageContaining("subscription can't be null");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoLocationAtSubLevel() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .build())
            .build();

    AzureCreateARMResourceParameterFile parametersFileBuilder = new AzureCreateARMResourceParameterFile();
    parametersFileBuilder.setStore(templateStore);
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(templateStore);
    azureCreateStepInfo.setCreateStepConfiguration(
        AzureCreateARMResourceStepConfiguration.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .template(templateFile)
            .parameters(parametersFileBuilder)
            .scope(
                AzureCreateARMResourceStepScope.builder()
                    .spec(
                        AzureSubscriptionSpec.builder().subscription(ParameterField.createValueField("foobar")).build())
                    .build())
            .build());
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters)
        .hasMessageContaining("deploymentDataLocation can't be null");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoManagementAtManagementLevel() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .build())
            .build();
    AzureCreateARMResourceParameterFile parametersFileBuilder = new AzureCreateARMResourceParameterFile();
    parametersFileBuilder.setStore(templateStore);
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(templateStore);
    azureCreateStepInfo.setCreateStepConfiguration(
        AzureCreateARMResourceStepConfiguration.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .parameters(parametersFileBuilder)
            .template(templateFile)
            .scope(AzureCreateARMResourceStepScope.builder()
                       .spec(AzureManagementSpec.builder().location(ParameterField.createValueField("foobar")).build())
                       .build())
            .build());
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters)
        .hasMessageContaining("managementGroupId can't be null");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoLocationAtManagementLevel() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .build())
            .build();
    AzureCreateARMResourceParameterFile parametersFileBuilder = new AzureCreateARMResourceParameterFile();
    parametersFileBuilder.setStore(templateStore);
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(templateStore);
    azureCreateStepInfo.setCreateStepConfiguration(
        AzureCreateARMResourceStepConfiguration.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .template(templateFile)
            .parameters(parametersFileBuilder)
            .scope(AzureCreateARMResourceStepScope.builder()
                       .spec(AzureManagementSpec.builder()
                                 .managementGroupId(ParameterField.createValueField("foobar"))
                                 .build())
                       .build())
            .build());
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters)
        .hasMessageContaining("deploymentDataLocation can't be null");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoLocationAtTenantLevel() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    StoreConfigWrapper templateStore =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder()
                      .paths(ParameterField.createValueField(new ArrayList<>(Collections.singletonList("foobar"))))
                      .connectorRef(ParameterField.createValueField("template-connector-ref"))
                      .build())
            .build();
    AzureCreateARMResourceParameterFile parametersFileBuilder = new AzureCreateARMResourceParameterFile();
    parametersFileBuilder.setStore(templateStore);
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(templateStore);
    azureCreateStepInfo.setCreateStepConfiguration(
        AzureCreateARMResourceStepConfiguration.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .template(templateFile)
            .parameters(parametersFileBuilder)
            .scope(AzureCreateARMResourceStepScope.builder().spec(AzureTenantSpec.builder().build()).build())
            .build());
    assertThatThrownBy(azureCreateStepInfo::validateSpecParameters)
        .hasMessageContaining("deploymentDataLocation can't be null");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractConnectorRefForARM() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    AzureTemplateFile templateFile = new AzureTemplateFile();
    AzureCreateARMResourceParameterFile parameterFileBuilder = new AzureCreateARMResourceParameterFile();
    StoreConfigWrapper fileStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(
                GithubStore.builder().connectorRef(ParameterField.createValueField("parameters-connector-ref")).build())
            .build();
    StoreConfigWrapper configStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder().connectorRef(ParameterField.createValueField("template-connector-ref")).build())
            .build();
    parameterFileBuilder.setStore(fileStoreConfigWrapper);
    templateFile.setStore(configStoreConfigWrapper);
    azureCreateStepInfo.setCreateStepConfiguration(AzureCreateARMResourceStepConfiguration.builder()
                                                       .template(templateFile)
                                                       .parameters(parameterFileBuilder)
                                                       .connectorRef(ParameterField.createValueField("azConnectorRef"))
                                                       .build());
    Map<String, ParameterField<String>> parameterFieldMap = azureCreateStepInfo.extractConnectorRefs();
    assertThat(parameterFieldMap.size()).isEqualTo(3);
    assertThat(parameterFieldMap.get("configuration.spec.connectorRef").getValue()).isEqualTo("azConnectorRef");
    assertThat(parameterFieldMap.get("configuration.spec.templateFile.store.spec.connectorRef").getValue())
        .isEqualTo("template-connector-ref");
    assertThat(parameterFieldMap.get("configuration.spec.parameters.store.spec.connectorRef").getValue())
        .isEqualTo("parameters-connector-ref");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractConnectorRefForBP() {
    AzureCreateBPStepInfo azureCreateStepInfo = new AzureCreateBPStepInfo();
    AzureTemplateFile templateFile = new AzureTemplateFile();
    StoreConfigWrapper configStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder().connectorRef(ParameterField.createValueField("template-connector-ref")).build())
            .build();
    templateFile.setStore(configStoreConfigWrapper);
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
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    String response = azureCreateStepInfo.getFacilitatorType();
    assertThat(response).isEqualTo("TASK_CHAIN");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetStepType() {
    AzureCreateARMResourceStepInfo azureCreateStepInfo = new AzureCreateARMResourceStepInfo();
    StepType response = azureCreateStepInfo.getStepType();
    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo("AzureCreateARMResource");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExtractRefs() {
    AzureCreateARMResourceStepInfo azureCreateARMResourceStepInfo = new AzureCreateARMResourceStepInfo();
    AzureTemplateFile templateFile = new AzureTemplateFile();
    StoreConfigWrapper configStoreConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(HarnessStore.builder().files(ParameterField.createValueField(List.of("/script.sh"))).build())
            .build();
    templateFile.setStore(configStoreConfigWrapper);
    azureCreateARMResourceStepInfo.setCreateStepConfiguration(
        AzureCreateARMResourceStepConfiguration.builder()
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("azConnectorRef"))
            .build());
    Map<String, ParameterField<List<String>>> fileMap;
    fileMap = azureCreateARMResourceStepInfo.extractFileRefs();
    assertThat(fileMap.get("configuration.template.store.spec.files").getValue().size()).isEqualTo(1);
    assertThat(fileMap.get("configuration.template.store.spec.files").getValue().get(0)).isEqualTo("/script.sh");
  }
}
