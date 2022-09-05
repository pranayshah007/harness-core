/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.FERNANDOD;

import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlHandlerValidator.validatePhaseAndRollbackPhase;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.rule.Owner;

import software.wings.beans.WorkflowPhase;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class WorkflowYamlHandlerValidatorTest {
  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotEnforcePhaseAndRollbackValidationWhenBothAreMissing() {
    assertThatCode(() -> validatePhaseAndRollbackPhase(Collections.emptyMap(), Collections.emptyMap()))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldVerifyEveryPhaseHasRollbackPhase() {
    Map<String, WorkflowPhase> workflowPhaseMap = new HashMap<>();
    workflowPhaseMap.put("A", aWorkflowPhase().build());
    workflowPhaseMap.put("B", aWorkflowPhase().build());
    workflowPhaseMap.put("C", aWorkflowPhase().build());

    Map<String, WorkflowPhase> rollbackPhaseMap = new HashMap<>();
    rollbackPhaseMap.put(generateUuid(), aWorkflowPhase().phaseNameForRollback("B").build());

    assertThatCode(() -> validatePhaseAndRollbackPhase(workflowPhaseMap, rollbackPhaseMap))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Missing rollback phase for one or more phases: [A, C]");
  }
}
