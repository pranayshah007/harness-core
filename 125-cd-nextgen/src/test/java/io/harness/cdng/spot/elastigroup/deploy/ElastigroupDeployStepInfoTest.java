/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.spot.elastigroup.deploy;

import static io.harness.rule.OwnerRule.FILIP;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.capacity.Capacity;
import io.harness.cdng.common.capacity.CapacitySpecType;
import io.harness.cdng.common.capacity.CountCapacitySpec;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class ElastigroupDeployStepInfoTest extends CategoryTest {
  private final ElastigroupDeployStepInfo stepInfo =
      ElastigroupDeployStepInfo.infoBuilder()
          .delegateSelectors(ParameterField.createValueField(
              Arrays.asList(new TaskSelectorYaml("delegate-1"), new TaskSelectorYaml("delegate-2"))))
          .newService(Capacity.builder()
                          .type(CapacitySpecType.COUNT)
                          .spec(CountCapacitySpec.builder().count(ParameterField.createValueField(5)).build())
                          .build())
          .oldService(Capacity.builder()
                          .type(CapacitySpecType.COUNT)
                          .spec(CountCapacitySpec.builder().count(ParameterField.createValueField(2)).build())
                          .build())
          .build();

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testGetType() {
    assertThat(stepInfo.getStepType()).isEqualTo(ElastigroupDeployStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    assertThat(stepInfo.getFacilitatorType()).isEqualTo(OrchestrationFacilitatorType.TASK);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testGetDelegateSelectors() {
    assertThat(stepInfo.fetchDelegateSelectors().getValue())
        .extracting(TaskSelectorYaml::getDelegateSelectors)
        .hasSize(2)
        .containsExactlyInAnyOrder("delegate-1", "delegate-2");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    // when
    SpecParameters specParameters = stepInfo.getSpecParameters();

    // then
    assertThat(specParameters).isNotNull().isInstanceOf(ElastigroupDeployStepParameters.class);

    ElastigroupDeployStepParameters elastigroupDeployStepParameters = (ElastigroupDeployStepParameters) specParameters;

    assertThat(elastigroupDeployStepParameters.getDelegateSelectors().getValue())
        .extracting(TaskSelectorYaml::getDelegateSelectors)
        .hasSize(2)
        .containsExactlyInAnyOrder("delegate-1", "delegate-2");

    assertThat(elastigroupDeployStepParameters.getNewService())
        .isNotNull()
        .isEqualTo(Capacity.builder()
                       .type(CapacitySpecType.COUNT)
                       .spec(CountCapacitySpec.builder().count(ParameterField.createValueField(5)).build())
                       .build());

    assertThat(elastigroupDeployStepParameters.getOldService())
        .isNotNull()
        .isEqualTo(Capacity.builder()
                       .type(CapacitySpecType.COUNT)
                       .spec(CountCapacitySpec.builder().count(ParameterField.createValueField(2)).build())
                       .build());
  }
}
