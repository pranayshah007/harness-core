/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.TaskSelector;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.plugin.ContainerStepInfo;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ContainerSpecUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetDelegateSelectors() {
    ContainerStepInfo containerStepSpec = ContainerStepInfo.infoBuilder().build();
    assertThat(ContainerSpecUtils.getDelegateSelectors(containerStepSpec)).isEmpty();

    containerStepSpec.setDelegateSelectors(ParameterField.ofNull());
    assertThat(ContainerSpecUtils.getDelegateSelectors(containerStepSpec)).isEmpty();

    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("foo");
    taskSelectorYaml.setOrigin("step");
    containerStepSpec.setDelegateSelectors(ParameterField.createValueField(List.of(taskSelectorYaml)));
    assertThat(ContainerSpecUtils.getDelegateSelectors(containerStepSpec))
        .containsExactly(TaskSelector.newBuilder().setSelector("foo").setOrigin("step").build());

    containerStepSpec.setDelegateSelectors(ParameterField.createExpressionField(true, "<+unresolved>", null, false));
    assertThat(ContainerSpecUtils.getDelegateSelectors(containerStepSpec)).isEmpty();
  }
}
