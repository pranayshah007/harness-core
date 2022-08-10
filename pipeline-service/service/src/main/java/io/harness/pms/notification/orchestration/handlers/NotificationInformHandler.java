/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.orchestration.handlers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.logging.AutoLogContext;
import io.harness.notification.PipelineEventType;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.notification.NotificationHelper;
import io.harness.pms.notification.orchestration.observers.NotificationObserver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class NotificationInformHandler implements AsyncInformObserver, NotificationObserver, OrchestrationEndObserver {
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject NotificationHelper notificationHelper;

  @Override
  public void onSuccess(Ambiance ambiance) {
    try (AutoLogContext autoLogContext = AmbianceUtils.autoLogContext(ambiance)) {
      notificationHelper.sendNotification(ambiance, PipelineEventType.PIPELINE_SUCCESS, null, null);
    }
  }

  @Override
  public void onPause(Ambiance ambiance) {
    try (AutoLogContext autoLogContext = AmbianceUtils.autoLogContext(ambiance)) {
      notificationHelper.sendNotification(ambiance, PipelineEventType.PIPELINE_PAUSED, null, null);
    }
  }

  @Override
  public void onFailure(Ambiance ambiance) {
    try (AutoLogContext autoLogContext = AmbianceUtils.autoLogContext(ambiance)) {
      notificationHelper.sendNotification(ambiance, PipelineEventType.PIPELINE_FAILED, null, null);
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }

  @Override
  public void onEnd(Ambiance ambiance) {
    try (AutoLogContext autoLogContext = AmbianceUtils.autoLogContext(ambiance)) {
      notificationHelper.sendNotification(ambiance, PipelineEventType.PIPELINE_END, null, null);
    }
  }
}
