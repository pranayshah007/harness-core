/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.mockito.Mockito.doReturn;

import io.harness.PipelineServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.notification.orchestration.observers.NotificationObserver;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class PlanStatusEventEmitterHandlerTest extends PipelineServiceTestBase {
  @Mock PlanExecutionService planExecutionService;
  @InjectMocks @Spy PlanStatusEventEmitterHandler planStatusEventEmitterHandler;

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testOnPlanStatusUpdate() {
    NotificationObserver onSuccessObserver = new OnSuccessObserver();
    NotificationObserver onPauseObserver = new OnPauseObserve();
    NotificationObserver onFailureObserver = new OnFailureObserver();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("planExecutionId").build();

    doReturn(PlanExecution.builder().status(Status.SUCCEEDED).build())
        .when(planExecutionService)
        .get("planExecutionId");
    planStatusEventEmitterHandler.getPlanExecutionSubject().register(onSuccessObserver);
    planStatusEventEmitterHandler.onPlanStatusUpdate(ambiance);

    doReturn(PlanExecution.builder().status(Status.IGNORE_FAILED).build())
        .when(planExecutionService)
        .get("planExecutionId");
    planStatusEventEmitterHandler.onPlanStatusUpdate(ambiance);

    planStatusEventEmitterHandler.getPlanExecutionSubject().unregister(onSuccessObserver);

    planStatusEventEmitterHandler.getPlanExecutionSubject().register(onPauseObserver);
    doReturn(PlanExecution.builder().status(Status.PAUSED).build()).when(planExecutionService).get("planExecutionId");
    planStatusEventEmitterHandler.onPlanStatusUpdate(ambiance);

    planStatusEventEmitterHandler.getPlanExecutionSubject().unregister(onPauseObserver);

    planStatusEventEmitterHandler.getPlanExecutionSubject().register(onFailureObserver);
    doReturn(PlanExecution.builder().status(Status.FAILED).build()).when(planExecutionService).get("planExecutionId");
    StatusUtils.brokeStatuses().forEach(status -> planStatusEventEmitterHandler.onPlanStatusUpdate(ambiance));
  }

  private static class OnSuccessObserver implements NotificationObserver {
    @Override
    public void onSuccess(Ambiance ambiance) {
      assert true;
    }

    @Override
    public void onPause(Ambiance ambiance) {
      assert false;
    }

    @Override
    public void onFailure(Ambiance ambiance) {
      assert false;
    }
  }
  private static class OnPauseObserve implements NotificationObserver {
    @Override
    public void onSuccess(Ambiance ambiance) {
      assert false;
    }

    @Override
    public void onPause(Ambiance ambiance) {
      assert true;
    }

    @Override
    public void onFailure(Ambiance ambiance) {
      assert false;
    }
  }

  private static class OnFailureObserver implements NotificationObserver {
    @Override
    public void onSuccess(Ambiance ambiance) {
      assert false;
    }

    @Override
    public void onPause(Ambiance ambiance) {
      assert false;
    }

    @Override
    public void onFailure(Ambiance ambiance) {
      assert true;
    }
  }
}
