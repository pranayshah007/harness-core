/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.wait;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.execution.Status;
import io.harness.repositories.WaitStepRepository;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.wait.WaitStepInstance;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import io.dropwizard.util.Sets;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class WaitStepServiceImplTest extends OrchestrationStepsTestBase {
  @Mock WaitNotifyEngine waitNotifyEngine;
  @Mock WaitStepRepository waitStepRepositoryMock;
  @Inject WaitStepRepository waitStepRepository;
  @Spy @InjectMocks WaitStepServiceImpl waitStepServiceImpl;
  @Mock PlanExecutionService planExecutionService;
  String nodeExecutionId;
  String correlationId;
  WaitStepInstance waitStepInstance;

  @Before
  public void setup() {
    nodeExecutionId = generateUuid();
    correlationId = generateUuid();
    waitStepInstance = WaitStepInstance.builder().waitStepInstanceId(correlationId).build();
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testSave() {
    WaitStepInstance waitStepInstance = WaitStepInstance.builder().build();
    waitStepServiceImpl.save(waitStepInstance);
    verify(waitStepRepositoryMock, times(1)).save(waitStepInstance);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testFindByNodeExecutionId() {
    waitStepServiceImpl.findByNodeExecutionId(nodeExecutionId);
    verify(waitStepRepositoryMock, times(1)).findByNodeExecutionId(nodeExecutionId);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testMarkAsFailOrSuccess() {
    doReturn(Optional.of(waitStepInstance)).when(waitStepRepositoryMock).findByNodeExecutionId(nodeExecutionId);
    when(planExecutionService.updateStatus("", Status.FAILED)).thenReturn(PlanExecution.builder().build());
    waitStepServiceImpl.markAsFailOrSuccess("", nodeExecutionId, WaitStepAction.MARK_AS_FAIL);
    verify(waitNotifyEngine, times(1))
        .doneWith(correlationId, WaitStepResponseData.builder().action(WaitStepAction.MARK_AS_FAIL).build());
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetWaitStepExecutionDetails() {
    doReturn(Optional.of(waitStepInstance)).when(waitStepRepositoryMock).findByNodeExecutionId(nodeExecutionId);
    WaitStepInstance waitStepInstance1 = waitStepServiceImpl.getWaitStepExecutionDetails(nodeExecutionId);
    assertEquals(waitStepInstance1, waitStepInstance);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testUpdatePlanStatus() {
    verify(planExecutionService, times(0)).updateStatus("plan", Status.FAILED);
  }

  @Test
  @Owner(developers = OwnerRule.ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteWaitStepInstances() {
    on(waitStepServiceImpl).set("waitStepRepository", waitStepRepository);

    String nodeExecutionId = generateUuid();
    String correlationId = generateUuid();
    WaitStepInstance waitStepInstance =
        WaitStepInstance.builder().waitStepInstanceId(correlationId).nodeExecutionId(nodeExecutionId).build();

    waitStepServiceImpl.save(waitStepInstance);
    waitStepServiceImpl.deleteWaitStepInstancesForGivenNodeExecutionIds(Sets.of(nodeExecutionId));

    Optional<WaitStepInstance> waitStepInstanceOptional = waitStepServiceImpl.findByNodeExecutionId(nodeExecutionId);
    assertThat(waitStepInstanceOptional).isEmpty();
  }
}
