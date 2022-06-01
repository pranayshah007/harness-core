/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class CloudformationRollbackStepInfoTest extends CategoryTest {
  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExtractConnectorRefs() {
    CloudformationRollbackStepInfo cloudformationRollbackStepInfo = new CloudformationRollbackStepInfo();
    assertThat(cloudformationRollbackStepInfo.extractConnectorRefs().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetStepType() {
    CloudformationRollbackStepInfo cloudformationRollbackStepInfo = new CloudformationRollbackStepInfo();
    assertThat(cloudformationRollbackStepInfo.getStepType()).isEqualTo(CloudformationRollbackStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    CloudformationRollbackStepInfo cloudformationRollbackStepInfo = new CloudformationRollbackStepInfo();
    assertThat(cloudformationRollbackStepInfo.getFacilitatorType()).isEqualTo(OrchestrationFacilitatorType.TASK);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    ParameterField<List<TaskSelectorYaml>> delegateSelectors = ParameterField.createValueField(new ArrayList<>());
    CloudformationRollbackStepConfiguration cloudformationStepConfiguration =
        CloudformationRollbackStepConfiguration.builder()
            .provisionerIdentifier(ParameterField.createValueField("provisionerIdentifier"))
            .build();
    CloudformationRollbackStepInfo cloudformationRollbackStepInfo =
        CloudformationRollbackStepInfo.infoBuilder()
            .cloudformationStepConfiguration(cloudformationStepConfiguration)
            .delegateSelectors(delegateSelectors)
            .build();

    CloudformationRollbackStepParameters parameters =
        (CloudformationRollbackStepParameters) cloudformationRollbackStepInfo.getSpecParameters();

    assertThat(parameters.getDelegateSelectors()).isEqualTo(delegateSelectors);
    assertThat(parameters.getConfiguration()).isEqualTo(cloudformationStepConfiguration);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetSpecParametersValidationFails() {
    ParameterField<List<TaskSelectorYaml>> delegateSelectors = ParameterField.createValueField(new ArrayList<>());
    CloudformationRollbackStepConfiguration cloudformationStepConfiguration =
        CloudformationRollbackStepConfiguration.builder().build();
    CloudformationRollbackStepInfo cloudformationRollbackStepInfo =
        CloudformationRollbackStepInfo.infoBuilder()
            .cloudformationStepConfiguration(cloudformationStepConfiguration)
            .delegateSelectors(delegateSelectors)
            .build();

    Assertions.assertThatThrownBy(cloudformationRollbackStepInfo::getSpecParameters)
        .hasMessageContaining("Provisioner identifier is null");
  }
}
