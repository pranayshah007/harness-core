/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionInputInstance;
import io.harness.repositories.ExecutionInputRepository;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionInputServiceImplTest extends OrchestrationTestBase {
  @Mock private ExecutionInputRepository executionInputRepository;
  @InjectMocks private ExecutionInputServiceImpl inputService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetExecutionInputInstance() {
    String nodeExecutionId = "nodeExecutionId";
    String template = "template";
    String inputInstanceId = "inputInstanceId";
    doReturn(Optional.of(ExecutionInputInstance.builder()
                             .inputInstanceId(inputInstanceId)
                             .nodeExecutionId(nodeExecutionId)
                             .template(template)
                             .build()))
        .when(executionInputRepository)
        .findByNodeExecutionId(nodeExecutionId);
    ExecutionInputInstance inputInstance = inputService.getExecutionInputInstance(nodeExecutionId);
    assertEquals(inputInstance.getNodeExecutionId(), nodeExecutionId);
    assertEquals(inputInstance.getInputInstanceId(), inputInstanceId);
    assertEquals(inputInstance.getTemplate(), template);

    doReturn(Optional.empty()).when(executionInputRepository).findByNodeExecutionId("differentNodeExecutionId");
    assertThatThrownBy(() -> inputService.getExecutionInputInstance("differentNodeExecutionId"))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testContinueExecution() {
    // TODO(BRIJESH): will write after completing the implementation of the method.
  }
}
