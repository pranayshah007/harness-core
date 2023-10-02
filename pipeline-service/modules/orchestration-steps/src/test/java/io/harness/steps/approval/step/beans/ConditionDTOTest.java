/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans;

import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class ConditionDTOTest extends OrchestrationStepsTestBase {
  // Testing the method fromCondition which converts Condition into ConditionDTO
  // Also testing the edge cases like unresolved expression, blank key value etc.
  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFromCondition() {
    assertThat(ConditionDTO.fromCondition(null)).isNull();

    // value null
    Condition condition = Condition.builder().build();
    assertThatThrownBy(() -> ConditionDTO.fromCondition(condition))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Value can't be null");

    // key as blank string
    Condition keyBlankcondition = Condition.builder()
                                      .key("  ")
                                      .value(ParameterField.<String>builder().value("value").typeString(true).build())
                                      .build();
    assertThatThrownBy(() -> ConditionDTO.fromCondition(keyBlankcondition))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Key can't be empty");

    // value as unresolved string
    Condition unresolvedValuecondition = Condition.builder()
                                             .key("key")
                                             .value(ParameterField.<String>builder()
                                                        .expression(true)
                                                        .expressionValue("<+pipeline.variables.var7>")
                                                        .typeString(true)
                                                        .build())
                                             .build();
    assertThatThrownBy(() -> ConditionDTO.fromCondition(unresolvedValuecondition))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Key [key] has invalid value: [<+pipeline.variables.var7>]");

    // normal conversation
    Condition normalCondition = Condition.builder()
                                    .key("key")
                                    .operator(Operator.EQ)
                                    .value(ParameterField.<String>builder().value("value").typeString(true).build())
                                    .build();
    ConditionDTO conditionDTO = ConditionDTO.fromCondition(normalCondition);
    assertThat(conditionDTO.getKey()).isEqualTo("key");
    assertThat(conditionDTO.getValue()).isEqualTo("value");
    assertThat(conditionDTO.getOperator()).isEqualTo(Operator.EQ);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testToString() {
    Condition normalCondition = Condition.builder()
                                    .key("key")
                                    .operator(Operator.EQ)
                                    .value(ParameterField.<String>builder().value("value").typeString(true).build())
                                    .build();
    assertThat(normalCondition.toString())
        .isEqualTo(
            "Condition(key=key, value=ParameterField(expressionValue=null, expression=false, value=value, defaultValue=null, typeString=true, isExecutionInput=false, inputSetValidator=null, jsonResponseField=false, responseField=null), operator=equals)");
  }
}
