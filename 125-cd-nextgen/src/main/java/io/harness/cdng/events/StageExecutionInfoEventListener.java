/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.events;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.eventsframework.consumer.Message;
import io.harness.ng.core.event.AbstractEntityCleanupListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
@Singleton
public class StageExecutionInfoEventListener extends AbstractEntityCleanupListener {
  private final StageExecutionInfoService stageExecutionInfoService;

  @Inject
  public StageExecutionInfoEventListener(StageExecutionInfoService stageExecutionInfoService) {
    this.stageExecutionInfoService = stageExecutionInfoService;
  }

  @Override
  public boolean handleMessage(Message message) {
    return super.cleanupAllScopes(message);
  }

  @Override
  public boolean processDeleteEvent(Scope scope) {
    try {
      stageExecutionInfoService.deleteAtAllScopes(scope);
    } catch (Exception e) {
      log.error("Could not process scope delete event for stage execution info. Exception", e);
      return false;
    }
    return true;
  }
}
