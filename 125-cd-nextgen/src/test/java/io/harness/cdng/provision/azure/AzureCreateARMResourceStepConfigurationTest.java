/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.GithubStore.GithubStoreBuilder;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStore.HarnessStoreBuilder;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class AzureCreateARMResourceStepConfigurationTest extends CategoryTest {
  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testInNumberOfFilesIsInvalidForTemplate() {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(buildStoreConfig("git", 2, false));
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    parameterFile.setStore(buildStoreConfig("git", 1, false));
    AzureCreateARMResourceStepConfiguration azureCreateARMResourceStepConfiguration =
        AzureCreateARMResourceStepConfiguration.builder()
            .parameters(parameterFile)
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("parameters-ConnectorDisconnectHandler-ref"))
            .scope(createScope())
            .build();
    assertThatThrownBy(azureCreateARMResourceStepConfiguration::validateParams)
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testWithFilesAndSecretsForTemplate() {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(StoreConfigWrapper.builder()
                              .spec(HarnessStore.builder()
                                        .files(ParameterField.createValueField(Arrays.asList("test")))
                                        .secretFiles(ParameterField.createValueField(Arrays.asList("test")))
                                        .build())
                              .build());
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    parameterFile.setStore(buildStoreConfig("git", 1, false));
    AzureCreateARMResourceStepConfiguration azureCreateARMResourceStepConfiguration =
        AzureCreateARMResourceStepConfiguration.builder()
            .parameters(parameterFile)
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("parameters-ConnectorDisconnectHandler-ref"))
            .scope(createScope())
            .build();

    assertThatThrownBy(azureCreateARMResourceStepConfiguration::validateParams)
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Only one one files / secretFiles should be set for template");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testWithHarnessStoreButNoFilesOrSecretsForTemplate() {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(StoreConfigWrapper.builder()
                              .spec(HarnessStore.builder()
                                        .files(ParameterField.createValueField(Collections.emptyList()))
                                        .secretFiles(ParameterField.createValueField(Collections.emptyList()))
                                        .build())
                              .build());
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    parameterFile.setStore(buildStoreConfig("git", 1, false));
    AzureCreateARMResourceStepConfiguration azureCreateARMResourceStepConfiguration =
        AzureCreateARMResourceStepConfiguration.builder()
            .parameters(parameterFile)
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("parameters-ConnectorDisconnectHandler-ref"))
            .scope(createScope())
            .build();

    assertThatThrownBy(azureCreateARMResourceStepConfiguration::validateParams)
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("At least one one files / secretFiles should be set for template");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testWithFilesAndSecretsForParameters() {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(buildStoreConfig("git", 1, false));
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    parameterFile.setStore(StoreConfigWrapper.builder()
                               .spec(HarnessStore.builder()
                                         .files(ParameterField.createValueField(Arrays.asList("test")))
                                         .secretFiles(ParameterField.createValueField(Arrays.asList("test")))
                                         .build())
                               .build());
    AzureCreateARMResourceStepConfiguration azureCreateARMResourceStepConfiguration =
        AzureCreateARMResourceStepConfiguration.builder()
            .parameters(parameterFile)
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("parameters-ConnectorDisconnectHandler-ref"))
            .scope(createScope())
            .build();

    assertThatThrownBy(azureCreateARMResourceStepConfiguration::validateParams)
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Only one one files / secretFiles should be set for parameters");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testWithHarnessStoreButNoFilesOrSecretsForParameters() {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(buildStoreConfig("git", 1, false));
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    parameterFile.setStore(StoreConfigWrapper.builder()
                               .spec(HarnessStore.builder()
                                         .files(ParameterField.createValueField(Collections.emptyList()))
                                         .secretFiles(ParameterField.createValueField(Collections.emptyList()))
                                         .build())
                               .build());
    AzureCreateARMResourceStepConfiguration azureCreateARMResourceStepConfiguration =
        AzureCreateARMResourceStepConfiguration.builder()
            .parameters(parameterFile)
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("parameters-ConnectorDisconnectHandler-ref"))
            .scope(createScope())
            .build();

    assertThatThrownBy(azureCreateARMResourceStepConfiguration::validateParams)
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("At least one one files / secretFiles should be set for parameters");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testInNumberOfFilesIsInvalidForParameters() {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(buildStoreConfig("git", 1, false));
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    parameterFile.setStore(buildStoreConfig("git", 2, false));
    AzureCreateARMResourceStepConfiguration azureCreateARMResourceStepConfiguration =
        AzureCreateARMResourceStepConfiguration.builder()
            .parameters(parameterFile)
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("parameters-ConnectorDisconnectHandler-ref"))
            .scope(createScope())
            .build();
    assertThatThrownBy(azureCreateARMResourceStepConfiguration::validateParams)
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testInNumberOfFilesIsValidForParameters() {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(buildStoreConfig("git", 1, false));
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    parameterFile.setStore(buildStoreConfig("git", 1, false));
    AzureCreateARMResourceStepConfiguration azureCreateARMResourceStepConfiguration =
        AzureCreateARMResourceStepConfiguration.builder()
            .parameters(parameterFile)
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("parameters-ConnectorDisconnectHandler-ref"))
            .scope(createScope())
            .build();
    assertDoesNotThrow(azureCreateARMResourceStepConfiguration::validateParams);

    AzureTemplateFile templateFile2 = new AzureTemplateFile();
    templateFile2.setStore(buildStoreConfig("git", 1, false));
    AzureCreateARMResourceParameterFile parameterFile2 = new AzureCreateARMResourceParameterFile();
    parameterFile2.setStore(buildStoreConfig("git", 0, false));
    AzureCreateARMResourceStepConfiguration azureCreateARMResourceStepConfiguration2 =
        AzureCreateARMResourceStepConfiguration.builder()
            .parameters(parameterFile2)
            .template(templateFile2)
            .connectorRef(ParameterField.createValueField("parameters-ConnectorDisconnectHandler-ref"))
            .scope(createScope())
            .build();
    assertDoesNotThrow(azureCreateARMResourceStepConfiguration2::validateParams);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testInNumberOfFilesIsInvalidForTemplateHarness() {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(buildStoreConfig("harness", 2, false));
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    parameterFile.setStore(buildStoreConfig("harness", 1, false));
    AzureCreateARMResourceStepConfiguration azureCreateARMResourceStepConfiguration =
        AzureCreateARMResourceStepConfiguration.builder()
            .parameters(parameterFile)
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("parameters-ConnectorDisconnectHandler-ref"))
            .scope(createScope())
            .build();
    assertThatThrownBy(azureCreateARMResourceStepConfiguration::validateParams)
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testInNumberOfFilesIsInvalidForTemplateHarnessWithSecretFiles() {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(buildStoreConfig("harness", 2, true));
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    parameterFile.setStore(buildStoreConfig("harness", 1, false));
    AzureCreateARMResourceStepConfiguration azureCreateARMResourceStepConfiguration =
        AzureCreateARMResourceStepConfiguration.builder()
            .parameters(parameterFile)
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("parameters-ConnectorDisconnectHandler-ref"))
            .scope(createScope())
            .build();
    assertThatThrownBy(azureCreateARMResourceStepConfiguration::validateParams)
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testInNumberOfFilesIsValidForTemplateHarnessWithSecretFiles() {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(buildStoreConfig("harness", 1, true));
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    parameterFile.setStore(buildStoreConfig("harness", 1, true));
    AzureCreateARMResourceStepConfiguration azureCreateARMResourceStepConfiguration =
        AzureCreateARMResourceStepConfiguration.builder()
            .parameters(parameterFile)
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("parameters-ConnectorDisconnectHandler-ref"))
            .scope(createScope())
            .build();
    assertDoesNotThrow(azureCreateARMResourceStepConfiguration::validateParams);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testInNumberOfFilesIsValidForTemplateHarnessWithFiles() {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(buildStoreConfig("harness", 1, false));
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    parameterFile.setStore(buildStoreConfig("harness", 1, false));
    AzureCreateARMResourceStepConfiguration azureCreateARMResourceStepConfiguration =
        AzureCreateARMResourceStepConfiguration.builder()
            .parameters(parameterFile)
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("parameters-ConnectorDisconnectHandler-ref"))
            .scope(createScope())
            .build();
    assertDoesNotThrow(azureCreateARMResourceStepConfiguration::validateParams);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testInNumberOfFilesIsValidForTemplateHarnessWithMixFiles() {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(buildStoreConfig("harness", 1, false));
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    parameterFile.setStore(buildStoreConfig("harness", 1, true));
    AzureCreateARMResourceStepConfiguration azureCreateARMResourceStepConfiguration =
        AzureCreateARMResourceStepConfiguration.builder()
            .parameters(parameterFile)
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("parameters-ConnectorDisconnectHandler-ref"))
            .scope(createScope())
            .build();
    assertDoesNotThrow(azureCreateARMResourceStepConfiguration::validateParams);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testInNumberOfFilesIsValidForTemplateHarnessWithNoParameters() {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    templateFile.setStore(buildStoreConfig("harness", 1, false));
    AzureCreateARMResourceStepConfiguration azureCreateARMResourceStepConfiguration =
        AzureCreateARMResourceStepConfiguration.builder()
            .parameters(null)
            .template(templateFile)
            .connectorRef(ParameterField.createValueField("parameters-ConnectorDisconnectHandler-ref"))
            .scope(createScope())
            .build();
    assertDoesNotThrow(azureCreateARMResourceStepConfiguration::validateParams);
  }

  private StoreConfigWrapper buildStoreConfig(String type, int numberOfFiles, boolean isSecret) {
    StoreConfigWrapper storeConfigWrapper;
    if (Objects.equals(type, "harness")) {
      HarnessStoreBuilder builder = HarnessStore.builder();
      if (isSecret) {
        builder.secretFiles(ParameterField.createValueField(createNumberOfFiles(numberOfFiles))).build();
      } else {
        builder.files(ParameterField.createValueField(createNumberOfFiles(numberOfFiles))).build();
      }
      storeConfigWrapper = StoreConfigWrapper.builder().spec(builder.build()).build();

    } else {
      GithubStoreBuilder builder = GithubStore.builder();
      builder.paths(ParameterField.createValueField(createNumberOfFiles(numberOfFiles)))
          .connectorRef(ParameterField.createValueField("parameters-ConnectorDisconnectHandler-ref"))
          .build();
      storeConfigWrapper = StoreConfigWrapper.builder().spec(builder.build()).build();
    }
    return storeConfigWrapper;
  }

  private List<String> createNumberOfFiles(int numberOfFiles) {
    int index = 0;
    List<String> files = new ArrayList<>();
    while (index < numberOfFiles) {
      files.add("project:/test" + index);
      index++;
    }
    return files;
  }

  private void assertDoesNotThrow(Runnable runnable) {
    try {
      runnable.run();
    } catch (Exception ex) {
      Assert.fail();
    }
  }

  private AzureCreateARMResourceStepScope createScope() {
    return AzureCreateARMResourceStepScope.builder()
        .spec(AzureResourceGroupSpec.builder()
                  .subscription(ParameterField.createValueField("foobar"))
                  .resourceGroup(ParameterField.createValueField("bar"))
                  .mode(AzureARMResourceDeploymentMode.COMPLETE)
                  .build())
        .build();
  }
}
