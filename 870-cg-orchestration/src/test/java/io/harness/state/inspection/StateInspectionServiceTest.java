/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.state.inspection;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.RAFAEL;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CgOrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StateInspectionServiceTest extends CgOrchestrationTestBase {
  @Inject private HPersistence persistence;
  @Inject private StateInspectionService stateInspectionService;

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldMerge() throws IOException {
    final String uuid = generateUuid();
    stateInspectionService.append(uuid, asList(DummyStateInspectionData.builder().value("dummy value").build()));

    StateInspection stateInspectionResult = stateInspectionService.get(uuid);
    assertThat(((DummyStateInspectionData) stateInspectionResult.getData().get("dummy")).getValue())
        .isEqualTo("dummy value");

    stateInspectionService.append(uuid, asList(DummiesStateInspectionData.builder().value("dummies value").build()));
    stateInspectionResult = stateInspectionService.get(uuid);
    assertThat(((DummiesStateInspectionData) stateInspectionResult.getData().get("dummies")).getValue())
        .isEqualTo("dummies value");
    assertThat(((DummyStateInspectionData) stateInspectionResult.getData().get("dummy")).getValue())
        .isEqualTo("dummy value");
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldRedactSecrets() {
    final String uuid = generateUuid();
    stateInspectionService.append(uuid,
        ExpressionVariableUsage.builder()
            .variables(List.of(ExpressionVariableUsage.Item.builder()
                                   .expression("k8sResources.manifests")
                                   .value(expression)
                                   .count(0)
                                   .build()))
            .build());
    StateInspection stateInspectionResult = stateInspectionService.get(uuid);
    ExpressionVariableUsage evu =
        (ExpressionVariableUsage) stateInspectionResult.getData().get("expressionVariableUsage");
    assertThat(evu.getVariables().get(0).getValue().contains("***"));
    assertThat(evu.getVariables().get(0).getValue()).isNotEqualTo(expression);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldntRedactSecrets() {
    final String uuid = generateUuid();
    stateInspectionService.append(uuid,
        ExpressionVariableUsage.builder()
            .variables(List.of(ExpressionVariableUsage.Item.builder()
                                   .expression("dummyexpression")
                                   .value(expression)
                                   .count(0)
                                   .build()))
            .build());
    StateInspection stateInspectionResult = stateInspectionService.get(uuid);
    ExpressionVariableUsage evu =
        (ExpressionVariableUsage) stateInspectionResult.getData().get("expressionVariableUsage");
    assertThat(evu.getVariables().get(0).getValue()).doesNotContain("***");
    assertThat(evu.getVariables().get(0).getValue()).isEqualTo(expression);
  }

  private final String expression =
      "---\n\napiVersion: v1\nkind: Secret\nmetadata: \n name: harness-example-prod\nstringData:\n key2: value2\n";
}
