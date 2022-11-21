/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class KeyValuesCriteriaSpecTest extends OrchestrationStepsTestBase {
  KeyValuesCriteriaSpec keyValuesCriteriaSpec = KeyValuesCriteriaSpec.builder()
                                                    .conditions(new ArrayList<>())
                                                    .matchAnyCondition(ParameterField.createValueField(true))
                                                    .build();
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetType() {
    assertEquals(keyValuesCriteriaSpec.getType(), CriteriaSpecType.KEY_VALUES);
  }

  // Testing the method toCriteriaSpecDTO which converts keyValuesCriteriaSpec into keyValuesCriteriaSpecDTO
  // Also testing the edge cases like when conditions field is null and skipEmpty is false, an error should be thrown
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToCriteriaSpecDTO() {
    assertThatThrownBy(() -> keyValuesCriteriaSpec.toCriteriaSpecDTO(false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("At least 1 condition is required in KeyValues criteria");
    KeyValuesCriteriaSpecDTO keyValuesCriteriaSpecDTO =
        (KeyValuesCriteriaSpecDTO) keyValuesCriteriaSpec.toCriteriaSpecDTO(true);
    assertTrue(keyValuesCriteriaSpecDTO.isMatchAnyCondition());
    assertEquals(keyValuesCriteriaSpecDTO.getConditions().size(), 0);
    Condition condition = Condition.builder().key("key").value(ParameterField.createValueField("value")).build();
    KeyValuesCriteriaSpec keyValuesCriteriaSpecWithConditions =
        KeyValuesCriteriaSpec.builder()
            .matchAnyCondition(ParameterField.createValueField(false))
            .conditions(List.of(condition))
            .build();
    KeyValuesCriteriaSpecDTO keyValuesCriteriaSpecDTOWithConditions =
        (KeyValuesCriteriaSpecDTO) keyValuesCriteriaSpecWithConditions.toCriteriaSpecDTO(false);
    assertFalse(keyValuesCriteriaSpecDTOWithConditions.isMatchAnyCondition());
    assertEquals(keyValuesCriteriaSpecDTOWithConditions.getConditions().get(0).getKey(), "key");
  }
}
