/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class CloudformationCreateStackStepInfoTest extends CategoryTest {
  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParams() {
    CloudformationCreateStackStepInfo cloudformationCreateStackStepInfo = new CloudformationCreateStackStepInfo();
    cloudformationCreateStackStepInfo.setCloudformationStepConfiguration(
        CloudformationCreateStackStepConfiguration.builder()
            .region(ParameterField.createValueField("test"))
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .build());
    cloudformationCreateStackStepInfo.validateSpecParameters();
    assertThat(cloudformationCreateStackStepInfo.getCloudformationStepConfiguration().getRegion().getValue())
        .isEqualTo("test");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoSpecParameters() {
    CloudformationCreateStackStepInfo cloudformationCreateStackStepInfo = new CloudformationCreateStackStepInfo();
    Assertions.assertThatThrownBy(cloudformationCreateStackStepInfo::validateSpecParameters)
        .hasMessageContaining("Cloudformation Step configuration is null");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoRegion() {
    CloudformationCreateStackStepInfo cloudformationCreateStackStepInfo = new CloudformationCreateStackStepInfo();
    cloudformationCreateStackStepInfo.setCloudformationStepConfiguration(
        CloudformationCreateStackStepConfiguration.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .build());
    Assertions.assertThatThrownBy(cloudformationCreateStackStepInfo::validateSpecParameters)
        .hasMessageContaining("AWS region is null");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParamsWithNoConnectorRef() {
    CloudformationCreateStackStepInfo cloudformationCreateStackStepInfo = new CloudformationCreateStackStepInfo();
    cloudformationCreateStackStepInfo.setCloudformationStepConfiguration(
        CloudformationCreateStackStepConfiguration.builder().region(ParameterField.createValueField("test")).build());
    Assertions.assertThatThrownBy(cloudformationCreateStackStepInfo::validateSpecParameters)
        .hasMessageContaining("AWS connector is null");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractConnectorRef() {
    CloudformationCreateStackStepInfo cloudformationCreateStackStepInfo = new CloudformationCreateStackStepInfo();
    RemoteCloudformationTemplateFileSpec templateFileSpec = new RemoteCloudformationTemplateFileSpec();
    CloudformationParametersFileSpec parametersFileSpec = new CloudformationParametersFileSpec();
    RemoteCloudformationTagsFileSpec tagsFileSpec = new RemoteCloudformationTagsFileSpec();

    StoreConfigWrapper storeConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder().connectorRef(ParameterField.createValueField("test-connector")).build())
            .build();
    parametersFileSpec.setStore(storeConfigWrapper);
    parametersFileSpec.setIdentifier("test-identifier");
    templateFileSpec.setStore(storeConfigWrapper);
    tagsFileSpec.setStore(storeConfigWrapper);
    cloudformationCreateStackStepInfo.setCloudformationStepConfiguration(
        CloudformationCreateStackStepConfiguration.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .parametersFilesSpecs(Collections.singletonList(parametersFileSpec))
            .tags(CloudformationTags.builder().spec(tagsFileSpec).build())
            .templateFile(CloudformationTemplateFile.builder()
                              .spec(templateFileSpec)
                              .type(CloudformationTemplateFileTypes.Remote)
                              .build())
            .build());

    Map<String, ParameterField<String>> response = cloudformationCreateStackStepInfo.extractConnectorRefs();
    assertThat(response.size()).isEqualTo(4);
    assertThat(response.get("configuration.connectorRef").getValue()).isEqualTo("connectorRef");
    assertThat(response.get("configuration.spec.templateFile.store.spec.connectorRef").getValue())
        .isEqualTo("test-connector");
    assertThat(response.get("configuration.spec.parameters.test-identifier.store.spec.connectorRef").getValue())
        .isEqualTo("test-connector");
    assertThat(response.get("configuration.spec.tags.store.spec.connectorRef").getValue()).isEqualTo("test-connector");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    CloudformationCreateStackStepInfo cloudformationCreateStackStepInfo = new CloudformationCreateStackStepInfo();
    RemoteCloudformationTemplateFileSpec templateFileSpec = new RemoteCloudformationTemplateFileSpec();
    CloudformationParametersFileSpec parametersFileSpec = new CloudformationParametersFileSpec();

    StoreConfigWrapper storeConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(GithubStore.builder().connectorRef(ParameterField.createValueField("test-connector")).build())
            .build();
    parametersFileSpec.setStore(storeConfigWrapper);
    parametersFileSpec.setIdentifier("test-identifier");
    templateFileSpec.setStore(storeConfigWrapper);

    cloudformationCreateStackStepInfo.setCloudformationStepConfiguration(
        CloudformationCreateStackStepConfiguration.builder()
            .region(ParameterField.createValueField("test"))
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .parametersFilesSpecs(Collections.singletonList(parametersFileSpec))
            .templateFile(CloudformationTemplateFile.builder()
                              .spec(templateFileSpec)
                              .type(CloudformationTemplateFileTypes.Remote)
                              .build())
            .build());

    SpecParameters specParameters = cloudformationCreateStackStepInfo.getSpecParameters();
    CloudformationCreateStackStepParameters stepParams = (CloudformationCreateStackStepParameters) specParameters;
    assertThat(stepParams).isNotNull();
    assertThat(stepParams.getConfiguration().getConnectorRef().getValue()).isEqualTo("connectorRef");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    CloudformationCreateStackStepInfo cloudformationCreateStackStepInfo = new CloudformationCreateStackStepInfo();
    String response = cloudformationCreateStackStepInfo.getFacilitatorType();
    assertThat(response).isEqualTo("TASK_CHAIN");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetStepType() {
    CloudformationCreateStackStepInfo cloudformationCreateStackStepInfo = new CloudformationCreateStackStepInfo();
    StepType response = cloudformationCreateStackStepInfo.getStepType();
    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo("CreateStack");
  }
}
