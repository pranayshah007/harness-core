/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.TaskSelector;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class TaskSelectorYamlTest extends CategoryTest {
  @Mock ParameterField parameterField;
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToTaskSelector() {
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("selectMe");
    TaskSelector taskSelector = TaskSelectorYaml.toTaskSelector(taskSelectorYaml);
    assertThat(taskSelector.getSelector()).isEqualTo("selectMe");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToTaskSelectorForList() {
    TaskSelectorYaml taskSelectorYaml0 = new TaskSelectorYaml("selectMe0");
    TaskSelectorYaml taskSelectorYaml1 = new TaskSelectorYaml("selectMe1");
    TaskSelectorYaml taskSelectorYaml2 = new TaskSelectorYaml("selectMe2");
    TaskSelectorYaml taskSelectorYaml3 = new TaskSelectorYaml("selectMe3");
    List<TaskSelectorYaml> taskSelectorYamlList =
        Arrays.asList(taskSelectorYaml0, taskSelectorYaml1, taskSelectorYaml2, taskSelectorYaml3);
    List<TaskSelector> taskSelectors = TaskSelectorYaml.toTaskSelector(taskSelectorYamlList);
    assertThat(taskSelectors).hasSize(4);
    for (int i = 0; i < 4; i++) {
      assertThat(taskSelectors.get(i).getSelector()).isEqualTo("selectMe" + i);
    }
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToSelector() {
    doReturn("abc").when(parameterField).getValue();
    assertThatThrownBy(() -> TaskSelectorYaml.toTaskSelector(parameterField))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The resolved value of Delegate Selectors abc is not a list");
    doReturn(List.of("abc")).when(parameterField).getValue();
    List<TaskSelector> taskSelectors = TaskSelectorYaml.toTaskSelector(parameterField);
    assertEquals(taskSelectors.size(), 1);
    assertEquals(taskSelectors.get(0).getSelector(), "abc");
    assertEquals(taskSelectors.get(0).getOrigin(), "default");
  }
}
