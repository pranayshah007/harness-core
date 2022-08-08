/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class CloudformationDeleteStackStepInfoTest extends CategoryTest {
  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateParams() {
    CloudformationDeleteStackStepInfo cloudformationDeleteStackStepInfo = new CloudformationDeleteStackStepInfo();
    Assertions.assertThatThrownBy(cloudformationDeleteStackStepInfo::validateSpecParameters)
        .hasMessageContaining("CloudformationStepConfiguration is null");
    cloudformationDeleteStackStepInfo.setCloudformationStepConfiguration(
        CloudformationDeleteStackStepConfiguration.builder().build());
    cloudformationDeleteStackStepInfo.validateSpecParameters();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractConnectorRef() {
    CloudformationDeleteStackStepInfo cloudformationDeleteStackStepInfo = new CloudformationDeleteStackStepInfo();
    cloudformationDeleteStackStepInfo.setCloudformationStepConfiguration(
        CloudformationDeleteStackStepConfiguration.builder()
            .type(CloudformationDeleteStackStepConfigurationTypes.Inline)
            .spec(InlineCloudformationDeleteStackStepConfiguration.builder()
                      .connectorRef(ParameterField.createValueField("connectorRef"))
                      .build())
            .build());
    Map<String, ParameterField<String>> response = cloudformationDeleteStackStepInfo.extractConnectorRefs();
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.get("configuration.spec.connectorRef").getValue()).isEqualTo("connectorRef");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    CloudformationDeleteStackStepInfo cloudformationDeleteStackStepInfo = new CloudformationDeleteStackStepInfo();
    cloudformationDeleteStackStepInfo.setCloudformationStepConfiguration(
        CloudformationDeleteStackStepConfiguration.builder()
            .type(CloudformationDeleteStackStepConfigurationTypes.Inline)
            .spec(InlineCloudformationDeleteStackStepConfiguration.builder()
                      .connectorRef(ParameterField.createValueField("connectorRef"))
                      .build())
            .build());
    SpecParameters specParameters = cloudformationDeleteStackStepInfo.getSpecParameters();
    CloudformationDeleteStackStepParameters stepParams = (CloudformationDeleteStackStepParameters) specParameters;
    assertThat(stepParams).isNotNull();
    assertThat(getParameterFieldValue(
                   ((InlineCloudformationDeleteStackStepConfiguration) stepParams.getConfiguration().getSpec())
                       .getConnectorRef()))
        .isEqualTo("connectorRef");
    assertThat(stepParams.getConfiguration().getType())
        .isEqualTo(CloudformationDeleteStackStepConfigurationTypes.Inline);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    CloudformationDeleteStackStepInfo cloudformationDeleteStackStepInfo = new CloudformationDeleteStackStepInfo();
    String response = cloudformationDeleteStackStepInfo.getFacilitatorType();
    assertThat(response).isEqualTo("TASK");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetStepType() {
    CloudformationDeleteStackStepInfo cloudformationDeleteStackStepInfo = new CloudformationDeleteStackStepInfo();
    StepType response = cloudformationDeleteStackStepInfo.getStepType();
    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo("DeleteStack");
  }
}
