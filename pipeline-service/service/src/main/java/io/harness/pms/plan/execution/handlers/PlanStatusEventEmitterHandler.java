/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.observers.PlanStatusUpdateObserver;
import io.harness.observer.AsyncInformObserver;
import io.harness.observer.Subject;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.notification.orchestration.observers.NotificationObserver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.Getter;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PlanStatusEventEmitterHandler implements AsyncInformObserver, PlanStatusUpdateObserver {
  private final ExecutorService executorService;
  private final PlanExecutionService planExecutionService;

  @Getter private final Subject<NotificationObserver> planExecutionSubject = new Subject<>();

  @Inject
  public PlanStatusEventEmitterHandler(
      @Named("PipelineExecutorService") ExecutorService executorService, PlanExecutionService planExecutionService) {
    this.executorService = executorService;
    this.planExecutionService = planExecutionService;
  }

  @Override
  public void onPlanStatusUpdate(Ambiance ambiance) {
    Status status = planExecutionService.get(ambiance.getPlanExecutionId()).getStatus();
    if (status == Status.SUCCEEDED || status == Status.IGNORE_FAILED) {
      planExecutionSubject.fireInform(NotificationObserver::onSuccess, ambiance);
    } else if (StatusUtils.brokeStatuses().contains(status)) {
      planExecutionSubject.fireInform(NotificationObserver::onFailure, ambiance);
    } else if (status == Status.PAUSED) {
      planExecutionSubject.fireInform(NotificationObserver::onPause, ambiance);
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
